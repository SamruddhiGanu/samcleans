package com.storagehealth.application.service.ranking;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.ScanSessionEntity;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Contract for computing a weighted importance score (0.0 – 1.0) for each file.
 *
 * <p>The score is composed of five components:
 * <ol>
 *   <li>Recency   — how recently the file was modified</li>
 *   <li>Frequency — how recently the file was last accessed</li>
 *   <li>Semantic  — intrinsic value based on file type</li>
 *   <li>Uniqueness — penalised when the file has duplicates</li>
 *   <li>User Feedback — explicit keep / delete / important signal</li>
 * </ol>
 */
public interface FileRankingService {

    /**
     * Computes and returns the importance score for a single file.
     * Does NOT persist the result.
     */
    double computeImportanceScore(FileEntity file);

    /**
     * Computes importance scores for all files in {@code session}
     * and persists the updated scores to the database.
     */
    void rankFiles(ScanSessionEntity session);

    /**
     * Returns files belonging to {@code session} ordered by importance score
     * descending (most important first), with pagination.
     */
    List<FileEntity> getFilesByImportance(ScanSessionEntity session, Pageable pageable);
}
