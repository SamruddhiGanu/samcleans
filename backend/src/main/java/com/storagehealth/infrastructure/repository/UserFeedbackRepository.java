package com.storagehealth.infrastructure.repository;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.UserFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link UserFeedbackEntity}.
 */
@Repository
public interface UserFeedbackRepository extends JpaRepository<UserFeedbackEntity, Long> {

    Optional<UserFeedbackEntity> findByFile(FileEntity file);
}
