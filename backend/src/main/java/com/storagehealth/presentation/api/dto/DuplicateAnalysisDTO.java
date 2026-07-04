package com.storagehealth.presentation.api.dto;

import lombok.*;

import java.util.List;

/** DTO returned after running duplicate detection on a session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateAnalysisDTO {
    private int groupCount;
    private int totalDuplicateFiles;
    private long totalRecoverableSpace;
    private List<DuplicateGroupDTO> groups;
}
