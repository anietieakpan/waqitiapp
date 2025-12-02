package com.waqiti.common.database.dto;

import lombok.Data;
import java.util.List;

/**
 * Batch request for database operations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Data
public class BatchRequest {
    private List<String> operations;
    private BatchType type;
    private int batchSize;
    
    public enum BatchType {
        INSERT, UPDATE, DELETE, MIXED
    }
}