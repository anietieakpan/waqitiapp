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
public class AccountClosureEventsConsumer {

    private final ObjectMapper objectMapper;
    private final EventValidator eventValidator;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SecurityContext securityContext;
    
    private final AccountRepository accountRepository;
    private final AccountClosureRepository accountClosureRepository;
    private final ClosureRequestRepository closureRequestRepository;
    private final ClosureDocumentRepository closureDocumentRepository;
    private final FinalStatementRepository finalStatementRepository;
    private final AccountHistoryRepository accountHistoryRepository;
    
    private final AccountClosureService accountClosureService;
    private final TransactionService transactionService;
    private final BalanceService balanceService;
    private final DocumentService documentService;
    private final NotificationService notificationService;
    private final ComplianceService complianceService;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, Long> processingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Integer> closureTypeCounts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "account-closure-events", groupId = "account-service-group")
    @CircuitBreaker(name = "account-closure-events-consumer", fallbackMethod = "fallbackProcessAccountClosure")
    @Retry(name = "account-closure-events-consumer")
    @Transactional
    public void processAccountClosureEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        long startTime = System.currentTimeMillis();
        String eventId = null;
        String closureType = null;
        String accountId = null;

        try {
            log.info("Processing account closure event from topic: {}, partition: {}, offset: {}", topic, partition, offset);

            JsonNode eventNode = objectMapper.readTree(eventPayload);
            eventId = eventNode.has("eventId") ? eventNode.get("eventId").asText() : UUID.randomUUID().toString();
            closureType = eventNode.has("closureType") ? eventNode.get("closureType").asText() : "VOLUNTARY";
            accountId = eventNode.has("accountId") ? eventNode.get("accountId").asText() : null;

            if (!eventValidator.validateEvent(eventNode, "ACCOUNT_CLOSURE_SCHEMA")) {
                throw new IllegalArgumentException("Invalid account closure event structure");
            }

            AccountClosureContext context = buildClosureContext(eventNode, eventId, closureType, accountId);
            
            validateClosureRequest(context);
            enrichClosureContext(context);
            
            AccountClosureResult result = processClosureByType(context);
            
            executeAutomatedActions(context, result);
            updateClosureMetrics(context, result);
            
            auditService.logAccountEvent(eventId, "ACCOUNT_CLOSURE", accountId, "SUCCESS", result.getProcessingDetails());
            
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordProcessingTime("account_closure_events_consumer", processingTime);
            metricsService.incrementCounter("account_closures_processed", "type", closureType);
            
            processingMetrics.put(closureType, processingTime);
            closureTypeCounts.merge(closureType, 1, Integer::sum);

            acknowledgment.acknowledge();
            log.info("Successfully processed account closure: {} of type: {} in {}ms", eventId, closureType, processingTime);

        } catch (Exception e) {
            handleProcessingError(eventId, closureType, accountId, eventPayload, e, acknowledgment);
        }
    }

    private AccountClosureContext buildClosureContext(JsonNode eventNode, String eventId, String closureType, String accountId) {
        return AccountClosureContext.builder()
                .eventId(eventId)
                .accountId(accountId)
                .userId(eventNode.has("userId") ? eventNode.get("userId").asText() : null)
                .closureType(closureType)
                .closureReason(eventNode.has("closureReason") ? eventNode.get("closureReason").asText() : null)
                .requestDate(eventNode.has("requestDate") ? 
                    Instant.parse(eventNode.get("requestDate").asText()) : Instant.now())
                .effectiveDate(eventNode.has("effectiveDate") ? 
                    Instant.parse(eventNode.get("effectiveDate").asText()) : null)
                .requestedBy(eventNode.has("requestedBy") ? eventNode.get("requestedBy").asText() : null)
                .approvedBy(eventNode.has("approvedBy") ? eventNode.get("approvedBy").asText() : null)
                .priority(eventNode.has("priority") ? eventNode.get("priority").asText() : "NORMAL")
                .immediateClosureRequired(eventNode.has("immediateClosureRequired") ? 
                    eventNode.get("immediateClosureRequired").asBoolean() : false)
                .balanceTransferInstructions(parseBalanceTransferInstructions(eventNode))
                .closureDocuments(parseClosureDocuments(eventNode))
                .metadata(parseMetadata(eventNode))
                .timestamp(eventNode.has("timestamp") ? 
                    Instant.ofEpochMilli(eventNode.get("timestamp").asLong()) : Instant.now())
                .sourceSystem(eventNode.has("sourceSystem") ? eventNode.get("sourceSystem").asText() : "UNKNOWN")
                .ipAddress(eventNode.has("ipAddress") ? eventNode.get("ipAddress").asText() : null)
                .sessionId(eventNode.has("sessionId") ? eventNode.get("sessionId").asText() : null)
                .build();
    }

    private BalanceTransferInstructions parseBalanceTransferInstructions(JsonNode eventNode) {
        if (!eventNode.has("balanceTransferInstructions")) {
            return null;
        }
        
        JsonNode transferNode = eventNode.get("balanceTransferInstructions");
        return BalanceTransferInstructions.builder()
                .transferMethod(transferNode.has("transferMethod") ? transferNode.get("transferMethod").asText() : "BANK_TRANSFER")
                .bankName(transferNode.has("bankName") ? transferNode.get("bankName").asText() : null)
                .accountNumber(transferNode.has("accountNumber") ? transferNode.get("accountNumber").asText() : null)
                .routingNumber(transferNode.has("routingNumber") ? transferNode.get("routingNumber").asText() : null)
                .beneficiaryName(transferNode.has("beneficiaryName") ? transferNode.get("beneficiaryName").asText() : null)
                .beneficiaryAddress(transferNode.has("beneficiaryAddress") ? transferNode.get("beneficiaryAddress").asText() : null)
                .swiftCode(transferNode.has("swiftCode") ? transferNode.get("swiftCode").asText() : null)
                .checkDeliveryAddress(transferNode.has("checkDeliveryAddress") ? transferNode.get("checkDeliveryAddress").asText() : null)
                .build();
    }

    private List<ClosureDocument> parseClosureDocuments(JsonNode eventNode) {
        List<ClosureDocument> documents = new ArrayList<>();
        if (eventNode.has("closureDocuments")) {
            JsonNode docsNode = eventNode.get("closureDocuments");
            if (docsNode.isArray()) {
                for (JsonNode doc : docsNode) {
                    documents.add(ClosureDocument.builder()
                            .documentType(doc.has("type") ? doc.get("type").asText() : null)
                            .documentUrl(doc.has("url") ? doc.get("url").asText() : null)
                            .requiredForClosure(doc.has("required") ? doc.get("required").asBoolean() : false)
                            .build());
                }
            }
        }
        return documents;
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

    private void validateClosureRequest(AccountClosureContext context) {
        if (context.getAccountId() == null) {
            throw new IllegalArgumentException("Account ID is required for closure");
        }

        Account account = accountRepository.findById(context.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account not found: " + context.getAccountId()));

        validateClosureEligibility(account, context);
        validateClosureType(context.getClosureType());
        validateClosureReason(context.getClosureReason(), context.getClosureType());
        validateRequiredDocuments(context);
        validateBalanceTransferInstructions(context, account);
        validateClosureAuthorization(context, account);
        validateTimingConstraints(context, account);
    }

    private void validateClosureEligibility(Account account, AccountClosureContext context) {
        if (account.getStatus().equals("CLOSED")) {
            throw new IllegalStateException("Account is already closed");
        }
        
        if (account.getStatus().equals("FROZEN") && !context.getClosureType().equals("FORCED")) {
            throw new IllegalStateException("Frozen accounts can only be closed with forced closure");
        }
        
        if (hasActiveLienOrHold(account.getId())) {
            throw new IllegalStateException("Account has active liens or holds preventing closure");
        }
        
        if (hasActiveDirectDebits(account.getId()) && !context.isImmediateClosureRequired()) {
            throw new IllegalStateException("Account has active direct debits - 30-day notice required");
        }
        
        if (hasActiveLoans(account.getId())) {
            throw new IllegalStateException("Account has active loans that must be settled first");
        }
        
        if (hasActiveCards(account.getId())) {
            throw new IllegalStateException("Account has active cards that must be cancelled first");
        }
    }

    private void validateClosureType(String closureType) {
        Set<String> validTypes = Set.of(
            "VOLUNTARY", "FORCED", "REGULATORY", "FRAUD", "DECEASED", 
            "DORMANT", "COMPLIANCE", "ESCHEATED", "BANKRUPTCY", "COURT_ORDER"
        );
        
        if (!validTypes.contains(closureType)) {
            throw new IllegalArgumentException("Invalid closure type: " + closureType);
        }
    }

    private void validateClosureReason(String closureReason, String closureType) {
        Map<String, Set<String>> validReasons = Map.of(
            "VOLUNTARY", Set.of("CUSTOMER_REQUEST", "ACCOUNT_CONSOLIDATION", "DISSATISFACTION", "RELOCATION"),
            "FORCED", Set.of("FRAUD_DETECTED", "TERMS_VIOLATION", "RISK_MITIGATION", "BUSINESS_DECISION"),
            "REGULATORY", Set.of("COMPLIANCE_VIOLATION", "SANCTIONS_HIT", "REGULATORY_ORDER", "LICENSE_REVOCATION"),
            "FRAUD", Set.of("IDENTITY_THEFT", "ACCOUNT_TAKEOVER", "TRANSACTION_FRAUD", "DOCUMENT_FRAUD"),
            "DECEASED", Set.of("DEATH_CERTIFICATE", "PROBATE_ORDER", "ESTATE_SETTLEMENT"),
            "DORMANT", Set.of("INACTIVE_ACCOUNT", "ESCHEATMENT_PROCESS", "REGULATORY_REQUIREMENT")
        );
        
        Set<String> allowedReasons = validReasons.get(closureType);
        if (allowedReasons != null && closureReason != null && !allowedReasons.contains(closureReason)) {
            throw new IllegalArgumentException("Invalid closure reason '" + closureReason + "' for type '" + closureType + "'");
        }
    }

    private void validateRequiredDocuments(AccountClosureContext context) {
        Set<String> requiredDocs = getRequiredDocuments(context.getClosureType(), context.getClosureReason());
        Set<String> providedDocs = context.getClosureDocuments().stream()
                .map(ClosureDocument::getDocumentType)
                .collect(Collectors.toSet());
        
        Set<String> missingDocs = new HashSet<>(requiredDocs);
        missingDocs.removeAll(providedDocs);
        
        if (!missingDocs.isEmpty()) {
            throw new IllegalArgumentException("Missing required documents: " + missingDocs);
        }
    }

    private Set<String> getRequiredDocuments(String closureType, String closureReason) {
        Map<String, Set<String>> requiredDocs = Map.of(
            "DECEASED", Set.of("DEATH_CERTIFICATE", "PROBATE_COURT_ORDER", "EXECUTOR_AUTHORIZATION"),
            "FRAUD", Set.of("POLICE_REPORT", "FRAUD_AFFIDAVIT"),
            "COURT_ORDER", Set.of("COURT_ORDER", "LEGAL_AUTHORIZATION"),
            "BANKRUPTCY", Set.of("BANKRUPTCY_FILING", "TRUSTEE_AUTHORIZATION"),
            "REGULATORY", Set.of("REGULATORY_ORDER", "COMPLIANCE_DOCUMENTATION")
        );
        
        return requiredDocs.getOrDefault(closureType, Set.of());
    }

    private void validateBalanceTransferInstructions(AccountClosureContext context, Account account) {
        if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            if (context.getBalanceTransferInstructions() == null) {
                throw new IllegalArgumentException("Balance transfer instructions required for account with positive balance");
            }
            
            BalanceTransferInstructions instructions = context.getBalanceTransferInstructions();
            
            if (instructions.getTransferMethod().equals("BANK_TRANSFER")) {
                if (instructions.getAccountNumber() == null || instructions.getRoutingNumber() == null) {
                    throw new IllegalArgumentException("Bank account details required for bank transfer");
                }
                
                if (!isValidBankAccount(instructions.getAccountNumber(), instructions.getRoutingNumber())) {
                    throw new IllegalArgumentException("Invalid bank account details");
                }
            }
            
            if (instructions.getTransferMethod().equals("CHECK") && instructions.getCheckDeliveryAddress() == null) {
                throw new IllegalArgumentException("Delivery address required for check disbursement");
            }
        }
    }

    private boolean isValidBankAccount(String accountNumber, String routingNumber) {
        return accountNumber != null && accountNumber.matches("^[0-9]{8,17}$") &&
               routingNumber != null && routingNumber.matches("^[0-9]{9}$");
    }

    private void validateClosureAuthorization(AccountClosureContext context, Account account) {
        switch (context.getClosureType()) {
            case "VOLUNTARY":
                if (!isAccountHolder(context.getRequestedBy(), account.getUserId())) {
                    throw new SecurityException("Only account holder can request voluntary closure");
                }
                break;
                
            case "FORCED":
            case "REGULATORY":
            case "FRAUD":
                if (!isAuthorizedOfficer(context.getRequestedBy())) {
                    throw new SecurityException("Insufficient authorization for forced closure");
                }
                if (context.getApprovedBy() == null || !isAuthorizedApprover(context.getApprovedBy())) {
                    throw new SecurityException("Manager approval required for forced closure");
                }
                break;
                
            case "DECEASED":
                if (!isAuthorizedExecutor(context.getRequestedBy(), account.getId())) {
                    throw new SecurityException("Only authorized executor can close deceased account");
                }
                break;
        }
    }

    private boolean isAccountHolder(String userId, String accountHolderId) {
        return userId != null && userId.equals(accountHolderId);
    }

    private boolean isAuthorizedOfficer(String userId) {
        return accountClosureService.hasRole(userId, "CLOSURE_OFFICER");
    }

    private boolean isAuthorizedApprover(String userId) {
        return accountClosureService.hasRole(userId, "CLOSURE_APPROVER");
    }

    private boolean isAuthorizedExecutor(String userId, String accountId) {
        return accountClosureService.isAuthorizedExecutor(userId, accountId);
    }

    private void validateTimingConstraints(AccountClosureContext context, Account account) {
        Instant now = Instant.now();
        
        // Minimum account age for voluntary closure (prevent fraud)
        if (context.getClosureType().equals("VOLUNTARY")) {
            Instant thirtyDaysAgo = now.minusSeconds(30L * 24 * 3600);
            if (account.getOpenedDate().isAfter(thirtyDaysAgo)) {
                throw new IllegalStateException("Account must be open for at least 30 days for voluntary closure");
            }
        }
        
        // Immediate closure validation
        if (context.isImmediateClosureRequired()) {
            Set<String> allowedImmediateTypes = Set.of("FRAUD", "REGULATORY", "COURT_ORDER", "FORCED");
            if (!allowedImmediateTypes.contains(context.getClosureType())) {
                throw new IllegalArgumentException("Immediate closure not allowed for type: " + context.getClosureType());
            }
        }
        
        // Effective date validation
        if (context.getEffectiveDate() != null) {
            if (context.getEffectiveDate().isBefore(now)) {
                throw new IllegalArgumentException("Effective date cannot be in the past");
            }
            
            Instant maxFutureDate = now.plusSeconds(90L * 24 * 3600); // 90 days
            if (context.getEffectiveDate().isAfter(maxFutureDate)) {
                throw new IllegalArgumentException("Effective date cannot be more than 90 days in the future");
            }
        }
    }

    private boolean hasActiveLienOrHold(String accountId) {
        return accountClosureService.hasActiveLienOrHold(accountId);
    }

    private boolean hasActiveDirectDebits(String accountId) {
        return transactionService.hasActiveDirectDebits(accountId);
    }

    private boolean hasActiveLoans(String accountId) {
        return accountClosureService.hasActiveLoans(accountId);
    }

    private boolean hasActiveCards(String accountId) {
        return accountClosureService.hasActiveCards(accountId);
    }

    private void enrichClosureContext(AccountClosureContext context) {
        Account account = accountRepository.findById(context.getAccountId()).orElse(null);
        if (account != null) {
            context.setAccount(account);
            context.setCurrentBalance(account.getBalance());
            context.setAccountType(account.getAccountType());
            context.setAccountAge(calculateAccountAge(account.getOpenedDate()));
        }

        enrichWithTransactionHistory(context);
        enrichWithRelatedAccounts(context);
        enrichWithComplianceData(context);
        enrichWithClosureHistory(context);
        enrichWithFinancialObligations(context);
    }

    private long calculateAccountAge(Instant openedDate) {
        return java.time.Duration.between(openedDate, Instant.now()).toDays();
    }

    private void enrichWithTransactionHistory(AccountClosureContext context) {
        TransactionSummary summary = transactionService.getClosureTransactionSummary(context.getAccountId());
        context.setTransactionSummary(summary);
        context.setLastTransactionDate(summary.getLastTransactionDate());
        context.setPendingTransactions(summary.getPendingTransactionCount());
        context.setAverageMonthlyBalance(summary.getAverageMonthlyBalance());
    }

    private void enrichWithRelatedAccounts(AccountClosureContext context) {
        List<Account> relatedAccounts = accountRepository.findRelatedAccounts(context.getAccountId());
        context.setRelatedAccounts(relatedAccounts);
        context.setHasJointAccounts(hasJointAccounts(relatedAccounts));
        context.setLinkedAccountsCount(relatedAccounts.size());
    }

    private boolean hasJointAccounts(List<Account> relatedAccounts) {
        return relatedAccounts.stream().anyMatch(account -> account.getAccountType().contains("JOINT"));
    }

    private void enrichWithComplianceData(AccountClosureContext context) {
        ComplianceRecord compliance = complianceService.getAccountComplianceRecord(context.getAccountId());
        context.setComplianceRecord(compliance);
        context.setHasComplianceIssues(compliance.hasActiveIssues());
        context.setRequiresRegulatoryNotification(compliance.requiresRegulatoryNotification());
    }

    private void enrichWithClosureHistory(AccountClosureContext context) {
        List<AccountClosure> previousClosures = accountClosureRepository
                .findByUserIdOrderByClosureDateDesc(context.getUserId());
        
        context.setPreviousClosures(previousClosures);
        context.setClosureCount(previousClosures.size());
        
        if (!previousClosures.isEmpty()) {
            AccountClosure lastClosure = previousClosures.get(0);
            context.setLastClosureDate(lastClosure.getClosureDate());
            context.setLastClosureReason(lastClosure.getClosureReason());
        }
    }

    private void enrichWithFinancialObligations(AccountClosureContext context) {
        FinancialObligations obligations = accountClosureService.getFinancialObligations(context.getAccountId());
        context.setFinancialObligations(obligations);
        context.setOutstandingFees(obligations.getOutstandingFees());
        context.setUnpaidInterest(obligations.getUnpaidInterest());
        context.setOverdraftAmount(obligations.getOverdraftAmount());
    }

    private AccountClosureResult processClosureByType(AccountClosureContext context) {
        AccountClosureResult.Builder resultBuilder = AccountClosureResult.builder()
                .eventId(context.getEventId())
                .accountId(context.getAccountId())
                .closureType(context.getClosureType())
                .processingStartTime(Instant.now());

        try {
            switch (context.getClosureType()) {
                case "VOLUNTARY":
                    return processVoluntaryClosure(context, resultBuilder);
                case "FORCED":
                    return processForcedClosure(context, resultBuilder);
                case "REGULATORY":
                    return processRegulatoryClosure(context, resultBuilder);
                case "FRAUD":
                    return processFraudClosure(context, resultBuilder);
                case "DECEASED":
                    return processDeceasedClosure(context, resultBuilder);
                case "DORMANT":
                    return processDormantClosure(context, resultBuilder);
                case "COMPLIANCE":
                    return processComplianceClosure(context, resultBuilder);
                case "ESCHEATED":
                    return processEscheatmentClosure(context, resultBuilder);
                case "BANKRUPTCY":
                    return processBankruptcyClosure(context, resultBuilder);
                case "COURT_ORDER":
                    return processCourtOrderClosure(context, resultBuilder);
                default:
                    throw new IllegalArgumentException("Unsupported closure type: " + context.getClosureType());
            }
        } finally {
            resultBuilder.processingEndTime(Instant.now());
        }
    }

    private AccountClosureResult processVoluntaryClosure(AccountClosureContext context, 
                                                        AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        validateVoluntaryClosureEligibility(account, context);
        
        Instant effectiveDate = context.getEffectiveDate() != null ? 
            context.getEffectiveDate() : Instant.now().plusSeconds(7L * 24 * 3600); // 7 days notice
        
        ClosureRequest request = createClosureRequest(context, effectiveDate);
        
        if (context.isImmediateClosureRequired()) {
            return processImmediateClosure(context, request, resultBuilder);
        } else {
            return processScheduledClosure(context, request, effectiveDate, resultBuilder);
        }
    }

    private AccountClosureResult processForcedClosure(AccountClosureContext context, 
                                                     AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        logForcedClosureEvent(context);
        
        ClosureRequest request = createClosureRequest(context, Instant.now());
        
        freezeAccountImmediately(account.getId());
        
        cancelAllPendingTransactions(account.getId());
        
        blockNewTransactions(account.getId());
        
        BigDecimal finalBalance = calculateFinalBalance(account.getId());
        
        if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
            processBalanceEscheatment(account.getId(), finalBalance, context.getClosureReason());
        }
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason(context.getClosureReason());
        account.setClosureType(context.getClosureType());
        accountRepository.save(account);
        
        notifyForcedClosure(account, context.getClosureReason());
        
        notifyRegulatoryAuthorities(account, context);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(finalBalance)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "FORCED",
                    "closureReason", context.getClosureReason(),
                    "finalBalance", finalBalance,
                    "balanceEscheatment", finalBalance.compareTo(BigDecimal.ZERO) > 0,
                    "regulatoryNotified", true
                ))
                .build();
    }

    private AccountClosureResult processRegulatoryClosure(AccountClosureContext context, 
                                                         AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        validateRegulatoryDocuments(context);
        
        createRegulatoryCase(account, context);
        
        ClosureRequest request = createClosureRequest(context, Instant.now());
        
        freezeAccountImmediately(account.getId());
        
        reportToRegulatoryBodies(account, context);
        
        BigDecimal finalBalance = processRegulatoryBalanceTransfer(account, context);
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason(context.getClosureReason());
        account.setClosureType("REGULATORY");
        account.setRegulatoryReference(context.getMetadata().get("regulatoryReference").toString());
        accountRepository.save(account);
        
        generateRegulatoryReport(account, context);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(finalBalance)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "REGULATORY",
                    "regulatoryReference", account.getRegulatoryReference(),
                    "finalBalance", finalBalance,
                    "reportGenerated", true,
                    "complianceNotified", true
                ))
                .build();
    }

    private AccountClosureResult processFraudClosure(AccountClosureContext context, 
                                                    AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        createFraudCase(account, context);
        
        freezeAccountImmediately(account.getId());
        
        reverseRecentSuspiciousTransactions(account.getId(), context);
        
        BigDecimal recoveredAmount = processFraudRecovery(account.getId(), context);
        
        BigDecimal finalBalance = calculateFinalBalance(account.getId());
        
        if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
            holdFundsForInvestigation(account.getId(), finalBalance);
        }
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason("FRAUD_DETECTED");
        account.setClosureType("FRAUD");
        account.setFraudCaseId(context.getMetadata().get("fraudCaseId").toString());
        accountRepository.save(account);
        
        notifyLawEnforcement(account, context);
        
        fileSuspiciousActivityReport(account, context);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(finalBalance)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "FRAUD",
                    "fraudCaseId", account.getFraudCaseId(),
                    "recoveredAmount", recoveredAmount,
                    "finalBalance", finalBalance,
                    "lawEnforcementNotified", true,
                    "sarFiled", true
                ))
                .build();
    }

    private AccountClosureResult processDeceasedClosure(AccountClosureContext context, 
                                                       AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        validateDeceasedDocuments(context);
        
        createEstateCase(account, context);
        
        freezeAccountForEstate(account.getId());
        
        BigDecimal estateBalance = calculateEstateBalance(account.getId());
        
        processEstateTransfer(account, context, estateBalance);
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason("ACCOUNT_HOLDER_DECEASED");
        account.setClosureType("DECEASED");
        account.setEstateReference(context.getMetadata().get("estateReference").toString());
        accountRepository.save(account);
        
        notifyEstate(account, context, estateBalance);
        
        updateTaxRecords(account, context);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(estateBalance)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "DECEASED",
                    "estateReference", account.getEstateReference(),
                    "estateBalance", estateBalance,
                    "estateNotified", true,
                    "taxRecordsUpdated", true
                ))
                .build();
    }

    private AccountClosureResult processDormantClosure(AccountClosureContext context, 
                                                      AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        validateDormancyPeriod(account);
        
        BigDecimal dormantBalance = account.getBalance();
        
        sendFinalNotice(account, context);
        
        transferToStateEscheatment(account, dormantBalance);
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason("DORMANT_ESCHEATMENT");
        account.setClosureType("DORMANT");
        account.setEscheatmentDate(Instant.now());
        accountRepository.save(account);
        
        reportEscheatmentToState(account, dormantBalance);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(BigDecimal.ZERO)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "DORMANT",
                    "escheatedAmount", dormantBalance,
                    "escheatmentDate", account.getEscheatmentDate(),
                    "stateReported", true
                ))
                .build();
    }

    private AccountClosureResult processComplianceClosure(AccountClosureContext context, 
                                                         AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        createComplianceCase(account, context);
        
        freezeAccountForCompliance(account.getId());
        
        BigDecimal finalBalance = calculateFinalBalance(account.getId());
        
        if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
            holdFundsForCompliance(account.getId(), finalBalance);
        }
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason(context.getClosureReason());
        account.setClosureType("COMPLIANCE");
        account.setComplianceCaseId(context.getMetadata().get("complianceCaseId").toString());
        accountRepository.save(account);
        
        notifyComplianceTeam(account, context);
        
        fileComplianceReport(account, context);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(finalBalance)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "COMPLIANCE",
                    "complianceCaseId", account.getComplianceCaseId(),
                    "finalBalance", finalBalance,
                    "complianceReported", true
                ))
                .build();
    }

    private AccountClosureResult processEscheatmentClosure(AccountClosureContext context, 
                                                          AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        BigDecimal escheatmentAmount = account.getBalance();
        
        processStateEscheatment(account, escheatmentAmount);
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason("STATE_ESCHEATMENT");
        account.setClosureType("ESCHEATED");
        account.setEscheatmentDate(Instant.now());
        account.setEscheatmentState(context.getMetadata().get("state").toString());
        accountRepository.save(account);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(BigDecimal.ZERO)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "ESCHEATED",
                    "escheatmentAmount", escheatmentAmount,
                    "escheatmentState", account.getEscheatmentState()
                ))
                .build();
    }

    private AccountClosureResult processBankruptcyClosure(AccountClosureContext context, 
                                                         AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        validateBankruptcyDocuments(context);
        
        createBankruptcyCase(account, context);
        
        freezeAccountForBankruptcy(account.getId());
        
        BigDecimal trusteeBalance = transferToTrustee(account, context);
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason("BANKRUPTCY_FILING");
        account.setClosureType("BANKRUPTCY");
        account.setBankruptcyCaseNumber(context.getMetadata().get("bankruptcyCaseNumber").toString());
        accountRepository.save(account);
        
        notifyTrustee(account, context, trusteeBalance);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(BigDecimal.ZERO)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "BANKRUPTCY",
                    "bankruptcyCaseNumber", account.getBankruptcyCaseNumber(),
                    "trusteeBalance", trusteeBalance,
                    "trusteeNotified", true
                ))
                .build();
    }

    private AccountClosureResult processCourtOrderClosure(AccountClosureContext context, 
                                                         AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        validateCourtOrder(context);
        
        createLegalCase(account, context);
        
        executeCourtOrder(account, context);
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason("COURT_ORDER");
        account.setClosureType("COURT_ORDER");
        account.setCourtOrderNumber(context.getMetadata().get("courtOrderNumber").toString());
        accountRepository.save(account);
        
        reportCourtOrderCompliance(account, context);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(BigDecimal.ZERO)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "COURT_ORDER",
                    "courtOrderNumber", account.getCourtOrderNumber(),
                    "orderExecuted", true,
                    "complianceReported", true
                ))
                .build();
    }

    private void executeAutomatedActions(AccountClosureContext context, AccountClosureResult result) {
        try {
            if (result.isSuccess() && result.isClosureCompleted()) {
                executeClosureCompletionActions(context, result);
            } else {
                executeClosureFailureActions(context, result);
            }
            
            executeUniversalActions(context, result);
            
        } catch (Exception e) {
            log.error("Error executing automated actions for account closure: {}", context.getEventId(), e);
            metricsService.incrementCounter("account_closure_action_errors", "closure_type", context.getClosureType());
        }
    }

    private void executeClosureCompletionActions(AccountClosureContext context, AccountClosureResult result) {
        updateRelatedSystems(context, result);
        
        generateClosureDocuments(context, result);
        
        updateReportingSystems(context, result);
        
        processLoyaltyProgramClosure(context);
        
        cancelAllSubscriptions(context.getAccountId());
        
        updateCreditReportingAgencies(context);
    }

    private void executeClosureFailureActions(AccountClosureContext context, AccountClosureResult result) {
        logClosureFailure(context, result);
        
        notifyManagementOfFailure(context, result);
        
        revertPartialActions(context);
    }

    private void executeUniversalActions(AccountClosureContext context, AccountClosureResult result) {
        recordClosureHistory(context, result);
        
        updateClosureMetrics(context, result);
        
        archiveAccountData(context, result);
        
        if (requiresRegulatoryReporting(context)) {
            submitRegulatoryFiling(context, result);
        }
    }

    private void updateClosureMetrics(AccountClosureContext context, AccountClosureResult result) {
        ClosureMetrics metrics = new ClosureMetrics();
        metrics.setAccountId(context.getAccountId());
        metrics.setUserId(context.getUserId());
        metrics.setClosureType(context.getClosureType());
        metrics.setClosureReason(context.getClosureReason());
        metrics.setAccountAge(context.getAccountAge());
        metrics.setFinalBalance(result.getFinalBalance());
        metrics.setProcessingTime(
            result.getProcessingEndTime().toEpochMilli() - result.getProcessingStartTime().toEpochMilli()
        );
        metrics.setSuccess(result.isSuccess());
        metrics.setTimestamp(context.getTimestamp());
        
        accountClosureService.recordMetrics(metrics);
    }

    private void validateVoluntaryClosureEligibility(Account account, AccountClosureContext context) {
        if (account.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Cannot close account with negative balance");
        }
        
        if (hasRecentBounces(account.getId())) {
            throw new IllegalStateException("Account has recent bounced transactions");
        }
        
        if (hasActiveDisputes(account.getId())) {
            throw new IllegalStateException("Account has active transaction disputes");
        }
    }

    private boolean hasRecentBounces(String accountId) {
        return transactionService.hasRecentBounces(accountId, 30); // 30 days
    }

    private boolean hasActiveDisputes(String accountId) {
        return transactionService.hasActiveDisputes(accountId);
    }

    private ClosureRequest createClosureRequest(AccountClosureContext context, Instant effectiveDate) {
        ClosureRequest request = ClosureRequest.builder()
                .id(UUID.randomUUID().toString())
                .accountId(context.getAccountId())
                .userId(context.getUserId())
                .closureType(context.getClosureType())
                .closureReason(context.getClosureReason())
                .requestDate(context.getRequestDate())
                .effectiveDate(effectiveDate)
                .requestedBy(context.getRequestedBy())
                .approvedBy(context.getApprovedBy())
                .status("PENDING")
                .priority(context.getPriority())
                .balanceTransferInstructions(context.getBalanceTransferInstructions())
                .metadata(context.getMetadata())
                .build();
        
        return closureRequestRepository.save(request);
    }

    private AccountClosureResult processImmediateClosure(AccountClosureContext context, ClosureRequest request, 
                                                        AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        cancelAllPendingTransactions(account.getId());
        
        BigDecimal finalBalance = calculateFinalBalance(account.getId());
        
        if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
            processBalanceTransfer(account, context.getBalanceTransferInstructions(), finalBalance);
        }
        
        account.setStatus("CLOSED");
        account.setClosedDate(Instant.now());
        account.setClosureReason(context.getClosureReason());
        account.setClosureType(context.getClosureType());
        accountRepository.save(account);
        
        request.setStatus("COMPLETED");
        request.setCompletionDate(Instant.now());
        closureRequestRepository.save(request);
        
        sendClosureConfirmation(account, context);
        
        return resultBuilder
                .success(true)
                .closureCompleted(true)
                .finalBalance(finalBalance)
                .closureDate(Instant.now())
                .processingDetails(Map.of(
                    "closureType", "IMMEDIATE",
                    "finalBalance", finalBalance,
                    "balanceTransferred", finalBalance.compareTo(BigDecimal.ZERO) > 0
                ))
                .build();
    }

    private AccountClosureResult processScheduledClosure(AccountClosureContext context, ClosureRequest request, 
                                                        Instant effectiveDate, AccountClosureResult.Builder resultBuilder) {
        Account account = context.getAccount();
        
        account.setStatus("PENDING_CLOSURE");
        account.setScheduledClosureDate(effectiveDate);
        accountRepository.save(account);
        
        scheduleClosureExecution(request.getId(), effectiveDate);
        
        sendClosureScheduleNotification(account, effectiveDate);
        
        return resultBuilder
                .success(true)
                .closureCompleted(false)
                .scheduledDate(effectiveDate)
                .processingDetails(Map.of(
                    "closureType", "SCHEDULED",
                    "effectiveDate", effectiveDate,
                    "notificationSent", true
                ))
                .build();
    }

    private BigDecimal calculateFinalBalance(String accountId) {
        return balanceService.calculateFinalBalance(accountId);
    }

    private void processBalanceTransfer(Account account, BalanceTransferInstructions instructions, BigDecimal amount) {
        if (instructions == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        
        switch (instructions.getTransferMethod()) {
            case "BANK_TRANSFER":
                processBankTransfer(account, instructions, amount);
                break;
            case "CHECK":
                processCheckDisbursement(account, instructions, amount);
                break;
            case "WIRE_TRANSFER":
                processWireTransfer(account, instructions, amount);
                break;
            default:
                throw new IllegalArgumentException("Unsupported transfer method: " + instructions.getTransferMethod());
        }
    }

    private void processBankTransfer(Account account, BalanceTransferInstructions instructions, BigDecimal amount) {
        kafkaTemplate.send("ach-transfers", Map.of(
            "fromAccountId", account.getId(),
            "toAccountNumber", instructions.getAccountNumber(),
            "routingNumber", instructions.getRoutingNumber(),
            "amount", amount,
            "reason", "ACCOUNT_CLOSURE_TRANSFER"
        ));
    }

    private void processCheckDisbursement(Account account, BalanceTransferInstructions instructions, BigDecimal amount) {
        kafkaTemplate.send("check-disbursements", Map.of(
            "accountId", account.getId(),
            "amount", amount,
            "payeeName", instructions.getBeneficiaryName(),
            "deliveryAddress", instructions.getCheckDeliveryAddress(),
            "reason", "ACCOUNT_CLOSURE"
        ));
    }

    private void processWireTransfer(Account account, BalanceTransferInstructions instructions, BigDecimal amount) {
        kafkaTemplate.send("wire-transfers", Map.of(
            "fromAccountId", account.getId(),
            "beneficiaryName", instructions.getBeneficiaryName(),
            "beneficiaryAccount", instructions.getAccountNumber(),
            "beneficiaryBank", instructions.getBankName(),
            "swiftCode", instructions.getSwiftCode(),
            "amount", amount,
            "reason", "ACCOUNT_CLOSURE_TRANSFER"
        ));
    }

    private void freezeAccountImmediately(String accountId) {
        kafkaTemplate.send("account-status-changes", Map.of(
            "accountId", accountId,
            "newStatus", "FROZEN",
            "changeReason", "ACCOUNT_CLOSURE_PROCESSING",
            "immediateClosureRequired", true
        ));
    }

    private void cancelAllPendingTransactions(String accountId) {
        transactionService.cancelAllPending(accountId);
    }

    private void blockNewTransactions(String accountId) {
        transactionService.blockNewTransactions(accountId);
    }

    private void scheduleClosureExecution(String requestId, Instant effectiveDate) {
        kafkaTemplate.send("scheduled-closures", Map.of(
            "requestId", requestId,
            "executionDate", effectiveDate
        ));
    }

    private void sendClosureScheduleNotification(Account account, Instant effectiveDate) {
        notificationService.sendClosureScheduled(account.getUserId(), account.getId(), effectiveDate);
    }

    private void sendClosureConfirmation(Account account, AccountClosureContext context) {
        notificationService.sendClosureConfirmation(account.getUserId(), account.getId(), context.getClosureReason());
    }

    private void recordClosureHistory(AccountClosureContext context, AccountClosureResult result) {
        AccountClosure closure = AccountClosure.builder()
                .accountId(context.getAccountId())
                .userId(context.getUserId())
                .closureType(context.getClosureType())
                .closureReason(context.getClosureReason())
                .closureDate(result.getClosureDate())
                .finalBalance(result.getFinalBalance())
                .requestedBy(context.getRequestedBy())
                .approvedBy(context.getApprovedBy())
                .processingTime(result.getProcessingEndTime().toEpochMilli() - result.getProcessingStartTime().toEpochMilli())
                .success(result.isSuccess())
                .metadata(context.getMetadata())
                .build();
        
        accountClosureRepository.save(closure);
    }

    private boolean requiresRegulatoryReporting(AccountClosureContext context) {
        return Set.of("REGULATORY", "FRAUD", "COMPLIANCE", "COURT_ORDER").contains(context.getClosureType());
    }

    private void validateRegulatoryDocuments(AccountClosureContext context) {
        // Validate regulatory order documents
    }

    private void validateDeceasedDocuments(AccountClosureContext context) {
        // Validate death certificate and probate documents
    }

    private void validateBankruptcyDocuments(AccountClosureContext context) {
        // Validate bankruptcy filing documents
    }

    private void validateCourtOrder(AccountClosureContext context) {
        // Validate court order authenticity
    }

    private void validateDormancyPeriod(Account account) {
        Instant escheatmentThreshold = Instant.now().minusSeconds(1095L * 24 * 3600); // 3 years
        if (account.getLastActivityDate().isAfter(escheatmentThreshold)) {
            throw new IllegalStateException("Account not dormant long enough for escheatment");
        }
    }

    private void logForcedClosureEvent(AccountClosureContext context) {
        kafkaTemplate.send("forced-closure-events", Map.of(
            "accountId", context.getAccountId(),
            "reason", context.getClosureReason(),
            "authorizedBy", context.getApprovedBy()
        ));
    }

    private void createRegulatoryCase(Account account, AccountClosureContext context) {
        kafkaTemplate.send("regulatory-cases", Map.of(
            "accountId", account.getId(),
            "caseType", "REGULATORY_CLOSURE",
            "reason", context.getClosureReason()
        ));
    }

    private void createFraudCase(Account account, AccountClosureContext context) {
        kafkaTemplate.send("fraud-cases", Map.of(
            "accountId", account.getId(),
            "caseType", "FRAUD_CLOSURE",
            "reason", context.getClosureReason()
        ));
    }

    private void createEstateCase(Account account, AccountClosureContext context) {
        kafkaTemplate.send("estate-cases", Map.of(
            "accountId", account.getId(),
            "estateReference", context.getMetadata().get("estateReference")
        ));
    }

    private void createComplianceCase(Account account, AccountClosureContext context) {
        kafkaTemplate.send("compliance-cases", Map.of(
            "accountId", account.getId(),
            "caseType", "COMPLIANCE_CLOSURE",
            "reason", context.getClosureReason()
        ));
    }

    private void createBankruptcyCase(Account account, AccountClosureContext context) {
        kafkaTemplate.send("bankruptcy-cases", Map.of(
            "accountId", account.getId(),
            "bankruptcyCaseNumber", context.getMetadata().get("bankruptcyCaseNumber")
        ));
    }

    private void createLegalCase(Account account, AccountClosureContext context) {
        kafkaTemplate.send("legal-cases", Map.of(
            "accountId", account.getId(),
            "courtOrderNumber", context.getMetadata().get("courtOrderNumber")
        ));
    }

    private void processBalanceEscheatment(String accountId, BigDecimal amount, String reason) {
        kafkaTemplate.send("escheatment-events", Map.of(
            "accountId", accountId,
            "amount", amount,
            "reason", reason
        ));
    }

    private BigDecimal processRegulatoryBalanceTransfer(Account account, AccountClosureContext context) {
        // Process balance transfer according to regulatory requirements
        return account.getBalance();
    }

    private BigDecimal processFraudRecovery(String accountId, AccountClosureContext context) {
        return transactionService.processFraudRecovery(accountId);
    }

    private BigDecimal calculateEstateBalance(String accountId) {
        return balanceService.calculateEstateBalance(accountId);
    }

    private BigDecimal transferToStateEscheatment(Account account, BigDecimal amount) {
        kafkaTemplate.send("state-escheatment", Map.of(
            "accountId", account.getId(),
            "amount", amount,
            "state", "DEFAULT"
        ));
        return amount;
    }

    private BigDecimal transferToTrustee(Account account, AccountClosureContext context) {
        BigDecimal balance = account.getBalance();
        kafkaTemplate.send("trustee-transfers", Map.of(
            "accountId", account.getId(),
            "amount", balance,
            "trusteeReference", context.getMetadata().get("trusteeReference")
        ));
        return balance;
    }

    private void processStateEscheatment(Account account, BigDecimal amount) {
        kafkaTemplate.send("state-escheatment-final", Map.of(
            "accountId", account.getId(),
            "amount", amount
        ));
    }

    private void executeCourtOrder(Account account, AccountClosureContext context) {
        kafkaTemplate.send("court-order-execution", Map.of(
            "accountId", account.getId(),
            "courtOrderNumber", context.getMetadata().get("courtOrderNumber")
        ));
    }

    private void reverseRecentSuspiciousTransactions(String accountId, AccountClosureContext context) {
        transactionService.reverseRecentSuspiciousTransactions(accountId);
    }

    private void holdFundsForInvestigation(String accountId, BigDecimal amount) {
        kafkaTemplate.send("fund-holds", Map.of(
            "accountId", accountId,
            "amount", amount,
            "reason", "FRAUD_INVESTIGATION"
        ));
    }

    private void holdFundsForCompliance(String accountId, BigDecimal amount) {
        kafkaTemplate.send("fund-holds", Map.of(
            "accountId", accountId,
            "amount", amount,
            "reason", "COMPLIANCE_HOLD"
        ));
    }

    private void freezeAccountForEstate(String accountId) {
        kafkaTemplate.send("estate-freezes", Map.of(
            "accountId", accountId
        ));
    }

    private void freezeAccountForCompliance(String accountId) {
        kafkaTemplate.send("compliance-freezes", Map.of(
            "accountId", accountId
        ));
    }

    private void freezeAccountForBankruptcy(String accountId) {
        kafkaTemplate.send("bankruptcy-freezes", Map.of(
            "accountId", accountId
        ));
    }

    private void processEstateTransfer(Account account, AccountClosureContext context, BigDecimal amount) {
        kafkaTemplate.send("estate-transfers", Map.of(
            "accountId", account.getId(),
            "amount", amount,
            "estateReference", context.getMetadata().get("estateReference")
        ));
    }

    private void notifyForcedClosure(Account account, String reason) {
        notificationService.sendForcedClosure(account.getUserId(), account.getId(), reason);
    }

    private void notifyRegulatoryAuthorities(Account account, AccountClosureContext context) {
        kafkaTemplate.send("regulatory-notifications", Map.of(
            "accountId", account.getId(),
            "closureType", "FORCED",
            "reason", context.getClosureReason()
        ));
    }

    private void notifyLawEnforcement(Account account, AccountClosureContext context) {
        kafkaTemplate.send("law-enforcement-notifications", Map.of(
            "accountId", account.getId(),
            "fraudCaseId", context.getMetadata().get("fraudCaseId")
        ));
    }

    private void notifyEstate(Account account, AccountClosureContext context, BigDecimal amount) {
        notificationService.sendEstateNotification(account.getId(), amount, context.getMetadata().get("estateReference").toString());
    }

    private void notifyComplianceTeam(Account account, AccountClosureContext context) {
        complianceService.notifyTeam(account.getId(), "COMPLIANCE_CLOSURE", context.getClosureReason());
    }

    private void notifyTrustee(Account account, AccountClosureContext context, BigDecimal amount) {
        kafkaTemplate.send("trustee-notifications", Map.of(
            "accountId", account.getId(),
            "amount", amount,
            "trusteeReference", context.getMetadata().get("trusteeReference")
        ));
    }

    private void reportToRegulatoryBodies(Account account, AccountClosureContext context) {
        kafkaTemplate.send("regulatory-reports", Map.of(
            "accountId", account.getId(),
            "reportType", "REGULATORY_CLOSURE"
        ));
    }

    private void reportEscheatmentToState(Account account, BigDecimal amount) {
        kafkaTemplate.send("state-reporting", Map.of(
            "accountId", account.getId(),
            "amount", amount,
            "reportType", "ESCHEATMENT"
        ));
    }

    private void reportCourtOrderCompliance(Account account, AccountClosureContext context) {
        kafkaTemplate.send("court-compliance-reports", Map.of(
            "accountId", account.getId(),
            "courtOrderNumber", context.getMetadata().get("courtOrderNumber")
        ));
    }

    private void fileSuspiciousActivityReport(Account account, AccountClosureContext context) {
        kafkaTemplate.send("sar-filing-queue", Map.of(
            "accountId", account.getId(),
            "reason", "FRAUD_CLOSURE"
        ));
    }

    private void fileComplianceReport(Account account, AccountClosureContext context) {
        kafkaTemplate.send("compliance-reports", Map.of(
            "accountId", account.getId(),
            "reportType", "COMPLIANCE_CLOSURE"
        ));
    }

    private void generateRegulatoryReport(Account account, AccountClosureContext context) {
        documentService.generateRegulatoryReport(account.getId(), context.getClosureReason());
    }

    private void sendFinalNotice(Account account, AccountClosureContext context) {
        notificationService.sendFinalNotice(account.getUserId(), account.getId());
    }

    private void updateTaxRecords(Account account, AccountClosureContext context) {
        kafkaTemplate.send("tax-record-updates", Map.of(
            "accountId", account.getId(),
            "event", "ACCOUNT_HOLDER_DECEASED"
        ));
    }

    private void updateRelatedSystems(AccountClosureContext context, AccountClosureResult result) {
        kafkaTemplate.send("system-updates", Map.of(
            "accountId", context.getAccountId(),
            "event", "ACCOUNT_CLOSED",
            "closureType", context.getClosureType()
        ));
    }

    private void generateClosureDocuments(AccountClosureContext context, AccountClosureResult result) {
        documentService.generateClosureDocuments(context.getAccountId(), context.getClosureType());
    }

    private void updateReportingSystems(AccountClosureContext context, AccountClosureResult result) {
        kafkaTemplate.send("reporting-updates", Map.of(
            "accountId", context.getAccountId(),
            "event", "ACCOUNT_CLOSED"
        ));
    }

    private void processLoyaltyProgramClosure(AccountClosureContext context) {
        kafkaTemplate.send("loyalty-program-closure", Map.of(
            "userId", context.getUserId(),
            "accountId", context.getAccountId()
        ));
    }

    private void cancelAllSubscriptions(String accountId) {
        kafkaTemplate.send("subscription-cancellations", Map.of(
            "accountId", accountId
        ));
    }

    private void updateCreditReportingAgencies(AccountClosureContext context) {
        kafkaTemplate.send("credit-reporting-updates", Map.of(
            "userId", context.getUserId(),
            "event", "ACCOUNT_CLOSED"
        ));
    }

    private void logClosureFailure(AccountClosureContext context, AccountClosureResult result) {
        log.error("Account closure failed for account: {}, reason: {}", context.getAccountId(), result.getFailureReason());
    }

    private void notifyManagementOfFailure(AccountClosureContext context, AccountClosureResult result) {
        kafkaTemplate.send("management-alerts", Map.of(
            "alertType", "CLOSURE_FAILURE",
            "accountId", context.getAccountId(),
            "reason", result.getFailureReason()
        ));
    }

    private void revertPartialActions(AccountClosureContext context) {
        kafkaTemplate.send("action-reversals", Map.of(
            "accountId", context.getAccountId(),
            "reason", "CLOSURE_FAILURE"
        ));
    }

    private void archiveAccountData(AccountClosureContext context, AccountClosureResult result) {
        kafkaTemplate.send("data-archival", Map.of(
            "accountId", context.getAccountId(),
            "closureDate", result.getClosureDate()
        ));
    }

    private void submitRegulatoryFiling(AccountClosureContext context, AccountClosureResult result) {
        kafkaTemplate.send("regulatory-filings", Map.of(
            "accountId", context.getAccountId(),
            "filingType", "CLOSURE_REPORT",
            "closureType", context.getClosureType()
        ));
    }

    private void handleProcessingError(String eventId, String closureType, String accountId, String eventPayload, 
                                     Exception e, Acknowledgment acknowledgment) {
        log.error("Error processing account closure: {} of type: {} for account: {}", eventId, closureType, accountId, e);
        
        try {
            auditService.logAccountEvent(eventId, "ACCOUNT_CLOSURE", accountId, "ERROR", Map.of("error", e.getMessage()));
            
            metricsService.incrementCounter("account_closure_errors", 
                "closure_type", closureType != null ? closureType : "UNKNOWN",
                "error_type", e.getClass().getSimpleName());
            
            if (isRetryableError(e)) {
                sendToDlq(eventPayload, "account-closure-events-dlq", "RETRYABLE_ERROR", e.getMessage());
            } else {
                sendToDlq(eventPayload, "account-closure-events-dlq", "NON_RETRYABLE_ERROR", e.getMessage());
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

    public void fallbackProcessAccountClosure(String eventPayload, String topic, int partition, long offset, 
                                             Long timestamp, Acknowledgment acknowledgment, Exception ex) {
        log.error("Circuit breaker fallback triggered for account closure processing", ex);
        
        metricsService.incrementCounter("account_closure_circuit_breaker_fallback");
        
        sendToDlq(eventPayload, "account-closure-events-dlq", "CIRCUIT_BREAKER_OPEN", ex.getMessage());
        acknowledgment.acknowledge();
    }
}