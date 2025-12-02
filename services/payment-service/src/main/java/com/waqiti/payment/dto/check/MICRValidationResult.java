package com.waqiti.payment.dto.check;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MICRValidationResult {
    private boolean valid;
    private String routingNumber;
    private String accountNumber;
    private String checkNumber;
    private List<String> errors;
}