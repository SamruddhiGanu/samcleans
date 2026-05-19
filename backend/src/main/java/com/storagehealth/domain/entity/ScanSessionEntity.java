package com.storagehealth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * JPA entity representing a single scanning session (one directory walk).
 * Maps to the {@code scan_sessions} table.
 */
@Entity
@Table(name = "scan_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScanSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    private String sessionName;

    @Column(nullable = false)
    private String scanPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    private Integer totalFiles;
    private Integer scannedFiles;
    private Long totalSize;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @CreationTimestamp
    private LocalDateTime createdDate;

    @OneToMany(mappedBy = "scanSession", cascade = CascadeType.ALL)
    @ToString.Exclude
    @Builder.Default
    private Set<FileEntity> files = new HashSet<>();
}
