package com.storagehealth.presentation.api.dto;

import lombok.*;

import java.math.BigDecimal;

/** DTO representing a single AI recommendation for a file. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDTO {
    private Long id;
    private Long fileId;
    private String fileName;
    private String filePath;
    private String type;
    private BigDecimal confidenceScore;
    private String explanation;
    private Long recoverableSpace;
}
