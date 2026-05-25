package com.storagehealth.application.service.cleanup;

import com.storagehealth.presentation.api.dto.CleanupSessionDTO;

import java.util.List;

public interface CleanupService {
    CleanupSessionDTO initiateCleanup(List<Long> fileIds);
    void executeCleanup(String sessionId);
    void undoCleanup(String sessionId);
    List<CleanupSessionDTO> listSessions();
}
