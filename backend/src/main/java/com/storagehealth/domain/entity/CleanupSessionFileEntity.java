package com.storagehealth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity linking a file to a cleanup session, recording the archived path after move.
 * Maps to the {@code cleanup_session_files} table.
 */
@Entity
@Table(name = "cleanup_session_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CleanupSessionFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleanup_session_id", nullable = false)
    @ToString.Exclude
    private CleanupSessionEntity cleanupSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    @ToString.Exclude
    private FileEntity file;

    @Column(nullable = false)
    private String originalPath;

    /** Null until the file has been physically moved to the archive location. */
    private String archivedPath;

    @CreationTimestamp
    private LocalDateTime createdDate;
}
