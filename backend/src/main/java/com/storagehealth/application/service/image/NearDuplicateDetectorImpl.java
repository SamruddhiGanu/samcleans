package com.storagehealth.application.service.image;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileHashRepository;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class NearDuplicateDetectorImpl implements NearDuplicateDetector {

    // Threshold out of 64 bits. <= 10 means highly similar.
    private static final int HAMMING_DISTANCE_THRESHOLD = 10;

    private final FileRepository fileRepository;
    private final FileHashRepository hashRepository;
    private final RecommendationRepository recommendationRepository;
    private final PerceptualHashService phashService;

    @Autowired
    public NearDuplicateDetectorImpl(FileRepository fileRepository,
                                     FileHashRepository hashRepository,
                                     RecommendationRepository recommendationRepository,
                                     PerceptualHashService phashService) {
        this.fileRepository = fileRepository;
        this.hashRepository = hashRepository;
        this.recommendationRepository = recommendationRepository;
        this.phashService = phashService;
    }

    @Override
    public void findNearDuplicates(ScanSessionEntity session) {
        log.info("Starting near-duplicate detection for session {}", session.getId());
        
        List<FileEntity> images = fileRepository.findByScanSession(session).stream()
            .filter(f -> f.getFileType() == FileType.IMAGE)
            .toList();

        log.debug("Found {} image files to analyze", images.size());

        List<ImageWithHash> hashedImages = new ArrayList<>();

        for (FileEntity img : images) {
            String hash = getOrComputePHash(img);
            if (hash != null) {
                hashedImages.add(new ImageWithHash(img, hash));
            }
        }

        int found = 0;
        // O(N^2) comparison. N is expected to be small-to-medium for images per scan.
        for (int i = 0; i < hashedImages.size(); i++) {
            for (int j = i + 1; j < hashedImages.size(); j++) {
                ImageWithHash img1 = hashedImages.get(i);
                ImageWithHash img2 = hashedImages.get(j);

                int dist = phashService.hammingDistance(img1.hash, img2.hash);
                if (dist != -1 && dist <= HAMMING_DISTANCE_THRESHOLD) {
                    found++;
                    createRecommendation(img1.file, img2.file, dist);
                    // Also mirror the recommendation so both files show it
                    createRecommendation(img2.file, img1.file, dist);
                }
            }
        }

        log.info("Completed near-duplicate detection. Found {} similar pairs.", found);
    }

    private String getOrComputePHash(FileEntity file) {
        // Check DB first
        var existingHash = hashRepository.findByFileAndHashType(file, HashType.PHASH);
        if (existingHash.isPresent()) {
            return existingHash.get().getHashValue();
        }

        // Compute it
        Path path = Paths.get(file.getPath());
        String hash = phashService.computeHash(path);

        if (hash != null) {
            FileHashEntity hashEntity = FileHashEntity.builder()
                .file(file)
                .hashType(HashType.PHASH)
                .hashValue(hash)
                .build();
            hashRepository.save(hashEntity);
        }

        return hash;
    }

    private void createRecommendation(FileEntity target, FileEntity similarTo, int distance) {
        // Check idempotency: already have a DUPLICATE / NEAR_DUPLICATE recommendation?
        // Note: For now we'll just use DUPLICATE as type, with a special explanation.
        // Or if you want a dedicated enum, we could add NEAR_DUPLICATE. 
        // Using existing DUPLICATE type to keep enum simple, but clarifying in explanation.
        
        boolean exists = recommendationRepository.findByFile(target).stream()
            .anyMatch(r -> r.getType() == RecommendationType.DUPLICATE 
                           && r.getExplanation().contains(similarTo.getName()));

        if (exists) return;

        // Confidence inversely proportional to distance. 
        // Dist 0 = 1.0 (100%), Dist 10 = ~0.84
        double conf = 1.0 - (distance / 64.0);

        RecommendationEntity rec = RecommendationEntity.builder()
            .file(target)
            .type(RecommendationType.NEAR_DUPLICATE)
            .confidenceScore(BigDecimal.valueOf(conf))
            .explanation("Visually similar to: " + similarTo.getName() + " (Distance: " + distance + ")")
            .recoverableSpace(target.getSizeBytes())
            .build();

        recommendationRepository.save(rec);
    }

    private record ImageWithHash(FileEntity file, String hash) {}
}
