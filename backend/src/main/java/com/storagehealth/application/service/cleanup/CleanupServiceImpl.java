package com.storagehealth.application.service.cleanup;

import com.storagehealth.domain.entity.CleanupSessionEntity;
import com.storagehealth.domain.entity.CleanupSessionFileEntity;
import com.storagehealth.domain.entity.CleanupStatus;
import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.infrastructure.repository.CleanupSessionRepository;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.presentation.api.dto.CleanupSessionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class CleanupServiceImpl implements CleanupService {
    private static final String CLEANUP_BASE_PATH = System.getProperty("user.home") + "/.storage-health/cleanup_sessions/";

    private final CleanupSessionRepository cleanupSessionRepository;
    private final FileRepository fileRepository;

    @Autowired
    public CleanupServiceImpl(CleanupSessionRepository cleanupSessionRepository, FileRepository fileRepository) {
        this.cleanupSessionRepository = cleanupSessionRepository;
        this.fileRepository = fileRepository;
    }

    @Override
    public CleanupSessionDTO initiateCleanup(List<Long> fileIds) {
        String sessionId = UUID.randomUUID().toString();

        List<FileEntity> files = fileRepository.findAllById(fileIds);
        long totalSize = files.stream().mapToLong(FileEntity::getSizeBytes).sum();

        CleanupSessionEntity session = CleanupSessionEntity.builder()
                .sessionId(sessionId)
                .filesCount(files.size())
                .totalSize(totalSize)
                .status(CleanupStatus.ACTIVE)
                .build();

        for (FileEntity file : files) {
            CleanupSessionFileEntity cleanupFile = CleanupSessionFileEntity.builder()
                    .cleanupSession(session)
                    .file(file)
                    .originalPath(file.getPath())
                    .build();
            session.getFiles().add(cleanupFile);
        }

        cleanupSessionRepository.save(session);

        Path sessionPath = Paths.get(CLEANUP_BASE_PATH, sessionId);
        try {
            Files.createDirectories(sessionPath);
            Files.createDirectory(sessionPath.resolve("files"));
        } catch (IOException e) {
            log.error("Failed to create cleanup session directory", e);
            throw new RuntimeException("Could not create cleanup directory", e);
        }

        return mapToDTO(session);
    }

    @Override
    public void executeCleanup(String sessionId) {
        Optional<CleanupSessionEntity> sessionOpt = cleanupSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Cleanup session not found: " + sessionId);
        }

        CleanupSessionEntity entity = sessionOpt.get();
        Path sessionPath = Paths.get(CLEANUP_BASE_PATH, sessionId);
        Path filesPath = sessionPath.resolve("files");

        try {
            for (CleanupSessionFileEntity file : entity.getFiles()) {
                Path originalPath = Paths.get(file.getOriginalPath());
                Path archivedPath = filesPath.resolve(originalPath.getFileName().toString());

                if (Files.exists(originalPath)) {
                    Files.move(originalPath, archivedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    file.setArchivedPath(archivedPath.toString());
                } else {
                    log.warn("File no longer exists at original path: {}", originalPath);
                }
            }

            entity.setStatus(CleanupStatus.COMPLETED);
            cleanupSessionRepository.save(entity);

            log.info("Cleanup session {} completed", sessionId);

        } catch (IOException e) {
            log.error("Cleanup failed for session: {}", sessionId, e);
            entity.setStatus(CleanupStatus.ACTIVE);
            cleanupSessionRepository.save(entity);
            throw new RuntimeException("Cleanup execution failed", e);
        }
    }

    @Override
    public void undoCleanup(String sessionId) {
        Optional<CleanupSessionEntity> sessionOpt = cleanupSessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("Cleanup session not found: " + sessionId);
        }

        CleanupSessionEntity entity = sessionOpt.get();

        if (entity.getStatus() != CleanupStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED sessions can be undone.");
        }

        try {
            for (CleanupSessionFileEntity file : entity.getFiles()) {
                if (file.getArchivedPath() != null) {
                    Path archivedPath = Paths.get(file.getArchivedPath());
                    Path originalPath = Paths.get(file.getOriginalPath());

                    if (Files.exists(archivedPath)) {
                        Files.createDirectories(originalPath.getParent());
                        Files.move(archivedPath, originalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    }
                }
            }

            entity.setStatus(CleanupStatus.RESTORED);
            cleanupSessionRepository.save(entity);

            log.info("Cleanup session {} restored", sessionId);

        } catch (IOException e) {
            log.error("Undo failed for session: {}", sessionId, e);
            throw new RuntimeException("Undo execution failed", e);
        }
    }

    @Override
    public List<CleanupSessionDTO> listSessions() {
        return cleanupSessionRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private CleanupSessionDTO mapToDTO(CleanupSessionEntity entity) {
        return CleanupSessionDTO.builder()
                .sessionId(entity.getSessionId())
                .filesCount(entity.getFilesCount())
                .totalSize(entity.getTotalSize())
                .status(entity.getStatus().name())
                .build();
    }
}
