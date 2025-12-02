package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "external-bank-service", 
    url = "${services.external-bank-service.url:http://external-bank-service:8080}",
    fallback = ExternalBankServiceClientFallback.class
)
public interface ExternalBankServiceClient {

    /**
     * Get settlement confirmation from external bank
     */
    @GetMapping("/api/v1/external/settlement-confirmation/{settlementId}")
    ExternalSettlementConfirmation getSettlementConfirmation(
        @PathVariable UUID settlementId,
        @RequestParam("settlementDate") LocalDate settlementDate
    );

    /**
     * Get nostro account statement
     */
    @GetMapping("/api/v1/external/nostro/{accountId}/statement")
    NostroAccountStatement getNostroAccountStatement(
        @PathVariable UUID accountId,
        @RequestParam("startDate") LocalDate startDate,
        @RequestParam("endDate") LocalDate endDate
    );

    /**
     * Get nostro balance confirmation
     */
    @GetMapping("/api/v1/external/nostro/{accountId}/balance")
    NostroBalanceConfirmation getNostroBalanceConfirmation(
        @PathVariable UUID accountId,
        @RequestParam("asOfDate") LocalDate asOfDate
    );

    /**
     * Send settlement instruction
     */
    @PostMapping("/api/v1/external/settlement-instruction")
    SettlementInstructionResult sendSettlementInstruction(@RequestBody SettlementInstructionRequest request);

    /**
     * Get payment confirmations
     */
    @GetMapping("/api/v1/external/payment-confirmations")
    List<PaymentConfirmation> getPaymentConfirmations(
        @RequestParam("startDate") LocalDate startDate,
        @RequestParam("endDate") LocalDate endDate,
        @RequestParam(value = "bankCode", required = false) String bankCode
    );

    /**
     * Verify SWIFT message
     */
    @PostMapping("/api/v1/external/swift/verify")
    SwiftMessageVerificationResult verifySwiftMessage(@RequestBody SwiftMessageVerificationRequest request);

    /**
     * Get account balance from correspondent bank
     */
    @GetMapping("/api/v1/external/correspondent/{bankCode}/balance/{accountNumber}")
    CorrespondentAccountBalance getCorrespondentAccountBalance(
        @PathVariable String bankCode,
        @PathVariable String accountNumber,
        @RequestParam("asOfDate") LocalDate asOfDate
    );

    /**
     * Download bank statement file
     */
    @GetMapping("/api/v1/external/statement-download/{bankCode}")
    BankStatementDownloadResult downloadBankStatement(
        @PathVariable String bankCode,
        @RequestParam("accountId") UUID accountId,
        @RequestParam("statementDate") LocalDate statementDate
    );

    /**
     * Check payment status with external bank
     */
    @GetMapping("/api/v1/external/payment-status/{externalReference}")
    ExternalPaymentStatus getExternalPaymentStatus(@PathVariable String externalReference);

    /**
     * Initiate account reconciliation with external bank
     */
    @PostMapping("/api/v1/external/reconciliation/initiate")
    ExternalReconciliationResult initiateExternalReconciliation(@RequestBody ExternalReconciliationRequest request);

    /**
     * Get foreign exchange rates from external provider
     */
    @GetMapping("/api/v1/external/fx-rates")
    ForeignExchangeRates getForeignExchangeRates(
        @RequestParam("baseCurrency") String baseCurrency,
        @RequestParam("targetCurrencies") List<String> targetCurrencies,
        @RequestParam("rateDate") LocalDate rateDate
    );

    /**
     * Submit regulatory report to external authority
     */
    @PostMapping("/api/v1/external/regulatory-report")
    RegulatoryReportSubmissionResult submitRegulatoryReport(@RequestBody RegulatoryReportRequest request);

    /**
     * Get clearing house settlement report
     */
    @GetMapping("/api/v1/external/clearing-house/{clearingHouseCode}/settlement-report")
    ClearingHouseSettlementReport getClearingHouseSettlementReport(
        @PathVariable String clearingHouseCode,
        @RequestParam("settlementDate") LocalDate settlementDate
    );

    /**
     * Validate counterparty information
     */
    @PostMapping("/api/v1/external/counterparty/validate")
    CounterpartyValidationResult validateCounterparty(@RequestBody CounterpartyValidationRequest request);
}