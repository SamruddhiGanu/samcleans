package com.storagehealth.presentation.api.dto;

import lombok.Data;
import java.util.List;

@Data
public class CleanupInitiateRequest {
    private List<Long> fileIds;
}
