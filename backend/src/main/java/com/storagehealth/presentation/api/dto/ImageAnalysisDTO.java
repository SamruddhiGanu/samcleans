package com.storagehealth.presentation.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageAnalysisDTO {
    private Long fileId;
    private String fileName;
    private double blurScore;
    private double brightnessScore;
    private double colorfulnessScore;
    private boolean isBlurry;
}
