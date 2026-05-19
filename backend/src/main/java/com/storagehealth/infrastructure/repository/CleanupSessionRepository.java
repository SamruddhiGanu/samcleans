package com.storagehealth.infrastructure.repository;

import com.storagehealth.domain.entity.CleanupSessionEntity;
import com.storagehealth.domain.entity.CleanupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link CleanupSessionEntity}.
 */
@Repository
public interface CleanupSessionRepository extends JpaRepository<CleanupSessionEntity, Long> {

    Optional<CleanupSessionEntity> findBySessionId(String sessionId);

    List<CleanupSessionEntity> findByStatus(CleanupStatus status);
}
