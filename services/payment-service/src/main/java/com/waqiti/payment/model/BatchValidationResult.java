package com.waqiti.payment.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchValidationResult {
    private String batchId;
    private Instant validationStartTime;
    private Instant validationEndTime;
    private Integer validPaymentCount;
    private Integer invalidPaymentCount;
    private List<PaymentValidationError> validationErrors;
    private Boolean isValid;

    public boolean isValid() {
        return Boolean.TRUE.equals(isValid);
    }

    public String toJson() {
        try {
            return new ObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
