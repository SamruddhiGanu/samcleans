package com.storagehealth.presentation.api;

import com.storagehealth.application.service.ranking.FileRankingService;
import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.infrastructure.repository.ScanSessionRepository;
import com.storagehealth.presentation.api.dto.RankedFileDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller exposing file ranking endpoints.
 *
 * <pre>
 *  POST /api/ranking/run/{sessionId}   — compute and persist importance scores
 *  GET  /api/ranking/files/{sessionId} — return ranked file list (paginated)
 * </pre>
 */
@RestController
@RequestMapping("/api/ranking")
@Slf4j
public class RankingController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final FileRankingService rankingService;
    private final ScanSessionRepository sessionRepository;

    @Autowired
    public RankingController(FileRankingService rankingService,
                             ScanSessionRepository sessionRepository) {
        this.rankingService    = rankingService;
        this.sessionRepository = sessionRepository;
    }

    /** Triggers ranking for all files in a completed scan session. */
    @PostMapping("/run/{sessionId}")
    public ResponseEntity<Void> runRanking(@PathVariable Long sessionId) {
        return sessionRepository.findById(sessionId)
            .map(session -> {
                log.info("Running ranking for session {}", sessionId);
                rankingService.rankFiles(session);
                return ResponseEntity.ok().<Void>build();
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns files in the session ordered by importance score (descending). */
    @GetMapping("/files/{sessionId}")
    public ResponseEntity<List<RankedFileDTO>> getRankedFiles(@PathVariable Long sessionId,
                                                               Pageable pageable) {
        return sessionRepository.findById(sessionId)
            .map(session -> {
                List<FileEntity> files = rankingService.getFilesByImportance(session, pageable);
                List<RankedFileDTO> dtos = files.stream().map(this::toDTO).collect(Collectors.toList());
                return ResponseEntity.ok(dtos);
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private RankedFileDTO toDTO(FileEntity f) {
        return RankedFileDTO.builder()
            .id(f.getId())
            .name(f.getName())
            .path(f.getPath())
            .fileType(f.getFileType() != null ? f.getFileType().name() : "UNKNOWN")
            .sizeBytes(f.getSizeBytes())
            .importanceScore(f.getImportanceScore())
            .modifiedAt(f.getModifiedAt() != null ? f.getModifiedAt().format(FMT) : null)
            .accessedAt(f.getAccessedAt() != null ? f.getAccessedAt().format(FMT) : null)
            .build();
    }
}
