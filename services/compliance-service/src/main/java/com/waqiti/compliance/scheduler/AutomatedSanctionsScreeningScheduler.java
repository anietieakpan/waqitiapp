package com.waqiti.compliance.scheduler;

import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.compliance.service.impl.OFACSanctionsScreeningServiceImpl;
import com.waqiti.compliance.service.SanctionsScreeningService;
import com.waqiti.compliance.repository.UserRepository;
import com.waqiti.compliance.repository.TransactionRepository;
import com.waqiti.compliance.model.SanctionsScreeningResult;
import com.waqiti.compliance.dto.SanctionsScreeningRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Automated Sanctions Screening Scheduler
 * 
 * CRITICAL COMPLIANCE COMPONENT: Automated AML/BSA sanctions screening
 * REGULATORY REQUIREMENT: OFAC compliance mandates regular sanctions list screening
 * 
 * This service implements automated sanctions screening with:
 * 1. Daily sanctions list updates (OFAC, EU, UN, UK)
 * 2. Weekly full customer rescreening
 * 3. Monthly comprehensive entity screening
 * 4. Real-time transaction screening (event-driven)
 * 5. Emergency screening on sanctions list updates
 * 
 * BUSINESS IMPACT:
 * - Prevents sanctions violations ($20M+ penalties per violation)
 * - Maintains banking relationships
 * - Protects reputation
 * - Ensures regulatory compliance
 * 
 * PERFORMANCE CHARACTERISTICS:
 * - Batch size: 1000 entities per batch
 * - Parallel processing: 10 threads
 * - Expected duration: 2-4 hours for full screening
 * - Database impact: Read-heavy, minimal writes except for matches
 * 
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutomatedSanctionsScreeningScheduler {

    private final OFACSanctionsScreeningServiceImpl ofacScreeningService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ComprehensiveAuditService auditService;

    @Value("${sanctions.screening.batch-size:1000}")
    private int batchSize;

    @Value("${sanctions.screening.parallel-threads:10}")
    private int parallelThreads;

    @Value("${sanctions.screening.enabled:true}")
    private boolean screeningEnabled;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Daily Sanctions List Update
     * Runs at 2:00 AM UTC daily
     * Downloads and updates OFAC SDN, EU, UN, and UK sanctions lists
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void dailySanctionsListUpdate() {
        if (!screeningEnabled) {
            log.info("SANCTIONS_AUTOMATION: Sanctions screening disabled - skipping daily update");
            return;
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("SANCTIONS_AUTOMATION: Starting daily sanctions list update - Job ID: {}", jobId);

        try {
            int totalUpdated = ofacScreeningService.updateSanctionsLists();

            LocalDateTime endTime = LocalDateTime.now();
            long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();

            auditService.auditSystemOperation(
                "SANCTIONS_DAILY_UPDATE_COMPLETED",
                "SCHEDULER",
                String.format("Daily sanctions list update completed: %d entries updated", totalUpdated),
                Map.of(
                    "jobId", jobId,
                    "totalUpdated", totalUpdated,
                    "startTime", startTime,
                    "endTime", endTime,
                    "durationMinutes", durationMinutes,
                    "lists", List.of("OFAC_SDN", "EU_SANCTIONS", "UN_SANCTIONS", "UK_SANCTIONS")
                )
            );

            log.info("SANCTIONS_AUTOMATION: Daily sanctions list update completed - {} entries updated in {} minutes",
                    totalUpdated, durationMinutes);

        } catch (Exception e) {
            log.error("SANCTIONS_AUTOMATION: Daily sanctions list update failed - Job ID: {}", jobId, e);

            auditService.auditCriticalComplianceEvent(
                "SANCTIONS_DAILY_UPDATE_FAILED",
                "SCHEDULER",
                "Critical: Daily sanctions list update failed - " + e.getMessage(),
                Map.of(
                    "jobId", jobId,
                    "error", e.getMessage(),
                    "startTime", startTime,
                    "failureTime", LocalDateTime.now()
                )
            );

            throw new RuntimeException("Sanctions list update failed", e);
        }
    }

    /**
     * Weekly Customer Rescreening
     * Runs every Sunday at 3:00 AM UTC
     * Screens all active customers against updated sanctions lists
     */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "UTC")
    @Transactional
    public void weeklyCustomerRescreening() {
        if (!screeningEnabled) {
            log.info("SANCTIONS_AUTOMATION: Sanctions screening disabled - skipping weekly rescreening");
            return;
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("SANCTIONS_AUTOMATION: Starting weekly customer rescreening - Job ID: {}", jobId);

        try {
            List<UUID> activeUserIds = userRepository.findAllActiveUserIds();

            log.info("SANCTIONS_AUTOMATION: Found {} active users for rescreening", activeUserIds.size());

            AtomicInteger screenedCount = new AtomicInteger(0);
            AtomicInteger matchCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            List<List<UUID>> batches = partition(activeUserIds, batchSize);

            log.info("SANCTIONS_AUTOMATION: Processing {} batches of {} users each", batches.size(), batchSize);

            for (int i = 0; i < batches.size(); i++) {
                List<UUID> batch = batches.get(i);
                int batchNumber = i + 1;

                log.info("SANCTIONS_AUTOMATION: Processing batch {}/{} - {} users",
                        batchNumber, batches.size(), batch.size());

                List<CompletableFuture<Void>> futures = batch.stream()
                    .map(userId -> CompletableFuture.runAsync(() -> {
                        try {
                            screenUser(userId);
                            screenedCount.incrementAndGet();
                        } catch (Exception e) {
                            log.error("SANCTIONS_AUTOMATION: Failed to screen user: {}", userId, e);
                            errorCount.incrementAndGet();
                        }
                    }, executorService))
                    .collect(Collectors.toList());

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, java.util.concurrent.TimeUnit.MINUTES);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("SANCTIONS_AUTOMATION: Batch screening timed out after 10 minutes", e);
                    futures.forEach(f -> f.cancel(true));
                } catch (Exception e) {
                    log.error("SANCTIONS_AUTOMATION: Batch screening failed", e);
                }

                if (batchNumber % 10 == 0) {
                    log.info("SANCTIONS_AUTOMATION: Progress: {}/{} batches completed, {} users screened, {} errors",
                            batchNumber, batches.size(), screenedCount.get(), errorCount.get());
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();

            auditService.auditSystemOperation(
                "SANCTIONS_WEEKLY_RESCREENING_COMPLETED",
                "SCHEDULER",
                String.format("Weekly customer rescreening completed: %d users screened, %d matches found",
                        screenedCount.get(), matchCount.get()),
                Map.of(
                    "jobId", jobId,
                    "totalUsers", activeUserIds.size(),
                    "screenedUsers", screenedCount.get(),
                    "matchesFound", matchCount.get(),
                    "errors", errorCount.get(),
                    "startTime", startTime,
                    "endTime", endTime,
                    "durationMinutes", durationMinutes
                )
            );

            log.info("SANCTIONS_AUTOMATION: Weekly rescreening completed - {} users screened in {} minutes",
                    screenedCount.get(), durationMinutes);

        } catch (Exception e) {
            log.error("SANCTIONS_AUTOMATION: Weekly customer rescreening failed - Job ID: {}", jobId, e);

            auditService.auditCriticalComplianceEvent(
                "SANCTIONS_WEEKLY_RESCREENING_FAILED",
                "SCHEDULER",
                "Critical: Weekly customer rescreening failed - " + e.getMessage(),
                Map.of(
                    "jobId", jobId,
                    "error", e.getMessage(),
                    "startTime", startTime,
                    "failureTime", LocalDateTime.now()
                )
            );

            throw new RuntimeException("Weekly customer rescreening failed", e);
        }
    }

    /**
     * Monthly High-Value Transaction Screening
     * Runs on the 1st of every month at 4:00 AM UTC
     * Screens all high-value transactions from previous month
     */
    @Scheduled(cron = "0 0 4 1 * *", zone = "UTC")
    @Transactional
    public void monthlyHighValueTransactionScreening() {
        if (!screeningEnabled) {
            log.info("SANCTIONS_AUTOMATION: Sanctions screening disabled - skipping monthly transaction screening");
            return;
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        log.info("SANCTIONS_AUTOMATION: Starting monthly high-value transaction screening - Job ID: {}", jobId);

        try {
            LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
            BigDecimal highValueThreshold = new BigDecimal("10000");

            List<UUID> highValueTransactionIds = transactionRepository
                .findHighValueTransactionIds(lastMonth, highValueThreshold);

            log.info("SANCTIONS_AUTOMATION: Found {} high-value transactions for screening",
                    highValueTransactionIds.size());

            AtomicInteger screenedCount = new AtomicInteger(0);
            AtomicInteger matchCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            List<List<UUID>> batches = partition(highValueTransactionIds, batchSize);

            for (List<UUID> batch : batches) {
                List<CompletableFuture<Void>> futures = batch.stream()
                    .map(transactionId -> CompletableFuture.runAsync(() -> {
                        try {
                            screenTransaction(transactionId);
                            screenedCount.incrementAndGet();
                        } catch (Exception e) {
                            log.error("SANCTIONS_AUTOMATION: Failed to screen transaction: {}", transactionId, e);
                            errorCount.incrementAndGet();
                        }
                    }, executorService))
                    .collect(Collectors.toList());

                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(10, java.util.concurrent.TimeUnit.MINUTES);
                } catch (java.util.concurrent.TimeoutException e) {
                    log.error("SANCTIONS_AUTOMATION: Transaction batch screening timed out after 10 minutes", e);
                    futures.forEach(f -> f.cancel(true));
                } catch (Exception e) {
                    log.error("SANCTIONS_AUTOMATION: Transaction batch screening failed", e);
                }
            }

            LocalDateTime endTime = LocalDateTime.now();
            long durationMinutes = java.time.Duration.between(startTime, endTime).toMinutes();

            auditService.auditSystemOperation(
                "SANCTIONS_MONTHLY_TRANSACTION_SCREENING_COMPLETED",
                "SCHEDULER",
                String.format("Monthly high-value transaction screening completed: %d transactions screened",
                        screenedCount.get()),
                Map.of(
                    "jobId", jobId,
                    "totalTransactions", highValueTransactionIds.size(),
                    "screenedTransactions", screenedCount.get(),
                    "matchesFound", matchCount.get(),
                    "errors", errorCount.get(),
                    "threshold", highValueThreshold,
                    "startTime", startTime,
                    "endTime", endTime,
                    "durationMinutes", durationMinutes
                )
            );

            log.info("SANCTIONS_AUTOMATION: Monthly transaction screening completed - {} transactions screened in {} minutes",
                    screenedCount.get(), durationMinutes);

        } catch (Exception e) {
            log.error("SANCTIONS_AUTOMATION: Monthly transaction screening failed - Job ID: {}", jobId, e);

            auditService.auditCriticalComplianceEvent(
                "SANCTIONS_MONTHLY_TRANSACTION_SCREENING_FAILED",
                "SCHEDULER",
                "Critical: Monthly transaction screening failed - " + e.getMessage(),
                Map.of(
                    "jobId", jobId,
                    "error", e.getMessage(),
                    "startTime", startTime,
                    "failureTime", LocalDateTime.now()
                )
            );

            throw new RuntimeException("Monthly transaction screening failed", e);
        }
    }

    /**
     * Hourly New User Screening
     * Runs every hour at :15 past the hour
     * Screens new users registered in the last hour
     */
    @Scheduled(cron = "0 15 * * * *", zone = "UTC")
    @Transactional
    public void hourlyNewUserScreening() {
        if (!screeningEnabled) {
            return;
        }

        String jobId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
            List<UUID> newUserIds = userRepository.findNewUsersSince(oneHourAgo);

            if (newUserIds.isEmpty()) {
                log.debug("SANCTIONS_AUTOMATION: No new users in the last hour - skipping screening");
                return;
            }

            log.info("SANCTIONS_AUTOMATION: Starting hourly new user screening - {} users", newUserIds.size());

            AtomicInteger screenedCount = new AtomicInteger(0);
            AtomicInteger matchCount = new AtomicInteger(0);

            newUserIds.forEach(userId -> {
                try {
                    SanctionsScreeningResult result = screenUser(userId);
                    screenedCount.incrementAndGet();
                    if (result.isHasMatch()) {
                        matchCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("SANCTIONS_AUTOMATION: Failed to screen new user: {}", userId, e);
                }
            });

            LocalDateTime endTime = LocalDateTime.now();

            auditService.auditSystemOperation(
                "SANCTIONS_HOURLY_NEW_USER_SCREENING_COMPLETED",
                "SCHEDULER",
                String.format("Hourly new user screening completed: %d users screened, %d matches",
                        screenedCount.get(), matchCount.get()),
                Map.of(
                    "jobId", jobId,
                    "totalUsers", newUserIds.size(),
                    "screenedUsers", screenedCount.get(),
                    "matchesFound", matchCount.get(),
                    "startTime", startTime,
                    "endTime", endTime
                )
            );

            log.info("SANCTIONS_AUTOMATION: Hourly new user screening completed - {} users screened",
                    screenedCount.get());

        } catch (Exception e) {
            log.error("SANCTIONS_AUTOMATION: Hourly new user screening failed - Job ID: {}", jobId, e);
        }
    }

    private SanctionsScreeningResult screenUser(UUID userId) {
        try {
            var user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            return ofacScreeningService.screenUser(
                userId,
                user.getFullName(),
                user.getDateOfBirth(),
                user.getCountry(),
                user.getNationalId()
            );
        } catch (Exception e) {
            log.error("SANCTIONS_AUTOMATION: Failed to screen user: {}", userId, e);
            throw new RuntimeException("User screening failed", e);
        }
    }

    private void screenTransaction(UUID transactionId) {
        try {
            var transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

            ofacScreeningService.screenTransaction(
                transactionId,
                transaction.getSenderId(),
                transaction.getRecipientId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getSenderCountry(),
                transaction.getRecipientCountry()
            );
        } catch (Exception e) {
            log.error("SANCTIONS_AUTOMATION: Failed to screen transaction: {}", transactionId, e);
            throw new RuntimeException("Transaction screening failed", e);
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        return list.stream()
            .collect(Collectors.groupingBy(item -> list.indexOf(item) / size))
            .values()
            .stream()
            .collect(Collectors.toList());
    }

    /**
     * Emergency Screening Trigger
     * Manually triggered via API or scheduled in emergency situations
     */
    public void triggerEmergencyScreening(String reason) {
        String jobId = UUID.randomUUID().toString();
        LocalDateTime startTime = LocalDateTime.now();

        log.error("SANCTIONS_AUTOMATION: EMERGENCY SCREENING TRIGGERED - Reason: {}", reason);

        auditService.auditCriticalComplianceEvent(
            "SANCTIONS_EMERGENCY_SCREENING_TRIGGERED",
            "SCHEDULER",
            "Emergency sanctions screening triggered: " + reason,
            Map.of(
                "jobId", jobId,
                "reason", reason,
                "triggeredAt", startTime
            )
        );

        weeklyCustomerRescreening();
    }
}
