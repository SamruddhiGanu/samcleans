package com.storagehealth.presentation.api;

import com.storagehealth.application.service.scanner.FileScanner;
import com.storagehealth.domain.entity.ScanSessionEntity;
import com.storagehealth.domain.entity.ScanStatus;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * REST controller exposing scan lifecycle endpoints.
 *
 * <pre>
 *  POST  /api/scan/start            — start a new scan (async)
 *  GET   /api/scan/progress/{id}    — poll scan progress
 *  POST  /api/scan/cancel/{id}      — request cancellation
 *  GET   /api/scan/list             — paginated list of all sessions
 *  GET   /api/scan/{id}             — get a single session by ID
 * </pre>
 */
@RestController
@RequestMapping("/api/scan")
@Slf4j
public class ScanController {

    private final FileScanner fileScanner;
    private final ScanSessionRepository scanSessionRepository;

    @Autowired
    public ScanController(FileScanner fileScanner,
                          ScanSessionRepository scanSessionRepository) {
        this.fileScanner           = fileScanner;
        this.scanSessionRepository = scanSessionRepository;
    }

    // ---------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------

    /**
     * Persists a new {@link ScanSessionEntity} and starts the scan asynchronously
     * so the HTTP response is returned immediately.
     */
    @PostMapping("/start")
    public ResponseEntity<ScanSessionDTO> startScan(@RequestBody ScanRequest request) {
        log.info("Start scan request received for path: {}", request.getPath());

        String sessionName = (request.getName() != null && !request.getName().isBlank())
            ? request.getName()
            : "Scan " + LocalDateTime.now();

        ScanSessionEntity session = ScanSessionEntity.builder()
            .sessionName(sessionName)
            .scanPath(request.getPath())
            .status(ScanStatus.INITIATED)
            .build();

        ScanSessionEntity saved = scanSessionRepository.save(session);

        // Fire-and-forget — scan runs in a virtual thread to avoid blocking the pool
        Thread.ofVirtual().name("scanner-" + saved.getId()).start(() -> {
            try {
                fileScanner.scanDirectory(request.getPath(), saved.getId());
            } catch (Exception e) {
                log.error("Async scan failed for session {}", saved.getId(), e);
            }
        });

        return ResponseEntity.ok(toDTO(saved));
    }

    /** Returns a live snapshot of scan progress for the given session. */
    @GetMapping("/progress/{sessionId}")
    public ResponseEntity<ScanProgressDTO> getProgress(@PathVariable Long sessionId) {
        Optional<ScanSessionEntity> session = scanSessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileScanner.ScanProgress p = fileScanner.getProgress();
        return ResponseEntity.ok(new ScanProgressDTO(
            p.totalFilesFound,
            p.filesProcessed,
            p.totalSize,
            p.currentSpeed,
            p.estimatedEndTime
        ));
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
        return ResponseEntity.ok(
            scanSessionRepository.findAll(pageable).map(this::toDTO)
        );
    }

    /** Returns a single scan session by ID. */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ScanSessionDTO> getSession(@PathVariable Long sessionId) {
        return scanSessionRepository.findById(sessionId)
            .map(s -> ResponseEntity.ok(toDTO(s)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ---------------------------------------------------------------
    // Mapping
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
}
