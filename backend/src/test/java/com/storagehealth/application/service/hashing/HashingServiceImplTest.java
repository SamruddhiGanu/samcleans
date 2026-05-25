package com.storagehealth.application.service.hashing;

import com.storagehealth.application.service.image.PerceptualHashService;
import com.storagehealth.domain.entity.HashType;
import com.storagehealth.infrastructure.repository.FileHashRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HashingServiceImplTest {

    @Mock
    private FileHashRepository hashRepository;

    @Mock
    private PerceptualHashService phashService;

    private HashingServiceImpl hashingService;

    @BeforeEach
    void setUp() {
        hashingService = new HashingServiceImpl(hashRepository, phashService);
    }

    @Test
    void sha256Hash_computesCorrectly(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "hello world");

        String expectedHash = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        String hash = hashingService.sha256Hash(testFile);

        assertThat(hash).isEqualTo(expectedHash);
    }

    @Test
    void sha256Hash_emptyFile(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("empty.txt");
        Files.createFile(testFile);

        String expectedHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String hash = hashingService.sha256Hash(testFile);

        assertThat(hash).isEqualTo(expectedHash);
    }

    @Test
    void sha256Hash_fileNotFound() {
        Path nonExistentFile = Path.of("non_existent_file_12345.txt");

        assertThatThrownBy(() -> hashingService.sha256Hash(nonExistentFile))
            .isInstanceOf(IOException.class);
    }

    @Test
    void computeHash_routesCorrectly_sha256(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("route_test.txt");
        Files.writeString(testFile, "test content");

        String expectedHash = hashingService.sha256Hash(testFile);
        String routedHash = hashingService.computeHash(testFile, HashType.SHA256);

        assertThat(routedHash).isEqualTo(expectedHash);
    }

    @Test
    void computeHash_routesCorrectly_phash(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("phash_test.jpg");
        Files.writeString(testFile, "fake image");
        
        when(phashService.computeHash(any(Path.class))).thenReturn("10101010");

        String hash = hashingService.computeHash(testFile, HashType.PHASH);

        assertThat(hash).isEqualTo("10101010");
    }

    @Test
    void computeHash_routesCorrectly_dphash(@TempDir Path tempDir) throws IOException {
        Path testFile = tempDir.resolve("image.jpg");
        Files.writeString(testFile, "fake image");
        
        when(phashService.computeHash(any(Path.class))).thenReturn("11110000");

        String hash = hashingService.dPhash(testFile);

        assertThat(hash).isEqualTo("11110000");
    }
}
