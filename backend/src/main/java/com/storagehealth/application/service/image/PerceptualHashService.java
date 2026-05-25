package com.storagehealth.application.service.image;

import java.nio.file.Path;

/**
 * Computes perceptual hashes (aHash, dHash, pHash) for images to find visually similar files.
 */
public interface PerceptualHashService {
    
    /**
     * Computes an 8x8 average-hash (aHash) for the given image.
     * @param imagePath path to the image
     * @return a 64-bit binary string (e.g. "101100..."), or null if hashing fails
     */
    String computeHash(Path imagePath);
    
    /**
     * Computes the Hamming distance between two 64-bit binary hash strings.
     * @return distance (0 to 64), or -1 if invalid
     */
    int hammingDistance(String hash1, String hash2);
}
