package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO for bill payment summary report
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillPaymentSummaryReport {

    private LocalDate fromDate;

    private LocalDate toDate;

    private String userId;

    private Integer totalPayments;

    private Integer successfulPayments;

    private Integer failedPayments;

    private Integer pendingPayments;

    private BigDecimal totalAmountPaid;

    private BigDecimal totalFees;

    private String currency;

    private BigDecimal averagePaymentAmount;

    private Map<String, Integer> paymentsByCategory; // Category -> Count

    private Map<String, BigDecimal> amountsByCategory; // Category -> Total Amount

    private Map<String, Integer> paymentsByBiller; // Biller Name -> Count

    private List<TopBillerDto> topBillers;

    private PaymentTrendDto paymentTrends;
}
