package com.storagehealth.application.service.scanner;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.ScanSessionEntity;
import com.storagehealth.domain.entity.ScanStatus;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FileScannerImpl}.
 * Uses {@link TempDir} for real filesystem interaction and mocks for the database.
 */
class FileScannerImplTest {

    @TempDir
    Path tempDir;

    @Mock FileRepository fileRepository;
    @Mock ScanSessionRepository sessionRepository;

    @InjectMocks
    FileScannerImpl scanner;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        scanner.shutdown();
    }

    // ---------------------------------------------------------------
    // scanDirectory tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("scanDirectory persists each regular file found in the directory")
    void scanDirectory_persistsFiles() throws Exception {
        Path file1 = tempDir.resolve("report.pdf");
        Path file2 = tempDir.resolve("photo.jpg");
        Files.createFile(file1);
        Files.createFile(file2);

        ScanSessionEntity session = buildSession(1L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(fileRepository.findByPath(anyString())).thenReturn(Optional.empty());

        scanner.scanDirectory(tempDir.toString(), 1L);

        // Two files should have been saved
        verify(fileRepository, atLeast(2)).save(any(FileEntity.class));
    }

    @Test
    @DisplayName("scanDirectory marks session as COMPLETED on success")
    void scanDirectory_marksCompleted() throws Exception {
        Files.createFile(tempDir.resolve("data.txt"));

        ScanSessionEntity session = buildSession(2L);
        when(sessionRepository.findById(2L)).thenReturn(Optional.of(session));
        when(fileRepository.findByPath(anyString())).thenReturn(Optional.empty());

        scanner.scanDirectory(tempDir.toString(), 2L);

        assertThat(session.getStatus()).isEqualTo(ScanStatus.COMPLETED);
    }

    @Test
    @DisplayName("scanDirectory re-associates existing files with the scan session")
    void scanDirectory_reAssociatesExistingFiles() throws Exception {
        Path existing = tempDir.resolve("existing.txt");
        Files.createFile(existing);

        ScanSessionEntity session = buildSession(3L);
        when(sessionRepository.findById(3L)).thenReturn(Optional.of(session));

        // Simulate the file is already in the database
        FileEntity alreadySaved = FileEntity.builder()
            .id(100L)
            .path(existing.toString()).name("existing.txt").sizeBytes(0L).build();
        when(fileRepository.findByPath(existing.toString())).thenReturn(Optional.of(alreadySaved));

        scanner.scanDirectory(tempDir.toString(), 3L);

        // It should have called save to update the session and timestamps
        verify(fileRepository, times(1)).save(alreadySaved);
        assertThat(alreadySaved.getScanSession()).isEqualTo(session);
    }

    @Test
    @DisplayName("scanDirectory throws IllegalArgumentException for unknown session ID")
    void scanDirectory_unknownSession() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanner.scanDirectory(tempDir.toString(), 999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("scanDirectory tracks progress — totalFilesFound equals file count")
    void scanDirectory_tracksProgress() throws Exception {
        Files.createFile(tempDir.resolve("a.txt"));
        Files.createFile(tempDir.resolve("b.txt"));
        Files.createFile(tempDir.resolve("c.txt"));

        ScanSessionEntity session = buildSession(4L);
        when(sessionRepository.findById(4L)).thenReturn(Optional.of(session));
        when(fileRepository.findByPath(anyString())).thenReturn(Optional.empty());

        scanner.scanDirectory(tempDir.toString(), 4L);

        assertThat(scanner.getProgress().totalFilesFound).isEqualTo(3);
    }

    // ---------------------------------------------------------------
    // cancelScan test
    // ---------------------------------------------------------------

    @Test
    @DisplayName("cancelScan sets the cancel flag without throwing")
    void cancelScan_setsFlagSafely() {
        assertThatCode(() -> scanner.cancelScan()).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------
    // Builder helper
    // ---------------------------------------------------------------

    private ScanSessionEntity buildSession(Long id) {
        return ScanSessionEntity.builder()
            .id(id).scanPath(tempDir.toString()).status(ScanStatus.INITIATED).build();
    }
}
