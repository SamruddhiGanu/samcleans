package com.storagehealth.infrastructure.repository;

import com.storagehealth.domain.entity.FileEntity;
import com.storagehealth.domain.entity.FileHashEntity;
import com.storagehealth.domain.entity.HashType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link FileHashEntity}.
 * Core to duplicate detection — enables hash-based lookups.
 */
@Repository
public interface FileHashRepository extends JpaRepository<FileHashEntity, Long> {

    List<FileHashEntity> findByHashValueAndHashType(String hashValue, HashType hashType);

    List<FileHashEntity> findByFile(FileEntity file);

    Optional<FileHashEntity> findByFileAndHashType(FileEntity file, HashType hashType);
}
