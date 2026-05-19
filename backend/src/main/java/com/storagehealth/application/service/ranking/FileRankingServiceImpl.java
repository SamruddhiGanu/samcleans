package com.storagehealth.application.service.ranking;

import com.storagehealth.domain.entity.*;
import com.storagehealth.infrastructure.repository.FileRepository;
import com.storagehealth.infrastructure.repository.UserFeedbackRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Computes a weighted importance score (0.0 – 1.0) for every file
 * in a scan session using five independent components:
 *
 * <pre>
 *   score = recency×0.2 + frequency×0.2 + semantic×0.3 + uniqueness×0.2 + userFeedback×0.1
 * </pre>
 *
 * <p>Each component is independently normalised to [0.0, 1.0] so the
 * weights are the sole control knobs for tuning.
 */
@Service
@Slf4j
@Transactional
public class FileRankingServiceImpl implements FileRankingService {

    // ---------------------------------------------------------------
    // Weights — must sum to 1.0
    // ---------------------------------------------------------------
    private static final double RECENCY_WEIGHT       = 0.2;
    private static final double FREQUENCY_WEIGHT     = 0.2;
    private static final double SEMANTIC_WEIGHT      = 0.3;
    private static final double UNIQUENESS_WEIGHT    = 0.2;
    private static final double USER_FEEDBACK_WEIGHT = 0.1;

    private final FileRepository fileRepository;
    private final UserFeedbackRepository feedbackRepository;

    @Autowired
    public FileRankingServiceImpl(FileRepository fileRepository,
                                  UserFeedbackRepository feedbackRepository) {
        this.fileRepository   = fileRepository;
        this.feedbackRepository = feedbackRepository;
    }

    // ---------------------------------------------------------------
    // FileRankingService implementation
    // ---------------------------------------------------------------

    @Override
    public double computeImportanceScore(FileEntity file) {
        double recency      = calculateRecency(file);
        double frequency    = calculateAccessFrequency(file);
        double semantic     = calculateSemanticValue(file);
        double uniqueness   = calculateUniqueness(file);
        double userFeedback = calculateUserFeedback(file);

        return (recency      * RECENCY_WEIGHT)
             + (frequency    * FREQUENCY_WEIGHT)
             + (semantic     * SEMANTIC_WEIGHT)
             + (uniqueness   * UNIQUENESS_WEIGHT)
             + (userFeedback * USER_FEEDBACK_WEIGHT);
    }

    @Override
    public void rankFiles(ScanSessionEntity session) {
        log.info("Ranking files for session {}", session.getId());

        List<FileEntity> files = fileRepository.findByScanSession(session);
        int count = 0;

        for (FileEntity file : files) {
            double score = computeImportanceScore(file);
            file.setImportanceScore(score);
            fileRepository.save(file);
            count++;
        }

        log.info("Ranked {} files for session {}", count, session.getId());
    }

    @Override
    public List<FileEntity> getFilesByImportance(ScanSessionEntity session, Pageable pageable) {
        return fileRepository
            .findByScanSessionOrderByImportanceScoreDesc(session, pageable)
            .getContent();
    }

    // ---------------------------------------------------------------
    // Score components
    // ---------------------------------------------------------------

    /**
     * Tiered recency based on days since last modification.
     * Recently-modified files are assumed more relevant.
     */
    private double calculateRecency(FileEntity file) {
        if (file.getModifiedAt() == null) return 0.5; // unknown

        long days = ChronoUnit.DAYS.between(file.getModifiedAt(), LocalDateTime.now());

        if (days < 7)   return 1.0;   // this week
        if (days < 30)  return 0.8;   // this month
        if (days < 180) return 0.6;   // last 6 months
        if (days < 365) return 0.4;   // last year
        return 0.2;                    // older
    }

    /**
     * Tiered frequency based on days since last access.
     * Frequently-accessed files have higher scores.
     */
    private double calculateAccessFrequency(FileEntity file) {
        if (file.getAccessedAt() == null) return 0.5; // unknown

        long days = ChronoUnit.DAYS.between(file.getAccessedAt(), LocalDateTime.now());

        if (days < 7)   return 1.0;
        if (days < 30)  return 0.8;
        if (days < 180) return 0.6;
        return 0.2;
    }

    /**
     * Assigns intrinsic value based on the broad category of the file.
     * Documents and images are considered more valuable than temp/executable files.
     */
    private double calculateSemanticValue(FileEntity file) {
        if (file.getFileType() == null) return 0.4;

        return switch (file.getFileType()) {
            case DOCUMENT   -> 0.9;
            case IMAGE      -> 0.8;
            case VIDEO      -> 0.7;
            case MEDIA      -> 0.7;
            case ARCHIVE    -> 0.6;
            case EXECUTABLE -> 0.5;
            case OTHER      -> 0.4;
            case TEMPORARY  -> 0.1;
        };
    }

    /**
     * Penalises files that have confirmed duplicates — duplicate files have lower uniqueness.
     * A file with N copies gets score 1/N, so the primary is not penalised more than duplicates.
     */
    private double calculateUniqueness(FileEntity file) {
        long count = fileRepository.countDuplicatesForFile(file.getId());
        if (count > 1) {
            return 1.0 / count; // share the "uniqueness" across copies
        }
        return 1.0; // no duplicates — fully unique
    }

    /**
     * Converts explicit user feedback into a numeric signal.
     * Returns 0.5 (neutral) when no feedback is recorded.
     */
    private double calculateUserFeedback(FileEntity file) {
        Optional<UserFeedbackEntity> feedback = feedbackRepository.findByFile(file);

        if (feedback.isEmpty()) return 0.5; // neutral

        String type = feedback.get().getFeedbackType();
        if (type == null) return 0.5;

        return switch (type.toUpperCase()) {
            case "IMPORTANT" -> 1.0;
            case "KEEP"      -> 1.0;
            case "DELETE"    -> 0.0;
            default          -> 0.5;
        };
    }
}
