package com.storagehealth.presentation.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single file's metadata as reported by the browser's File System Access API.
 * The browser cannot provide createdAt; only name, size, lastModified, and the
 * relative path within the selected root folder.
 */
@Data
@NoArgsConstructor
public class BrowserFileMetadata {
    private String path;          // full relative path inside scanned folder
    private String name;          // file name with extension
    private long   sizeBytes;
    private long   lastModifiedMs; // epoch millis from File.lastModified
    private String mimeType;       // from File.type (may be empty)
}
