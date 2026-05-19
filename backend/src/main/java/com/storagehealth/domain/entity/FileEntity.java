package com.storagehealth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity representing a file discovered during a scan session.
 * Maps to the {@code files} table in SQLite.
 */
@Entity
@Table(name = "files", indexes = {
    @Index(name = "idx_path",      columnList = "path"),
    @Index(name = "idx_extension", columnList = "extension"),
    @Index(name = "idx_size",      columnList = "size_bytes")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Absolute filesystem path — unique across the database. */
    @Column(unique = true, nullable = false)
    private String path;

    @Column(nullable = false)
    private String name;

    private String extension;
    private String mimeType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    private FileType fileType;

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private LocalDateTime accessedAt;

    @CreationTimestamp
    private LocalDateTime createdDate;

    @UpdateTimestamp
    private LocalDateTime updatedDate;

    /**
     * Computed by {@link com.storagehealth.application.service.ranking.FileRankingService}.
     * Range 0.0 (least important) – 1.0 (most important). Null until ranking has been run.
     */
    @Column(name = "importance_score")
    private Double importanceScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_session_id")
    @ToString.Exclude
    private ScanSessionEntity scanSession;

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @Builder.Default
    private Set<FileHashEntity> hashes = new HashSet<>();

    @OneToMany(mappedBy = "file", cascade = CascadeType.ALL)
    @ToString.Exclude
    @Builder.Default
    private Set<RecommendationEntity> recommendations = new HashSet<>();
}
