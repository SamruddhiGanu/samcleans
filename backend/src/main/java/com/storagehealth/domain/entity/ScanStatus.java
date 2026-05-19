package com.storagehealth.domain.entity;

/**
 * Enum representing the lifecycle state of a scan session.
 */
public enum ScanStatus {
    INITIATED,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    PAUSED
}
