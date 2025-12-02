package com.waqiti.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentValidationError {
    private String paymentId;
    private String errorCode;
    private String errorMessage;
}
