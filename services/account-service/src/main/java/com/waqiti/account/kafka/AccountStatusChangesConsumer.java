package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.EventValidator;
import com.waqiti.account.model.*;
import com.waqiti.account.repository.*;
import com.waqiti.account.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountStatusChangesConsumer {

    private final ObjectMapper objectMapper;
    private final EventValidator eventValidator;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SecurityContext securityContext;
    
    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final AccountRestrictionsRepository accountRestrictionsRepository;
    private final AccountLimitsRepository accountLimitsRepository;
    private final AccountComplianceRepository accountComplianceRepository;
    private final UserRepository userRepository;
    
    private final AccountService accountService;
    private final AccountStatusService accountStatusService;
    private final LimitsManagementService limitsManagementService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final TransactionService transactionService;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, Long> processingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Integer> statusChangeCounts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "account-status-changes", groupId = "account-service-group")
    @CircuitBreaker(name = "account-status-changes-consumer", fallbackMethod = "fallbackProcessAccountStatusChange")
    @Retry(name = "account-status-changes-consumer")
    @Transactional
    public void processAccountStatusChange(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        long startTime = System.currentTimeMillis();
        String eventId = null;
        String newStatus = null;
        String accountId = null;

        try {
            log.info("Processing account status change from topic: {}, partition: {}, offset: {}", topic, partition, offset);

            JsonNode eventNode = objectMapper.readTree(eventPayload);
            eventId = eventNode.has("eventId") ? eventNode.get("eventId").asText() : UUID.randomUUID().toString();
            newStatus = eventNode.has("newStatus") ? eventNode.get("newStatus").asText() : "UNKNOWN";
            accountId = eventNode.has("accountId") ? eventNode.get("accountId").asText() : null;

            if (!eventValidator.validateEvent(eventNode, "ACCOUNT_STATUS_CHANGE_SCHEMA")) {
                throw new IllegalArgumentException("Invalid account status change event structure");
            }

            AccountStatusContext context = buildStatusContext(eventNode, eventId, newStatus, accountId);
            
            validateStatusChange(context);
            enrichStatusContext(context);
            
            AccountStatusChangeResult result = processStatusTransition(context);
            
            executeAutomatedActions(context, result);
            updateAccountMetrics(context, result);
            
            auditService.logAccountEvent(eventId, "ACCOUNT_STATUS_CHANGE", accountId, "SUCCESS", result.getProcessingDetails());
            
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordProcessingTime("account_status_changes_consumer", processingTime);
            metricsService.incrementCounter("account_status_changes_processed", "status", newStatus);
            
            processingMetrics.put(newStatus, processingTime);
            statusChangeCounts.merge(newStatus, 1, Integer::sum);

            acknowledgment.acknowledge();
            log.info("Successfully processed account status change: {} to status: {} in {}ms", eventId, newStatus, processingTime);

        } catch (Exception e) {
            handleProcessingError(eventId, newStatus, accountId, eventPayload, e, acknowledgment);
        }
    }

    private AccountStatusContext buildStatusContext(JsonNode eventNode, String eventId, String newStatus, String accountId) {
        return AccountStatusContext.builder()
                .eventId(eventId)
                .accountId(accountId)
                .userId(eventNode.has("userId") ? eventNode.get("userId").asText() : null)
                .newStatus(newStatus)
                .previousStatus(eventNode.has("previousStatus") ? eventNode.get("previousStatus").asText() : null)
                .changeReason(eventNode.has("changeReason") ? eventNode.get("changeReason").asText() : null)
                .changeType(eventNode.has("changeType") ? eventNode.get("changeType").asText() : "MANUAL")
                .initiatedBy(eventNode.has("initiatedBy") ? eventNode.get("initiatedBy").asText() : "SYSTEM")
                .effectiveDate(eventNode.has("effectiveDate") ? 
                    Instant.parse(eventNode.get("effectiveDate").asText()) : Instant.now())
                .expiryDate(eventNode.has("expiryDate") ? 
                    Instant.parse(eventNode.get("expiryDate").asText()) : null)
                .restrictions(parseRestrictions(eventNode))
                .metadata(parseMetadata(eventNode))
                .timestamp(eventNode.has("timestamp") ? 
                    Instant.ofEpochMilli(eventNode.get("timestamp").asLong()) : Instant.now())
                .sourceSystem(eventNode.has("sourceSystem") ? eventNode.get("sourceSystem").asText() : "UNKNOWN")
                .ipAddress(eventNode.has("ipAddress") ? eventNode.get("ipAddress").asText() : null)
                .sessionId(eventNode.has("sessionId") ? eventNode.get("sessionId").asText() : null)
                .build();
    }

    private List<AccountRestriction> parseRestrictions(JsonNode eventNode) {
        List<AccountRestriction> restrictions = new ArrayList<>();
        if (eventNode.has("restrictions")) {
            JsonNode restrictionsNode = eventNode.get("restrictions");
            if (restrictionsNode.isArray()) {
                for (JsonNode restriction : restrictionsNode) {
                    restrictions.add(AccountRestriction.builder()
                            .type(restriction.has("type") ? restriction.get("type").asText() : null)
                            .reason(restriction.has("reason") ? restriction.get("reason").asText() : null)
                            .effectiveDate(restriction.has("effectiveDate") ? 
                                Instant.parse(restriction.get("effectiveDate").asText()) : Instant.now())
                            .expiryDate(restriction.has("expiryDate") ? 
                                Instant.parse(restriction.get("expiryDate").asText()) : null)
                            .build());
                }
            }
        }
        return restrictions;
    }

    private Map<String, Object> parseMetadata(JsonNode eventNode) {
        Map<String, Object> metadata = new HashMap<>();
        if (eventNode.has("metadata")) {
            JsonNode metadataNode = eventNode.get("metadata");
            metadataNode.fieldNames().forEachRemaining(fieldName -> 
                metadata.put(fieldName, metadataNode.get(fieldName).asText()));
        }
        return metadata;
    }

    private void validateStatusChange(AccountStatusContext context) {
        if (context.getAccountId() == null) {
            throw new IllegalArgumentException("Account ID is required for status changes");
        }

        Account account = accountRepository.findById(context.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found: " + context.getAccountId()));

        validateStatusTransition(account.getStatus(), context.getNewStatus());
        validateChangeAuthorization(context);
        validateBusinessRules(context, account);
        validateComplianceRequirements(context, account);
        validateTimingConstraints(context, account);
    }

    private void validateStatusTransition(String currentStatus, String newStatus) {
        Set<String> validStatuses = Set.of(
            "PENDING_ACTIVATION", "ACTIVE", "INACTIVE", "DORMANT", "SUSPENDED",
            "FROZEN", "CLOSED", "BLOCKED", "RESTRICTED", "UNDER_REVIEW",
            "PENDING_CLOSURE", "ARCHIVED", "LOCKED", "LIMITED"
        );
        
        if (!validStatuses.contains(newStatus)) {
            throw new IllegalArgumentException("Invalid account status: " + newStatus);
        }
        
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }
    }

    private boolean isValidTransition(String fromStatus, String toStatus) {
        Map<String, Set<String>> validTransitions = Map.of(
            "PENDING_ACTIVATION", Set.of("ACTIVE", "CLOSED", "BLOCKED"),
            "ACTIVE", Set.of("INACTIVE", "DORMANT", "SUSPENDED", "FROZEN", "CLOSED", "RESTRICTED", "LIMITED"),
            "INACTIVE", Set.of("ACTIVE", "DORMANT", "CLOSED"),
            "DORMANT", Set.of("ACTIVE", "CLOSED"),
            "SUSPENDED", Set.of("ACTIVE", "CLOSED", "BLOCKED"),
            "FROZEN", Set.of("ACTIVE", "SUSPENDED", "CLOSED"),
            "RESTRICTED", Set.of("ACTIVE", "SUSPENDED", "CLOSED"),
            "LIMITED", Set.of("ACTIVE", "RESTRICTED", "SUSPENDED"),
            "UNDER_REVIEW", Set.of("ACTIVE", "SUSPENDED", "BLOCKED", "CLOSED"),
            "BLOCKED", Set.of("UNDER_REVIEW", "CLOSED")
        );
        
        return validTransitions.getOrDefault(fromStatus, Set.of()).contains(toStatus) ||
               (fromStatus.equals("CLOSED") && toStatus.equals("ARCHIVED"));
    }

    private void validateChangeAuthorization(AccountStatusContext context) {
        if (context.getChangeType().equals("MANUAL")) {
            if (!isAuthorizedUser(context.getInitiatedBy())) {
                throw new SecurityException("User not authorized to change account status: " + context.getInitiatedBy());
            }
            
            if (requiresDualAuthorization(context.getNewStatus()) && !hasDualAuthorization(context)) {
                throw new SecurityException("Dual authorization required for status: " + context.getNewStatus());
            }
        }
    }

    private boolean isAuthorizedUser(String userId) {
        return accountStatusService.isUserAuthorized(userId, "CHANGE_ACCOUNT_STATUS");
    }

    private boolean requiresDualAuthorization(String status) {
        return Set.of("CLOSED", "FROZEN", "BLOCKED").contains(status);
    }

    private boolean hasDualAuthorization(AccountStatusContext context) {
        return context.getMetadata().containsKey("secondaryAuthorizer") &&
               context.getMetadata().containsKey("authorizationCode");
    }

    private void validateBusinessRules(AccountStatusContext context, Account account) {
        switch (context.getNewStatus()) {
            case "CLOSED":
                if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                    throw new IllegalStateException("Cannot close account with non-zero balance: " + account.getBalance());
                }
                if (hasPendingTransactions(account.getId())) {
                    throw new IllegalStateException("Cannot close account with pending transactions");
                }
                break;
                
            case "FROZEN":
                if (!hasValidFreezeReason(context.getChangeReason())) {
                    throw new IllegalArgumentException("Invalid freeze reason: " + context.getChangeReason());
                }
                break;
                
            case "DORMANT":
                if (!isEligibleForDormancy(account)) {
                    throw new IllegalStateException("Account not eligible for dormancy");
                }
                break;
        }
    }

    private boolean hasPendingTransactions(String accountId) {
        return transactionService.countPendingTransactions(accountId) > 0;
    }

    private boolean hasValidFreezeReason(String reason) {
        return Set.of("FRAUD_SUSPECTED", "LEGAL_HOLD", "COMPLIANCE_REVIEW", 
                     "SECURITY_BREACH", "COURT_ORDER").contains(reason);
    }

    private boolean isEligibleForDormancy(Account account) {
        Instant sixMonthsAgo = Instant.now().minusSeconds(180L * 24 * 3600);
        return account.getLastActivityDate().isBefore(sixMonthsAgo);
    }

    private void validateComplianceRequirements(AccountStatusContext context, Account account) {
        AccountCompliance compliance = accountComplianceRepository.findByAccountId(account.getId()).orElse(null);
        
        if (compliance != null && compliance.hasActiveRestrictions()) {
            Set<String> allowedStatuses = Set.of("FROZEN", "BLOCKED", "SUSPENDED", "CLOSED");
            if (!allowedStatuses.contains(context.getNewStatus())) {
                throw new IllegalStateException("Account has compliance restrictions preventing status change");
            }
        }
        
        if (context.getNewStatus().equals("ACTIVE") && compliance != null) {
            if (!compliance.isKycComplete()) {
                throw new IllegalStateException("KYC must be complete to activate account");
            }
            if (compliance.hasSanctionsHits()) {
                throw new IllegalStateException("Account has sanctions hits preventing activation");
            }
        }
    }

    private void validateTimingConstraints(AccountStatusContext context, Account account) {
        AccountStatusHistory lastChange = accountStatusHistoryRepository
                .findTopByAccountIdOrderByChangeDateDesc(account.getId()).orElse(null);
        
        if (lastChange != null) {
            long hoursSinceLastChange = java.time.Duration.between(
                lastChange.getChangeDate().atZone(ZoneOffset.UTC).toInstant(), 
                Instant.now()
            ).toHours();
            
            if (hoursSinceLastChange < 1 && !context.getMetadata().containsKey("override")) {
                throw new IllegalStateException("Status change too frequent - minimum 1 hour between changes");
            }
        }
        
        if (context.getNewStatus().equals("CLOSED")) {
            Instant thirtyDaysAgo = Instant.now().minusSeconds(30L * 24 * 3600);
            if (account.getOpenedDate().isAfter(thirtyDaysAgo)) {
                throw new IllegalStateException("Account must be open for at least 30 days before closure");
            }
        }
    }

    private void enrichStatusContext(AccountStatusContext context) {
        Account account = accountRepository.findById(context.getAccountId()).orElse(null);
        if (account != null) {
            context.setAccount(account);
            context.setAccountType(account.getAccountType());
            context.setAccountTier(account.getTier());
            context.setCurrentBalance(account.getBalance());
            context.setPreviousStatus(account.getStatus());
        }

        User user = userRepository.findById(context.getUserId()).orElse(null);
        if (user != null) {
            context.setUser(user);
            context.setUserTier(user.getTier());
            context.setUserStatus(user.getStatus());
        }

        enrichWithComplianceData(context);
        enrichWithTransactionHistory(context);
        enrichWithStatusHistory(context);
        enrichWithRiskProfile(context);
        enrichWithRestrictions(context);
    }

    private void enrichWithComplianceData(AccountStatusContext context) {
        AccountCompliance compliance = accountComplianceRepository.findByAccountId(context.getAccountId()).orElse(null);
        if (compliance != null) {
            context.setComplianceData(compliance);
            context.setComplianceScore(compliance.getComplianceScore());
            context.setHasActiveInvestigation(compliance.hasActiveInvestigation());
            context.setSanctionsStatus(compliance.getSanctionsStatus());
        }
    }

    private void enrichWithTransactionHistory(AccountStatusContext context) {
        TransactionSummary summary = transactionService.getAccountTransactionSummary(context.getAccountId());
        context.setTransactionSummary(summary);
        context.setLastTransactionDate(summary.getLastTransactionDate());
        context.setMonthlyTransactionVolume(summary.getMonthlyVolume());
        context.setAverageBalance(summary.getAverageBalance());
    }

    private void enrichWithStatusHistory(AccountStatusContext context) {
        List<AccountStatusHistory> history = accountStatusHistoryRepository
                .findByAccountIdOrderByChangeDateDesc(context.getAccountId());
        
        context.setStatusHistory(history);
        context.setTotalStatusChanges(history.size());
        
        if (!history.isEmpty()) {
            context.setLastStatusChangeDate(history.get(0).getChangeDate().toInstant(ZoneOffset.UTC));
            
            long freezeCount = history.stream()
                    .filter(h -> "FROZEN".equals(h.getNewStatus()))
                    .count();
            context.setFreezeCount((int) freezeCount);
        }
    }

    private void enrichWithRiskProfile(AccountStatusContext context) {
        RiskProfile riskProfile = accountService.getAccountRiskProfile(context.getAccountId());
        if (riskProfile != null) {
            context.setRiskProfile(riskProfile);
            context.setRiskScore(riskProfile.getCurrentScore());
            context.setRiskLevel(riskProfile.getRiskLevel());
            context.setHighRiskIndicators(riskProfile.getHighRiskIndicators());
        }
    }

    private void enrichWithRestrictions(AccountStatusContext context) {
        List<AccountRestriction> activeRestrictions = accountRestrictionsRepository
                .findActiveByAccountId(context.getAccountId());
        
        context.setActiveRestrictions(activeRestrictions);
        context.setHasRestrictions(!activeRestrictions.isEmpty());
        
        if (!activeRestrictions.isEmpty()) {
            context.setRestrictedFeatures(
                activeRestrictions.stream()
                    .map(AccountRestriction::getRestrictedFeature)
                    .collect(Collectors.toSet())
            );
        }
    }

    private AccountStatusChangeResult processStatusTransition(AccountStatusContext context) {
        AccountStatusChangeResult.Builder resultBuilder = AccountStatusChangeResult.builder()
                .eventId(context.getEventId())
                .accountId(context.getAccountId())
                .previousStatus(context.getPreviousStatus())
                .newStatus(context.getNewStatus())
                .processingStartTime(Instant.now());

        try {
            Account account = context.getAccount();
            String previousStatus = account.getStatus();
            
            account.setStatus(context.getNewStatus());
            account.setStatusChangeDate(context.getEffectiveDate());
            account.setStatusChangeReason(context.getChangeReason());
            account.setLastModifiedBy(context.getInitiatedBy());
            account.setLastModifiedDate(Instant.now());
            
            switch (context.getNewStatus()) {
                case "ACTIVE":
                    return processAccountActivation(context, account, resultBuilder);
                case "INACTIVE":
                    return processAccountDeactivation(context, account, resultBuilder);
                case "DORMANT":
                    return processAccountDormancy(context, account, resultBuilder);
                case "SUSPENDED":
                    return processAccountSuspension(context, account, resultBuilder);
                case "FROZEN":
                    return processAccountFreeze(context, account, resultBuilder);
                case "CLOSED":
                    return processAccountClosure(context, account, resultBuilder);
                case "BLOCKED":
                    return processAccountBlock(context, account, resultBuilder);
                case "RESTRICTED":
                    return processAccountRestriction(context, account, resultBuilder);
                case "LIMITED":
                    return processAccountLimitation(context, account, resultBuilder);
                case "UNDER_REVIEW":
                    return processAccountReview(context, account, resultBuilder);
                case "PENDING_CLOSURE":
                    return processAccountPendingClosure(context, account, resultBuilder);
                case "ARCHIVED":
                    return processAccountArchival(context, account, resultBuilder);
                case "LOCKED":
                    return processAccountLock(context, account, resultBuilder);
                default:
                    return processGenericStatusChange(context, account, resultBuilder);
            }
        } finally {
            resultBuilder.processingEndTime(Instant.now());
        }
    }

    private AccountStatusChangeResult processAccountActivation(AccountStatusContext context, Account account, 
                                                              AccountStatusChangeResult.Builder resultBuilder) {
        account.setActivatedDate(context.getEffectiveDate());
        account.setActivatedBy(context.getInitiatedBy());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "ACTIVATED");
        
        removeAllRestrictions(account.getId());
        
        AccountLimits standardLimits = limitsManagementService.getStandardLimits(account.getTier());
        limitsManagementService.applyAccountLimits(account.getId(), standardLimits);
        
        List<String> enabledFeatures = enableAccountFeatures(account.getId());
        
        sendActivationNotification(account, enabledFeatures);
        
        updateComplianceStatus(account.getId(), "ACTIVE");
        
        kafkaTemplate.send("account-activated-events", Map.of(
            "accountId", account.getId(),
            "userId", account.getUserId(),
            "activationDate", context.getEffectiveDate(),
            "accountType", account.getAccountType()
        ));
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "activationDate", account.getActivatedDate().toString(),
                    "restrictionsRemoved", true,
                    "limitsApplied", true,
                    "featuresEnabled", enabledFeatures.size(),
                    "notificationSent", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountDeactivation(AccountStatusContext context, Account account, 
                                                                AccountStatusChangeResult.Builder resultBuilder) {
        account.setDeactivatedDate(context.getEffectiveDate());
        account.setDeactivationReason(context.getChangeReason());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "DEACTIVATED");
        
        suspendRecurringTransactions(account.getId());
        
        disableNonEssentialFeatures(account.getId());
        
        sendDeactivationNotification(account, context.getChangeReason());
        
        scheduleReactivationReview(account.getId(), 90); // Review after 90 days
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "deactivationDate", account.getDeactivatedDate().toString(),
                    "deactivationReason", context.getChangeReason(),
                    "recurringTransactionsSuspended", true,
                    "featuresDisabled", true,
                    "reviewScheduled", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountDormancy(AccountStatusContext context, Account account, 
                                                           AccountStatusChangeResult.Builder resultBuilder) {
        account.setDormancyDate(context.getEffectiveDate());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "DORMANT");
        
        applyDormancyRestrictions(account.getId());
        
        BigDecimal previousLimit = account.getDailyTransactionLimit();
        limitsManagementService.applyDormancyLimits(account.getId());
        
        sendDormancyNotification(account);
        
        scheduleEscheatmentProcess(account.getId(), context.getMetadata());
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "dormancyDate", account.getDormancyDate().toString(),
                    "daysSinceLastActivity", calculateDaysSinceLastActivity(account),
                    "previousLimit", previousLimit,
                    "newLimit", BigDecimal.valueOf(100),
                    "escheatmentScheduled", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountSuspension(AccountStatusContext context, Account account, 
                                                             AccountStatusChangeResult.Builder resultBuilder) {
        account.setSuspendedDate(context.getEffectiveDate());
        account.setSuspensionReason(context.getChangeReason());
        account.setSuspensionExpiryDate(context.getExpiryDate());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "SUSPENDED");
        
        blockAllTransactions(account.getId());
        
        freezeAccountBalance(account.getId());
        
        createInvestigationCase(account, context.getChangeReason());
        
        sendSuspensionNotification(account, context.getChangeReason(), context.getExpiryDate());
        
        notifyComplianceTeam(account, context.getChangeReason());
        
        if (context.getExpiryDate() != null) {
            scheduleAutomaticUnsuspension(account.getId(), context.getExpiryDate());
        }
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "suspensionDate", account.getSuspendedDate().toString(),
                    "suspensionReason", context.getChangeReason(),
                    "transactionsBlocked", true,
                    "balanceFrozen", true,
                    "investigationCaseCreated", true,
                    "expiryDate", context.getExpiryDate() != null ? context.getExpiryDate().toString() : "INDEFINITE"
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountFreeze(AccountStatusContext context, Account account, 
                                                         AccountStatusChangeResult.Builder resultBuilder) {
        account.setFrozenDate(context.getEffectiveDate());
        account.setFreezeReason(context.getChangeReason());
        account.setFreezeAuthorizedBy(context.getInitiatedBy());
        
        if (context.getMetadata().containsKey("courtOrderNumber")) {
            account.setCourtOrderNumber(context.getMetadata().get("courtOrderNumber").toString());
        }
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "FROZEN");
        
        freezeAllFunds(account.getId());
        
        blockAllDebits(account.getId());
        
        boolean allowCredits = context.getMetadata().getOrDefault("allowCredits", "false").equals("true");
        if (!allowCredits) {
            blockAllCredits(account.getId());
        }
        
        sendFreezeNotification(account, context.getChangeReason());
        
        notifyLegalTeam(account, context);
        
        createFreezeRecord(account, context);
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "freezeDate", account.getFrozenDate().toString(),
                    "freezeReason", context.getChangeReason(),
                    "fundsAmount", account.getBalance(),
                    "allowCredits", allowCredits,
                    "courtOrder", account.getCourtOrderNumber() != null,
                    "legalNotified", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountClosure(AccountStatusContext context, Account account, 
                                                          AccountStatusChangeResult.Builder resultBuilder) {
        validateAccountEligibleForClosure(account);
        
        account.setClosedDate(context.getEffectiveDate());
        account.setClosureReason(context.getChangeReason());
        account.setClosedBy(context.getInitiatedBy());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "CLOSED");
        
        cancelAllPendingTransactions(account.getId());
        
        closeAllSubAccounts(account.getId());
        
        archiveAccountData(account.getId());
        
        processRemainingBalance(account);
        
        sendClosureConfirmation(account, context.getChangeReason());
        
        updateReportingSystems(account);
        
        scheduleDataRetention(account.getId(), getRetentionPeriod(account));
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "closureDate", account.getClosedDate().toString(),
                    "closureReason", context.getChangeReason(),
                    "finalBalance", account.getBalance(),
                    "subAccountsClosed", countSubAccounts(account.getId()),
                    "dataArchived", true,
                    "retentionYears", getRetentionPeriod(account)
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountBlock(AccountStatusContext context, Account account, 
                                                        AccountStatusChangeResult.Builder resultBuilder) {
        account.setBlockedDate(context.getEffectiveDate());
        account.setBlockReason(context.getChangeReason());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "BLOCKED");
        
        blockAllActivities(account.getId());
        
        disableAllAccess(account.getId());
        
        String caseId = createSecurityCase(account, context.getChangeReason());
        
        sendBlockNotification(account, context.getChangeReason());
        
        escalateToSecurityTeam(account, context);
        
        initiateAccountRecoveryProcess(account.getId());
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "blockedDate", account.getBlockedDate().toString(),
                    "blockReason", context.getChangeReason(),
                    "securityCaseId", caseId,
                    "allActivitiesBlocked", true,
                    "accessDisabled", true,
                    "recoveryInitiated", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountRestriction(AccountStatusContext context, Account account, 
                                                              AccountStatusChangeResult.Builder resultBuilder) {
        account.setRestrictedDate(context.getEffectiveDate());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "RESTRICTED");
        
        List<AccountRestriction> appliedRestrictions = applyRestrictions(account.getId(), context.getRestrictions());
        
        Map<String, BigDecimal> adjustedLimits = adjustLimitsForRestrictions(account.getId(), appliedRestrictions);
        
        Set<String> disabledFeatures = disableRestrictedFeatures(account.getId(), appliedRestrictions);
        
        sendRestrictionNotification(account, appliedRestrictions);
        
        scheduleRestrictionReview(account.getId(), context.getExpiryDate());
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .restrictions(appliedRestrictions)
                .processingDetails(Map.of(
                    "restrictionDate", account.getRestrictedDate().toString(),
                    "restrictionsApplied", appliedRestrictions.size(),
                    "limitsAdjusted", adjustedLimits.size(),
                    "featuresDisabled", disabledFeatures.size(),
                    "reviewScheduled", context.getExpiryDate() != null
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountLimitation(AccountStatusContext context, Account account, 
                                                             AccountStatusChangeResult.Builder resultBuilder) {
        account.setLimitedDate(context.getEffectiveDate());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "LIMITED");
        
        Map<String, BigDecimal> reducedLimits = reduceLimits(account.getId(), context.getMetadata());
        
        Set<String> limitedFeatures = limitFeatures(account.getId(), context.getMetadata());
        
        sendLimitationNotification(account, reducedLimits, limitedFeatures);
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "limitationDate", account.getLimitedDate().toString(),
                    "limitsReduced", reducedLimits,
                    "featuresLimited", limitedFeatures,
                    "temporaryStatus", context.getExpiryDate() != null
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountReview(AccountStatusContext context, Account account, 
                                                         AccountStatusChangeResult.Builder resultBuilder) {
        account.setReviewStartDate(context.getEffectiveDate());
        account.setReviewReason(context.getChangeReason());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "UNDER_REVIEW");
        
        applyReviewRestrictions(account.getId());
        
        String reviewId = createReviewCase(account, context.getChangeReason());
        
        assignReviewer(reviewId, context.getMetadata());
        
        sendReviewNotification(account, context.getChangeReason());
        
        scheduledReviewDeadline(reviewId, 72); // 72 hours SLA
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "reviewStartDate", account.getReviewStartDate().toString(),
                    "reviewReason", context.getChangeReason(),
                    "reviewId", reviewId,
                    "reviewSLA", "72 hours",
                    "restrictionsApplied", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountPendingClosure(AccountStatusContext context, Account account, 
                                                                  AccountStatusChangeResult.Builder resultBuilder) {
        account.setPendingClosureDate(context.getEffectiveDate());
        account.setScheduledClosureDate(context.getEffectiveDate().plusSeconds(30 * 24 * 3600)); // 30 days
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "PENDING_CLOSURE");
        
        stopNewTransactions(account.getId());
        
        notifyPendingClosure(account, account.getScheduledClosureDate());
        
        scheduleClosureReminders(account.getId());
        
        prepareClosureDocuments(account.getId());
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "pendingClosureDate", account.getPendingClosureDate().toString(),
                    "scheduledClosureDate", account.getScheduledClosureDate().toString(),
                    "daysUntilClosure", 30,
                    "newTransactionsStopped", true,
                    "documentsPrep", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountArchival(AccountStatusContext context, Account account, 
                                                           AccountStatusChangeResult.Builder resultBuilder) {
        if (!account.getStatus().equals("CLOSED")) {
            throw new IllegalStateException("Only closed accounts can be archived");
        }
        
        account.setArchivedDate(context.getEffectiveDate());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "ARCHIVED");
        
        String archiveId = archiveAccountCompletely(account);
        
        removeFromOperationalSystems(account.getId());
        
        createArchivalRecord(account, archiveId);
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "archivedDate", account.getArchivedDate().toString(),
                    "archiveId", archiveId,
                    "dataRetentionPeriod", getRetentionPeriod(account) + " years",
                    "operationalRemoval", true
                ))
                .build();
    }

    private AccountStatusChangeResult processAccountLock(AccountStatusContext context, Account account, 
                                                       AccountStatusChangeResult.Builder resultBuilder) {
        account.setLockedDate(context.getEffectiveDate());
        account.setLockReason(context.getChangeReason());
        
        accountRepository.save(account);
        
        recordStatusHistory(context, "LOCKED");
        
        lockAllOperations(account.getId());
        
        requireAdminUnlock(account.getId());
        
        sendLockNotification(account, context.getChangeReason());
        
        logSecurityEvent(account, "ACCOUNT_LOCKED", context.getChangeReason());
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "lockedDate", account.getLockedDate().toString(),
                    "lockReason", context.getChangeReason(),
                    "adminUnlockRequired", true,
                    "operationsLocked", true
                ))
                .build();
    }

    private AccountStatusChangeResult processGenericStatusChange(AccountStatusContext context, Account account, 
                                                               AccountStatusChangeResult.Builder resultBuilder) {
        accountRepository.save(account);
        
        recordStatusHistory(context, context.getNewStatus());
        
        sendStatusChangeNotification(account, context.getNewStatus(), context.getChangeReason());
        
        return resultBuilder
                .success(true)
                .statusChanged(true)
                .processingDetails(Map.of(
                    "statusChangeDate", context.getEffectiveDate().toString(),
                    "newStatus", context.getNewStatus(),
                    "changeReason", context.getChangeReason() != null ? context.getChangeReason() : "N/A"
                ))
                .build();
    }

    private void executeAutomatedActions(AccountStatusContext context, AccountStatusChangeResult result) {
        try {
            if (result.isSuccess()) {
                executeStatusSpecificActions(context, result);
                executeComplianceActions(context, result);
                executeNotificationActions(context, result);
            }
            
            executeUniversalActions(context, result);
            
        } catch (Exception e) {
            log.error("Error executing automated actions for account status change: {}", context.getEventId(), e);
            metricsService.incrementCounter("account_status_change_action_errors", "status", context.getNewStatus());
        }
    }

    private void executeStatusSpecificActions(AccountStatusContext context, AccountStatusChangeResult result) {
        switch (context.getNewStatus()) {
            case "FROZEN":
                reportToRegulatoryAuthorities(context);
                break;
            case "CLOSED":
                finalizeAccountClosure(context);
                break;
            case "SUSPENDED":
                initiateInvestigation(context);
                break;
            case "ACTIVE":
                restoreAccountCapabilities(context);
                break;
        }
    }

    private void executeComplianceActions(AccountStatusContext context, AccountStatusChangeResult result) {
        updateComplianceRecords(context, result);
        
        if (requiresRegulatoryReporting(context)) {
            submitRegulatoryReport(context, result);
        }
        
        if (requiresSARFiling(context)) {
            initiateSARFiling(context);
        }
    }

    private void executeNotificationActions(AccountStatusContext context, AccountStatusChangeResult result) {
        notifyAccountHolder(context, result);
        
        if (requiresInternalNotification(context)) {
            notifyInternalTeams(context, result);
        }
        
        if (requiresPartnerNotification(context)) {
            notifyPartners(context, result);
        }
    }

    private void executeUniversalActions(AccountStatusContext context, AccountStatusChangeResult result) {
        updateAccountMetrics(context, result);
        recordComplianceAudit(context, result);
        syncWithExternalSystems(context, result);
        
        if (isHighRiskChange(context)) {
            triggerRiskReview(context);
        }
    }

    private void updateAccountMetrics(AccountStatusContext context, AccountStatusChangeResult result) {
        AccountMetrics metrics = new AccountMetrics();
        metrics.setAccountId(context.getAccountId());
        metrics.setUserId(context.getUserId());
        metrics.setStatusChangeType(context.getNewStatus());
        metrics.setPreviousStatus(context.getPreviousStatus());
        metrics.setChangeReason(context.getChangeReason());
        metrics.setProcessingTime(
            result.getProcessingEndTime().toEpochMilli() - result.getProcessingStartTime().toEpochMilli()
        );
        metrics.setTimestamp(context.getTimestamp());
        
        accountService.recordMetrics(metrics);
    }

    private void recordStatusHistory(AccountStatusContext context, String action) {
        AccountStatusHistory history = AccountStatusHistory.builder()
                .accountId(context.getAccountId())
                .previousStatus(context.getPreviousStatus())
                .newStatus(context.getNewStatus())
                .changeReason(context.getChangeReason())
                .changeType(context.getChangeType())
                .changedBy(context.getInitiatedBy())
                .changeDate(LocalDateTime.ofInstant(context.getEffectiveDate(), ZoneOffset.UTC))
                .metadata(context.getMetadata())
                .build();
        
        accountStatusHistoryRepository.save(history);
    }

    private void validateAccountEligibleForClosure(Account account) {
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Account has non-zero balance: " + account.getBalance());
        }
        
        if (hasPendingTransactions(account.getId())) {
            throw new IllegalStateException("Account has pending transactions");
        }
        
        if (hasActiveLoans(account.getId())) {
            throw new IllegalStateException("Account has active loans");
        }
        
        if (hasLinkedAccounts(account.getId())) {
            throw new IllegalStateException("Account has linked sub-accounts");
        }
    }

    private boolean hasActiveLoans(String accountId) {
        return accountService.countActiveLoans(accountId) > 0;
    }

    private boolean hasLinkedAccounts(String accountId) {
        return accountService.countLinkedAccounts(accountId) > 0;
    }

    private boolean requiresRegulatoryReporting(AccountStatusContext context) {
        return Set.of("FROZEN", "BLOCKED", "SUSPENDED").contains(context.getNewStatus()) &&
               Set.of("FRAUD_SUSPECTED", "COURT_ORDER", "REGULATORY_ACTION").contains(context.getChangeReason());
    }

    private boolean requiresSARFiling(AccountStatusContext context) {
        return context.getChangeReason() != null &&
               context.getChangeReason().contains("SUSPICIOUS_ACTIVITY");
    }

    private boolean requiresInternalNotification(AccountStatusContext context) {
        return Set.of("FROZEN", "BLOCKED", "SUSPENDED", "CLOSED").contains(context.getNewStatus());
    }

    private boolean requiresPartnerNotification(AccountStatusContext context) {
        return context.getAccount() != null &&
               context.getAccount().hasExternalIntegrations();
    }

    private boolean isHighRiskChange(AccountStatusContext context) {
        return Set.of("FROZEN", "BLOCKED", "SUSPENDED").contains(context.getNewStatus()) ||
               (context.getRiskScore() != null && context.getRiskScore().compareTo(BigDecimal.valueOf(0.8)) > 0);
    }

    private int calculateDaysSinceLastActivity(Account account) {
        return (int) java.time.Duration.between(
            account.getLastActivityDate(),
            Instant.now()
        ).toDays();
    }

    private int getRetentionPeriod(Account account) {
        // Regulatory requirement: 7 years for financial records
        return 7;
    }

    private int countSubAccounts(String accountId) {
        return accountService.countSubAccounts(accountId);
    }

    private void removeAllRestrictions(String accountId) {
        accountRestrictionsRepository.removeAllByAccountId(accountId);
    }

    private List<String> enableAccountFeatures(String accountId) {
        return accountService.enableAllFeatures(accountId);
    }

    private void sendActivationNotification(Account account, List<String> enabledFeatures) {
        notificationService.sendAccountActivation(account.getUserId(), account.getId(), enabledFeatures);
    }

    private void updateComplianceStatus(String accountId, String status) {
        complianceService.updateAccountComplianceStatus(accountId, status);
    }

    private void suspendRecurringTransactions(String accountId) {
        transactionService.suspendRecurringTransactions(accountId);
    }

    private void disableNonEssentialFeatures(String accountId) {
        accountService.disableNonEssentialFeatures(accountId);
    }

    private void sendDeactivationNotification(Account account, String reason) {
        notificationService.sendAccountDeactivation(account.getUserId(), account.getId(), reason);
    }

    private void scheduleReactivationReview(String accountId, int days) {
        kafkaTemplate.send("scheduled-reviews", Map.of(
            "accountId", accountId,
            "reviewType", "REACTIVATION",
            "scheduledDate", Instant.now().plusSeconds(days * 24L * 3600)
        ));
    }

    private void applyDormancyRestrictions(String accountId) {
        accountRestrictionsRepository.applyDormancyRestrictions(accountId);
    }

    private void sendDormancyNotification(Account account) {
        notificationService.sendDormancyNotification(account.getUserId(), account.getId());
    }

    private void scheduleEscheatmentProcess(String accountId, Map<String, Object> metadata) {
        kafkaTemplate.send("escheatment-schedule", Map.of(
            "accountId", accountId,
            "scheduledDate", Instant.now().plusSeconds(365L * 24 * 3600),
            "state", metadata.getOrDefault("state", "DEFAULT")
        ));
    }

    private void blockAllTransactions(String accountId) {
        transactionService.blockAllTransactions(accountId);
    }

    private void freezeAccountBalance(String accountId) {
        accountService.freezeBalance(accountId);
    }

    private void createInvestigationCase(Account account, String reason) {
        kafkaTemplate.send("investigation-cases", Map.of(
            "accountId", account.getId(),
            "userId", account.getUserId(),
            "reason", reason,
            "priority", "HIGH"
        ));
    }

    private void sendSuspensionNotification(Account account, String reason, Instant expiryDate) {
        notificationService.sendAccountSuspension(account.getUserId(), account.getId(), reason, expiryDate);
    }

    private void notifyComplianceTeam(Account account, String reason) {
        complianceService.notifyTeam(account.getId(), "ACCOUNT_SUSPENDED", reason);
    }

    private void scheduleAutomaticUnsuspension(String accountId, Instant expiryDate) {
        kafkaTemplate.send("scheduled-status-changes", Map.of(
            "accountId", accountId,
            "newStatus", "ACTIVE",
            "scheduledDate", expiryDate
        ));
    }

    private void freezeAllFunds(String accountId) {
        accountService.freezeAllFunds(accountId);
    }

    private void blockAllDebits(String accountId) {
        transactionService.blockDebits(accountId);
    }

    private void blockAllCredits(String accountId) {
        transactionService.blockCredits(accountId);
    }

    private void sendFreezeNotification(Account account, String reason) {
        notificationService.sendAccountFreeze(account.getUserId(), account.getId(), reason);
    }

    private void notifyLegalTeam(Account account, AccountStatusContext context) {
        kafkaTemplate.send("legal-notifications", Map.of(
            "accountId", account.getId(),
            "action", "ACCOUNT_FROZEN",
            "reason", context.getChangeReason(),
            "courtOrder", context.getMetadata().get("courtOrderNumber")
        ));
    }

    private void createFreezeRecord(Account account, AccountStatusContext context) {
        kafkaTemplate.send("freeze-records", Map.of(
            "accountId", account.getId(),
            "freezeDate", context.getEffectiveDate(),
            "reason", context.getChangeReason(),
            "authorizedBy", context.getInitiatedBy()
        ));
    }

    private void cancelAllPendingTransactions(String accountId) {
        transactionService.cancelAllPending(accountId);
    }

    private void closeAllSubAccounts(String accountId) {
        accountService.closeAllSubAccounts(accountId);
    }

    private void archiveAccountData(String accountId) {
        accountService.archiveAccountData(accountId);
    }

    private void processRemainingBalance(Account account) {
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            // This should not happen as we validate zero balance before closure
            log.error("Account {} has remaining balance during closure: {}", account.getId(), account.getBalance());
        }
    }

    private void sendClosureConfirmation(Account account, String reason) {
        notificationService.sendAccountClosureConfirmation(account.getUserId(), account.getId(), reason);
    }

    private void updateReportingSystems(Account account) {
        kafkaTemplate.send("reporting-updates", Map.of(
            "accountId", account.getId(),
            "event", "ACCOUNT_CLOSED",
            "closureDate", account.getClosedDate()
        ));
    }

    private void scheduleDataRetention(String accountId, int retentionYears) {
        kafkaTemplate.send("data-retention-schedule", Map.of(
            "accountId", accountId,
            "retentionYears", retentionYears,
            "purgeDate", Instant.now().plusSeconds(retentionYears * 365L * 24 * 3600)
        ));
    }

    private void blockAllActivities(String accountId) {
        accountService.blockAllActivities(accountId);
    }

    private void disableAllAccess(String accountId) {
        accountService.disableAllAccess(accountId);
    }

    private String createSecurityCase(Account account, String reason) {
        String caseId = UUID.randomUUID().toString();
        kafkaTemplate.send("security-cases", Map.of(
            "caseId", caseId,
            "accountId", account.getId(),
            "reason", reason,
            "severity", "CRITICAL"
        ));
        return caseId;
    }

    private void sendBlockNotification(Account account, String reason) {
        notificationService.sendAccountBlocked(account.getUserId(), account.getId(), reason);
    }

    private void escalateToSecurityTeam(Account account, AccountStatusContext context) {
        kafkaTemplate.send("security-escalations", Map.of(
            "accountId", account.getId(),
            "escalationType", "ACCOUNT_BLOCKED",
            "reason", context.getChangeReason(),
            "priority", "URGENT"
        ));
    }

    private void initiateAccountRecoveryProcess(String accountId) {
        kafkaTemplate.send("account-recovery-events", Map.of(
            "accountId", accountId,
            "recoveryType", "SECURITY_BLOCK",
            "initiatedAt", Instant.now()
        ));
    }

    private List<AccountRestriction> applyRestrictions(String accountId, List<AccountRestriction> restrictions) {
        for (AccountRestriction restriction : restrictions) {
            restriction.setAccountId(accountId);
            accountRestrictionsRepository.save(restriction);
        }
        return restrictions;
    }

    private Map<String, BigDecimal> adjustLimitsForRestrictions(String accountId, List<AccountRestriction> restrictions) {
        return limitsManagementService.adjustLimitsForRestrictions(accountId, restrictions);
    }

    private Set<String> disableRestrictedFeatures(String accountId, List<AccountRestriction> restrictions) {
        return accountService.disableRestrictedFeatures(accountId, restrictions);
    }

    private void sendRestrictionNotification(Account account, List<AccountRestriction> restrictions) {
        notificationService.sendAccountRestrictions(account.getUserId(), account.getId(), restrictions);
    }

    private void scheduleRestrictionReview(String accountId, Instant expiryDate) {
        if (expiryDate != null) {
            kafkaTemplate.send("scheduled-reviews", Map.of(
                "accountId", accountId,
                "reviewType", "RESTRICTION_REVIEW",
                "scheduledDate", expiryDate
            ));
        }
    }

    private Map<String, BigDecimal> reduceLimits(String accountId, Map<String, Object> metadata) {
        BigDecimal reductionFactor = new BigDecimal(metadata.getOrDefault("reductionFactor", "0.5").toString());
        return limitsManagementService.reduceLimits(accountId, reductionFactor);
    }

    private Set<String> limitFeatures(String accountId, Map<String, Object> metadata) {
        List<String> limitedFeatures = (List<String>) metadata.getOrDefault("limitedFeatures", List.of());
        return accountService.limitFeatures(accountId, limitedFeatures);
    }

    private void sendLimitationNotification(Account account, Map<String, BigDecimal> reducedLimits, Set<String> limitedFeatures) {
        notificationService.sendAccountLimitation(account.getUserId(), account.getId(), reducedLimits, limitedFeatures);
    }

    private void applyReviewRestrictions(String accountId) {
        accountRestrictionsRepository.applyReviewRestrictions(accountId);
    }

    private String createReviewCase(Account account, String reason) {
        String reviewId = UUID.randomUUID().toString();
        kafkaTemplate.send("review-cases", Map.of(
            "reviewId", reviewId,
            "accountId", account.getId(),
            "reason", reason,
            "priority", "HIGH"
        ));
        return reviewId;
    }

    private void assignReviewer(String reviewId, Map<String, Object> metadata) {
        String reviewer = metadata.getOrDefault("assignedReviewer", "AUTO_ASSIGN").toString();
        kafkaTemplate.send("review-assignments", Map.of(
            "reviewId", reviewId,
            "reviewer", reviewer
        ));
    }

    private void sendReviewNotification(Account account, String reason) {
        notificationService.sendAccountUnderReview(account.getUserId(), account.getId(), reason);
    }

    private void scheduledReviewDeadline(String reviewId, int hours) {
        kafkaTemplate.send("review-deadlines", Map.of(
            "reviewId", reviewId,
            "deadline", Instant.now().plusSeconds(hours * 3600L)
        ));
    }

    private void stopNewTransactions(String accountId) {
        transactionService.stopNewTransactions(accountId);
    }

    private void notifyPendingClosure(Account account, Instant scheduledDate) {
        notificationService.sendPendingClosure(account.getUserId(), account.getId(), scheduledDate);
    }

    private void scheduleClosureReminders(String accountId) {
        kafkaTemplate.send("closure-reminders", Map.of(
            "accountId", accountId,
            "reminderDates", List.of(
                Instant.now().plusSeconds(7L * 24 * 3600),
                Instant.now().plusSeconds(14L * 24 * 3600),
                Instant.now().plusSeconds(21L * 24 * 3600)
            )
        ));
    }

    private void prepareClosureDocuments(String accountId) {
        kafkaTemplate.send("document-preparation", Map.of(
            "accountId", accountId,
            "documentType", "ACCOUNT_CLOSURE",
            "requestDate", Instant.now()
        ));
    }

    private String archiveAccountCompletely(Account account) {
        String archiveId = UUID.randomUUID().toString();
        accountService.archiveAccount(account.getId(), archiveId);
        return archiveId;
    }

    private void removeFromOperationalSystems(String accountId) {
        accountService.removeFromOperationalSystems(accountId);
    }

    private void createArchivalRecord(Account account, String archiveId) {
        kafkaTemplate.send("archival-records", Map.of(
            "accountId", account.getId(),
            "archiveId", archiveId,
            "archivalDate", account.getArchivedDate()
        ));
    }

    private void lockAllOperations(String accountId) {
        accountService.lockAllOperations(accountId);
    }

    private void requireAdminUnlock(String accountId) {
        accountService.setAdminUnlockRequired(accountId);
    }

    private void sendLockNotification(Account account, String reason) {
        notificationService.sendAccountLocked(account.getUserId(), account.getId(), reason);
    }

    private void logSecurityEvent(Account account, String eventType, String details) {
        kafkaTemplate.send("security-events", Map.of(
            "accountId", account.getId(),
            "eventType", eventType,
            "details", details,
            "timestamp", Instant.now()
        ));
    }

    private void sendStatusChangeNotification(Account account, String newStatus, String reason) {
        notificationService.sendStatusChange(account.getUserId(), account.getId(), newStatus, reason);
    }

    private void reportToRegulatoryAuthorities(AccountStatusContext context) {
        kafkaTemplate.send("regulatory-reports", Map.of(
            "accountId", context.getAccountId(),
            "reportType", "ACCOUNT_FREEZE",
            "reason", context.getChangeReason(),
            "reportDate", Instant.now()
        ));
    }

    private void finalizeAccountClosure(AccountStatusContext context) {
        kafkaTemplate.send("closure-finalization", Map.of(
            "accountId", context.getAccountId(),
            "closureDate", context.getEffectiveDate()
        ));
    }

    private void initiateInvestigation(AccountStatusContext context) {
        kafkaTemplate.send("fraud-detection", Map.of(
            "accountId", context.getAccountId(),
            "investigationType", "ACCOUNT_SUSPENSION",
            "reason", context.getChangeReason()
        ));
    }

    private void restoreAccountCapabilities(AccountStatusContext context) {
        accountService.restoreAllCapabilities(context.getAccountId());
    }

    private void updateComplianceRecords(AccountStatusContext context, AccountStatusChangeResult result) {
        complianceService.updateAccountStatus(context.getAccountId(), context.getNewStatus(), result.getProcessingDetails());
    }

    private void submitRegulatoryReport(AccountStatusContext context, AccountStatusChangeResult result) {
        complianceService.submitRegulatoryReport(context.getAccountId(), context.getNewStatus(), context.getChangeReason());
    }

    private void initiateSARFiling(AccountStatusContext context) {
        kafkaTemplate.send("sar-filing-queue", Map.of(
            "accountId", context.getAccountId(),
            "reason", context.getChangeReason(),
            "filingType", "ACCOUNT_STATUS_CHANGE"
        ));
    }

    private void notifyAccountHolder(AccountStatusContext context, AccountStatusChangeResult result) {
        notificationService.notifyAccountHolder(context.getUserId(), context.getAccountId(), context.getNewStatus());
    }

    private void notifyInternalTeams(AccountStatusContext context, AccountStatusChangeResult result) {
        kafkaTemplate.send("internal-notifications", Map.of(
            "accountId", context.getAccountId(),
            "statusChange", context.getNewStatus(),
            "teams", List.of("COMPLIANCE", "RISK", "OPERATIONS")
        ));
    }

    private void notifyPartners(AccountStatusContext context, AccountStatusChangeResult result) {
        kafkaTemplate.send("partner-notifications", Map.of(
            "accountId", context.getAccountId(),
            "statusChange", context.getNewStatus(),
            "partners", context.getAccount().getExternalPartners()
        ));
    }

    private void recordComplianceAudit(AccountStatusContext context, AccountStatusChangeResult result) {
        auditService.recordComplianceAudit(
            context.getAccountId(),
            "ACCOUNT_STATUS_CHANGE",
            context.getNewStatus(),
            result.getProcessingDetails()
        );
    }

    private void syncWithExternalSystems(AccountStatusContext context, AccountStatusChangeResult result) {
        kafkaTemplate.send("external-sync-events", Map.of(
            "accountId", context.getAccountId(),
            "syncType", "ACCOUNT_STATUS",
            "status", context.getNewStatus()
        ));
    }

    private void triggerRiskReview(AccountStatusContext context) {
        kafkaTemplate.send("risk-review-events", Map.of(
            "accountId", context.getAccountId(),
            "reviewType", "STATUS_CHANGE_RISK",
            "newStatus", context.getNewStatus(),
            "riskFactors", context.getHighRiskIndicators()
        ));
    }

    private void handleProcessingError(String eventId, String newStatus, String accountId, String eventPayload, 
                                     Exception e, Acknowledgment acknowledgment) {
        log.error("Error processing account status change: {} to status: {} for account: {}", eventId, newStatus, accountId, e);
        
        try {
            auditService.logAccountEvent(eventId, "ACCOUNT_STATUS_CHANGE", accountId, "ERROR", Map.of("error", e.getMessage()));
            
            metricsService.incrementCounter("account_status_change_errors", 
                "status", newStatus != null ? newStatus : "UNKNOWN",
                "error_type", e.getClass().getSimpleName());
            
            if (isRetryableError(e)) {
                sendToDlq(eventPayload, "account-status-changes-dlq", "RETRYABLE_ERROR", e.getMessage());
            } else {
                sendToDlq(eventPayload, "account-status-changes-dlq", "NON_RETRYABLE_ERROR", e.getMessage());
            }
            
        } catch (Exception dlqError) {
            log.error("Failed to send message to DLQ", dlqError);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private boolean isRetryableError(Exception e) {
        return e instanceof org.springframework.dao.TransientDataAccessException ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof org.springframework.web.client.ResourceAccessException;
    }

    private void sendToDlq(String originalMessage, String dlqTopic, String errorType, String errorMessage) {
        Map<String, Object> dlqMessage = Map.of(
            "originalMessage", originalMessage,
            "errorType", errorType,
            "errorMessage", errorMessage,
            "timestamp", Instant.now().toString(),
            "service", "account-service"
        );
        
        kafkaTemplate.send(dlqTopic, dlqMessage);
    }

    public void fallbackProcessAccountStatusChange(String eventPayload, String topic, int partition, long offset, 
                                                  Long timestamp, Acknowledgment acknowledgment, Exception ex) {
        log.error("Circuit breaker fallback triggered for account status change processing", ex);
        
        metricsService.incrementCounter("account_status_change_circuit_breaker_fallback");
        
        sendToDlq(eventPayload, "account-status-changes-dlq", "CIRCUIT_BREAKER_OPEN", ex.getMessage());
        acknowledgment.acknowledge();
    }
}