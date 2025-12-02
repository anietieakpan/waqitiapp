package com.waqiti.payment.businessprofile.validation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValidationResult {
    private boolean valid;
    private String errorMessage;
    private String suggestion;
    private String field;
    private Object validatedValue;
}