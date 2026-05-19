package com.storagehealth.application.service.hashing;

import com.storagehealth.domain.entity.HashType;
import com.storagehealth.infrastructure.repository.FileHashRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link HashingServiceImpl}.
 * Uses a temporary directory so no real disk state is shared between tests.
 */
class HashingServiceImplTest {

    @TempDir
    Path tempDir;

    @Mock
    FileHashRepository fileHashRepository;

    @InjectMocks
    HashingServiceImpl hashingService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    // ---------------------------------------------------------------
    // SHA-256 tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("sha256Hash should return a 64-char lowercase hex string for any file")
    void sha256Hash_returnsCorrectLength() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "Hello, Storage Health Ranker!");

        String hash = hashingService.sha256Hash(file);

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("sha256Hash should return the same hash for identical content")
    void sha256Hash_deterministicForSameContent() throws IOException {
        String content = "duplicate content";
        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file1, content);
        Files.writeString(file2, content);

        assertThat(hashingService.sha256Hash(file1))
            .isEqualTo(hashingService.sha256Hash(file2));
    }

    @Test
    @DisplayName("sha256Hash should return different hashes for different content")
    void sha256Hash_differentHashForDifferentContent() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "content A");
        Files.writeString(file2, "content B");

        assertThat(hashingService.sha256Hash(file1))
            .isNotEqualTo(hashingService.sha256Hash(file2));
    }

    @Test
    @DisplayName("sha256Hash should work correctly for an empty file")
    void sha256Hash_emptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.bin");
        Files.createFile(emptyFile);

        // Known SHA-256 of empty input
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertThat(hashingService.sha256Hash(emptyFile)).isEqualTo(expected);
    }

    @Test
    @DisplayName("computeHash with SHA256 type should delegate to sha256Hash")
    void computeHash_sha256Delegates() throws IOException {
        Path file = tempDir.resolve("delegate.txt");
        Files.writeString(file, "delegate test");

        String direct   = hashingService.sha256Hash(file);
        String computed = hashingService.computeHash(file, HashType.SHA256);

        assertThat(computed).isEqualTo(direct);
    }

    // ---------------------------------------------------------------
    // dPhash stub tests
    // ---------------------------------------------------------------

    @Test
    @DisplayName("dPhash should throw UnsupportedOperationException (Phase 3 stub)")
    void dPhash_throwsUnsupported() throws IOException {
        Path file = tempDir.resolve("image.png");
        Files.createFile(file);

        assertThatThrownBy(() -> hashingService.dPhash(file))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("computeHash with PHASH should return empty string (Phase 3 stub)")
    void computeHash_phashReturnsEmpty() throws IOException {
        Path file = tempDir.resolve("photo.jpg");
        Files.createFile(file);

        String result = hashingService.computeHash(file, HashType.PHASH);
        assertThat(result).isEmpty();
    }
}
