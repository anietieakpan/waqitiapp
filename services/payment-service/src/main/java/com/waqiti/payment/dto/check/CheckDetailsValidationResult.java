package com.waqiti.payment.dto.check;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckDetailsValidationResult {
    private boolean valid;
    private boolean routingNumberValid;
    private boolean accountNumberValid;
    private boolean checkNumberValid;
    private boolean amountValid;
    private boolean dateValid;
    private List<String> errors;
    private List<String> warnings;
    private Map<String, String> fieldStatus;
}