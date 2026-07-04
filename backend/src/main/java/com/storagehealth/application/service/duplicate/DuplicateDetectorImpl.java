package com.storagehealth.application.service.duplicate;

import com.storagehealth.application.service.hashing.HashingService;
import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects exact-duplicate files using a two-pass strategy:
 * <ol>
 *   <li><b>Size grouping</b> — files with different sizes can never be identical, so they
 *       are excluded early without touching the disk.</li>
 *   <li><b>SHA-256 hashing</b> — within each size group, hashes are computed and stored;
 *       groups that resolve to the same hash are reported as duplicates.</li>
 * </ol>
 *
 * <p>Duplicate recommendations are created for every file <em>after the first</em>
 * in each group. The first file is treated as the "primary" copy to keep.
 */
@Service
@Slf4j
@Transactional
public class DuplicateDetectorImpl implements DuplicateDetector {

    private final FileRepository fileRepository;
    private final FileHashRepository hashRepository;
    private final HashingService hashingService;
    private final RecommendationRepository recommendationRepository;

    @Autowired
    public DuplicateDetectorImpl(FileRepository fileRepository,
                                 FileHashRepository hashRepository,
                                 HashingService hashingService,
                                 RecommendationRepository recommendationRepository) {
        this.fileRepository           = fileRepository;
        this.hashRepository           = hashRepository;
        this.hashingService           = hashingService;
        this.recommendationRepository = recommendationRepository;
    }

    // ---------------------------------------------------------------
    // DuplicateDetector implementation
    // ---------------------------------------------------------------

    @Override
    public List<DuplicateGroup> findExactDuplicates(ScanSessionEntity session) {
        log.info("Starting duplicate detection for session {}", session.getId());

        List<FileEntity> files = fileRepository.findByScanSession(session);
        log.debug("Total files in session: {}", files.size());

        // Pass 1: group by size (O(n) — no I/O)
        Map<Long, List<FileEntity>> sizeGroups = files.stream()
            .collect(Collectors.groupingBy(FileEntity::getSizeBytes));

        List<DuplicateGroup> duplicates = new ArrayList<>();

        // Pass 2: hash files that share a size
        for (Map.Entry<Long, List<FileEntity>> entry : sizeGroups.entrySet()) {
            if (entry.getValue().size() < 2) continue; // nothing to compare

            Map<String, List<FileEntity>> hashGroups = computeHashesAndGroup(entry.getValue());

            for (Map.Entry<String, List<FileEntity>> hashEntry : hashGroups.entrySet()) {
                if (hashEntry.getValue().size() > 1) {
                    DuplicateGroup group = new DuplicateGroup(
                        hashEntry.getKey(), hashEntry.getValue(), 0L, 0L);
                    group.calculate();
                    duplicates.add(group);

                    log.info("Duplicate group: {} files, hash={}..., recoverable={} bytes",
                        group.getFiles().size(),
                        hashEntry.getKey().substring(0, 8),
                        group.getRecoverableSpace());
                }
            }
        }

        log.info("Duplicate detection complete. Groups found: {}", duplicates.size());
        return duplicates;
    }

    @Override
    public void markDuplicates(List<DuplicateGroup> groups) {
        for (DuplicateGroup group : groups) {
            List<FileEntity> files = group.getFiles();
            // Index 0 is the "primary" — skip it; mark [1..n-1] as duplicates
            for (int i = 1; i < files.size(); i++) {
                FileEntity duplicate = files.get(i);

                if (recommendationRepository.existsByFileAndTypeAndIsActedOnFalse(
                        duplicate, RecommendationType.DUPLICATE)) {
                    log.debug("Duplicate recommendation already exists for file {}", duplicate.getId());
                    continue;
                }

                RecommendationEntity rec = RecommendationEntity.builder()
                    .file(duplicate)
                    .type(RecommendationType.DUPLICATE)
                    .confidenceScore(BigDecimal.valueOf(1.0))
                    .explanation("Exact duplicate of: " + files.get(0).getPath())
                    .recoverableSpace(duplicate.getSizeBytes())
                    .isActedOn(false)
                    .build();

                recommendationRepository.save(rec);
            }
        }
        log.info("Marked {} files as duplicate recommendations", groups.stream()
            .mapToInt(g -> Math.max(0, g.getFiles().size() - 1)).sum());
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Hashes each file in {@code files} with SHA-256, persists the hash if new,
     * and groups files by their hash value.
     */
    private Map<String, List<FileEntity>> computeHashesAndGroup(List<FileEntity> files) {
        Map<String, List<FileEntity>> hashGroups = new HashMap<>();

        for (FileEntity file : files) {
            try {
                // Re-use an existing hash record if it was already computed
                Optional<FileHashEntity> existing =
                    hashRepository.findByFileAndHashType(file, HashType.SHA256);

                String hash;
                if (existing.isPresent()) {
                    hash = existing.get().getHashValue();
                } else if (file.getSizeBytes() == 0) {
                    hash = "empty-file";
                } else {
                    hash = hashingService.sha256Hash(Paths.get(file.getPath()));
                    FileHashEntity hashEntity = FileHashEntity.builder()
                        .file(file)
                        .hashType(HashType.SHA256)
                        .hashValue(hash)
                        .build();
                    hashRepository.save(hashEntity);
                }

                hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);

            } catch (IOException e) {
                log.warn("Failed to hash file (will be skipped): {}", file.getPath(), e);
            }
        }

        return hashGroups;
    }
}
