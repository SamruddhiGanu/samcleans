package com.storagehealth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity storing cryptographic or perceptual hash values for a file.
 * Maps to the {@code file_hashes} table.
 */
@Entity
@Table(name = "file_hashes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FileHashEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    @ToString.Exclude
    private FileEntity file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HashType hashType;

    @Column(nullable = false)
    private String hashValue;

    @CreationTimestamp
    private LocalDateTime createdDate;
}
