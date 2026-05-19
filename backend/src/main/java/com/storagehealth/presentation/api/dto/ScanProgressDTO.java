package com.storagehealth.presentation.api.dto;

import lombok.*;

import java.time.LocalDateTime;

/** DTO reporting real-time scan progress. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScanProgressDTO {
    private int totalFilesFound;
    private int filesProcessed;
    private long totalSize;
    private long currentSpeed;
    private LocalDateTime estimatedEndTime;
}
