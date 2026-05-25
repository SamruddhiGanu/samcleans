package com.storagehealth.application.service.hashing;

import com.storagehealth.domain.entity.HashType;
import com.storagehealth.infrastructure.repository.FileHashRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Implementation of {@link HashingService}.
 *
 * <p>Phase 1 supports SHA-256 exact-match hashing only.
 * dPhash and pHash (perceptual image hashing) are stubbed for Phase 3.
 */
@Service
@Slf4j
public class HashingServiceImpl implements HashingService {

    /** Read buffer size — 8 KiB balances memory use and I/O throughput. */
    private static final int BUFFER_SIZE = 8_192;

    private final FileHashRepository hashRepository;
    private final com.storagehealth.application.service.image.PerceptualHashService phashService;

    @Autowired
    public HashingServiceImpl(FileHashRepository hashRepository,
                              com.storagehealth.application.service.image.PerceptualHashService phashService) {
        this.hashRepository = hashRepository;
        this.phashService = phashService;
    }

    // ---------------------------------------------------------------
    // HashingService implementation
    // ---------------------------------------------------------------

    @Override
    public String computeHash(Path filePath, HashType hashType) throws IOException {
        return switch (hashType) {
            case SHA256 -> sha256Hash(filePath);
            case DPHASH, PHASH -> phashService.computeHash(filePath);
        };
    }

    @Override
    public String sha256Hash(Path filePath) throws IOException {
        log.debug("Computing SHA-256 for: {}", filePath);
        try (InputStream fis = Files.newInputStream(filePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }

            return bytesToHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available on this JVM", e);
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    @Override
    public String dPhash(Path filePath) throws IOException {
        return phashService.computeHash(filePath);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    /**
     * Converts a raw byte array to a lowercase hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}
