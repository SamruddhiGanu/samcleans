package com.storagehealth.application.service.health;

import com.storagehealth.domain.entity.ScanSessionEntity;

/**
 * Contract for computing the composite storage health score for a scan session.
 */
public interface HealthScoreCalculator {

    /**
     * Analyses all files in {@code session} and returns a {@link StorageHealthScore}
     * capturing duplicate waste, clutter level, and organisation quality.
     */
    StorageHealthScore calculateHealthScore(ScanSessionEntity session);
}
