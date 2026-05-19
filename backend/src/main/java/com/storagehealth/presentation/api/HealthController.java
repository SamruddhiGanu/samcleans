package com.storagehealth.presentation.api;

import com.storagehealth.application.service.health.HealthScoreCalculator;
import com.storagehealth.application.service.health.StorageHealthScore;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.HealthScoreDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing storage health score endpoints.
 *
 * <pre>
 *  GET  /api/health/score/{sessionId}  — compute and return the health score
 * </pre>
 */
@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {

    private final HealthScoreCalculator healthCalculator;
    private final ScanSessionRepository sessionRepository;

    @Autowired
    public HealthController(HealthScoreCalculator healthCalculator,
                            ScanSessionRepository sessionRepository) {
        this.healthCalculator  = healthCalculator;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Computes the storage health score for the given scan session.
     * The calculation is performed on-the-fly using the current database state.
     */
    @GetMapping("/score/{sessionId}")
    public ResponseEntity<HealthScoreDTO> getHealthScore(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
            .map(session -> {
                StorageHealthScore score = healthCalculator.calculateHealthScore(session);
                return ResponseEntity.ok(toDTO(score));
            })
            .orElseGet(() -> {
                log.warn("Health score requested for unknown session: {}", sessionId);
                return ResponseEntity.notFound().build();
            });
    }

    private HealthScoreDTO toDTO(StorageHealthScore s) {
        return HealthScoreDTO.builder()
            .overallScore(s.getOverallScore())
            .duplicateWasteScore(s.getDuplicateWasteScore())
            .clutterScore(s.getClutterScore())
            .organizationScore(s.getOrganizationScore())
            .status(s.getHealthStatus())
            .totalSize(s.getTotalSize())
            .duplicateWaste(s.getDuplicateWaste())
            .clutteredSize(s.getClutteredSize())
            .temporaryFileSize(s.getTemporaryFileSize())
            .build();
    }
}
