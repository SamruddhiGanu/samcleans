package com.storagehealth.application.service.duplicate;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.ScanSessionEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Contract for detecting exact-duplicate files within a scan session.
 */
public interface DuplicateDetector {

    /**
     * Groups files in the given session by SHA-256 hash and returns groups
     * that contain more than one file (i.e. exact duplicates).
     */
    List<DuplicateGroup> findExactDuplicates(ScanSessionEntity session);

    /**
     * Creates {@link com.storagehealth.domain.entity.RecommendationEntity} records
     * for every non-primary file in each duplicate group.
     */
    void markDuplicates(List<DuplicateGroup> groups);

    // ---------------------------------------------------------------
    // Value class
    // ---------------------------------------------------------------

    @Data
    @AllArgsConstructor
    class DuplicateGroup {
        private String hashValue;
        private List<FileEntity> files;
        private Long totalSize;
        private Long recoverableSpace;

        /**
         * Derives {@code totalSize} and {@code recoverableSpace} from the file list.
         * Recoverable space = (duplicates − 1) × single file size.
         */
        public void calculate() {
            if (files == null || files.isEmpty()) return;
            totalSize        = files.stream().mapToLong(FileEntity::getSizeBytes).sum();
            recoverableSpace = (long) (files.size() - 1) * files.get(0).getSizeBytes();
        }
    }
}
