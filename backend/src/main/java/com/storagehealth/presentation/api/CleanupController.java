package com.storagehealth.presentation.api;

import com.storagehealth.application.service.cleanup.CleanupService;
import com.storagehealth.presentation.api.dto.CleanupInitiateRequest;
import com.storagehealth.presentation.api.dto.CleanupSessionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cleanup")
public class CleanupController {

    private final CleanupService cleanupService;

    @Autowired
    public CleanupController(CleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<CleanupSessionDTO> initiateCleanup(@RequestBody CleanupInitiateRequest request) {
        CleanupSessionDTO session = cleanupService.initiateCleanup(request.getFileIds());
        return ResponseEntity.ok(session);
    }

    @PostMapping("/execute/{sessionId}")
    public ResponseEntity<Void> executeCleanup(@PathVariable String sessionId) {
        cleanupService.executeCleanup(sessionId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/undo/{sessionId}")
    public ResponseEntity<Void> undoCleanup(@PathVariable String sessionId) {
        cleanupService.undoCleanup(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<CleanupSessionDTO>> listSessions() {
        return ResponseEntity.ok(cleanupService.listSessions());
    }
}
