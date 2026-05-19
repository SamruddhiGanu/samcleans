package com.storagehealth.domain.entity;

/**
 * Enum representing the hashing algorithm applied to a file.
 * SHA256 = exact-match duplicate detection.
 * DPHASH = perceptual difference hash for near-duplicate images (Phase 3).
 * PHASH  = perceptual hash for visual similarity (Phase 3).
 */
public enum HashType {
    SHA256,
    DPHASH,
    PHASH
}
