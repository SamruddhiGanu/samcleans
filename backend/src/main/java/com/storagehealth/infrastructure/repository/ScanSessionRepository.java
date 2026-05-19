package com.storagehealth.infrastructure.repository;

import com.storagehealth.domain.entity.ScanSessionEntity;
import com.storagehealth.domain.entity.ScanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ScanSessionEntity}.
 */
@Repository
public interface ScanSessionRepository extends JpaRepository<ScanSessionEntity, Long> {

    /** Returns the most recently created session regardless of status. */
    Optional<ScanSessionEntity> findTopByOrderByCreatedDateDesc();

    List<ScanSessionEntity> findByStatus(ScanStatus status);
}
