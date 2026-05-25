package com.storagehealth.application.service.image;

import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;

@Service
@Slf4j
public class ImageAnalysisServiceImpl implements ImageAnalysisService {

    private static final double BLUR_THRESHOLD = 100.0; // Standard threshold for Laplacian variance
    private boolean openCvLoaded = false;

    @PostConstruct
    public void init() {
        try {
            // openpnp auto-extracts and loads the correct native library for the current OS/Arch
            OpenCV.loadLocally();
            openCvLoaded = true;
            log.info("OpenCV native library loaded successfully.");
        } catch (Throwable t) {
            log.error("Failed to load OpenCV native library. Image analysis will be disabled.", t);
        }
    }

    @Override
    public ImageAnalysisResult analyzeImage(Path imagePath) {
        if (!openCvLoaded) return null;

        try {
            Mat image = Imgcodecs.imread(imagePath.toString());
            if (image.empty()) {
                log.warn("Failed to load image: {}", imagePath);
                return null;
            }

            double blurScore = calculateBlurScoreInternal(image);
            double brightnessScore = calculateBrightnessScore(image);
            double colorfulnessScore = calculateColorfulnessScore(image);

            image.release();

            return ImageAnalysisResult.builder()
                .blurScore(blurScore)
                .brightnessScore(brightnessScore)
                .colorfulnessScore(colorfulnessScore)
                .isBlurry(blurScore < BLUR_THRESHOLD)
                .build();

        } catch (Exception e) {
            log.error("Error analyzing image: {}", imagePath, e);
            return null;
        }
    }

    @Override
    public double calculateBlurScore(Path imagePath) {
        if (!openCvLoaded) return 0.0;
        
        Mat image = Imgcodecs.imread(imagePath.toString(), Imgcodecs.IMREAD_GRAYSCALE);
        if (image.empty()) return 0.0;
        
        double score = calculateBlurScoreInternal(image);
        image.release();
        return score;
    }

    @Override
    public boolean isImageBlurry(Path imagePath) {
        double score = calculateBlurScore(imagePath);
        return score > 0 && score < BLUR_THRESHOLD;
    }

    // --- OpenCV internals ---

    private double calculateBlurScoreInternal(Mat image) {
        Mat gray = new Mat();
        if (image.channels() > 1) {
            Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY);
        } else {
            image.copyTo(gray);
        }

        Mat laplacian = new Mat();
        Imgproc.Laplacian(gray, laplacian, CvType.CV_64F);

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(laplacian, mean, stddev);

        double stdDevVal = stddev.toArray()[0];
        
        gray.release();
        laplacian.release();
        mean.release();
        stddev.release();
        
        return stdDevVal * stdDevVal; // Variance
    }

    private double calculateBrightnessScore(Mat image) {
        Mat hsv = new Mat();
        Imgproc.cvtColor(image, hsv, Imgproc.COLOR_BGR2HSV);
        MatOfDouble mean = new MatOfDouble();
        Core.mean(hsv, mean);
        double val = mean.toArray()[2]; // V channel is brightness
        hsv.release();
        mean.release();
        return val;
    }

    private double calculateColorfulnessScore(Mat image) {
        Mat rgb = new Mat();
        Imgproc.cvtColor(image, rgb, Imgproc.COLOR_BGR2RGB);
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(rgb, mean, stddev);
        
        double score = stddev.toArray()[0] + stddev.toArray()[1] + stddev.toArray()[2];
        rgb.release();
        mean.release();
        stddev.release();
        return score;
    }
}
