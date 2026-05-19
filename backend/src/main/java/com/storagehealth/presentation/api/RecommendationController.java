package com.storagehealth.presentation.api;

import com.storagehealth.application.service.recommendations.RecommendationEngine;
import com.storagehealth.domain.entity.RecommendationEntity;
import com.storagehealth.domain.entity.RecommendationType;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.RecommendationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing recommendation management endpoints.
 *
 * <pre>
 *  POST  /api/recommendations/generate/{sessionId}  — run recommendation engine
 *  GET   /api/recommendations/list                  — paginated list (optional type filter)
 *  PATCH /api/recommendations/{id}/acted            — mark a recommendation as acted-on
 * </pre>
 */
@RestController
@RequestMapping("/api/recommendations")
@Slf4j
public class RecommendationController {

    private final RecommendationEngine recommendationEngine;
    private final RecommendationRepository recommendationRepository;
    private final ScanSessionRepository sessionRepository;

    @Autowired
    public RecommendationController(RecommendationEngine recommendationEngine,
                                    RecommendationRepository recommendationRepository,
                                    ScanSessionRepository sessionRepository) {
        this.recommendationEngine     = recommendationEngine;
        this.recommendationRepository = recommendationRepository;
        this.sessionRepository        = sessionRepository;
    }

    /** Runs the recommendation engine against all files in the given session. */
    @PostMapping("/generate/{sessionId}")
    public ResponseEntity<Void> generateRecommendations(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
            .map(session -> {
                log.info("Generating recommendations for session {}", sessionId);
                recommendationEngine.generateRecommendations(session);
                return ResponseEntity.ok().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns a paginated list of recommendations.
     * If {@code type} is provided it filters by {@link RecommendationType};
     * otherwise returns all unacted-on recommendations.
     */
    @GetMapping("/list")
    public ResponseEntity<Page<RecommendationDTO>> listRecommendations(
            @RequestParam(required = false) String type,
            Pageable pageable) {

        Page<RecommendationEntity> page;

        if (type != null && !type.isBlank()) {
            try {
                RecommendationType rType = RecommendationType.valueOf(type.toUpperCase());
                page = recommendationRepository.findByType(rType, pageable);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown recommendation type requested: {}", type);
                return ResponseEntity.badRequest().build();
            }
        } else {
            page = recommendationRepository.findByIsActedOnFalse(pageable);
        }

        return ResponseEntity.ok(page.map(this::toDTO));
    }

    /** Marks a specific recommendation as acted-on (e.g. file was deleted). */
    @PatchMapping("/{id}/acted")
    public ResponseEntity<Void> markActedOn(@PathVariable Long id) {
        return recommendationRepository.findById(id)
            .map(rec -> {
                rec.setIsActedOn(true);
                recommendationRepository.save(rec);
                log.info("Recommendation {} marked as acted-on", id);
                return ResponseEntity.ok().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private RecommendationDTO toDTO(RecommendationEntity e) {
        return RecommendationDTO.builder()
            .id(e.getId())
            .fileId(e.getFile().getId())
            .fileName(e.getFile().getName())
            .filePath(e.getFile().getPath())
            .type(e.getType().name())
            .confidenceScore(e.getConfidenceScore())
            .explanation(e.getExplanation())
            .recoverableSpace(e.getRecoverableSpace())
            .build();
    }
}
