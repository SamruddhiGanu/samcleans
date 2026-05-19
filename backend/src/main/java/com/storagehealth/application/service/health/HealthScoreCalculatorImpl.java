package com.storagehealth.application.service.health;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculates a composite storage health score (0 – 100) broken into three sub-scores:
 *
 * <ul>
 *   <li><b>Duplicate waste</b> (40 %) — space occupied by confirmed duplicates</li>
 *   <li><b>Clutter</b> (30 %) — space occupied by temporary/cache files</li>
 *   <li><b>Organisation</b> (30 %) — number of distinct directories (more = better spread)</li>
 * </ul>
 */
@Service
@Slf4j
public class HealthScoreCalculatorImpl implements HealthScoreCalculator {

    private final FileRepository fileRepository;
    private final RecommendationRepository recommendationRepository;

    @Autowired
    public HealthScoreCalculatorImpl(FileRepository fileRepository,
                                     RecommendationRepository recommendationRepository) {
        this.fileRepository           = fileRepository;
        this.recommendationRepository = recommendationRepository;
    }

    // ---------------------------------------------------------------
    // HealthScoreCalculator implementation
    // ---------------------------------------------------------------

    @Override
    public StorageHealthScore calculateHealthScore(ScanSessionEntity session) {
        log.info("Calculating health score for session {}", session.getId());

        List<FileEntity> files = fileRepository.findByScanSession(session);

        if (files.isEmpty()) {
            log.warn("No files found in session {} — returning zero score", session.getId());
            return StorageHealthScore.builder()
                .overallScore(0.0).duplicateWasteScore(0.0)
                .clutterScore(0.0).organizationScore(0.0)
                .totalSize(0L).duplicateWaste(0L)
                .clutteredSize(0L).temporaryFileSize(0L)
                .build();
        }

        long totalSize       = files.stream().mapToLong(FileEntity::getSizeBytes).sum();
        long duplicateWaste  = calculateDuplicateWaste();
        long clutteredSize   = calculateClutteredSize(files);
        long tempSize        = clutteredSize; // temp files are the clutter for now
        double orgScore      = calculateOrganizationScore(files);

        // Guard against division by zero for empty / single-byte scans
        double dupFraction     = totalSize > 0 ? (double) duplicateWaste / totalSize : 0.0;
        double clutterFraction = totalSize > 0 ? (double) clutteredSize  / totalSize : 0.0;

        double duplicateWasteScore = 100.0 * (1.0 - dupFraction);
        double clutterScore        = 100.0 * (1.0 - clutterFraction);

        double overall = (duplicateWasteScore * 0.4)
                       + (clutterScore        * 0.3)
                       + (orgScore            * 0.3);

        overall = Math.min(100.0, Math.max(0.0, overall));

        log.info("Health score: overall={}, dup={}, clutter={}, org={}",
            String.format("%.1f", overall),
            String.format("%.1f", duplicateWasteScore),
            String.format("%.1f", clutterScore),
            String.format("%.1f", orgScore));

        return StorageHealthScore.builder()
            .overallScore(overall)
            .duplicateWasteScore(duplicateWasteScore)
            .clutterScore(clutterScore)
            .organizationScore(orgScore)
            .totalSize(totalSize)
            .duplicateWaste(duplicateWaste)
            .clutteredSize(clutteredSize)
            .temporaryFileSize(tempSize)
            .build();
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /** Total bytes consumed by files flagged as DUPLICATE recommendations. */
    private long calculateDuplicateWaste() {
        return recommendationRepository.findByType(RecommendationType.DUPLICATE).stream()
            .filter(r -> r.getFile() != null)
            .mapToLong(r -> r.getFile().getSizeBytes())
            .sum();
    }

    /** Total bytes consumed by TEMPORARY-type files. */
    private long calculateClutteredSize(List<FileEntity> files) {
        return files.stream()
            .filter(f -> f.getFileType() == FileType.TEMPORARY)
            .mapToLong(FileEntity::getSizeBytes)
            .sum();
    }

    /**
     * Organisation score (0 – 100): more distinct directories = better organised.
     * Capped so deeply nested single-file trees don't score perfect.
     *
     * <p>Formula: min(100, 50 + distinctFolders × 5)
     */
    private double calculateOrganizationScore(List<FileEntity> files) {
        Map<String, Long> folderCounts = files.stream()
            .map(f -> {
                try {
                    return Paths.get(f.getPath()).getParent().toString();
                } catch (Exception e) {
                    return "UNKNOWN";
                }
            })
            .collect(Collectors.groupingBy(p -> p, Collectors.counting()));

        int distinctFolders = folderCounts.size();
        return Math.min(100.0, 50.0 + distinctFolders * 5.0);
    }
}
