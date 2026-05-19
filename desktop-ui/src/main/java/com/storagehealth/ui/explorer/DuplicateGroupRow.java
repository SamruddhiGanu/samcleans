package com.storagehealth.ui.explorer;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * JavaFX table row model for a single duplicate group.
 */
@Data
@AllArgsConstructor
public class DuplicateGroupRow {
    private String hashPrefix;
    private int    fileCount;
    private long   totalSize;
    private long   recoverableSpace;

    public String getFormattedTotalSize()   { return formatSize(totalSize); }
    public String getFormattedRecoverable() { return formatSize(recoverableSpace); }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
