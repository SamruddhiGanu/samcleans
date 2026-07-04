package com.storagehealth.application.service.scanner;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.FileHashRepository;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multi-threaded file scanner that walks a directory tree using
 * {@link Files#walkFileTree} and persists each regular file to the database.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>A fixed thread pool sized to available CPU cores is reused across scans.</li>
 *   <li>System / excluded directories are skipped early via {@code preVisitDirectory}.</li>
 *   <li>Each file is checked for prior indexing so re-scanning is safe (idempotent).</li>
 *   <li>Cancellation is cooperative — the scanner checks a flag at each file boundary.</li>
 * </ul>
 */
@Service
@Slf4j
public class FileScannerImpl implements FileScanner {

    // ---------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private static final Set<String> EXCLUDED_DIR_NAMES = Set.of(
        "$RECYCLE.BIN", "System Volume Information", ".git", "__pycache__",
        "node_modules", ".DS_Store", "Thumbs.db"
    );

    private static final Set<String> SYSTEM_PATH_PREFIXES = Set.of(
        "C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)", "C:\\ProgramData",
        "/System", "/Library", "/usr", "/etc", "/var", "/proc", "/sys"
    );

    // ---------------------------------------------------------------
    // Dependencies
    // ---------------------------------------------------------------
    private final FileRepository fileRepository;
    private final ScanSessionRepository sessionRepository;
    private final ExecutorService executorService;

    // ---------------------------------------------------------------
    // State (reset per scan)
    // ---------------------------------------------------------------
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final ScanProgress progress = new ScanProgress();

    @Autowired
    public FileScannerImpl(FileRepository fileRepository,
                           ScanSessionRepository sessionRepository) {
        this.fileRepository = fileRepository;
        this.sessionRepository = sessionRepository;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    // ---------------------------------------------------------------
    // FileScanner implementation
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public void scanDirectory(String directoryPath, Long sessionId) throws IOException {
        log.info("Starting scan of directory '{}' under session {}", directoryPath, sessionId);

        ScanSessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Scan session not found: " + sessionId));

        session.setStatus(ScanStatus.IN_PROGRESS);
        session.setStartTime(LocalDateTime.now());
        sessionRepository.save(session);

        // Reset progress
        progress.totalFilesFound = 0;
        progress.filesProcessed  = 0;
        progress.totalSize       = 0;
        progress.startTime       = LocalDateTime.now();
        cancelRequested.set(false);

        List<Future<?>> futures = new ArrayList<>();

        try {
            Path rootPath = Paths.get(directoryPath);

            Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (cancelRequested.get()) return FileVisitResult.TERMINATE;
                    if (isExcludedDirectory(dir)) {
                        log.debug("Skipping excluded directory: {}", dir);
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (cancelRequested.get()) return FileVisitResult.TERMINATE;
                    if (attrs.isRegularFile()) {
                        futures.add(executorService.submit(() -> processFile(file, attrs, session)));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.warn("Cannot access file: {} — {}", file, exc.getMessage());
                    return FileVisitResult.CONTINUE;
                }
            });

            // Wait for all submitted tasks
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception e) { log.warn("Task failed", e); }
            }

            ScanStatus finalStatus = cancelRequested.get() ? ScanStatus.PAUSED : ScanStatus.COMPLETED;
            session.setStatus(finalStatus);
            session.setEndTime(LocalDateTime.now());
            session.setScannedFiles(progress.totalFilesFound);
            session.setTotalSize(progress.totalSize);
            sessionRepository.save(session);

            log.info("Scan finished. Status={}, Files={}, Size={} bytes",
                finalStatus, progress.totalFilesFound, progress.totalSize);

        } catch (Exception e) {
            session.setStatus(ScanStatus.FAILED);
            sessionRepository.save(session);
            log.error("Scan failed unexpectedly", e);
            throw e;
        }
    }

    @Override
    public void cancelScan() {
        log.info("Scan cancellation requested");
        cancelRequested.set(true);
    }

    @Override
    public ScanProgress getProgress() {
        return progress;
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down scanner thread pool");
        executorService.shutdownNow();
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private void processFile(Path filePath, BasicFileAttributes attrs, ScanSessionEntity session) {
        try {
            // Re-associate existing file records with the current session so that
            // Health / Duplicates / Recommendations always operate on the correct file set.
            var existing = fileRepository.findByPath(filePath.toString());
            if (existing.isPresent()) {
                FileEntity f = existing.get();
                f.setScanSession(session);
                // Refresh timestamps from disk in case the file changed
                f.setModifiedAt(toLocalDateTime(attrs.lastModifiedTime().toInstant()));
                f.setAccessedAt(toLocalDateTime(attrs.lastAccessTime().toInstant()));
                f.setSizeBytes(attrs.size());
                fileRepository.save(f);
                log.debug("Re-linked existing file to session {}: {}", session.getId(), filePath);
            } else {
                FileEntity file = FileEntity.builder()
                    .path(filePath.toString())
                    .name(filePath.getFileName().toString())
                    .extension(getExtension(filePath))
                    .mimeType(getMimeType(filePath))
                    .sizeBytes(attrs.size())
                    .fileType(determineFileType(filePath))
                    .createdAt(toLocalDateTime(attrs.creationTime().toInstant()))
                    .modifiedAt(toLocalDateTime(attrs.lastModifiedTime().toInstant()))
                    .accessedAt(toLocalDateTime(attrs.lastAccessTime().toInstant()))
                    .scanSession(session)
                    .build();
                fileRepository.save(file);
                log.debug("Indexed new file under session {}: {}", session.getId(), filePath);
            }

            synchronized (progress) {
                progress.totalFilesFound++;
                progress.totalSize += attrs.size();
                updateProgressEstimate();
            }

        } catch (Exception e) {
            log.warn("Failed to process file: {}", filePath, e);
        }
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase() : "";
    }

    private String getMimeType(Path path) {
        try {
            String mime = Files.probeContentType(path);
            return mime != null ? mime : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    private FileType determineFileType(Path path) {
        String ext = getExtension(path);
        if (isImage(ext))      return FileType.IMAGE;
        if (isVideo(ext))      return FileType.VIDEO;
        if (isDocument(ext))   return FileType.DOCUMENT;
        if (isArchive(ext))    return FileType.ARCHIVE;
        if (isExecutable(ext)) return FileType.EXECUTABLE;
        if (isMedia(ext))      return FileType.MEDIA;
        if (isTemporary(ext))  return FileType.TEMPORARY;
        return FileType.OTHER;
    }

    private boolean isImage(String e)      { return Set.of("jpg","jpeg","png","gif","bmp","webp","svg","ico","tiff","heic").contains(e); }
    private boolean isVideo(String e)      { return Set.of("mp4","mkv","avi","mov","flv","wmv","webm","m4v").contains(e); }
    private boolean isDocument(String e)   { return Set.of("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","odt","rtf").contains(e); }
    private boolean isArchive(String e)    { return Set.of("zip","rar","7z","tar","gz","bz2","xz").contains(e); }
    private boolean isExecutable(String e) { return Set.of("exe","msi","bat","sh","app","deb","rpm","cmd").contains(e); }
    private boolean isMedia(String e)      { return Set.of("mp3","wav","flac","aac","m4a","wma","ogg").contains(e); }
    private boolean isTemporary(String e)  { return Set.of("tmp","temp","cache","log","bak","swp").contains(e); }

    private boolean isExcludedDirectory(Path path) {
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
        String pathStr  = path.toString();

        if (EXCLUDED_DIR_NAMES.stream().anyMatch(fileName::equalsIgnoreCase)) return true;
        return SYSTEM_PATH_PREFIXES.stream().anyMatch(prefix ->
            pathStr.startsWith(prefix) || pathStr.toLowerCase().startsWith(prefix.toLowerCase()));
    }

    private void updateProgressEstimate() {
        if (progress.startTime == null) return;
        long elapsedSeconds = Duration.between(progress.startTime, LocalDateTime.now()).getSeconds();
        if (elapsedSeconds > 0) {
            progress.currentSpeed = progress.totalFilesFound / elapsedSeconds;
        }
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
