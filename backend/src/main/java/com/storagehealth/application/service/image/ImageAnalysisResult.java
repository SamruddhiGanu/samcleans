package com.storagehealth.application.service.image;

import lombok.Builder;
import lombok.Data;

/**
 * Value object holding the results of an image analysis operation.
 */
@Data
@Builder
public class ImageAnalysisResult {
    private double blurScore;
    private double brightnessScore;
    private double colorfulnessScore;
    private boolean isBlurry;
}
