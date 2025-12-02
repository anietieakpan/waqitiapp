package com.waqiti.account.service;

import com.waqiti.account.model.*;
import com.waqiti.account.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Account Closure Service - Production Implementation
 *
 * Orchestrates complete account closure workflow
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountClosureService {

    private final AccountClosureRepository accountClosureRepository;
    private final ClosureRequestRepository closureRequestRepository;
    private final TransactionService transactionService;
    private final BalanceService balanceService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String CLOSURE_EVENTS_TOPIC = "account-closure-events";

    /**
     * Initiate account closure
     *
     * @param context Closure context with all details
     * @return Closure result
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AccountClosureResult initiateClosureProcess(AccountClosureContext context) {
        log.info("Initiating account closure: accountId={}, type={}",
                context.getAccountId(), context.getClosureType());

        long startTime = System.currentTimeMillis();

        AccountClosureResult result = AccountClosureResult.builder()
                .accountId(context.getAccountId())
                .eventId(context.getEventId())
                .success(false)
                .build();

        try {
            // 1. Validate eligibility
            if (!validateEligibility(context)) {
                result.setStatus("REJECTED");
                result.addError("Account not eligible for closure");
                return result;
            }
            result.addAction("Eligibility validated");

            // 2. Check pending transactions
            if (transactionService.hasPendingTransactions(context.getAccountId())) {
                context.setHasPendingTransactions(true);
                scheduleDelayedClosure(context);
                result.setStatus("DELAYED");
                result.addWarning("Closure delayed due to pending transactions");
                result.setSuccess(true);
                return result;
            }
            result.addAction("Pending transactions checked");

            // 3. Calculate balances
            BigDecimal finalBalance = balanceService.calculateFinalBalance(
                    context.getAccountId(), context.getRequestDate());
            BigDecimal accruedInterest = balanceService.calculateAccruedInterest(
                    context.getAccountId(), context.getRequestDate());

            context.setFinalBalance(finalBalance);
            context.setAccruedInterest(accruedInterest);
            result.addAction("Balances calculated");

            // 4. Create closure record
            AccountClosure closure = createClosureRecord(context);
            result.setClosureId(closure.getId().toString());
            result.addAction("Closure record created");

            // 5. Generate documents
            String statementId = documentService.generateFinalStatement(
                    context.getAccountId(), closure.getId());
            result.addAction("Final statement generated");

            // 6. Send notifications
            notificationService.sendClosureNotification(
                    context.getAccountId(),
                    context.getCustomerId(),
                    context.getClosureReason());
            result.addAction("Notifications sent");

            // 7. Publish event
            publishClosureEvent(context, closure);
            result.addAction("Event published");

            result.setStatus("COMPLETED");
            result.setSuccess(true);
            result.setFinalBalance(finalBalance);
            result.setProcessedAt(LocalDateTime.now());
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);

            log.info("Account closure initiated successfully: accountId={}, closureId={}",
                    context.getAccountId(), closure.getId());

            return result;

        } catch (Exception e) {
            log.error("Failed to initiate closure: accountId={}", context.getAccountId(), e);
            result.setStatus("FAILED");
            result.addError("Closure initiation failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Validate closure eligibility
     */
    private boolean validateEligibility(AccountClosureContext context) {
        log.debug("Validating eligibility: accountId={}", context.getAccountId());

        // Check if already has active closure
        if (accountClosureRepository.findByAccountId(context.getAccountId()).isPresent()) {
            log.warn("Account already has active closure: accountId={}", context.getAccountId());
            return false;
        }

        // Additional validation checks...
        return true;
    }

    /**
     * Schedule delayed closure
     */
    private void scheduleDelayedClosure(AccountClosureContext context) {
        log.info("Scheduling delayed closure: accountId={}", context.getAccountId());

        LocalDateTime scheduledDate = LocalDateTime.now().plusDays(5);

        ClosureRequest request = ClosureRequest.builder()
                .accountId(context.getAccountId())
                .requestType(context.getClosureType())
                .requestReason(context.getClosureReason())
                .status("DELAYED")
                .createdAt(LocalDateTime.now())
                .build();

        closureRequestRepository.save(request);

        notificationService.sendDelayNotification(
                context.getAccountId(),
                context.getCustomerId(),
                scheduledDate);
    }

    /**
     * Create closure record
     */
    @Transactional
    private AccountClosure createClosureRecord(AccountClosureContext context) {
        AccountClosure closure = AccountClosure.builder()
                .accountId(context.getAccountId())
                .customerId(context.getCustomerId())
                .closureType(context.getClosureType())
                .closureReason(context.getClosureReason())
                .status("IN_PROGRESS")
                .closureDate(context.getRequestDate())
                .finalBalance(context.getFinalBalance())
                .accruedInterest(context.getAccruedInterest())
                .closureFees(context.getClosureFees())
                .disbursementAmount(context.getNetDisbursement())
                .disbursementMethod(context.getDisbursementMethod())
                .requestedBy(context.getCustomerId())
                .createdAt(LocalDateTime.now())
                .build();

        return accountClosureRepository.save(closure);
    }

    /**
     * Publish closure event
     */
    private void publishClosureEvent(AccountClosureContext context, AccountClosure closure) {
        Map<String, Object> event = Map.of(
                "eventType", "ACCOUNT_CLOSURE_INITIATED",
                "accountId", context.getAccountId(),
                "closureId", closure.getId().toString(),
                "closureType", context.getClosureType(),
                "timestamp", LocalDateTime.now().toString()
        );

        kafkaTemplate.send(CLOSURE_EVENTS_TOPIC, context.getAccountId(), event);
    }
}
