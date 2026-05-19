package com.storagehealth.infrastructure.repository;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.ScanSessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link FileEntity}. Provides common query methods
 * used by the scanner and ranking services.
 */
@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {

    Optional<FileEntity> findByPath(String path);

    List<FileEntity> findByExtension(String extension);

    List<FileEntity> findBySizeBytesGreaterThan(Long size);

    List<FileEntity> findByScanSession(ScanSessionEntity scanSession);

    Page<FileEntity> findAll(Pageable pageable);

    long countByScanSession(ScanSessionEntity session);

    /**
     * Returns files belonging to {@code session} ordered by importance score descending.
     * Used by the ranking service to surface the most important files first.
     */
    Page<FileEntity> findByScanSessionOrderByImportanceScoreDesc(ScanSessionEntity session, Pageable pageable);

    /**
     * Returns the number of files sharing the same SHA-256 hash as the given file.
     * Used to determine uniqueness during the ranking phase.
     */
    @Query("""
        SELECT COUNT(f) FROM FileEntity f
        WHERE f.id IN (
            SELECT fh.file.id FROM FileHashEntity fh
            WHERE fh.hashType = 'SHA256'
            AND fh.hashValue = (
                SELECT fh2.hashValue FROM FileHashEntity fh2
                WHERE fh2.file.id = :fileId AND fh2.hashType = 'SHA256'
            )
        )
    """)
    long countDuplicatesForFile(@Param("fileId") Long fileId);
}
