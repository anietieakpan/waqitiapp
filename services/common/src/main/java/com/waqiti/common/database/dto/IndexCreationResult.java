package com.waqiti.common.database.dto;

import lombok.Data;

/**
 * Result of index creation operation.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class IndexCreationResult {
    private String indexName;
    private boolean success;
    private String errorMessage;
    
    public IndexCreationResult() {}
    
    public IndexCreationResult(String indexName, boolean success, String errorMessage) {
        this.indexName = indexName;
        this.success = success;
        this.errorMessage = errorMessage;
    }
}