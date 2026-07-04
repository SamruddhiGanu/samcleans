package com.storagehealth.application.service.health;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileHashRepository;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final FileHashRepository fileHashRepository;
    private final RecommendationRepository recommendationRepository;

    @Autowired
    public HealthScoreCalculatorImpl(FileRepository fileRepository,
                                     FileHashRepository fileHashRepository,
                                     RecommendationRepository recommendationRepository) {
        this.fileRepository           = fileRepository;
        this.fileHashRepository       = fileHashRepository;
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
        long duplicateWaste  = calculateDuplicateWaste(files);
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

    /** Total bytes recoverable from confirmed duplicate hashes in this scan session. */
    private long calculateDuplicateWaste(List<FileEntity> files) {
        // Phase 1: Use stored SHA-256 hashes to identify exact duplicates
        // This is accurate for files the browser or backend actually hashed.
        Set<Long> alreadyAccountedIds = new java.util.HashSet<>();

        Map<String, List<FileEntity>> hashGroups = files.stream()
            .flatMap(file -> hashesForFile(file).stream()
                .filter(hash -> hash.getHashType() == HashType.SHA256)
                .map(hash -> Map.entry(hash.getHashValue(), file)))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        long hashWaste = 0L;
        for (List<FileEntity> group : hashGroups.values()) {
            if (group.size() > 1) {
                long total   = group.stream().mapToLong(FileEntity::getSizeBytes).sum();
                long keepOne = group.stream().mapToLong(FileEntity::getSizeBytes).max().orElse(0L);
                hashWaste += (total - keepOne);
                group.forEach(f -> alreadyAccountedIds.add(f.getId()));
            }
        }

        // Phase 2: Size-based duplicate detection for files without stored hashes.
        // For browser scans, the front-end only hashes files that share a size, but
        // there may be files whose hashes were not submitted (e.g. too large, or
        // skipped due to errors). Files with the same non-zero size are conservatively
        // counted as *potential* duplicates contributing to waste.
        Map<Long, List<FileEntity>> sizeGroups = files.stream()
            .filter(f -> f.getSizeBytes() > 0)
            .filter(f -> !alreadyAccountedIds.contains(f.getId()))
            .collect(Collectors.groupingBy(FileEntity::getSizeBytes));

        long sizeWaste = sizeGroups.values().stream()
            .filter(group -> group.size() > 1)
            .mapToLong(group -> {
                // Keep one copy — the rest is recoverable waste
                long singleCopySize = group.get(0).getSizeBytes();
                return singleCopySize * (group.size() - 1);
            })
            .sum();

        // Also count already-actioned duplicate recommendations
        long recommendationWaste = recommendationRepository.findByType(RecommendationType.DUPLICATE).stream()
            .filter(r -> r.getFile() != null)
            .filter(r -> files.contains(r.getFile()))
            .filter(r -> !Boolean.TRUE.equals(r.getIsActedOn()))
            .mapToLong(r -> r.getFile().getSizeBytes())
            .sum();

        return Math.max(hashWaste + sizeWaste, recommendationWaste);
    }

    private List<FileHashEntity> hashesForFile(FileEntity file) {
        List<FileHashEntity> hashes = fileHashRepository.findByFile(file);
        return hashes != null ? hashes : Collections.emptyList();
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
