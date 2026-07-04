package com.storagehealth.presentation.api.dto;

import lombok.*;

import java.util.Map;

/**
 * DTO returned by {@code GET /api/health/breakdown/{sessionId}}.
 * Maps each FileType name to the total bytes consumed by files of that type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileTypeBreakdownDTO {
    /** e.g. {"IMAGE": 104857600, "VIDEO": 52428800, "OTHER": 10485760} */
    private Map<String, Long> sizeByType;
    private long totalSize;
}
