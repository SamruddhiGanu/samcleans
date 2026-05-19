package com.storagehealth.application.service.recommendations;

import com.storagehealth.domain.entity.RecommendationType;
import com.storagehealth.domain.entity.ScanSessionEntity;
import com.storagehealth.presentation.api.dto.RecommendationDTO;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Contract for the recommendation engine that produces actionable storage-cleanup suggestions.
 */
public interface RecommendationEngine {

    /**
     * Scans all files in {@code session} and creates
     * {@link com.storagehealth.domain.entity.RecommendationEntity} records for:
     * old screenshots, temporary files, unused large files, empty folders, and stale downloads.
     */
    void generateRecommendations(ScanSessionEntity session);

    /**
     * Returns paginated recommendations of the given {@code type}, mapped to DTOs.
     */
    List<RecommendationDTO> getRecommendations(RecommendationType type, Pageable pageable);
}
