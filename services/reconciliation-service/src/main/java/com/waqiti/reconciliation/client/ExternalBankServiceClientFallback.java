package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ExternalBankServiceClientFallback implements ExternalBankServiceClient {

    @Override
    public ExternalSettlementConfirmation getSettlementConfirmation(UUID settlementId, LocalDate settlementDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve settlement confirmation - External Bank Service unavailable. " +
                "SettlementId: {}, Date: {}", settlementId, settlementDate);
        
        return ExternalSettlementConfirmation.builder()
                .settlementId(settlementId)
                .status("UNAVAILABLE")
                .confirmed(false)
                .message("External bank confirmation temporarily unavailable - manual verification required")
                .requiresManualReview(true)
                .build();
    }

    @Override
    public NostroAccountStatement getNostroAccountStatement(UUID accountId, LocalDate startDate, LocalDate endDate) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Cannot retrieve nostro statement for reconciliation. " +
                "Account: {}, Period: {} to {}", accountId, startDate, endDate);
        
        return NostroAccountStatement.builder()
                .accountId(accountId)
                .startDate(startDate)
                .endDate(endDate)
                .entries(Collections.emptyList())
                .status("UNAVAILABLE")
                .message("Nostro statement unavailable - reconciliation blocked")
                .build();
    }

    @Override
    public NostroBalanceConfirmation getNostroBalanceConfirmation(UUID accountId, LocalDate asOfDate) {
        log.error("FALLBACK ACTIVATED: Cannot confirm nostro balance - External Bank Service unavailable. " +
                "Account: {}, Date: {}", accountId, asOfDate);
        
        return NostroBalanceConfirmation.builder()
                .accountId(accountId)
                .asOfDate(asOfDate)
                .balance(null)
                .confirmed(false)
                .status("UNAVAILABLE")
                .message("Balance confirmation unavailable - manual verification required")
                .build();
    }

    @Override
    public SettlementInstructionResult sendSettlementInstruction(SettlementInstructionRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING settlement instruction - External Bank Service unavailable. " +
                "Amount: {}, Currency: {}", request.getAmount(), request.getCurrency());
        
        return SettlementInstructionResult.builder()
                .success(false)
                .instructionId(null)
                .status("BLOCKED")
                .message("Settlement instruction blocked - external bank service unavailable")
                .errorCode("EXTERNAL_BANK_UNAVAILABLE")
                .requiresRetry(true)
                .build();
    }

    @Override
    public List<PaymentConfirmation> getPaymentConfirmations(LocalDate startDate, LocalDate endDate, String bankCode) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve payment confirmations. Period: {} to {}, Bank: {}", 
                startDate, endDate, bankCode);
        return Collections.emptyList();
    }

    @Override
    public SwiftMessageVerificationResult verifySwiftMessage(SwiftMessageVerificationRequest request) {
        log.error("FALLBACK ACTIVATED: Cannot verify SWIFT message - External Bank Service unavailable");
        
        return SwiftMessageVerificationResult.builder()
                .verified(false)
                .messageId(request.getMessageId())
                .status("VERIFICATION_UNAVAILABLE")
                .message("SWIFT verification unavailable - manual check required")
                .build();
    }

    @Override
    public CorrespondentAccountBalance getCorrespondentAccountBalance(String bankCode, String accountNumber, LocalDate asOfDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve correspondent balance. Bank: {}, Account: {}", 
                bankCode, accountNumber);
        
        return CorrespondentAccountBalance.builder()
                .bankCode(bankCode)
                .accountNumber(accountNumber)
                .balance(null)
                .currency(null)
                .asOfDate(asOfDate)
                .status("UNAVAILABLE")
                .build();
    }

    @Override
    public BankStatementDownloadResult downloadBankStatement(String bankCode, UUID accountId, LocalDate statementDate) {
        log.error("FALLBACK ACTIVATED: Cannot download bank statement. Bank: {}, Date: {}", bankCode, statementDate);
        
        return BankStatementDownloadResult.builder()
                .success(false)
                .statementUrl(null)
                .errorMessage("Bank statement download unavailable")
                .build();
    }

    @Override
    public ExternalPaymentStatus getExternalPaymentStatus(String externalReference) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve external payment status. Reference: {}", externalReference);
        
        return ExternalPaymentStatus.builder()
                .reference(externalReference)
                .status("UNKNOWN")
                .message("External payment status unavailable")
                .build();
    }

    @Override
    public ExternalReconciliationResult initiateExternalReconciliation(ExternalReconciliationRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING external reconciliation - External Bank Service unavailable");
        
        return ExternalReconciliationResult.builder()
                .success(false)
                .reconciliationId(null)
                .status("BLOCKED")
                .message("External reconciliation blocked - service unavailable")
                .build();
    }

    @Override
    public ForeignExchangeRates getForeignExchangeRates(String baseCurrency, List<String> targetCurrencies, LocalDate rateDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve FX rates. Base: {}, Date: {}", baseCurrency, rateDate);
        
        return ForeignExchangeRates.builder()
                .baseCurrency(baseCurrency)
                .rateDate(rateDate)
                .rates(Collections.emptyMap())
                .status("UNAVAILABLE")
                .message("FX rates unavailable - use fallback rates")
                .build();
    }

    @Override
    public RegulatoryReportSubmissionResult submitRegulatoryReport(RegulatoryReportRequest request) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Cannot submit regulatory report! Type: {}", 
                request.getReportType());
        
        return RegulatoryReportSubmissionResult.builder()
                .success(false)
                .submissionId(null)
                .status("FAILED")
                .message("Regulatory report submission failed - MANUAL SUBMISSION REQUIRED")
                .requiresManualSubmission(true)
                .build();
    }

    @Override
    public ClearingHouseSettlementReport getClearingHouseSettlementReport(String clearingHouseCode, LocalDate settlementDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve clearing house report. Code: {}, Date: {}", 
                clearingHouseCode, settlementDate);
        
        return ClearingHouseSettlementReport.builder()
                .clearingHouseCode(clearingHouseCode)
                .settlementDate(settlementDate)
                .transactions(Collections.emptyList())
                .status("UNAVAILABLE")
                .build();
    }

    @Override
    public CounterpartyValidationResult validateCounterparty(CounterpartyValidationRequest request) {
        log.warn("FALLBACK ACTIVATED: Cannot validate counterparty - defaulting to INVALID");
        
        return CounterpartyValidationResult.builder()
                .valid(false)
                .counterpartyId(request.getCounterpartyId())
                .status("VALIDATION_UNAVAILABLE")
                .message("Counterparty validation unavailable - treating as invalid for safety")
                .build();
    }
}