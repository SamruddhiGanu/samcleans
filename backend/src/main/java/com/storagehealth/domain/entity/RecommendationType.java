package com.storagehealth.domain.entity;

/**
 * Enum representing the type of AI-generated recommendation for a file.
 */
public enum RecommendationType {
    DUPLICATE,
    NEAR_DUPLICATE,
    BLURRY_IMAGE,
    OLD_SCREENSHOT,
    TEMP_FILE,
    UNUSED_LARGE_FILE,
    EMPTY_FOLDER,
    STALE_DOWNLOAD
}
