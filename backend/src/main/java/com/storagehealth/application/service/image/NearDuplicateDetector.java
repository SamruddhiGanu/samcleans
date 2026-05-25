package com.storagehealth.application.service.image;

import com.storagehealth.domain.entity.ScanSessionEntity;

/**
 * Service that identifies visually similar images (near-duplicates)
 * within a scan session using perceptual hashing.
 */
public interface NearDuplicateDetector {
    
    /**
     * Scans all image files in a session, computes their perceptual hashes,
     * and generates NEAR_DUPLICATE recommendations for any similar pairs.
     * @param session the scan session to analyze
     */
    void findNearDuplicates(ScanSessionEntity session);
}
