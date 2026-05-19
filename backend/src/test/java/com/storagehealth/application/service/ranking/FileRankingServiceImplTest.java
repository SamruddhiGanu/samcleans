package com.storagehealth.application.service.ranking;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.UserFeedbackRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FileRankingServiceImpl}.
 */
class FileRankingServiceImplTest {

    @Mock FileRepository fileRepository;
    @Mock UserFeedbackRepository feedbackRepository;
    @InjectMocks FileRankingServiceImpl rankingService;

    private AutoCloseable mocks;

    @BeforeEach void setUp()           { mocks = MockitoAnnotations.openMocks(this); }
    @AfterEach  void tearDown() throws Exception { mocks.close(); }

    // ---------------------------------------------------------------
    // computeImportanceScore tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Score is between 0.0 and 1.0 for a typical file")
    void score_inRange() {
        FileEntity file = buildFile(FileType.DOCUMENT,
            LocalDateTime.now().minusDays(5),
            LocalDateTime.now().minusDays(3));

        when(fileRepository.countDuplicatesForFile(anyLong())).thenReturn(1L);
        when(feedbackRepository.findByFile(file)).thenReturn(Optional.empty());

        double score = rankingService.computeImportanceScore(file);
        assertThat(score).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Recent DOCUMENT scores higher than old TEMPORARY file")
    void recentDocument_higherThanOldTemp() {
        FileEntity doc = buildFile(FileType.DOCUMENT,
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(1));

        FileEntity tmp = buildFile(FileType.TEMPORARY,
            LocalDateTime.now().minusYears(2),
            LocalDateTime.now().minusYears(2));

        when(fileRepository.countDuplicatesForFile(anyLong())).thenReturn(1L);
        when(feedbackRepository.findByFile(any())).thenReturn(Optional.empty());

        double docScore = rankingService.computeImportanceScore(doc);
        double tmpScore = rankingService.computeImportanceScore(tmp);

        assertThat(docScore).isGreaterThan(tmpScore);
    }

    @Test
    @DisplayName("File marked IMPORTANT gets score boost over neutral file")
    void importantFeedback_boostsScore() {
        FileEntity file = buildFile(FileType.OTHER,
            LocalDateTime.now().minusDays(100),
            LocalDateTime.now().minusDays(100));

        UserFeedbackEntity importantFeedback = new UserFeedbackEntity();
        importantFeedback.setFeedbackType("IMPORTANT");

        when(fileRepository.countDuplicatesForFile(anyLong())).thenReturn(1L);
        when(feedbackRepository.findByFile(file)).thenReturn(Optional.of(importantFeedback));

        double importantScore = rankingService.computeImportanceScore(file);

        // Neutral baseline (same file, no feedback)
        when(feedbackRepository.findByFile(file)).thenReturn(Optional.empty());
        double neutralScore = rankingService.computeImportanceScore(file);

        assertThat(importantScore).isGreaterThan(neutralScore);
    }

    @Test
    @DisplayName("Duplicate files (count=3) score lower than unique file (count=1)")
    void duplicate_lowerUniqueness() {
        FileEntity unique    = buildFile(FileType.IMAGE, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(5));
        FileEntity duplicate = buildFile(FileType.IMAGE, LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(5));

        when(feedbackRepository.findByFile(any())).thenReturn(Optional.empty());
        when(fileRepository.countDuplicatesForFile(unique.getId())).thenReturn(1L);
        when(fileRepository.countDuplicatesForFile(duplicate.getId())).thenReturn(3L);

        double uniqueScore = rankingService.computeImportanceScore(unique);
        double dupScore    = rankingService.computeImportanceScore(duplicate);

        assertThat(uniqueScore).isGreaterThan(dupScore);
    }

    @Test
    @DisplayName("File with DELETE feedback scores lower than neutral")
    void deleteFeedback_lowersScore() {
        FileEntity file = buildFile(FileType.DOCUMENT,
            LocalDateTime.now().minusDays(5),
            LocalDateTime.now().minusDays(2));

        UserFeedbackEntity deleteFeedback = new UserFeedbackEntity();
        deleteFeedback.setFeedbackType("DELETE");

        when(fileRepository.countDuplicatesForFile(anyLong())).thenReturn(1L);
        when(feedbackRepository.findByFile(file)).thenReturn(Optional.of(deleteFeedback));
        double deleteScore = rankingService.computeImportanceScore(file);

        when(feedbackRepository.findByFile(file)).thenReturn(Optional.empty());
        double neutralScore = rankingService.computeImportanceScore(file);

        assertThat(deleteScore).isLessThan(neutralScore);
    }

    // ---------------------------------------------------------------
    // rankFiles tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("rankFiles saves importance score back for every file in session")
    void rankFiles_savesAllFiles() {
        ScanSessionEntity session = ScanSessionEntity.builder()
            .id(1L).scanPath("/test").status(ScanStatus.COMPLETED).build();

        FileEntity f1 = buildFile(FileType.DOCUMENT, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        FileEntity f2 = buildFile(FileType.TEMPORARY, LocalDateTime.now().minusYears(2), LocalDateTime.now().minusYears(2));

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(f1, f2));
        when(fileRepository.countDuplicatesForFile(anyLong())).thenReturn(1L);
        when(feedbackRepository.findByFile(any())).thenReturn(Optional.empty());

        rankingService.rankFiles(session);

        verify(fileRepository, times(2)).save(any(FileEntity.class));
        assertThat(f1.getImportanceScore()).isNotNull().isBetween(0.0, 1.0);
        assertThat(f2.getImportanceScore()).isNotNull().isBetween(0.0, 1.0);
    }

    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private static long idSeq = 100;

    private FileEntity buildFile(FileType type, LocalDateTime modified, LocalDateTime accessed) {
        return FileEntity.builder()
            .id(++idSeq)
            .name("file-" + idSeq + ".dat")
            .path("/test/file-" + idSeq + ".dat")
            .sizeBytes(1024L)
            .fileType(type)
            .modifiedAt(modified)
            .accessedAt(accessed)
            .build();
    }
}
