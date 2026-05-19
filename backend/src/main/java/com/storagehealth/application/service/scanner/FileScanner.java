package com.storagehealth.application.service.scanner;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Contract for a file-system scanner that walks a directory tree,
 * persists discovered files to the database and tracks progress.
 */
public interface FileScanner {

    /**
     * Recursively scans {@code directoryPath}, saving each discovered file
     * to the database under the given {@code session}.
     *
     * @param directoryPath absolute path to the root directory to scan
     * @param sessionId     ID of the pre-created {@link com.storagehealth.domain.entity.ScanSessionEntity}
     * @throws IOException if the root path cannot be accessed
     */
    void scanDirectory(String directoryPath, Long sessionId) throws IOException;

    /** Signals the running scan to stop at the next safe checkpoint. */
    void cancelScan();

    /** Returns a snapshot of the current scan progress (thread-safe). */
    ScanProgress getProgress();

    // ---------------------------------------------------------------
    // Inner value class — kept here to avoid a separate file for a
    // simple data carrier that is tightly coupled to this interface.
    // ---------------------------------------------------------------
    class ScanProgress {
        public volatile int totalFilesFound;
        public volatile int filesProcessed;
        public volatile long totalSize;
        public volatile long currentSpeed;          // files / second
        public volatile LocalDateTime startTime;
        public volatile LocalDateTime estimatedEndTime;

        @Override
        public String toString() {
            return "ScanProgress{files=" + totalFilesFound +
                   ", size=" + totalSize + " bytes, speed=" + currentSpeed + " f/s}";
        }
    }
}
