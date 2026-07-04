package com.storagehealth.infrastructure.repository;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.RecommendationEntity;
import com.storagehealth.domain.entity.RecommendationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link RecommendationEntity}.
 * Supports filtering by type and acted-on status.
 */
@Repository
public interface RecommendationRepository extends JpaRepository<RecommendationEntity, Long> {

    List<RecommendationEntity> findByType(RecommendationType type);

    Page<RecommendationEntity> findByType(RecommendationType type, Pageable pageable);

    Page<RecommendationEntity> findByTypeAndIsActedOnFalse(RecommendationType type, Pageable pageable);

    List<RecommendationEntity> findByFile(FileEntity file);

    boolean existsByFileAndTypeAndIsActedOnFalse(FileEntity file, RecommendationType type);

    List<RecommendationEntity> findByIsActedOnFalse();

    Page<RecommendationEntity> findByIsActedOnFalse(Pageable pageable);
}
