package com.storagehealth.presentation.api.dto;

import lombok.*;

/**
 * DTO returned by {@code GET /api/health/score/{sessionId}}.
 * All scores are in the range 0 – 100 (higher = healthier).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthScoreDTO {
    private Double overallScore;
    private Double duplicateWasteScore;
    private Double clutterScore;
    private Double organizationScore;
    /** Human-readable tier: EXCELLENT | GOOD | FAIR | POOR */
    private String status;
    private Long   totalSize;
    private Long   duplicateWaste;
    private Long   clutteredSize;
    private Long   temporaryFileSize;
}
