package com.storagehealth.application.service.health;

import lombok.*;

/**
 * Value object holding all computed health-score components for a scan session.
 * Scores are 0 – 100 (higher = healthier).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageHealthScore {

    /** Weighted composite score: duplicateWaste × 0.4 + clutter × 0.3 + organization × 0.3 */
    private Double overallScore;

    /** 100 × (1 − duplicateWaste / totalSize) */
    private Double duplicateWasteScore;

    /** 100 × (1 − clutteredSize / totalSize) */
    private Double clutterScore;

    /** Based on folder spread — more directories = better organised */
    private Double organizationScore;

    private Long totalSize;
    private Long duplicateWaste;
    private Long clutteredSize;
    private Long temporaryFileSize;

    /**
     * Human-readable tier based on overall score.
     */
    public String getHealthStatus() {
        if (overallScore == null)    return "UNKNOWN";
        if (overallScore >= 80)      return "EXCELLENT";
        if (overallScore >= 60)      return "GOOD";
        if (overallScore >= 40)      return "FAIR";
        return "POOR";
    }
}
