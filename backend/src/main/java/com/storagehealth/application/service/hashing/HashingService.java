package com.storagehealth.application.service.hashing;

import com.storagehealth.domain.entity.HashType;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Contract for computing cryptographic and perceptual hashes of files.
 */
public interface HashingService {

    /**
     * Dispatches to the correct hashing algorithm based on {@code hashType}.
     *
     * @param filePath the file to hash
     * @param hashType algorithm to use
     * @return hex-encoded hash string
     * @throws IOException                   if the file cannot be read
     * @throws UnsupportedOperationException if the algorithm is not yet implemented
     */
    String computeHash(Path filePath, HashType hashType) throws IOException;

    /**
     * Computes a SHA-256 hash of the file contents using a buffered stream.
     *
     * @param filePath the file to hash
     * @return lowercase hex string (64 chars)
     * @throws IOException if the file cannot be read
     */
    String sha256Hash(Path filePath) throws IOException;

    /**
     * Computes a difference (perceptual) hash for images.
     * Stub implementation — will be completed in Phase 3 with OpenCV.
     *
     * @param filePath path to an image file
     * @return dPhash hex string
     * @throws IOException                   if the file cannot be read
     * @throws UnsupportedOperationException always, until Phase 3
     */
    String dPhash(Path filePath) throws IOException;
}
