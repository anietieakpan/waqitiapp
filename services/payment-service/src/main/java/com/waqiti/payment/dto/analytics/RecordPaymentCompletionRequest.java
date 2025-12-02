package com.waqiti.payment.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordPaymentCompletionRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @NotNull private UUID paymentId;
    @NotNull private UUID userId;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    @NotBlank @Size(min = 3, max = 3) private String currency;
    @NotBlank private String paymentMethod;
    private UUID merchantId;
    private String merchantCategory;
    private Instant completedAt;
}