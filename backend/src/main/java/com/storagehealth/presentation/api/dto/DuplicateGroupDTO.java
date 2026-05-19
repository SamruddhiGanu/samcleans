package com.storagehealth.presentation.api.dto;

import lombok.*;

/** Summary of one group of duplicate files sharing the same hash. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateGroupDTO {
    private String hashValue;
    private int fileCount;
    private long totalSize;
    private long recoverableSpace;
}
