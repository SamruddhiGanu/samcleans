package com.storagehealth.application.service.image;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.FileType;
import com.storagehealth.domain.entity.RecommendationType;
import com.storagehealth.domain.entity.ScanSessionEntity;
import com.storagehealth.infrastructure.repository.FileHashRepository;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NearDuplicateDetectorImplTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileHashRepository hashRepository;

    @Mock
    private RecommendationRepository recommendationRepository;

    @Mock
    private PerceptualHashService phashService;

    private NearDuplicateDetectorImpl detector;

    @BeforeEach
    void setUp() {
        detector = new NearDuplicateDetectorImpl(fileRepository, hashRepository, recommendationRepository, phashService);
    }

    @Test
    void findNearDuplicates_createsRecommendationsForSimilarPairs() {
        ScanSessionEntity session = ScanSessionEntity.builder().id(1L).build();

        FileEntity img1 = FileEntity.builder().id(101L).name("photo1.jpg").path("C:/photo1.jpg").fileType(FileType.IMAGE).build();
        FileEntity img2 = FileEntity.builder().id(102L).name("photo2.jpg").path("C:/photo2.jpg").fileType(FileType.IMAGE).build();

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(img1, img2));

        when(hashRepository.findByFileAndHashType(eq(img1), any())).thenReturn(Optional.empty());
        when(hashRepository.findByFileAndHashType(eq(img2), any())).thenReturn(Optional.empty());

        when(phashService.computeHash(any())).thenReturn("1010101010101010"); // Dummy hash
        when(phashService.hammingDistance(anyString(), anyString())).thenReturn(5); // distance <= 10 means near-duplicate

        when(recommendationRepository.findByFile(any())).thenReturn(List.of()); // No existing recommendations

        detector.findNearDuplicates(session);

        // Should save hashes and create recommendations (one for each side)
        verify(hashRepository, times(2)).save(any());
        verify(recommendationRepository, times(2)).save(argThat(rec -> rec.getType() == RecommendationType.DUPLICATE));
    }
}
