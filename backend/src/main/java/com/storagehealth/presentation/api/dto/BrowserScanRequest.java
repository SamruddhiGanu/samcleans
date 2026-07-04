package com.storagehealth.presentation.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Wraps a browser-initiated scan: session metadata + list of file entries
 * collected by the browser's File System Access API.
 */
@Data
@NoArgsConstructor
public class BrowserScanRequest {
    private String sessionName;
    private String rootPath;
    private List<BrowserFileMetadata> files;
}
