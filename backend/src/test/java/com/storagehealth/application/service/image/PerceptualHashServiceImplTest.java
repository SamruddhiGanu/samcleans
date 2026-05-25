package com.storagehealth.application.service.image;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PerceptualHashServiceImplTest {

    private PerceptualHashServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PerceptualHashServiceImpl();
        service.init();
    }

    @Test
    void computeHash_handlesMissingFile() {
        Path missing = Path.of("missing.jpg");
        String hash = service.computeHash(missing);
        assertThat(hash).isNull();
    }

    @Test
    void computeHash_handlesNonImageFile(@TempDir Path tempDir) throws IOException {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "This is not an image");

        String hash = service.computeHash(textFile);
        assertThat(hash).isNull();
    }

    @Test
    void hammingDistance_calculatesCorrectly() {
        String hash1 = "1111111100000000111111110000000011111111000000001111111100000000";
        String hash2 = "1111111100000000111111110000000011111111000000001111111111111111"; // Last 8 bits differ
        
        int distance = service.hammingDistance(hash1, hash2);
        assertThat(distance).isEqualTo(8);
    }

    @Test
    void hammingDistance_handlesInvalidLengths() {
        assertThat(service.hammingDistance("1010", "1010")).isEqualTo(-1);
        assertThat(service.hammingDistance(null, "1010")).isEqualTo(-1);
    }
}
