package com.waqiti.common.batch;

import lombok.Builder;
import lombok.Data;

/**
 * Represents an error during batch processing
 */
@Data
@Builder
public class BatchError {
    
    private Object item;
    private String error;
    private int index;
    private String itemId;
    
    public boolean hasItemId() {
        return itemId != null && !itemId.trim().isEmpty();
    }
}