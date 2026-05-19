package com.storagehealth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity capturing user feedback (keep / delete / important) on a file.
 * Maps to the {@code user_feedback} table.
 */
@Entity
@Table(name = "user_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    @ToString.Exclude
    private FileEntity file;

    /** keep | delete | important */
    private String feedbackType;

    /** User-assigned importance on a numeric scale. */
    private Integer importanceScore;

    private String userNotes;

    @CreationTimestamp
    private LocalDateTime createdDate;
}
