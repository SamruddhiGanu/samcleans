package com.storagehealth.presentation.api.dto;

import lombok.*;

import java.time.LocalDateTime;

/** DTO returned when listing or starting a scan session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanSessionDTO {
    private Long id;
    private String sessionName;
    private String scanPath;
    private String status;
    private Integer totalFiles;
    private Long totalSize;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
