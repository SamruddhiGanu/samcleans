package com.storagehealth.presentation.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupSessionDTO {
    private String sessionId;
    private Integer filesCount;
    private Long totalSize;
    private String status;
}
