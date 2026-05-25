package com.storagehealth.presentation.api;

import com.storagehealth.application.service.image.ImageAnalysisResult;
import com.storagehealth.application.service.image.ImageAnalysisService;
import com.storagehealth.application.service.image.NearDuplicateDetector;
import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.FileType;
import com.storagehealth.domain.entity.RecommendationEntity;
import com.storagehealth.domain.entity.RecommendationType;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.ImageAnalysisDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/images")
@Slf4j
public class ImageAnalysisController {

    private final ImageAnalysisService imageAnalysisService;
    private final NearDuplicateDetector nearDuplicateDetector;
    private final ScanSessionRepository sessionRepository;
    private final FileRepository fileRepository;
    private final RecommendationRepository recommendationRepository;

    @Autowired
    public ImageAnalysisController(ImageAnalysisService imageAnalysisService,
                                   NearDuplicateDetector nearDuplicateDetector,
                                   ScanSessionRepository sessionRepository,
                                   FileRepository fileRepository,
                                   RecommendationRepository recommendationRepository) {
        this.imageAnalysisService = imageAnalysisService;
        this.nearDuplicateDetector = nearDuplicateDetector;
        this.sessionRepository = sessionRepository;
        this.fileRepository = fileRepository;
        this.recommendationRepository = recommendationRepository;
    }

    @PostMapping("/analyze/{sessionId}")
    public ResponseEntity<Void> analyzeImages(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId).map(session -> {
            log.info("Starting image analysis for session {}", sessionId);
            List<FileEntity> images = fileRepository.findByScanSession(session).stream()
                    .filter(f -> f.getFileType() == FileType.IMAGE)
                    .toList();

            for (FileEntity img : images) {
                ImageAnalysisResult result = imageAnalysisService.analyzeImage(Paths.get(img.getPath()));
                if (result != null) {
                    img.setBlurScore(result.getBlurScore());
                    img.setBrightnessScore(result.getBrightnessScore());
                    img.setColorfulnessScore(result.getColorfulnessScore());
                    img.setIsBlurry(result.isBlurry());
                    fileRepository.save(img);

                    if (result.isBlurry()) {
                        createBlurryRecommendation(img);
                    }
                }
            }
            log.info("Finished image analysis for session {}", sessionId);
            return ResponseEntity.ok().<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/near-duplicates/{sessionId}")
    public ResponseEntity<Void> detectNearDuplicates(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId).map(session -> {
            log.info("Starting near-duplicate detection for session {}", sessionId);
            nearDuplicateDetector.findNearDuplicates(session);
            return ResponseEntity.ok().<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/blurry/{sessionId}")
    public ResponseEntity<List<ImageAnalysisDTO>> getBlurryImages(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId).map(session -> {
            List<FileEntity> blurryImages = fileRepository.findByScanSession(session).stream()
                    .filter(f -> f.getFileType() == FileType.IMAGE)
                    .filter(f -> Boolean.TRUE.equals(f.getIsBlurry()))
                    .toList();

            List<ImageAnalysisDTO> dtos = blurryImages.stream()
                    .map(img -> ImageAnalysisDTO.builder()
                            .fileId(img.getId())
                            .fileName(img.getName())
                            .blurScore(img.getBlurScore() != null ? img.getBlurScore() : 0.0)
                            .brightnessScore(img.getBrightnessScore() != null ? img.getBrightnessScore() : 0.0)
                            .colorfulnessScore(img.getColorfulnessScore() != null ? img.getColorfulnessScore() : 0.0)
                            .isBlurry(true)
                            .build())
                    .toList();

            return ResponseEntity.ok(dtos);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void createBlurryRecommendation(FileEntity file) {
        boolean exists = recommendationRepository.findByFile(file).stream()
                .anyMatch(r -> r.getExplanation().contains("Blurry image"));

        if (!exists) {
            RecommendationEntity rec = RecommendationEntity.builder()
                    .file(file)
                    .type(RecommendationType.BLURRY_IMAGE) // Corrected from DUPLICATE
                    .confidenceScore(BigDecimal.valueOf(0.95)) // High confidence for blur
                    .explanation("Blurry image detected. Consider deleting to save space.")
                    .recoverableSpace(file.getSizeBytes())
                    .build();
            recommendationRepository.save(rec);
        }
    }
}
