package com.storagehealth.application.service.image;

import lombok.extern.slf4j.Slf4j;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Path;

@Service
@Slf4j
public class PerceptualHashServiceImpl implements PerceptualHashService {

    private boolean openCvLoaded = false;

    @PostConstruct
    public void init() {
        try {
            OpenCV.loadLocally();
            openCvLoaded = true;
        } catch (Throwable t) {
            log.error("Failed to load OpenCV. Perceptual hashing will be disabled.", t);
        }
    }

    @Override
    public String computeHash(Path imagePath) {
        if (!openCvLoaded) return null;

        try {
            Mat image = Imgcodecs.imread(imagePath.toString(), Imgcodecs.IMREAD_GRAYSCALE);
            if (image.empty()) return null;

            // 1. Resize to 8x8
            Mat resized = new Mat();
            Imgproc.resize(image, resized, new Size(8, 8));

            // 2. Compute average pixel value
            MatOfDouble mean = new MatOfDouble();
            MatOfDouble stddev = new MatOfDouble();
            Core.meanStdDev(resized, mean, stddev);
            double avg = mean.toArray()[0];

            // 3. Compute bits (1 if > avg, 0 otherwise)
            StringBuilder hash = new StringBuilder(64);
            for (int r = 0; r < resized.rows(); r++) {
                for (int c = 0; c < resized.cols(); c++) {
                    double pixelVal = resized.get(r, c)[0];
                    hash.append(pixelVal > avg ? "1" : "0");
                }
            }

            image.release();
            resized.release();
            mean.release();
            stddev.release();

            return hash.toString();

        } catch (Exception e) {
            log.error("Error computing pHash for {}: {}", imagePath, e.getMessage());
            return null;
        }
    }

    @Override
    public int hammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != 64 || hash2.length() != 64) {
            return -1;
        }
        
        int distance = 0;
        for (int i = 0; i < 64; i++) {
            if (hash1.charAt(i) != hash2.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }
}
