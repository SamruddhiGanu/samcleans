package com.storagehealth.presentation.api.dto;

import lombok.*;

/** Request body for starting a new scan. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanRequest {
    /** Absolute path of the directory to scan. */
    private String path;
    /** Optional human-readable name for this scan session. */
    private String name;
}
