package com.waqiti.common.saga;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Predefined saga definitions for common payment workflows.
 * Provides templates for typical financial operations in the Waqiti platform.
 */
@Component
public class PaymentSagaDefinitions {

    /**
     * P2P Money Transfer Saga
     * Orchestrates money transfer between two users
     */
    public SagaDefinition createP2PTransferSaga() {
        return SagaDefinition.builder()
            .sagaType("P2P_TRANSFER")
            .name("Peer-to-Peer Money Transfer")
            .description("Transfer money between two user accounts with validation and notifications")
            .version("1.0")
            .timeoutMinutes(15)
            .stepTimeoutMinutes(3)
            .maxRetries(3)
            .parallelExecution(true)
            .compensationStrategy(CompensationStrategy.REVERSE_ORDER)
            .build()
            
            // Step 1: Validate sender account and balance
            .addStep(SagaStep.builder()
                .stepId("validate_sender")
                .stepType("validation")
                .name("Validate Sender Account")
                .serviceEndpoint("/account-service/validate-sender")
                .httpMethod("POST")
                .parameters(Map.of(
                    "validationType", "BALANCE_CHECK",
                    "includeHolds", true
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .withTag("validation")
                .build())
            
            // Step 2: Validate receiver account
            .addStep(SagaStep.builder()
                .stepId("validate_receiver")
                .stepType("validation")
                .name("Validate Receiver Account")
                .serviceEndpoint("/account-service/validate-receiver")
                .httpMethod("POST")
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .withTag("validation")
                .build())
            
            // Step 3: Check fraud and compliance
            .addStep(SagaStep.builder()
                .stepId("fraud_check")
                .stepType("security")
                .name("Fraud and Compliance Check")
                .serviceEndpoint("/fraud-service/check-transaction")
                .httpMethod("POST")
                .dependencies(Arrays.asList("validate_sender", "validate_receiver"))
                .parameters(Map.of(
                    "checkType", "P2P_TRANSFER",
                    "riskProfile", "STANDARD"
                ))
                .timeoutSeconds(60)
                .critical(true)
                .idempotent(true)
                .withTag("security")
                .build())
            
            // Step 4: Place hold on sender account
            .addStep(SagaStep.builder()
                .stepId("place_hold")
                .stepType("financial")
                .name("Place Hold on Sender Account")
                .serviceEndpoint("/account-service/place-hold")
                .httpMethod("POST")
                .dependencies(Arrays.asList("fraud_check"))
                .parameters(Map.of(
                    "holdType", "TRANSFER_HOLD",
                    "holdReason", "P2P_TRANSFER_PENDING"
                ))
                .timeoutSeconds(45)
                .critical(true)
                .idempotent(false)
                .withCompensation("/account-service/release-hold", Map.of(
                    "compensationType", "RELEASE_HOLD"
                ))
                .withTag("financial")
                .build())
            
            // Step 5: Create transaction record
            .addStep(SagaStep.builder()
                .stepId("create_transaction")
                .stepType("record")
                .name("Create Transaction Record")
                .serviceEndpoint("/transaction-service/create")
                .httpMethod("POST")
                .dependencies(Arrays.asList("place_hold"))
                .parameters(Map.of(
                    "transactionType", "P2P_TRANSFER",
                    "status", "PENDING"
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .withCompensation("/transaction-service/cancel", Map.of(
                    "reason", "SAGA_COMPENSATION"
                ))
                .withTag("record")
                .build())
            
            // Step 6: Process the actual transfer
            .addStep(SagaStep.builder()
                .stepId("process_transfer")
                .stepType("financial")
                .name("Process Money Transfer")
                .serviceEndpoint("/payment-service/process-transfer")
                .httpMethod("POST")
                .dependencies(Arrays.asList("create_transaction"))
                .parameters(Map.of(
                    "processingType", "IMMEDIATE",
                    "confirmationRequired", false
                ))
                .timeoutSeconds(90)
                .critical(true)
                .idempotent(false)
                .withCompensation("/payment-service/reverse-transfer", Map.of(
                    "reverseType", "FULL_REVERSAL"
                ))
                .withTag("financial")
                .build())
            
            // Step 7: Update transaction status
            .addStep(SagaStep.builder()
                .stepId("update_transaction")
                .stepType("record")
                .name("Update Transaction Status")
                .serviceEndpoint("/transaction-service/update-status")
                .httpMethod("PATCH")
                .dependencies(Arrays.asList("process_transfer"))
                .parameters(Map.of(
                    "status", "COMPLETED",
                    "completedAt", "{{current_timestamp}}"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .withTag("record")
                .build())
            
            // Step 8: Send notification to sender
            .addStep(SagaStep.builder()
                .stepId("notify_sender")
                .stepType("notification")
                .name("Notify Sender")
                .serviceEndpoint("/notification-service/send")
                .httpMethod("POST")
                .dependencies(Arrays.asList("update_transaction"))
                .parameters(Map.of(
                    "template", "TRANSFER_SENT_CONFIRMATION",
                    "channel", "EMAIL_AND_PUSH"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .asOptional()
                .withTag("notification")
                .build())
            
            // Step 9: Send notification to receiver
            .addStep(SagaStep.builder()
                .stepId("notify_receiver")
                .stepType("notification")
                .name("Notify Receiver")
                .serviceEndpoint("/notification-service/send")
                .httpMethod("POST")
                .dependencies(Arrays.asList("update_transaction"))
                .parameters(Map.of(
                    "template", "TRANSFER_RECEIVED_NOTIFICATION",
                    "channel", "EMAIL_AND_PUSH"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .asOptional()
                .withTag("notification")
                .build())
            
            .withProperty("category", "payment")
            .withProperty("riskLevel", "medium")
            .withTag("p2p")
            .withTag("transfer")
            .withTag("payment");
    }

    /**
     * Virtual Card Payment Saga
     * Orchestrates payment using a virtual card
     */
    public SagaDefinition createVirtualCardPaymentSaga() {
        return SagaDefinition.builder()
            .sagaType("VIRTUAL_CARD_PAYMENT")
            .name("Virtual Card Payment Processing")
            .description("Process payment using virtual card with merchant validation")
            .version("1.0")
            .timeoutMinutes(10)
            .stepTimeoutMinutes(2)
            .maxRetries(3)
            .parallelExecution(true)
            .compensationStrategy(CompensationStrategy.REVERSE_ORDER)
            .build()
            
            // Step 1: Validate virtual card
            .addStep(SagaStep.builder()
                .stepId("validate_card")
                .stepType("validation")
                .name("Validate Virtual Card")
                .serviceEndpoint("/card-service/validate")
                .httpMethod("POST")
                .parameters(Map.of(
                    "validationType", "PAYMENT_AUTHORIZATION",
                    "checkLimits", true,
                    "checkStatus", true
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 2: Validate merchant
            .addStep(SagaStep.builder()
                .stepId("validate_merchant")
                .stepType("validation")
                .name("Validate Merchant")
                .serviceEndpoint("/merchant-service/validate")
                .httpMethod("POST")
                .parameters(Map.of(
                    "validationType", "PAYMENT_ACCEPTANCE",
                    "checkStatus", true
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 3: Fraud screening
            .addStep(SagaStep.builder()
                .stepId("fraud_screening")
                .stepType("security")
                .name("Fraud Screening")
                .serviceEndpoint("/fraud-service/screen-payment")
                .httpMethod("POST")
                .dependencies(Arrays.asList("validate_card", "validate_merchant"))
                .parameters(Map.of(
                    "screeningType", "CARD_PAYMENT",
                    "riskLevel", "HIGH"
                ))
                .timeoutSeconds(45)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 4: Authorize payment
            .addStep(SagaStep.builder()
                .stepId("authorize_payment")
                .stepType("financial")
                .name("Authorize Payment")
                .serviceEndpoint("/payment-service/authorize")
                .httpMethod("POST")
                .dependencies(Arrays.asList("fraud_screening"))
                .parameters(Map.of(
                    "authorizationType", "CARD_PAYMENT",
                    "captureMode", "IMMEDIATE"
                ))
                .timeoutSeconds(60)
                .critical(true)
                .idempotent(false)
                .withCompensation("/payment-service/void-authorization", Map.of(
                    "voidReason", "SAGA_COMPENSATION"
                ))
                .build())
            
            // Step 5: Update card limits
            .addStep(SagaStep.builder()
                .stepId("update_limits")
                .stepType("financial")
                .name("Update Card Limits")
                .serviceEndpoint("/card-service/update-limits")
                .httpMethod("PATCH")
                .dependencies(Arrays.asList("authorize_payment"))
                .parameters(Map.of(
                    "updateType", "DECREMENT_AVAILABLE"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .withCompensation("/card-service/restore-limits", Map.of(
                    "restoreType", "INCREMENT_AVAILABLE"
                ))
                .build())
            
            // Step 6: Record transaction
            .addStep(SagaStep.builder()
                .stepId("record_transaction")
                .stepType("record")
                .name("Record Transaction")
                .serviceEndpoint("/transaction-service/record-payment")
                .httpMethod("POST")
                .dependencies(Arrays.asList("authorize_payment"))
                .parameters(Map.of(
                    "paymentMethod", "VIRTUAL_CARD",
                    "status", "COMPLETED"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .build())
            
            // Step 7: Send receipt
            .addStep(SagaStep.builder()
                .stepId("send_receipt")
                .stepType("notification")
                .name("Send Payment Receipt")
                .serviceEndpoint("/notification-service/send-receipt")
                .httpMethod("POST")
                .dependencies(Arrays.asList("record_transaction"))
                .parameters(Map.of(
                    "receiptType", "CARD_PAYMENT",
                    "channel", "EMAIL"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .asOptional()
                .build())
            
            .withProperty("category", "payment")
            .withProperty("paymentMethod", "virtual_card")
            .withTag("card")
            .withTag("payment")
            .withTag("merchant");
    }

    /**
     * Account Top-Up Saga
     * Orchestrates account funding from external source
     */
    public SagaDefinition createAccountTopUpSaga() {
        return SagaDefinition.builder()
            .sagaType("ACCOUNT_TOPUP")
            .name("Account Top-Up Processing")
            .description("Add funds to user account from external payment method")
            .version("1.0")
            .timeoutMinutes(20)
            .stepTimeoutMinutes(5)
            .maxRetries(3)
            .parallelExecution(false) // Sequential for banking operations
            .compensationStrategy(CompensationStrategy.REVERSE_ORDER)
            .build()
            
            // Step 1: Validate user account
            .addStep(SagaStep.builder()
                .stepId("validate_account")
                .stepType("validation")
                .name("Validate User Account")
                .serviceEndpoint("/account-service/validate-topup")
                .httpMethod("POST")
                .parameters(Map.of(
                    "validationType", "TOPUP_ELIGIBILITY",
                    "checkLimits", true
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 2: Validate payment method
            .addStep(SagaStep.builder()
                .stepId("validate_payment_method")
                .stepType("validation")
                .name("Validate Payment Method")
                .serviceEndpoint("/payment-service/validate-source")
                .httpMethod("POST")
                .dependencies(Arrays.asList("validate_account"))
                .parameters(Map.of(
                    "validationType", "FUNDING_SOURCE",
                    "checkStatus", true
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 3: AML/KYC check
            .addStep(SagaStep.builder()
                .stepId("aml_check")
                .stepType("compliance")
                .name("AML/KYC Compliance Check")
                .serviceEndpoint("/compliance-service/check-topup")
                .httpMethod("POST")
                .dependencies(Arrays.asList("validate_payment_method"))
                .parameters(Map.of(
                    "checkType", "TOPUP_COMPLIANCE",
                    "includeAML", true,
                    "includeKYC", true
                ))
                .timeoutSeconds(90)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 4: Charge external payment method
            .addStep(SagaStep.builder()
                .stepId("charge_external")
                .stepType("financial")
                .name("Charge External Payment Method")
                .serviceEndpoint("/payment-service/charge-external")
                .httpMethod("POST")
                .dependencies(Arrays.asList("aml_check"))
                .parameters(Map.of(
                    "chargeType", "ACCOUNT_FUNDING",
                    "captureMode", "IMMEDIATE"
                ))
                .timeoutSeconds(120)
                .critical(true)
                .idempotent(false)
                .withCompensation("/payment-service/refund-external", Map.of(
                    "refundType", "FULL_REFUND",
                    "reason", "TOPUP_FAILED"
                ))
                .build())
            
            // Step 5: Credit user account
            .addStep(SagaStep.builder()
                .stepId("credit_account")
                .stepType("financial")
                .name("Credit User Account")
                .serviceEndpoint("/account-service/credit")
                .httpMethod("POST")
                .dependencies(Arrays.asList("charge_external"))
                .parameters(Map.of(
                    "creditType", "TOPUP_CREDIT",
                    "immediateAvailability", true
                ))
                .timeoutSeconds(60)
                .critical(true)
                .idempotent(false)
                .withCompensation("/account-service/debit", Map.of(
                    "debitType", "TOPUP_REVERSAL"
                ))
                .build())
            
            // Step 6: Record transaction
            .addStep(SagaStep.builder()
                .stepId("record_topup")
                .stepType("record")
                .name("Record Top-Up Transaction")
                .serviceEndpoint("/transaction-service/record-topup")
                .httpMethod("POST")
                .dependencies(Arrays.asList("credit_account"))
                .parameters(Map.of(
                    "transactionType", "ACCOUNT_TOPUP",
                    "status", "COMPLETED"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .build())
            
            // Step 7: Send confirmation
            .addStep(SagaStep.builder()
                .stepId("send_confirmation")
                .stepType("notification")
                .name("Send Top-Up Confirmation")
                .serviceEndpoint("/notification-service/send-confirmation")
                .httpMethod("POST")
                .dependencies(Arrays.asList("record_topup"))
                .parameters(Map.of(
                    "confirmationType", "TOPUP_SUCCESSFUL",
                    "channel", "EMAIL_AND_PUSH"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .asOptional()
                .build())
            
            .withProperty("category", "funding")
            .withProperty("riskLevel", "medium")
            .withTag("topup")
            .withTag("funding")
            .withTag("external");
    }

    /**
     * Multi-Currency Exchange Saga
     * Orchestrates currency exchange with rate locking
     */
    public SagaDefinition createCurrencyExchangeSaga() {
        return SagaDefinition.builder()
            .sagaType("CURRENCY_EXCHANGE")
            .name("Multi-Currency Exchange")
            .description("Exchange currency between user wallets with rate locking")
            .version("1.0")
            .timeoutMinutes(10)
            .stepTimeoutMinutes(2)
            .maxRetries(2)
            .parallelExecution(true)
            .compensationStrategy(CompensationStrategy.REVERSE_ORDER)
            .build()
            
            // Step 1: Lock exchange rate
            .addStep(SagaStep.builder()
                .stepId("lock_rate")
                .stepType("financial")
                .name("Lock Exchange Rate")
                .serviceEndpoint("/exchange-service/lock-rate")
                .httpMethod("POST")
                .parameters(Map.of(
                    "lockDuration", 300, // 5 minutes
                    "rateType", "REAL_TIME"
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .withCompensation("/exchange-service/release-rate-lock", Map.of(
                    "releaseReason", "SAGA_COMPENSATION"
                ))
                .build())
            
            // Step 2: Validate source wallet
            .addStep(SagaStep.builder()
                .stepId("validate_source")
                .stepType("validation")
                .name("Validate Source Wallet")
                .serviceEndpoint("/wallet-service/validate-exchange-source")
                .httpMethod("POST")
                .parameters(Map.of(
                    "validationType", "BALANCE_AND_LIMITS"
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 3: Validate target wallet
            .addStep(SagaStep.builder()
                .stepId("validate_target")
                .stepType("validation")
                .name("Validate Target Wallet")
                .serviceEndpoint("/wallet-service/validate-exchange-target")
                .httpMethod("POST")
                .parameters(Map.of(
                    "validationType", "CURRENCY_SUPPORT"
                ))
                .timeoutSeconds(30)
                .critical(true)
                .idempotent(true)
                .build())
            
            // Step 4: Debit source wallet
            .addStep(SagaStep.builder()
                .stepId("debit_source")
                .stepType("financial")
                .name("Debit Source Wallet")
                .serviceEndpoint("/wallet-service/debit")
                .httpMethod("POST")
                .dependencies(Arrays.asList("lock_rate", "validate_source", "validate_target"))
                .parameters(Map.of(
                    "debitType", "CURRENCY_EXCHANGE"
                ))
                .timeoutSeconds(45)
                .critical(true)
                .idempotent(false)
                .withCompensation("/wallet-service/credit", Map.of(
                    "creditType", "EXCHANGE_REVERSAL"
                ))
                .build())
            
            // Step 5: Credit target wallet
            .addStep(SagaStep.builder()
                .stepId("credit_target")
                .stepType("financial")
                .name("Credit Target Wallet")
                .serviceEndpoint("/wallet-service/credit")
                .httpMethod("POST")
                .dependencies(Arrays.asList("debit_source"))
                .parameters(Map.of(
                    "creditType", "CURRENCY_EXCHANGE"
                ))
                .timeoutSeconds(45)
                .critical(true)
                .idempotent(false)
                .withCompensation("/wallet-service/debit", Map.of(
                    "debitType", "EXCHANGE_REVERSAL"
                ))
                .build())
            
            // Step 6: Record exchange transaction
            .addStep(SagaStep.builder()
                .stepId("record_exchange")
                .stepType("record")
                .name("Record Exchange Transaction")
                .serviceEndpoint("/transaction-service/record-exchange")
                .httpMethod("POST")
                .dependencies(Arrays.asList("credit_target"))
                .parameters(Map.of(
                    "transactionType", "CURRENCY_EXCHANGE",
                    "status", "COMPLETED"
                ))
                .timeoutSeconds(30)
                .critical(false)
                .idempotent(true)
                .build())
            
            .withProperty("category", "exchange")
            .withProperty("riskLevel", "low")
            .withTag("currency")
            .withTag("exchange")
            .withTag("wallet");
    }

    /**
     * Get saga definition by type
     */
    public SagaDefinition getSagaDefinition(String sagaType) {
        switch (sagaType) {
            case "P2P_TRANSFER":
                return createP2PTransferSaga();
            case "VIRTUAL_CARD_PAYMENT":
                return createVirtualCardPaymentSaga();
            case "ACCOUNT_TOPUP":
                return createAccountTopUpSaga();
            case "CURRENCY_EXCHANGE":
                return createCurrencyExchangeSaga();
            default:
                throw new IllegalArgumentException("Unknown saga type: " + sagaType);
        }
    }

    /**
     * Get all available saga types
     */
    public List<String> getAvailableSagaTypes() {
        return Arrays.asList(
            "P2P_TRANSFER",
            "VIRTUAL_CARD_PAYMENT", 
            "ACCOUNT_TOPUP",
            "CURRENCY_EXCHANGE"
        );
    }

    /**
     * Validate saga definition
     */
    public void validateSagaDefinition(String sagaType) {
        SagaDefinition definition = getSagaDefinition(sagaType);
        definition.validate();
    }
}