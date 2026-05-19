package com.storagehealth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity holding an AI-generated recommendation for a file.
 * Maps to the {@code recommendations} table.
 */
@Entity
@Table(name = "recommendations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RecommendationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    @ToString.Exclude
    private FileEntity file;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false)
    private RecommendationType type;

    @Column(columnDefinition = "DECIMAL(3,2)")
    private BigDecimal confidenceScore;

    private String explanation;
    private Long recoverableSpace;

    @Builder.Default
    private Boolean isActedOn = false;

    @CreationTimestamp
    private LocalDateTime createdDate;
}
