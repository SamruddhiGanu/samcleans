package com.storagehealth.presentation.api.dto;

import lombok.*;

import java.util.List;

/** Summary of one group of duplicate files sharing the same hash. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateGroupDTO {
    private String hashValue;
    private int fileCount;
    private long totalSize;
    private long recoverableSpace;
    private List<String> filePaths;
}
