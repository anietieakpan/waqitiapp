package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ACHPaymentEvent {
    private String eventId;
    private String achTransactionId;
    private String customerId;
    private String transactionType;
    private String secCode;
    private BigDecimal amount;
    private String currency;
    private String routingNumber;
    private String accountNumber;
    private String accountType;
    private String companyId;
    private String companyName;
    private String description;
    private LocalDate effectiveDate;
    private String originatorId;
    private String receiverId;
    private String status;
    private Instant timestamp;
    private String eventType;
}
