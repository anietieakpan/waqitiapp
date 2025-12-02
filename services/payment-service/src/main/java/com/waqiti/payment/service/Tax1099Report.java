package com.waqiti.payment.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class Tax1099Report {
    private UUID reportId;
    private UUID businessProfileId;
    private String businessName;
    private String taxId;
    private int year;
    private BigDecimal totalPayments;
    private String documentUrl;
    private List<PaymentSummary> paymentSummaries;
    private Instant generatedAt;
    private String status;
}