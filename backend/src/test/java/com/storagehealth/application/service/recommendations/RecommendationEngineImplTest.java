package com.storagehealth.application.service.recommendations;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.RecommendationRepository;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RecommendationEngineImpl}.
 */
class RecommendationEngineImplTest {

    @Mock FileRepository fileRepository;
    @Mock RecommendationRepository recommendationRepository;
    @InjectMocks RecommendationEngineImpl engine;

    private AutoCloseable mocks;

    @BeforeEach void setUp()           { mocks = MockitoAnnotations.openMocks(this); }
    @AfterEach  void tearDown() throws Exception { mocks.close(); }

    // ---------------------------------------------------------------
    // generateRecommendations — TEMP_FILE rule
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Temporary files receive TEMP_FILE recommendations")
    void tempFile_getsRecommendation() {
        ScanSessionEntity session = session(1L);
        FileEntity tmpFile = file("cache.tmp", FileType.TEMPORARY, 512_000L,
            LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(10));

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(tmpFile));
        when(recommendationRepository.findByFile(tmpFile)).thenReturn(List.of());

        engine.generateRecommendations(session);

        verify(recommendationRepository, times(1)).save(argThat(r ->
            r.getType() == RecommendationType.TEMP_FILE &&
            r.getFile().equals(tmpFile)
        ));
    }

    @Test
    @DisplayName("Non-temporary files do NOT receive TEMP_FILE recommendations")
    void nonTempFile_noTempRecommendation() {
        ScanSessionEntity session = session(2L);
        FileEntity doc = file("report.pdf", FileType.DOCUMENT, 2_000_000L,
            LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(3));

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(doc));
        when(recommendationRepository.findByFile(doc)).thenReturn(List.of());

        engine.generateRecommendations(session);

        verify(recommendationRepository, never()).save(argThat(r ->
            r.getType() == RecommendationType.TEMP_FILE));
    }

    // ---------------------------------------------------------------
    // OLD_SCREENSHOT rule
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Screenshot older than 6 months receives OLD_SCREENSHOT recommendation")
    void oldScreenshot_getsRecommendation() {
        ScanSessionEntity session = session(3L);
        FileEntity scr = file("Screenshot_2024.png", FileType.IMAGE, 800_000L,
            LocalDateTime.now().minusMonths(8), LocalDateTime.now().minusMonths(8));

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(scr));
        when(recommendationRepository.findByFile(scr)).thenReturn(List.of());

        engine.generateRecommendations(session);

        verify(recommendationRepository, atLeastOnce()).save(argThat(r ->
            r.getType() == RecommendationType.OLD_SCREENSHOT));
    }

    @Test
    @DisplayName("Recent screenshot (< 6 months) does NOT get OLD_SCREENSHOT recommendation")
    void recentScreenshot_noRecommendation() {
        ScanSessionEntity session = session(4L);
        FileEntity scr = file("screenshot_today.png", FileType.IMAGE, 800_000L,
            LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(5));

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(scr));
        when(recommendationRepository.findByFile(scr)).thenReturn(List.of());

        engine.generateRecommendations(session);

        verify(recommendationRepository, never()).save(argThat(r ->
            r.getType() == RecommendationType.OLD_SCREENSHOT));
    }

    // ---------------------------------------------------------------
    // UNUSED_LARGE_FILE rule
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Large file (>500MB) not accessed in 1 year gets UNUSED_LARGE_FILE recommendation")
    void unusedLargeFile_getsRecommendation() {
        ScanSessionEntity session = session(5L);
        long oversized = 600L * 1024 * 1024; // 600 MB
        FileEntity large = file("backup.zip", FileType.ARCHIVE, oversized,
            LocalDateTime.now().minusYears(2), LocalDateTime.now().minusYears(2));

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(large));
        when(recommendationRepository.findByFile(large)).thenReturn(List.of());

        engine.generateRecommendations(session);

        verify(recommendationRepository, atLeastOnce()).save(argThat(r ->
            r.getType() == RecommendationType.UNUSED_LARGE_FILE));
    }

    // ---------------------------------------------------------------
    // STALE_DOWNLOAD rule
    // ---------------------------------------------------------------

    @Test
    @DisplayName("File in Downloads not accessed for 3+ months gets STALE_DOWNLOAD recommendation")
    void staleDownload_getsRecommendation() {
        ScanSessionEntity session = session(6L);
        FileEntity dl = FileEntity.builder()
            .id(999L)
            .name("installer.exe")
            .path("C:\\Users\\Test\\Downloads\\installer.exe")
            .sizeBytes(50_000_000L)
            .fileType(FileType.EXECUTABLE)
            .accessedAt(LocalDateTime.now().minusMonths(5))
            .modifiedAt(LocalDateTime.now().minusMonths(5))
            .build();

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(dl));
        when(recommendationRepository.findByFile(dl)).thenReturn(List.of());

        engine.generateRecommendations(session);

        verify(recommendationRepository, atLeastOnce()).save(argThat(r ->
            r.getType() == RecommendationType.STALE_DOWNLOAD));
    }

    // ---------------------------------------------------------------
    // Idempotency
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Duplicate recommendations are NOT created if one already exists (idempotency)")
    void idempotent_noDuplicateRecommendations() {
        ScanSessionEntity session = session(7L);
        FileEntity tmpFile = file("old.tmp", FileType.TEMPORARY, 100_000L,
            LocalDateTime.now().minusDays(20), LocalDateTime.now().minusDays(20));

        // Simulate an existing TEMP_FILE recommendation
        RecommendationEntity existing = RecommendationEntity.builder()
            .file(tmpFile).type(RecommendationType.TEMP_FILE).build();
        when(fileRepository.findByScanSession(session)).thenReturn(List.of(tmpFile));
        when(recommendationRepository.findByFile(tmpFile)).thenReturn(List.of(existing));

        engine.generateRecommendations(session);

        // save should NOT be called because recommendation already exists
        verify(recommendationRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private static long idSeq = 300;

    private ScanSessionEntity session(Long id) {
        return ScanSessionEntity.builder()
            .id(id).scanPath("/data").status(ScanStatus.COMPLETED).build();
    }

    private FileEntity file(String name, FileType type, long size,
                            LocalDateTime modified, LocalDateTime accessed) {
        return FileEntity.builder()
            .id(++idSeq)
            .name(name)
            .path("/data/" + name)
            .sizeBytes(size)
            .fileType(type)
            .modifiedAt(modified)
            .accessedAt(accessed)
            .build();
    }
}
