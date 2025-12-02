package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Event DTO for Kafka messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private String paymentId;
    private String customerId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private String failureReason;
    private LocalDateTime timestamp;
    private String eventType;
    private String correlationId;
}
