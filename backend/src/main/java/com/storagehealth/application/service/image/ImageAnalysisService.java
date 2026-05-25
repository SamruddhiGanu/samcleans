package com.storagehealth.application.service.image;

import java.nio.file.Path;

/**
 * Service for analyzing image files using OpenCV.
 */
public interface ImageAnalysisService {
    
    /**
     * Fully analyzes an image and returns a composite result.
     * @param imagePath absolute path to the image
     * @return analysis result, or null if OpenCV fails or image is invalid
     */
    ImageAnalysisResult analyzeImage(Path imagePath);
    
    /**
     * Calculates the blur score (Laplacian variance) for an image.
     */
    double calculateBlurScore(Path imagePath);
    
    /**
     * Convenience method to check if an image is considered blurry based on a threshold.
     */
    boolean isImageBlurry(Path imagePath);
}
