package com.waqiti.saga.definition;

import com.waqiti.saga.model.SagaStep;
import com.waqiti.saga.model.SagaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Payment Saga Definition
 *
 * Defines the workflow for processing payment transactions through
 * the saga orchestration pattern.
 *
 * Supports multiple payment types:
 * - SPLIT: Split payments across multiple recipients
 * - GROUP: Group payments with member approvals
 * - RECURRING: Subscription-based recurring payments
 * - INTERNATIONAL: Cross-border transfers with compliance
 * - BNPL: Buy-now-pay-later with credit checks
 * - STANDARD: Default payment flow
 *
 * Standard Payment Saga Steps:
 * 1. VALIDATE_PAYMENT_REQUEST - Validate payment details
 * 2. RESERVE_PAYMENT_FUNDS - Reserve funds in payer's wallet
 * 3. PERFORM_PAYMENT_ROUTING - Route payment to provider
 * 4. RECORD_PAYMENT_LEDGER - Record double-entry accounting
 * 5. SEND_PAYMENT_NOTIFICATION - Notify payer and payee
 * 6. FINALIZE_PAYMENT - Update payment status
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Component
@Slf4j
public class PaymentSagaDefinition implements SagaDefinition {

    @Override
    public String getSagaType() {
        return SagaType.PAYMENT.name();
    }

    @Override
    public List<SagaStep> defineSteps(String sagaId, Map<String, Object> sagaData) {
        String paymentType = (String) sagaData.getOrDefault("paymentType", "STANDARD");

        log.info("Defining payment saga steps: sagaId={}, paymentType={}", sagaId, paymentType);

        return switch (paymentType) {
            case "SPLIT" -> defineSplitPaymentSteps(sagaId, sagaData);
            case "GROUP" -> defineGroupPaymentSteps(sagaId, sagaData);
            case "RECURRING" -> defineRecurringPaymentSteps(sagaId, sagaData);
            case "INTERNATIONAL" -> defineInternationalPaymentSteps(sagaId, sagaData);
            case "BNPL" -> defineBnplPaymentSteps(sagaId, sagaData);
            default -> defineStandardPaymentSteps(sagaId, sagaData);
        };
    }

    /**
     * Standard payment saga steps
     */
    private List<SagaStep> defineStandardPaymentSteps(String sagaId, Map<String, Object> sagaData) {
        List<SagaStep> steps = new ArrayList<>();

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("VALIDATE_PAYMENT_REQUEST")
            .serviceName("payment-service")
            .endpoint("/api/payments/validate")
            .retryable(false)
            .compensable(false)
            .timeout(10000) // 10 seconds
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("RESERVE_PAYMENT_FUNDS")
            .serviceName("wallet-service")
            .endpoint("/api/wallets/reserve")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/wallets/release")
            .timeout(20000) // 20 seconds
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("PERFORM_PAYMENT_ROUTING")
            .serviceName("payment-service")
            .endpoint("/api/payments/execute")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/reverse")
            .timeout(30000) // 30 seconds
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("RECORD_PAYMENT_LEDGER")
            .serviceName("ledger-service")
            .endpoint("/api/ledger/record")
            .retryable(true)
            .compensable(false) // Ledger entries are append-only
            .timeout(10000) // 10 seconds
            .maxRetries(2)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("SEND_PAYMENT_NOTIFICATION")
            .serviceName("notification-service")
            .endpoint("/api/notifications/payment-completed")
            .retryable(true)
            .compensable(false)
            .timeout(5000) // 5 seconds
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("FINALIZE_PAYMENT")
            .serviceName("payment-service")
            .endpoint("/api/payments/finalize")
            .retryable(true)
            .compensable(false)
            .timeout(5000) // 5 seconds
            .maxRetries(2)
            .build());

        return steps;
    }

    /**
     * Split payment saga steps
     */
    private List<SagaStep> defineSplitPaymentSteps(String sagaId, Map<String, Object> sagaData) {
        List<SagaStep> steps = new ArrayList<>();

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("VALIDATE_SPLIT_DETAILS")
            .serviceName("payment-service")
            .endpoint("/api/payments/split/validate")
            .retryable(false)
            .compensable(false)
            .timeout(10000)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("RESERVE_FUNDS")
            .serviceName("wallet-service")
            .endpoint("/api/wallets/reserve")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/wallets/release")
            .timeout(20000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("CALCULATE_SPLITS")
            .serviceName("payment-service")
            .endpoint("/api/payments/split/calculate")
            .retryable(false)
            .compensable(false)
            .timeout(5000)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("EXECUTE_SPLIT_TRANSFERS")
            .serviceName("payment-service")
            .endpoint("/api/payments/split/execute")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/split/reverse")
            .timeout(60000) // 60 seconds for multiple transfers
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("RECORD_LEDGER_ENTRIES")
            .serviceName("ledger-service")
            .endpoint("/api/ledger/batch-record")
            .retryable(true)
            .compensable(false)
            .timeout(15000)
            .maxRetries(2)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("SEND_NOTIFICATIONS")
            .serviceName("notification-service")
            .endpoint("/api/notifications/split-payment-completed")
            .retryable(true)
            .compensable(false)
            .timeout(10000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("FINALIZE_SPLIT_PAYMENT")
            .serviceName("payment-service")
            .endpoint("/api/payments/split/finalize")
            .retryable(true)
            .compensable(false)
            .timeout(5000)
            .maxRetries(2)
            .build());

        return steps;
    }

    /**
     * Group payment saga steps
     */
    private List<SagaStep> defineGroupPaymentSteps(String sagaId, Map<String, Object> sagaData) {
        List<SagaStep> steps = new ArrayList<>();

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("VALIDATE_GROUP_MEMBERS")
            .serviceName("payment-service")
            .endpoint("/api/payments/group/validate")
            .retryable(false)
            .compensable(false)
            .timeout(10000)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("REQUEST_MEMBER_APPROVALS")
            .serviceName("payment-service")
            .endpoint("/api/payments/group/request-approvals")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/group/cancel-approvals")
            .timeout(300000) // 5 minutes for approvals
            .maxRetries(1)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("COLLECT_MEMBER_FUNDS")
            .serviceName("wallet-service")
            .endpoint("/api/wallets/collect-group")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/wallets/refund-group")
            .timeout(60000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("EXECUTE_GROUP_PAYMENT")
            .serviceName("payment-service")
            .endpoint("/api/payments/group/execute")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/group/reverse")
            .timeout(30000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("DISTRIBUTE_RECEIPTS")
            .serviceName("notification-service")
            .endpoint("/api/notifications/group-payment-completed")
            .retryable(true)
            .compensable(false)
            .timeout(15000)
            .maxRetries(3)
            .build());

        return steps;
    }

    /**
     * Recurring payment saga steps
     */
    private List<SagaStep> defineRecurringPaymentSteps(String sagaId, Map<String, Object> sagaData) {
        List<SagaStep> steps = new ArrayList<>();

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("VALIDATE_RECURRING_SETUP")
            .serviceName("payment-service")
            .endpoint("/api/payments/recurring/validate")
            .retryable(false)
            .compensable(false)
            .timeout(10000)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("CREATE_SUBSCRIPTION")
            .serviceName("payment-service")
            .endpoint("/api/payments/recurring/create-subscription")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/recurring/cancel-subscription")
            .timeout(15000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("SETUP_PAYMENT_MANDATE")
            .serviceName("wallet-service")
            .endpoint("/api/wallets/setup-mandate")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/wallets/revoke-mandate")
            .timeout(20000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("EXECUTE_INITIAL_PAYMENT")
            .serviceName("payment-service")
            .endpoint("/api/payments/recurring/execute-initial")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/recurring/reverse-initial")
            .timeout(30000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("SCHEDULE_FUTURE_PAYMENTS")
            .serviceName("payment-service")
            .endpoint("/api/payments/recurring/schedule")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/recurring/unschedule")
            .timeout(10000)
            .maxRetries(2)
            .build());

        return steps;
    }

    /**
     * International payment saga steps
     */
    private List<SagaStep> defineInternationalPaymentSteps(String sagaId, Map<String, Object> sagaData) {
        List<SagaStep> steps = new ArrayList<>();

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("COMPLIANCE_CHECK")
            .serviceName("compliance-service")
            .endpoint("/api/compliance/check-international-payment")
            .retryable(false)
            .compensable(false)
            .timeout(15000)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("SANCTIONS_SCREENING")
            .serviceName("compliance-service")
            .endpoint("/api/compliance/sanctions-screening")
            .retryable(true)
            .compensable(false)
            .timeout(20000)
            .maxRetries(2)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("CURRENCY_CONVERSION")
            .serviceName("currency-service")
            .endpoint("/api/currency/convert")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/currency/reverse-conversion")
            .timeout(10000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("INITIATE_SWIFT_TRANSFER")
            .serviceName("payment-service")
            .endpoint("/api/v1/international/transfers")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/international/swift/cancel")
            .timeout(120000) // 2 minutes for international transfer
            .maxRetries(2)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("TRACK_TRANSFER_STATUS")
            .serviceName("payment-service")
            .endpoint("/api/v1/international/track")
            .retryable(true)
            .compensable(false)
            .timeout(30000)
            .maxRetries(5)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("REGULATORY_REPORTING")
            .serviceName("compliance-service")
            .endpoint("/api/compliance/report-international-payment")
            .retryable(true)
            .compensable(false)
            .timeout(10000)
            .maxRetries(3)
            .build());

        return steps;
    }

    /**
     * BNPL (Buy Now Pay Later) payment saga steps
     */
    private List<SagaStep> defineBnplPaymentSteps(String sagaId, Map<String, Object> sagaData) {
        List<SagaStep> steps = new ArrayList<>();

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("CREDIT_CHECK")
            .serviceName("bnpl-service")
            .endpoint("/api/bnpl/credit-check")
            .retryable(false)
            .compensable(false)
            .timeout(15000)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("CALCULATE_INSTALLMENTS")
            .serviceName("bnpl-service")
            .endpoint("/api/bnpl/calculate-installments")
            .retryable(false)
            .compensable(false)
            .timeout(5000)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("CREATE_LOAN_AGREEMENT")
            .serviceName("bnpl-service")
            .endpoint("/api/bnpl/create-agreement")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/bnpl/cancel-agreement")
            .timeout(20000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("EXECUTE_MERCHANT_PAYMENT")
            .serviceName("payment-service")
            .endpoint("/api/payments/merchant/execute")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/payments/merchant/reverse")
            .timeout(30000)
            .maxRetries(3)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("SETUP_INSTALLMENT_SCHEDULE")
            .serviceName("bnpl-service")
            .endpoint("/api/bnpl/setup-schedule")
            .retryable(true)
            .compensable(true)
            .compensationEndpoint("/api/bnpl/cancel-schedule")
            .timeout(10000)
            .maxRetries(2)
            .build());

        steps.add(SagaStep.builder()
            .sagaId(sagaId)
            .stepName("SEND_AGREEMENT_DOCUMENTS")
            .serviceName("notification-service")
            .endpoint("/api/notifications/bnpl-agreement")
            .retryable(true)
            .compensable(false)
            .timeout(10000)
            .maxRetries(3)
            .build());

        return steps;
    }
}
