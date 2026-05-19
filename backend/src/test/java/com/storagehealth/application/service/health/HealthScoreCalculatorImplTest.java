package com.storagehealth.application.service.health;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HealthScoreCalculatorImpl}.
 */
class HealthScoreCalculatorImplTest {

    @Mock FileRepository fileRepository;
    @Mock RecommendationRepository recommendationRepository;
    @InjectMocks HealthScoreCalculatorImpl calculator;

    private AutoCloseable mocks;

    @BeforeEach void setUp()           { mocks = MockitoAnnotations.openMocks(this); }
    @AfterEach  void tearDown() throws Exception { mocks.close(); }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Returns zero score for an empty session (no files)")
    void emptySession_returnsZero() {
        ScanSessionEntity session = session(1L);
        when(fileRepository.findByScanSession(session)).thenReturn(List.of());
        when(recommendationRepository.findByType(RecommendationType.DUPLICATE)).thenReturn(List.of());

        StorageHealthScore score = calculator.calculateHealthScore(session);

        assertThat(score.getOverallScore()).isEqualTo(0.0);
        assertThat(score.getTotalSize()).isEqualTo(0L);
    }

    // ---------------------------------------------------------------
    // Overall score composition
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Overall score is in range 0–100 for a normal session")
    void overallScore_inRange() {
        ScanSessionEntity session = session(2L);
        List<FileEntity> files = List.of(
            file(FileType.DOCUMENT, 5_000_000L),
            file(FileType.IMAGE,    2_000_000L),
            file(FileType.TEMPORARY, 500_000L)
        );
        when(fileRepository.findByScanSession(session)).thenReturn(files);
        when(recommendationRepository.findByType(RecommendationType.DUPLICATE)).thenReturn(List.of());

        StorageHealthScore score = calculator.calculateHealthScore(session);

        assertThat(score.getOverallScore()).isBetween(0.0, 100.0);
        assertThat(score.getDuplicateWasteScore()).isBetween(0.0, 100.0);
        assertThat(score.getClutterScore()).isBetween(0.0, 100.0);
        assertThat(score.getOrganizationScore()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Duplicate waste reduces the overall score")
    void duplicateWaste_lowersScore() {
        ScanSessionEntity session = session(3L);
        FileEntity original   = file(FileType.DOCUMENT, 10_000_000L);
        FileEntity duplicate  = file(FileType.DOCUMENT, 10_000_000L);

        RecommendationEntity rec = RecommendationEntity.builder()
            .file(duplicate)
            .type(RecommendationType.DUPLICATE)
            .confidenceScore(BigDecimal.ONE)
            .recoverableSpace(duplicate.getSizeBytes())
            .build();

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(original, duplicate));
        when(recommendationRepository.findByType(RecommendationType.DUPLICATE)).thenReturn(List.of(rec));

        StorageHealthScore withDup = calculator.calculateHealthScore(session);

        // Session without duplicates
        when(fileRepository.findByScanSession(session)).thenReturn(List.of(original));
        when(recommendationRepository.findByType(RecommendationType.DUPLICATE)).thenReturn(List.of());

        StorageHealthScore noDup = calculator.calculateHealthScore(session);

        assertThat(withDup.getOverallScore()).isLessThan(noDup.getOverallScore());
    }

    @Test
    @DisplayName("Clutter (TEMPORARY files) lowers the clutter sub-score")
    void clutterFiles_lowerClutterScore() {
        ScanSessionEntity session = session(4L);

        // Clean session — only documents
        List<FileEntity> clean = List.of(
            file(FileType.DOCUMENT, 5_000_000L),
            file(FileType.DOCUMENT, 3_000_000L)
        );

        // Cluttered session — 50% temp files
        List<FileEntity> cluttered = List.of(
            file(FileType.DOCUMENT,  5_000_000L),
            file(FileType.TEMPORARY, 5_000_000L)
        );

        when(recommendationRepository.findByType(RecommendationType.DUPLICATE)).thenReturn(List.of());

        when(fileRepository.findByScanSession(session)).thenReturn(clean);
        StorageHealthScore cleanScore = calculator.calculateHealthScore(session);

        when(fileRepository.findByScanSession(session)).thenReturn(cluttered);
        StorageHealthScore clutteredScore = calculator.calculateHealthScore(session);

        assertThat(cleanScore.getClutterScore()).isGreaterThan(clutteredScore.getClutterScore());
    }

    // ---------------------------------------------------------------
    // Health status mapping
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getHealthStatus returns correct tier labels")
    void healthStatus_tierMapping() {
        assertThat(scoreWithOverall(85.0).getHealthStatus()).isEqualTo("EXCELLENT");
        assertThat(scoreWithOverall(65.0).getHealthStatus()).isEqualTo("GOOD");
        assertThat(scoreWithOverall(45.0).getHealthStatus()).isEqualTo("FAIR");
        assertThat(scoreWithOverall(20.0).getHealthStatus()).isEqualTo("POOR");
    }

    @Test
    @DisplayName("getHealthStatus returns UNKNOWN for null overall score")
    void healthStatus_nullScore() {
        StorageHealthScore score = new StorageHealthScore();
        assertThat(score.getHealthStatus()).isEqualTo("UNKNOWN");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static long idSeq = 200;

    private ScanSessionEntity session(Long id) {
        return ScanSessionEntity.builder()
            .id(id).scanPath("/data").status(ScanStatus.COMPLETED).build();
    }

    private FileEntity file(FileType type, long sizeBytes) {
        return FileEntity.builder()
            .id(++idSeq)
            .name("f-" + idSeq)
            .path("/data/folder-" + (idSeq % 4) + "/f-" + idSeq)
            .sizeBytes(sizeBytes)
            .fileType(type)
            .build();
    }

    private StorageHealthScore scoreWithOverall(double overall) {
        return StorageHealthScore.builder().overallScore(overall).build();
    }
}
