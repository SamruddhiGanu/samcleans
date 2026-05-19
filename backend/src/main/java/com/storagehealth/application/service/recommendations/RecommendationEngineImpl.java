package com.storagehealth.application.service.recommendations;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import com.storagehealth.presentation.api.dto.RecommendationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates actionable storage-cleanup recommendations by inspecting files in a scan session.
 *
 * <p>Recommendation categories produced:
 * <ul>
 *   <li>{@link RecommendationType#OLD_SCREENSHOT} — screenshots older than 6 months</li>
 *   <li>{@link RecommendationType#TEMP_FILE} — files with temporary extensions</li>
 *   <li>{@link RecommendationType#UNUSED_LARGE_FILE} — files &gt;500 MB not accessed for 1 year</li>
 *   <li>{@link RecommendationType#STALE_DOWNLOAD} — downloads not accessed for 3 months</li>
 * </ul>
 *
 * <p>Each recommendation avoids creating a duplicate DB record by checking for an existing
 * recommendation of the same type for the same file before saving.
 */
@Service
@Slf4j
@Transactional
public class RecommendationEngineImpl implements RecommendationEngine {

    /** Files larger than this threshold are flagged as UNUSED_LARGE_FILE if stale. */
    private static final long LARGE_FILE_THRESHOLD_BYTES = 500L * 1024 * 1024; // 500 MB

    private final FileRepository fileRepository;
    private final RecommendationRepository recommendationRepository;

    @Autowired
    public RecommendationEngineImpl(FileRepository fileRepository,
                                    RecommendationRepository recommendationRepository) {
        this.fileRepository           = fileRepository;
        this.recommendationRepository = recommendationRepository;
    }

    // ---------------------------------------------------------------
    // RecommendationEngine implementation
    // ---------------------------------------------------------------

    @Override
    public void generateRecommendations(ScanSessionEntity session) {
        log.info("Generating recommendations for session {}", session.getId());

        List<FileEntity> files = fileRepository.findByScanSession(session);
        log.debug("Evaluating {} files", files.size());

        recommendOldScreenshots(files);
        recommendTemporaryFiles(files);
        recommendUnusedLargeFiles(files);
        recommendStaleDownloads(files);

        log.info("Recommendation generation complete for session {}", session.getId());
    }

    @Override
    public List<RecommendationDTO> getRecommendations(RecommendationType type, Pageable pageable) {
        return recommendationRepository.findByType(type, pageable)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Rule implementations
    // ---------------------------------------------------------------

    /** Screenshots older than 6 months are likely safe to delete. */
    private void recommendOldScreenshots(List<FileEntity> files) {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(6);
        int count = 0;

        for (FileEntity file : files) {
            if (!isScreenshot(file)) continue;
            if (file.getModifiedAt() == null || !file.getModifiedAt().isBefore(cutoff)) continue;
            if (exists(file, RecommendationType.OLD_SCREENSHOT)) continue;

            save(file, RecommendationType.OLD_SCREENSHOT,
                BigDecimal.valueOf(0.85),
                "Old screenshot from " + file.getModifiedAt().toLocalDate() + ". Safe to delete.",
                file.getSizeBytes());
            count++;
        }
        log.debug("Old screenshot recommendations: {}", count);
    }

    /** TEMPORARY-type files (tmp, cache, log, bak) are safe to clean up. */
    private void recommendTemporaryFiles(List<FileEntity> files) {
        int count = 0;

        for (FileEntity file : files) {
            if (file.getFileType() != FileType.TEMPORARY) continue;
            if (exists(file, RecommendationType.TEMP_FILE)) continue;

            save(file, RecommendationType.TEMP_FILE,
                BigDecimal.valueOf(0.95),
                "Temporary/cache file. Safe to delete.",
                file.getSizeBytes());
            count++;
        }
        log.debug("Temporary file recommendations: {}", count);
    }

    /** Large files not accessed in the last year are flagged for review. */
    private void recommendUnusedLargeFiles(List<FileEntity> files) {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(1);
        int count = 0;

        for (FileEntity file : files) {
            if (file.getSizeBytes() <= LARGE_FILE_THRESHOLD_BYTES) continue;
            if (file.getAccessedAt() == null || !file.getAccessedAt().isBefore(cutoff)) continue;
            if (exists(file, RecommendationType.UNUSED_LARGE_FILE)) continue;

            save(file, RecommendationType.UNUSED_LARGE_FILE,
                BigDecimal.valueOf(0.70),
                "Large file (" + formatSize(file.getSizeBytes()) + ") not accessed for over 1 year.",
                file.getSizeBytes());
            count++;
        }
        log.debug("Unused large file recommendations: {}", count);
    }

    /** Downloads untouched for 3 months are considered stale. */
    private void recommendStaleDownloads(List<FileEntity> files) {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(3);
        int count = 0;

        for (FileEntity file : files) {
            if (!isInDownloadsFolder(file)) continue;
            if (file.getAccessedAt() == null || !file.getAccessedAt().isBefore(cutoff)) continue;
            if (exists(file, RecommendationType.STALE_DOWNLOAD)) continue;

            save(file, RecommendationType.STALE_DOWNLOAD,
                BigDecimal.valueOf(0.75),
                "Download not accessed for 3+ months. Size: " + formatSize(file.getSizeBytes()),
                file.getSizeBytes());
            count++;
        }
        log.debug("Stale download recommendations: {}", count);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private boolean isScreenshot(FileEntity file) {
        if (file.getFileType() != FileType.IMAGE) return false;
        String lower = file.getName().toLowerCase();
        return lower.contains("screenshot") || lower.contains("screen shot")
            || lower.contains("screen_shot") || lower.startsWith("scr_");
    }

    private boolean isInDownloadsFolder(FileEntity file) {
        String path = file.getPath().toLowerCase();
        return path.contains("downloads") || path.contains("download");
    }

    /** Returns true if a recommendation of this type already exists for the file. */
    private boolean exists(FileEntity file, RecommendationType type) {
        return recommendationRepository.findByFile(file).stream()
            .anyMatch(r -> r.getType() == type);
    }

    private void save(FileEntity file, RecommendationType type,
                      BigDecimal confidence, String explanation, Long recoverableSpace) {
        RecommendationEntity rec = RecommendationEntity.builder()
            .file(file)
            .type(type)
            .confidenceScore(confidence)
            .explanation(explanation)
            .recoverableSpace(recoverableSpace)
            .isActedOn(false)
            .build();
        recommendationRepository.save(rec);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private RecommendationDTO toDTO(RecommendationEntity e) {
        return RecommendationDTO.builder()
            .id(e.getId())
            .fileId(e.getFile().getId())
            .fileName(e.getFile().getName())
            .filePath(e.getFile().getPath())
            .type(e.getType().name())
            .confidenceScore(e.getConfidenceScore())
            .explanation(e.getExplanation())
            .recoverableSpace(e.getRecoverableSpace())
            .build();
    }
}
