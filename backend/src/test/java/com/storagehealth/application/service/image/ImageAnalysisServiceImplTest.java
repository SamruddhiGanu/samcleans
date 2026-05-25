package com.storagehealth.application.service.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageAnalysisServiceImplTest {

    private ImageAnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImageAnalysisServiceImpl();
        // Since we cannot mock native loads easily without PowerMock, we just let it init
        service.init();
    }

    @Test
    void analyzeImage_handlesMissingFile() {
        Path missing = Path.of("does_not_exist.jpg");
        ImageAnalysisResult result = service.analyzeImage(missing);
        
        // OpenCV returns empty mat for missing files, which our code checks and returns null
        assertThat(result).isNull();
    }

    @Test
    void analyzeImage_handlesNonImageFile(@TempDir Path tempDir) throws IOException {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "This is not an image");

        ImageAnalysisResult result = service.analyzeImage(textFile);
        
        // Imread fails gracefully and returns an empty mat
        assertThat(result).isNull();
    }

    @Test
    void isImageBlurry_handlesMissingFile() {
        Path missing = Path.of("missing.jpg");
        boolean blurry = service.isImageBlurry(missing);
        
        // Returns false safely
        assertThat(blurry).isFalse();
    }
}
