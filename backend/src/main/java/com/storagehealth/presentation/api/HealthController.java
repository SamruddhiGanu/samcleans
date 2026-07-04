package com.storagehealth.presentation.api;

import com.storagehealth.application.service.health.HealthScoreCalculator;
import com.storagehealth.application.service.health.StorageHealthScore;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.FileTypeBreakdownDTO;
import com.storagehealth.presentation.api.dto.HealthScoreDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller exposing storage health score endpoints.
 *
 * <pre>
 *  GET  /api/health/score/{sessionId}      — compute and return the health score
 *  GET  /api/health/breakdown/{sessionId}  — return real file type size breakdown
 * </pre>
 */
@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {

    private final HealthScoreCalculator healthCalculator;
    private final ScanSessionRepository sessionRepository;
    private final FileRepository fileRepository;

    @Autowired
    public HealthController(HealthScoreCalculator healthCalculator,
                            ScanSessionRepository sessionRepository,
                            FileRepository fileRepository) {
        this.healthCalculator  = healthCalculator;
        this.sessionRepository = sessionRepository;
        this.fileRepository    = fileRepository;
    }

    /**
     * Computes the storage health score for the given scan session.
     * The calculation is performed on-the-fly using the current database state.
     */
    @GetMapping("/score/{sessionId}")
    public ResponseEntity<HealthScoreDTO> getHealthScore(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
            .map(session -> {
                StorageHealthScore score = healthCalculator.calculateHealthScore(session);
                return ResponseEntity.ok(toDTO(score));
            })
            .orElseGet(() -> {
                log.warn("Health score requested for unknown session: {}", sessionId);
                return ResponseEntity.notFound().build();
            });
    }

    /**
     * Returns the real bytes-per-file-type breakdown for a scan session.
     * Used to render the storage breakdown chart on the dashboard.
     */
    @GetMapping("/breakdown/{sessionId}")
    public ResponseEntity<FileTypeBreakdownDTO> getBreakdown(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
            .map(session -> {
                var rows = fileRepository.sumSizeByFileType(session);
                Map<String, Long> sizeByType = new LinkedHashMap<>();
                long total = 0L;
                for (Object[] row : rows) {
                    String typeName = row[0] != null ? row[0].toString() : "OTHER";
                    long   bytes    = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                    sizeByType.put(typeName, bytes);
                    total += bytes;
                }
                return ResponseEntity.ok(FileTypeBreakdownDTO.builder()
                    .sizeByType(sizeByType)
                    .totalSize(total)
                    .build());
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private HealthScoreDTO toDTO(StorageHealthScore s) {
        return HealthScoreDTO.builder()
            .overallScore(s.getOverallScore())
            .duplicateWasteScore(s.getDuplicateWasteScore())
            .clutterScore(s.getClutterScore())
            .organizationScore(s.getOrganizationScore())
            .status(s.getHealthStatus())
            .totalSize(s.getTotalSize())
            .duplicateWaste(s.getDuplicateWaste())
            .clutteredSize(s.getClutteredSize())
            .temporaryFileSize(s.getTemporaryFileSize())
            .build();
    }
}
