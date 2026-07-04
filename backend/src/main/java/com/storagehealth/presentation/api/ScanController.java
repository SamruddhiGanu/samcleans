package com.storagehealth.presentation.api;

import com.storagehealth.application.service.scanner.FileScanner;
import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing scan lifecycle endpoints.
 *
 * <pre>
 *  POST  /api/scan/start              — start a server-side scan (async)
 *  POST  /api/scan/submit-metadata    — accept browser File System Access API metadata
 *  GET   /api/scan/progress/{id}      — poll scan progress
 *  POST  /api/scan/cancel/{id}        — request cancellation
 *  GET   /api/scan/list               — paginated list of all sessions
 *  GET   /api/scan/{id}               — get a single session by ID
 * </pre>
 */
@RestController
@RequestMapping("/api/scan")
@Slf4j
public class ScanController {

    private final FileScanner           fileScanner;
    private final ScanSessionRepository scanSessionRepository;
    private final FileRepository        fileRepository;

    @Autowired
    public ScanController(FileScanner fileScanner,
                          ScanSessionRepository scanSessionRepository,
                          FileRepository fileRepository) {
        this.fileScanner           = fileScanner;
        this.scanSessionRepository = scanSessionRepository;
        this.fileRepository        = fileRepository;
    }

    // ---------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------

    /**
     * Persists a new {@link ScanSessionEntity} and starts the scan asynchronously.
     * Used when the backend itself can access the filesystem (e.g. local dev).
     */
    @PostMapping("/start")
    public ResponseEntity<ScanSessionDTO> startScan(@RequestBody ScanRequest request) {
        log.info("Start scan request for path: {}", request.getPath());

        String sessionName = (request.getName() != null && !request.getName().isBlank())
            ? request.getName()
            : "Scan " + LocalDateTime.now();

        ScanSessionEntity session = ScanSessionEntity.builder()
            .sessionName(sessionName)
            .scanPath(request.getPath())
            .status(ScanStatus.INITIATED)
            .build();

        ScanSessionEntity saved = scanSessionRepository.save(session);

        Thread.ofVirtual().name("scanner-" + saved.getId()).start(() -> {
            try {
                fileScanner.scanDirectory(request.getPath(), saved.getId());
            } catch (Exception e) {
                log.error("Async scan failed for session {}", saved.getId(), e);
            }
        });

        return ResponseEntity.ok(toDTO(saved));
    }

    /**
     * Accepts file metadata collected by the browser's File System Access API.
     * The browser enumerates files locally (no upload), then POSTs metadata here.
     * This is the primary scan endpoint when the app runs as a web service.
     *
     * <p>Request body:
     * <pre>{
     *   "sessionName": "My Documents",
     *   "rootPath": "C:/Users/Name/Documents",
     *   "files": [
     *     { "path": "report.pdf", "name": "report.pdf", "sizeBytes": 204800,
     *       "lastModifiedMs": 1720000000000, "mimeType": "application/pdf" },
     *     ...
     *   ]
     * }</pre>
     */
    @PostMapping("/submit-metadata")
    public ResponseEntity<ScanSessionDTO> submitBrowserMetadata(
            @RequestBody BrowserScanRequest request) {

        log.info("Browser metadata scan: {} files from '{}'",
            request.getFiles().size(), request.getRootPath());

        ScanSessionEntity session = ScanSessionEntity.builder()
            .sessionName(request.getSessionName() != null
                ? request.getSessionName() : "Web Scan " + LocalDateTime.now())
            .scanPath(request.getRootPath())
            .status(ScanStatus.IN_PROGRESS)
            .startTime(LocalDateTime.now())
            .build();

        session = scanSessionRepository.save(session);
        final ScanSessionEntity finalSession = session;

        List<BrowserFileMetadata> files = request.getFiles();
        int indexed = 0;

        for (BrowserFileMetadata meta : files) {
            try {
                String fullPath = request.getRootPath() + "/" + meta.getPath();

                var existing = fileRepository.findByPath(fullPath);
                if (existing.isPresent()) {
                    FileEntity f = existing.get();
                    f.setScanSession(finalSession);
                    f.setSizeBytes(meta.getSizeBytes());
                    if (meta.getLastModifiedMs() > 0) {
                        f.setModifiedAt(toLocalDateTime(meta.getLastModifiedMs()));
                    }
                    fileRepository.save(f);
                } else {
                    String ext = extractExtension(meta.getName());
                    LocalDateTime modTime = meta.getLastModifiedMs() > 0
                        ? toLocalDateTime(meta.getLastModifiedMs()) : null;

                    FileEntity file = FileEntity.builder()
                        .path(fullPath)
                        .name(meta.getName())
                        .extension(ext)
                        .mimeType(meta.getMimeType())
                        .sizeBytes(meta.getSizeBytes())
                        .fileType(determineFileType(ext))
                        .modifiedAt(modTime)
                        .accessedAt(modTime)
                        .scanSession(finalSession)
                        .build();

                    fileRepository.save(file);
                }
                indexed++;
            } catch (Exception e) {
                log.warn("Failed to index browser file: {}", meta.getPath(), e);
            }
        }

        session.setStatus(ScanStatus.COMPLETED);
        session.setEndTime(LocalDateTime.now());
        session.setScannedFiles(indexed);
        session.setTotalFiles(files.size());
        session.setTotalSize(files.stream().mapToLong(BrowserFileMetadata::getSizeBytes).sum());
        scanSessionRepository.save(session);

        log.info("Browser scan complete: session={}, indexed={}", session.getId(), indexed);
        return ResponseEntity.ok(toDTO(session));
    }

    /** Returns a live snapshot of scan progress. */
    @GetMapping("/progress/{sessionId}")
    public ResponseEntity<ScanProgressDTO> getProgress(@PathVariable Long sessionId) {
        Optional<ScanSessionEntity> session = scanSessionRepository.findById(sessionId);
        if (session.isEmpty()) return ResponseEntity.notFound().build();

        FileScanner.ScanProgress p = fileScanner.getProgress();
        return ResponseEntity.ok(new ScanProgressDTO(
            p.totalFilesFound, p.filesProcessed, p.totalSize,
            p.currentSpeed, p.estimatedEndTime));
    }

    /** Signals the currently running scan to cancel. */
    @PostMapping("/cancel/{sessionId}")
    public ResponseEntity<Void> cancelScan(@PathVariable Long sessionId) {
        log.info("Cancel requested for session {}", sessionId);
        fileScanner.cancelScan();
        return ResponseEntity.ok().build();
    }

    /** Returns a paginated list of all scan sessions. */
    @GetMapping("/list")
    public ResponseEntity<Page<ScanSessionDTO>> listScans(Pageable pageable) {
        return ResponseEntity.ok(scanSessionRepository.findAll(pageable).map(this::toDTO));
    }

    /** Returns a single scan session by ID. */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ScanSessionDTO> getSession(@PathVariable Long sessionId) {
        return scanSessionRepository.findById(sessionId)
            .map(s -> ResponseEntity.ok(toDTO(s)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private ScanSessionDTO toDTO(ScanSessionEntity e) {
        return ScanSessionDTO.builder()
            .id(e.getId())
            .sessionName(e.getSessionName())
            .scanPath(e.getScanPath())
            .status(e.getStatus().name())
            .totalFiles(e.getTotalFiles())
            .totalSize(e.getTotalSize())
            .startTime(e.getStartTime())
            .endTime(e.getEndTime())
            .build();
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private FileType determineFileType(String ext) {
        if (ext == null) return FileType.OTHER;
        return switch (ext) {
            case "jpg","jpeg","png","gif","bmp","webp","svg","ico","tiff","heic" -> FileType.IMAGE;
            case "mp4","mkv","avi","mov","flv","wmv","webm","m4v"               -> FileType.VIDEO;
            case "pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","rtf"  -> FileType.DOCUMENT;
            case "zip","rar","7z","tar","gz","bz2","xz"                          -> FileType.ARCHIVE;
            case "exe","msi","bat","sh","app","deb","rpm","cmd"                  -> FileType.EXECUTABLE;
            case "mp3","wav","flac","aac","m4a","wma","ogg"                      -> FileType.MEDIA;
            case "tmp","temp","cache","log","bak","swp"                          -> FileType.TEMPORARY;
            default                                                               -> FileType.OTHER;
        };
    }
}
