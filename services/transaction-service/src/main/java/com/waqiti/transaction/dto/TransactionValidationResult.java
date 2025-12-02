package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransactionValidationResult {
    private boolean valid;
    private String validationErrors;
}