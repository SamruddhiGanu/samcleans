package com.storagehealth.presentation.api;

import com.storagehealth.application.service.duplicate.DuplicateDetector;
import com.storagehealth.domain.entity.RecommendationEntity;
import com.storagehealth.domain.entity.RecommendationType;
import com.storagehealth.domain.entity.ScanSessionEntity;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for duplicate-detection and recommendation endpoints.
 *
 * <pre>
 *  POST /api/duplicates/detect/{sessionId}  — run detection and store recommendations
 *  GET  /api/duplicates/recommendations     — paginated list of duplicate recommendations
 * </pre>
 */
@RestController
@RequestMapping("/api/duplicates")
@Slf4j
public class DuplicateController {

    private final DuplicateDetector duplicateDetector;
    private final ScanSessionRepository scanSessionRepository;
    private final RecommendationRepository recommendationRepository;

    @Autowired
    public DuplicateController(DuplicateDetector duplicateDetector,
                               ScanSessionRepository scanSessionRepository,
                               RecommendationRepository recommendationRepository) {
        this.duplicateDetector        = duplicateDetector;
        this.scanSessionRepository    = scanSessionRepository;
        this.recommendationRepository = recommendationRepository;
    }

    // ---------------------------------------------------------------
    // Endpoints
    // ---------------------------------------------------------------

    /**
     * Runs exact-duplicate detection on the given completed scan session
     * and persists recommendations for all duplicate files found.
     */
    @PostMapping("/detect/{sessionId}")
    public ResponseEntity<DuplicateAnalysisDTO> detectDuplicates(@PathVariable Long sessionId) {
        Optional<ScanSessionEntity> session = scanSessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            log.warn("Duplicate detection requested for unknown session: {}", sessionId);
            return ResponseEntity.notFound().build();
        }

        List<DuplicateDetector.DuplicateGroup> duplicates =
            duplicateDetector.findExactDuplicates(session.get());
        duplicateDetector.markDuplicates(duplicates);

        long totalRecoverable = duplicates.stream()
            .mapToLong(DuplicateDetector.DuplicateGroup::getRecoverableSpace)
            .sum();

        List<DuplicateGroupDTO> groupDTOs = duplicates.stream()
            .map(g -> new DuplicateGroupDTO(
                g.getHashValue(),
                g.getFiles().size(),
                g.getTotalSize(),
                g.getRecoverableSpace(),
                g.getFiles().stream().map(f -> f.getPath()).collect(Collectors.toList())))
            .collect(Collectors.toList());

        int totalDuplicateFiles = duplicates.stream()
            .mapToInt(g -> g.getFiles().size())
            .sum();

        return ResponseEntity.ok(DuplicateAnalysisDTO.builder()
            .groupCount(duplicates.size())
            .totalDuplicateFiles(totalDuplicateFiles)
            .totalRecoverableSpace(totalRecoverable)
            .groups(groupDTOs)
            .build());
    }

    /**
     * Returns a paginated list of unacted-on duplicate recommendations.
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Page<RecommendationDTO>> getDuplicateRecommendations(Pageable pageable) {
        return ResponseEntity.ok(
            recommendationRepository.findByTypeAndIsActedOnFalse(RecommendationType.DUPLICATE, pageable)
                .map(this::toDTO)
        );
    }

    // ---------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------

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
