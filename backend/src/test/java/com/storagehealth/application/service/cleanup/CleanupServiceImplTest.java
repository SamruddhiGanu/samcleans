package com.storagehealth.application.service.cleanup;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.CleanupSessionRepository;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.presentation.api.dto.CleanupSessionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CleanupServiceImplTest {

    @Mock
    private CleanupSessionRepository sessionRepository;

    @Mock
    private FileRepository fileRepository;

    private CleanupServiceImpl cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new CleanupServiceImpl(sessionRepository, fileRepository);
    }

    @Test
    void initiateCleanup_createsSession() {
        FileEntity f1 = FileEntity.builder().id(1L).path("C:/fake/1.txt").sizeBytes(100L).build();
        FileEntity f2 = FileEntity.builder().id(2L).path("C:/fake/2.txt").sizeBytes(200L).build();

        when(fileRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(f1, f2));

        CleanupSessionDTO dto = cleanupService.initiateCleanup(List.of(1L, 2L));

        assertThat(dto.getFilesCount()).isEqualTo(2);
        assertThat(dto.getTotalSize()).isEqualTo(300L);
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        
        verify(sessionRepository, times(1)).save(any(CleanupSessionEntity.class));
    }

    @Test
    void executeCleanup_movesFilesAndUpdatesStatus(@TempDir Path tempDir) throws IOException {
        Path fakeOriginal = tempDir.resolve("original.txt");
        Files.writeString(fakeOriginal, "test");

        CleanupSessionEntity session = new CleanupSessionEntity();
        session.setSessionId("test-session");
        session.setStatus(CleanupStatus.ACTIVE);

        CleanupSessionFileEntity f = new CleanupSessionFileEntity();
        f.setCleanupSession(session);
        f.setOriginalPath(fakeOriginal.toString());
        session.getFiles().add(f);

        when(sessionRepository.findBySessionId("test-session")).thenReturn(Optional.of(session));

        Path sessionPath = Path.of(System.getProperty("user.home"), ".storage-health", "cleanup_sessions", "test-session", "files");
        Files.createDirectories(sessionPath);

        cleanupService.executeCleanup("test-session");

        assertThat(session.getStatus()).isEqualTo(CleanupStatus.COMPLETED);
        assertThat(f.getArchivedPath()).isNotNull();
        assertThat(Files.exists(fakeOriginal)).isFalse(); // Should have been moved

        verify(sessionRepository, times(1)).save(session);
    }

    @Test
    void undoCleanup_restoresFiles(@TempDir Path tempDir) throws IOException {
        Path archiveDir = tempDir.resolve("archive");
        Files.createDirectories(archiveDir);
        Path archivedFile = archiveDir.resolve("original.txt");
        Files.writeString(archivedFile, "test");

        Path originalLocation = tempDir.resolve("restored.txt");

        CleanupSessionEntity session = new CleanupSessionEntity();
        session.setSessionId("undo-session");
        session.setStatus(CleanupStatus.COMPLETED);

        CleanupSessionFileEntity f = new CleanupSessionFileEntity();
        f.setCleanupSession(session);
        f.setArchivedPath(archivedFile.toString());
        f.setOriginalPath(originalLocation.toString());
        session.getFiles().add(f);

        when(sessionRepository.findBySessionId("undo-session")).thenReturn(Optional.of(session));

        cleanupService.undoCleanup("undo-session");

        assertThat(session.getStatus()).isEqualTo(CleanupStatus.RESTORED);
        assertThat(Files.exists(archivedFile)).isFalse(); // Should have been moved back
        assertThat(Files.exists(originalLocation)).isTrue(); // Back to original

        verify(sessionRepository, times(1)).save(session);
    }
}
