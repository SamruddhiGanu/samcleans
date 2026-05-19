package com.storagehealth.domain.entity;

/**
 * Lifecycle states for a cleanup (safe-delete) session.
 */
public enum CleanupStatus {
    ACTIVE,
    COMPLETED,
    RESTORED,
    ARCHIVED
}
