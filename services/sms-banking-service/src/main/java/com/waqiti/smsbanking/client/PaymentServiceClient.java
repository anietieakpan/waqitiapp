package com.waqiti.smsbanking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign Client for Payment Service
 *
 * Handles communication with the payment-service microservice for
 * airtime purchases, bill payments, and loan operations.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 */
@FeignClient(
    name = "payment-service",
    url = "${services.payment-service.url:http://payment-service:8080}"
)
public interface PaymentServiceClient {

    /**
     * Purchase airtime
     *
     * @param request Airtime purchase request
     * @return Purchase result
     */
    @PostMapping("/api/v1/payments/airtime")
    AirtimePurchaseResult purchaseAirtime(@RequestBody AirtimePurchaseRequest request);

    /**
     * Get loan status
     *
     * @param userId User ID
     * @return Loan status information
     */
    @GetMapping("/api/v1/loans/{userId}/status")
    LoanStatusDTO getLoanStatus(@PathVariable("userId") String userId);

    /**
     * Make loan payment
     *
     * @param request Loan payment request
     * @return Payment result
     */
    @PostMapping("/api/v1/loans/payment")
    LoanPaymentResult makeLoanPayment(@RequestBody LoanPaymentRequest request);

    /**
     * Pay bill
     *
     * @param request Bill payment request
     * @return Payment result
     */
    @PostMapping("/api/v1/payments/bills")
    BillPaymentResult payBill(@RequestBody BillPaymentRequest request);

    /**
     * Get supported billers
     *
     * @return List of supported billers
     */
    @GetMapping("/api/v1/payments/billers")
    List<BillerDTO> getSupportedBillers();

    /**
     * Airtime Purchase Request
     */
    record AirtimePurchaseRequest(
        String userId,
        String phoneNumber,
        BigDecimal amount,
        String provider,
        String channel,
        String correlationId
    ) {}

    /**
     * Airtime Purchase Result
     */
    record AirtimePurchaseResult(
        boolean success,
        String transactionId,
        String message,
        String confirmationCode,
        BigDecimal newBalance
    ) {}

    /**
     * Loan Status DTO
     */
    record LoanStatusDTO(
        String loanId,
        String status,
        BigDecimal outstandingBalance,
        BigDecimal monthlyPayment,
        LocalDateTime nextPaymentDate,
        boolean hasActiveLoan
    ) {}

    /**
     * Loan Payment Request
     */
    record LoanPaymentRequest(
        String userId,
        String loanId,
        BigDecimal amount,
        String channel,
        String correlationId
    ) {}

    /**
     * Loan Payment Result
     */
    record LoanPaymentResult(
        boolean success,
        String transactionId,
        String message,
        BigDecimal remainingBalance
    ) {}

    /**
     * Bill Payment Request
     */
    record BillPaymentRequest(
        String userId,
        String billerCode,
        String accountNumber,
        BigDecimal amount,
        String channel,
        String correlationId
    ) {}

    /**
     * Bill Payment Result
     */
    record BillPaymentResult(
        boolean success,
        String transactionId,
        String message,
        String receiptNumber
    ) {}

    /**
     * Biller DTO
     */
    record BillerDTO(
        String code,
        String name,
        String category
    ) {}
}
