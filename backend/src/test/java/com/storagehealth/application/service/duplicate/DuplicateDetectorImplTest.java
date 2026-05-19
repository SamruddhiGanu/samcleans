package com.storagehealth.application.service.duplicate;

import com.storagehealth.application.service.hashing.HashingService;
import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DuplicateDetectorImpl}.
 * All dependencies are mocked — no database or real filesystem required.
 */
class DuplicateDetectorImplTest {

    @Mock FileRepository fileRepository;
    @Mock FileHashRepository hashRepository;
    @Mock HashingService hashingService;
    @Mock RecommendationRepository recommendationRepository;

    @InjectMocks
    DuplicateDetectorImpl detector;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ---------------------------------------------------------------
    // findExactDuplicates tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("findExactDuplicates returns empty list when no files in session")
    void findExactDuplicates_emptySession() {
        ScanSessionEntity session = buildSession(1L);
        when(fileRepository.findByScanSession(session)).thenReturn(List.of());

        List<DuplicateDetector.DuplicateGroup> result = detector.findExactDuplicates(session);

        assertThat(result).isEmpty();
        verifyNoInteractions(hashingService);
    }

    @Test
    @DisplayName("findExactDuplicates identifies two identical files as one duplicate group")
    void findExactDuplicates_twoIdenticalFiles() throws IOException {
        ScanSessionEntity session = buildSession(1L);

        FileEntity f1 = buildFile(1L, "/data/file.txt", 1024L);
        FileEntity f2 = buildFile(2L, "/backup/file.txt", 1024L);

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(f1, f2));
        when(hashRepository.findByFileAndHashType(any(), eq(HashType.SHA256))).thenReturn(Optional.empty());
        when(hashingService.sha256Hash(any(Path.class))).thenReturn("aabbccdd");

        List<DuplicateDetector.DuplicateGroup> groups = detector.findExactDuplicates(session);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getFiles()).hasSize(2);
        assertThat(groups.get(0).getRecoverableSpace()).isEqualTo(1024L);
    }

    @Test
    @DisplayName("findExactDuplicates skips files with different sizes (no hashing needed)")
    void findExactDuplicates_differentSizesSkipped() throws IOException {
        ScanSessionEntity session = buildSession(2L);

        FileEntity f1 = buildFile(1L, "/a.txt", 100L);
        FileEntity f2 = buildFile(2L, "/b.txt", 200L);

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(f1, f2));

        List<DuplicateDetector.DuplicateGroup> groups = detector.findExactDuplicates(session);

        assertThat(groups).isEmpty();
        verifyNoInteractions(hashingService);
    }

    @Test
    @DisplayName("findExactDuplicates reuses existing hash records to avoid re-hashing")
    void findExactDuplicates_reusesExistingHash() throws IOException {
        ScanSessionEntity session = buildSession(3L);

        FileEntity f1 = buildFile(1L, "/x.jpg", 512L);
        FileEntity f2 = buildFile(2L, "/y.jpg", 512L);

        FileHashEntity existingHash1 = FileHashEntity.builder()
            .file(f1).hashType(HashType.SHA256).hashValue("samehash").build();
        FileHashEntity existingHash2 = FileHashEntity.builder()
            .file(f2).hashType(HashType.SHA256).hashValue("samehash").build();

        when(fileRepository.findByScanSession(session)).thenReturn(List.of(f1, f2));
        when(hashRepository.findByFileAndHashType(f1, HashType.SHA256)).thenReturn(Optional.of(existingHash1));
        when(hashRepository.findByFileAndHashType(f2, HashType.SHA256)).thenReturn(Optional.of(existingHash2));

        List<DuplicateDetector.DuplicateGroup> groups = detector.findExactDuplicates(session);

        assertThat(groups).hasSize(1);
        // hashingService.sha256Hash should NOT be called since records existed
        verify(hashingService, never()).sha256Hash(any(Path.class));
    }

    // ---------------------------------------------------------------
    // markDuplicates tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("markDuplicates creates recommendations for all non-primary duplicates")
    void markDuplicates_createsRecommendations() {
        FileEntity primary   = buildFile(1L, "/original.pdf", 2048L);
        FileEntity duplicate = buildFile(2L, "/copy.pdf",     2048L);

        DuplicateDetector.DuplicateGroup group = new DuplicateDetector.DuplicateGroup(
            "hash123", List.of(primary, duplicate), 4096L, 2048L);

        detector.markDuplicates(List.of(group));

        verify(recommendationRepository, times(1)).save(argThat(rec ->
            rec.getFile().equals(duplicate) &&
            rec.getType() == RecommendationType.DUPLICATE &&
            rec.getConfidenceScore().compareTo(BigDecimal.valueOf(1.0)) == 0
        ));
    }

    @Test
    @DisplayName("markDuplicates does NOT create a recommendation for the primary file")
    void markDuplicates_primaryNotMarked() {
        FileEntity primary   = buildFile(1L, "/keep.doc",  500L);
        FileEntity duplicate = buildFile(2L, "/dupe.doc",  500L);

        DuplicateDetector.DuplicateGroup group = new DuplicateDetector.DuplicateGroup(
            "hxyz", List.of(primary, duplicate), 1000L, 500L);

        detector.markDuplicates(List.of(group));

        // Only 1 save call — for the duplicate, not the primary
        verify(recommendationRepository, times(1)).save(any());
    }

    // ---------------------------------------------------------------
    // DuplicateGroup.calculate() tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("DuplicateGroup.calculate correctly computes totalSize and recoverableSpace")
    void duplicateGroup_calculate() {
        FileEntity f1 = buildFile(1L, "/a", 1000L);
        FileEntity f2 = buildFile(2L, "/b", 1000L);
        FileEntity f3 = buildFile(3L, "/c", 1000L);

        DuplicateDetector.DuplicateGroup group = new DuplicateDetector.DuplicateGroup(
            "abc", List.of(f1, f2, f3), 0L, 0L);
        group.calculate();

        assertThat(group.getTotalSize()).isEqualTo(3000L);
        assertThat(group.getRecoverableSpace()).isEqualTo(2000L); // (3-1) × 1000
    }

    // ---------------------------------------------------------------
    // Builders
    // ---------------------------------------------------------------

    private ScanSessionEntity buildSession(Long id) {
        return ScanSessionEntity.builder()
            .id(id).scanPath("/test").status(ScanStatus.COMPLETED).build();
    }

    private FileEntity buildFile(Long id, String path, Long size) {
        return FileEntity.builder()
            .id(id).path(path).name(Path.of(path).getFileName().toString())
            .sizeBytes(size).fileType(FileType.DOCUMENT).build();
    }
}
