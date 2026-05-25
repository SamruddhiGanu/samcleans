package com.storagehealth.ui.panels;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JavaFX TableView row model for the Recommendations panel. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRow {
    private Long   id;
    private Long   fileId;
    private String type;
    private String fileName;
    private String filePath;
    private String confidence;
    private long   recoverableSpace;
    private String explanation;
    private boolean selected;

    public String getRecoverableFormatted() {
        if (recoverableSpace < 1024) return recoverableSpace + " B";
        int exp = (int) (Math.log(recoverableSpace) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", recoverableSpace / Math.pow(1024, exp), pre);
    }
}
