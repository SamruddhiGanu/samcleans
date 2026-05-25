package com.storagehealth.ui.panels;

public record CleanupSessionRow(String sessionId, Integer filesCount, Long totalSize, String status) {
}
