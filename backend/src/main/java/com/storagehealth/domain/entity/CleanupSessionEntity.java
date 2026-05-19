package com.storagehealth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity tracking a group of files staged for safe deletion (cleanup session).
 * Maps to the {@code cleanup_sessions} table.
 */
@Entity
@Table(name = "cleanup_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CleanupSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** UUID string identifying this cleanup session externally. */
    @Column(unique = true, nullable = false)
    private String sessionId;

    @CreationTimestamp
    private LocalDateTime creationTime;

    private Integer filesCount;
    private Long totalSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CleanupStatus status;

    @OneToMany(mappedBy = "cleanupSession", cascade = CascadeType.ALL)
    @ToString.Exclude
    @Builder.Default
    private Set<CleanupSessionFileEntity> files = new HashSet<>();
}
