package com.storagehealth.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDiscoveredEvent {
    private Long fileId;
    private Long scanSessionId;
    private String path;
    private String name;
    private Long sizeBytes;
    private String mimeType;
    private String extension;
}
