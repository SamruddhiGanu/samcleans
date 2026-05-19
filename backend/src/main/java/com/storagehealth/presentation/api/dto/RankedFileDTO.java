package com.storagehealth.presentation.api.dto;

import lombok.*;

/**
 * DTO returned in paginated file lists from the ranking endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankedFileDTO {
    private Long   id;
    private String name;
    private String path;
    private String fileType;
    private Long   sizeBytes;
    private Double importanceScore;
    private String modifiedAt;
    private String accessedAt;
}
