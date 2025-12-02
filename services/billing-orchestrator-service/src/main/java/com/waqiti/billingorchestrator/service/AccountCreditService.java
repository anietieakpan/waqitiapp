package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.request.ApplyCreditRequest;
import com.waqiti.billingorchestrator.dto.response.AccountCreditsResponse;
import com.waqiti.billingorchestrator.dto.response.CreditResponse;
import com.waqiti.billingorchestrator.entity.AccountCredit;
import com.waqiti.billingorchestrator.entity.AccountCredit.CreditStatus;
import com.waqiti.billingorchestrator.entity.AccountCredit.CreditType;
import com.waqiti.billingorchestrator.repository.AccountCreditRepository;
import com.waqiti.common.idempotency.Idempotent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Account Credit Service
 *
 * Manages customer account credits for refunds, goodwill, promotions.
 *
 * BUSINESS USE CASES:
 * - Service refunds (downtime, bugs)
 * - Goodwill credits (customer retention)
 * - Promotional credits (marketing campaigns)
 * - Referral bonuses
 * - Dispute settlements
 *
 * AUTO-APPLICATION:
 * - Credits auto-apply to invoices if autoApply = true
 * - FIFO (First In, First Out) application order
 * - Partial credits supported
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountCreditService {

    private final AccountCreditRepository accountCreditRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    private final Counter creditsIssued;
    private final Counter creditsApplied;
    private final Counter creditsExpired;

    public AccountCreditService(
            AccountCreditRepository accountCreditRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.accountCreditRepository = accountCreditRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;

        // Initialize metrics
        this.creditsIssued = Counter.builder("billing.credits.issued")
                .description("Total credits issued")
                .register(meterRegistry);
        this.creditsApplied = Counter.builder("billing.credits.applied")
                .description("Total credits applied to invoices")
                .register(meterRegistry);
        this.creditsExpired = Counter.builder("billing.credits.expired")
                .description("Total credits expired")
                .register(meterRegistry);
    }

    /**
     * Issues credit to account
     */
    @Transactional
    @Idempotent(
        keyExpression = "'account-credit:' + #request.accountId + ':' + #request.referenceId",
        serviceName = "billing-orchestrator-service",
        operationType = "APPLY_CREDIT",
        userIdExpression = "#request.accountId",
        ttlHours = 168
    )
    @CircuitBreaker(name = "account-credit-service")
    public CreditResponse applyCredit(ApplyCreditRequest request, UUID issuedBy) {
        log.info("Issuing credit to account: {}, amount: {}, type: {}",
                request.getAccountId(), request.getAmount(), request.getCreditType());

        // Validate amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        // Create credit
        AccountCredit credit = AccountCredit.builder()
                .accountId(request.getAccountId())
                .customerId(request.getCustomerId())
                .originalAmount(request.getAmount())
                .remainingBalance(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .creditType(request.getCreditType() != null ? request.getCreditType() : CreditType.GOODWILL)
                .reason(request.getReason())
                .referenceId(request.getReferenceId())
                .status(CreditStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiryDate(request.getExpiryDate())
                .autoApply(request.getAutoApply() != null ? request.getAutoApply() : true)
                .issuedBy(issuedBy)
                .build();

        credit = accountCreditRepository.save(credit);

        // Increment metrics
        creditsIssued.increment();

        // Publish credit issued event
        publishCreditEvent("CREDIT_ISSUED", credit);

        log.info("Credit issued successfully: {}, balance: {}", credit.getId(), credit.getRemainingBalance());

        return mapToResponse(credit);
    }

    /**
     * Gets available credits for account
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "account-credit-service")
    public AccountCreditsResponse getAccountCredits(UUID accountId) {
        log.debug("Retrieving credits for account: {}", accountId);

        LocalDateTime now = LocalDateTime.now();

        // Get all credits
        List<AccountCredit> allCredits = accountCreditRepository.findByAccountIdOrderByIssuedAtAsc(accountId);

        // Get available credits
        List<AccountCredit> availableCredits = accountCreditRepository.findAvailableCredits(accountId, now);

        // Calculate balances
        BigDecimal totalCredits = allCredits.stream()
                .map(AccountCredit::getOriginalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableBalance = accountCreditRepository.getTotalAvailableBalance(accountId, now);

        BigDecimal appliedCredits = allCredits.stream()
                .filter(c -> c.getStatus() == CreditStatus.FULLY_APPLIED ||
                             c.getStatus() == CreditStatus.PARTIALLY_APPLIED)
                .map(c -> c.getOriginalAmount().subtract(c.getRemainingBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Map to response DTOs
        List<CreditResponse> creditResponses = allCredits.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        log.debug("Account {} has {} total credits, {} available balance",
                accountId, totalCredits, availableBalance);

        return AccountCreditsResponse.builder()
                .accountId(accountId)
                .totalCredits(totalCredits)
                .availableCredits(availableBalance)
                .appliedCredits(appliedCredits)
                .credits(creditResponses)
                .build();
    }

    /**
     * Auto-applies available credits to invoice
     */
    @Transactional
    public BigDecimal autoApplyCreditsToInvoice(UUID accountId, UUID invoiceId, BigDecimal invoiceAmount) {
        log.info("Auto-applying credits to invoice: {}, account: {}, amount: {}",
                invoiceId, accountId, invoiceAmount);

        LocalDateTime now = LocalDateTime.now();

        // Get available credits (FIFO order - oldest first)
        List<AccountCredit> availableCredits = accountCreditRepository.findAvailableCredits(accountId, now)
                .stream()
                .filter(AccountCredit::getAutoApply)
                .collect(Collectors.toList());

        if (availableCredits.isEmpty()) {
            log.debug("No auto-apply credits available for account: {}", accountId);
            return BigDecimal.ZERO;
        }

        BigDecimal remainingInvoiceAmount = invoiceAmount;
        BigDecimal totalCreditApplied = BigDecimal.ZERO;

        // Apply credits FIFO until invoice is paid or credits exhausted
        for (AccountCredit credit : availableCredits) {
            if (remainingInvoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;  // Invoice fully paid
            }

            BigDecimal creditApplied = credit.applyToInvoice(remainingInvoiceAmount, invoiceId);

            if (creditApplied.compareTo(BigDecimal.ZERO) > 0) {
                accountCreditRepository.save(credit);

                remainingInvoiceAmount = remainingInvoiceAmount.subtract(creditApplied);
                totalCreditApplied = totalCreditApplied.add(creditApplied);

                creditsApplied.increment();

                log.debug("Applied credit: {}, amount: {}, remaining balance: {}",
                        credit.getId(), creditApplied, credit.getRemainingBalance());

                publishCreditEvent("CREDIT_APPLIED", credit);
            }
        }

        if (totalCreditApplied.compareTo(BigDecimal.ZERO) > 0) {
            log.info("Total credits applied to invoice {}: {}", invoiceId, totalCreditApplied);
        }

        return totalCreditApplied;
    }

    /**
     * Expires old credits (scheduled job - runs daily)
     */
    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    @Transactional
    public void expireOldCredits() {
        log.info("Running credit expiration job");

        LocalDateTime now = LocalDateTime.now();
        List<AccountCredit> expiredCredits = accountCreditRepository.findExpiredCredits(now);

        log.info("Found {} expired credits", expiredCredits.size());

        for (AccountCredit credit : expiredCredits) {
            try {
                credit.markExpired();
                accountCreditRepository.save(credit);

                creditsExpired.increment();

                publishCreditEvent("CREDIT_EXPIRED", credit);

                log.debug("Expired credit: {}, remaining balance: {} forfeited",
                        credit.getId(), credit.getRemainingBalance());

            } catch (Exception e) {
                log.error("Failed to expire credit: {}", credit.getId(), e);
            }
        }

        log.info("Credit expiration job completed. Expired {} credits", expiredCredits.size());
    }

    /**
     * Cancels a credit (admin action)
     */
    @Transactional
    public void cancelCredit(UUID creditId, String reason) {
        log.info("Cancelling credit: {}, reason: {}", creditId, reason);

        AccountCredit credit = accountCreditRepository.findById(creditId)
                .orElseThrow(() -> new IllegalArgumentException("Credit not found: " + creditId));

        if (credit.getStatus() == CreditStatus.FULLY_APPLIED) {
            throw new IllegalStateException("Cannot cancel fully applied credit");
        }

        credit.setStatus(CreditStatus.CANCELLED);
        accountCreditRepository.save(credit);

        publishCreditEvent("CREDIT_CANCELLED", credit);

        log.info("Credit cancelled: {}", creditId);
    }

    // ==================== Helper Methods ====================

    private void publishCreditEvent(String eventType, AccountCredit credit) {
        try {
            String event = String.format(
                    "{\"eventType\":\"%s\",\"creditId\":\"%s\",\"accountId\":\"%s\"," +
                    "\"amount\":\"%s\",\"remainingBalance\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\"}",
                    eventType, credit.getId(), credit.getAccountId(),
                    credit.getOriginalAmount(), credit.getRemainingBalance(),
                    credit.getStatus(), LocalDateTime.now()
            );

            kafkaTemplate.send("billing.credit.events", credit.getId().toString(), event);

        } catch (Exception e) {
            log.error("Failed to publish credit event: {}", eventType, e);
        }
    }

    private CreditResponse mapToResponse(AccountCredit credit) {
        return CreditResponse.builder()
                .creditId(credit.getId())
                .accountId(credit.getAccountId())
                .amount(credit.getOriginalAmount())
                .remainingBalance(credit.getRemainingBalance())
                .currency(credit.getCurrency())
                .creditType(credit.getCreditType() != null ? credit.getCreditType().name() : null)
                .reason(credit.getReason())
                .status(credit.getStatus().name())
                .issuedAt(credit.getIssuedAt())
                .expiryDate(credit.getExpiryDate())
                .appliedAt(credit.getAppliedAt())
                .appliedToInvoiceId(credit.getAppliedToInvoiceId())
                .autoApply(credit.getAutoApply())
                .build();
    }
}
