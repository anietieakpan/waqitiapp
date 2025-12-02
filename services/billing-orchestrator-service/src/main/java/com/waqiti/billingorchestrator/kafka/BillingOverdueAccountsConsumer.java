package com.waqiti.billingorchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.billingorchestrator.model.*;
import com.waqiti.billingorchestrator.repository.OverdueAccountRepository;
import com.waqiti.billingorchestrator.service.*;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for billing overdue accounts events
 * Processes overdue billing accounts with enterprise patterns
 * 
 * Critical for: Revenue recovery, customer retention, compliance
 * SLA: Must process overdue accounts within 60 seconds for timely action
 */
@Component
@Slf4j
public class BillingOverdueAccountsConsumer {

    private final OverdueAccountRepository overdueAccountRepository;
    private final BillingService billingService;
    private final CollectionService collectionService;
    private final NotificationService notificationService;
    private final CustomerRetentionService customerRetentionService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter overdueAccountCounter;
    private final Counter highValueOverdueCounter;
    private final Counter criticalOverdueCounter;
    private final Timer overdueProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");
    private static final int CRITICAL_DAYS_OVERDUE = 90;

    public BillingOverdueAccountsConsumer(
            OverdueAccountRepository overdueAccountRepository,
            BillingService billingService,
            CollectionService collectionService,
            NotificationService notificationService,
            CustomerRetentionService customerRetentionService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.overdueAccountRepository = overdueAccountRepository;
        this.billingService = billingService;
        this.collectionService = collectionService;
        this.notificationService = notificationService;
        this.customerRetentionService = customerRetentionService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.overdueAccountCounter = Counter.builder("billing.overdue.accounts.events")
            .description("Count of billing overdue account events")
            .register(meterRegistry);
        
        this.highValueOverdueCounter = Counter.builder("billing.overdue.accounts.high.value.events")
            .description("Count of high-value overdue accounts")
            .register(meterRegistry);
        
        this.criticalOverdueCounter = Counter.builder("billing.overdue.accounts.critical.events")
            .description("Count of critical overdue accounts")
            .register(meterRegistry);
        
        this.overdueProcessingTimer = Timer.builder("billing.overdue.accounts.processing.duration")
            .description("Time taken to process overdue account events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "billing-overdue-accounts",
        groupId = "billing-overdue-accounts-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "billing-overdue-accounts-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleBillingOverdueAccountEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received billing overdue account event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String accountId = (String) eventData.get("accountId");
            String userId = (String) eventData.get("userId");
            String invoiceId = (String) eventData.get("invoiceId");
            BigDecimal overdueAmount = new BigDecimal(eventData.get("overdueAmount").toString());
            String currency = (String) eventData.get("currency");
            Integer daysOverdue = (Integer) eventData.get("daysOverdue");
            String overdueReason = (String) eventData.get("overdueReason");
            String customerTier = (String) eventData.get("customerTier");
            String billingCycle = (String) eventData.get("billingCycle");
            BigDecimal totalOutstanding = new BigDecimal(eventData.get("totalOutstanding").toString());
            Integer overdueCount = (Integer) eventData.getOrDefault("overdueCount", 1);
            Boolean hasPaymentMethod = (Boolean) eventData.getOrDefault("hasPaymentMethod", false);
            Boolean isBusinessAccount = (Boolean) eventData.getOrDefault("isBusinessAccount", false);
            
            String correlationId = String.format("billing-overdue-%s-%d", 
                accountId, System.currentTimeMillis());
            
            log.warn("Processing billing overdue account - accountId: {}, amount: {}, days: {}, correlationId: {}", 
                accountId, overdueAmount, daysOverdue, correlationId);
            
            overdueAccountCounter.increment();
            
            processBillingOverdueAccount(accountId, userId, invoiceId, overdueAmount, currency,
                daysOverdue, overdueReason, customerTier, billingCycle, totalOutstanding,
                overdueCount, hasPaymentMethod, isBusinessAccount, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(overdueProcessingTimer);
            
            log.info("Successfully processed billing overdue account event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process billing overdue account event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Billing overdue account processing failed", e);
        }
    }

    @CircuitBreaker(name = "billing", fallbackMethod = "processBillingOverdueAccountFallback")
    @Retry(name = "billing")
    private void processBillingOverdueAccount(
            String accountId,
            String userId,
            String invoiceId,
            BigDecimal overdueAmount,
            String currency,
            Integer daysOverdue,
            String overdueReason,
            String customerTier,
            String billingCycle,
            BigDecimal totalOutstanding,
            Integer overdueCount,
            Boolean hasPaymentMethod,
            Boolean isBusinessAccount,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create overdue account record
        OverdueAccount overdueAccount = OverdueAccount.builder()
            .id(java.util.UUID.randomUUID().toString())
            .accountId(accountId)
            .userId(userId)
            .invoiceId(invoiceId)
            .overdueAmount(overdueAmount)
            .currency(currency)
            .daysOverdue(daysOverdue)
            .overdueReason(overdueReason)
            .customerTier(CustomerTier.fromString(customerTier))
            .billingCycle(billingCycle)
            .totalOutstanding(totalOutstanding)
            .overdueCount(overdueCount)
            .hasPaymentMethod(hasPaymentMethod)
            .isBusinessAccount(isBusinessAccount)
            .status(OverdueStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        overdueAccountRepository.save(overdueAccount);
        
        // Handle high-value overdue accounts
        if (overdueAmount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            handleHighValueOverdueAccount(overdueAccount, correlationId);
        }
        
        // Handle critical overdue accounts
        if (daysOverdue >= CRITICAL_DAYS_OVERDUE) {
            handleCriticalOverdueAccount(overdueAccount, correlationId);
        }
        
        // Initiate collection process
        initiateCollectionProcess(overdueAccount, correlationId);
        
        // Send customer notifications
        sendOverdueNotifications(overdueAccount, correlationId);
        
        // Apply account restrictions if necessary
        applyAccountRestrictions(overdueAccount, correlationId);
        
        // Update customer retention strategies
        updateRetentionStrategies(overdueAccount, correlationId);
        
        // Generate collection workflows
        generateCollectionWorkflows(overdueAccount, correlationId);
        
        // Update billing metrics
        updateBillingMetrics(overdueAccount, correlationId);
        
        // Publish overdue analytics
        publishOverdueAnalytics(overdueAccount, correlationId);
        
        // Audit the overdue processing
        auditService.logBillingEvent(
            "BILLING_OVERDUE_ACCOUNT_PROCESSED",
            overdueAccount.getId(),
            Map.of(
                "accountId", accountId,
                "userId", userId,
                "invoiceId", invoiceId,
                "overdueAmount", overdueAmount,
                "currency", currency,
                "daysOverdue", daysOverdue,
                "overdueReason", overdueReason,
                "customerTier", customerTier,
                "totalOutstanding", totalOutstanding,
                "overdueCount", overdueCount,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.warn("Billing overdue account processed - accountId: {}, amount: {}, days: {}, correlationId: {}", 
            accountId, overdueAmount, daysOverdue, correlationId);
    }

    private void handleHighValueOverdueAccount(OverdueAccount overdueAccount, String correlationId) {
        highValueOverdueCounter.increment();
        
        log.error("HIGH VALUE OVERDUE: High-value overdue account - accountId: {}, amount: {}, correlationId: {}", 
            overdueAccount.getAccountId(), overdueAccount.getOverdueAmount(), correlationId);
        
        // Send high-value alert
        kafkaTemplate.send("high-value-overdue-alerts", Map.of(
            "accountId", overdueAccount.getAccountId(),
            "userId", overdueAccount.getUserId(),
            "overdueAmount", overdueAccount.getOverdueAmount(),
            "currency", overdueAccount.getCurrency(),
            "daysOverdue", overdueAccount.getDaysOverdue(),
            "totalOutstanding", overdueAccount.getTotalOutstanding(),
            "priority", "URGENT",
            "requiresEscalation", true,
            "alertType", "HIGH_VALUE_OVERDUE",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify executive team
        notificationService.sendExecutiveAlert(
            "High-Value Overdue Account",
            String.format("High-value overdue account: %s %s overdue for %d days", 
                overdueAccount.getOverdueAmount(), overdueAccount.getCurrency(), overdueAccount.getDaysOverdue()),
            Map.of(
                "accountId", overdueAccount.getAccountId(),
                "overdueAmount", overdueAccount.getOverdueAmount(),
                "currency", overdueAccount.getCurrency(),
                "daysOverdue", overdueAccount.getDaysOverdue(),
                "correlationId", correlationId
            )
        );
    }

    private void handleCriticalOverdueAccount(OverdueAccount overdueAccount, String correlationId) {
        criticalOverdueCounter.increment();
        
        log.error("CRITICAL OVERDUE: Critical overdue account - accountId: {}, days: {}, correlationId: {}", 
            overdueAccount.getAccountId(), overdueAccount.getDaysOverdue(), correlationId);
        
        // Send critical alert
        kafkaTemplate.send("critical-overdue-alerts", Map.of(
            "accountId", overdueAccount.getAccountId(),
            "userId", overdueAccount.getUserId(),
            "overdueAmount", overdueAccount.getOverdueAmount(),
            "daysOverdue", overdueAccount.getDaysOverdue(),
            "totalOutstanding", overdueAccount.getTotalOutstanding(),
            "severity", "CRITICAL",
            "requiresLegalAction", true,
            "alertType", "CRITICAL_OVERDUE",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Trigger legal review process
        kafkaTemplate.send("legal-review-required", Map.of(
            "reviewType", "OVERDUE_ACCOUNT_LEGAL_ACTION",
            "accountId", overdueAccount.getAccountId(),
            "overdueAmount", overdueAccount.getOverdueAmount(),
            "daysOverdue", overdueAccount.getDaysOverdue(),
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void initiateCollectionProcess(OverdueAccount overdueAccount, String correlationId) {
        // Determine collection strategy based on days overdue and amount
        CollectionStrategy strategy = collectionService.determineCollectionStrategy(
            overdueAccount.getDaysOverdue(),
            overdueAccount.getOverdueAmount(),
            overdueAccount.getCustomerTier(),
            overdueAccount.getOverdueCount()
        );
        
        // Start collection workflow
        collectionService.initiateCollectionWorkflow(overdueAccount, strategy, correlationId);
        
        // Update overdue account with collection details
        overdueAccount.setCollectionStrategy(strategy.getType());
        overdueAccount.setCollectionStartDate(LocalDateTime.now());
        overdueAccountRepository.save(overdueAccount);
    }

    private void sendOverdueNotifications(OverdueAccount overdueAccount, String correlationId) {
        // Send customer notification based on days overdue
        if (overdueAccount.getDaysOverdue() <= 30) {
            // Gentle reminder
            notificationService.sendOverdueReminder(
                overdueAccount.getUserId(),
                "Payment Reminder",
                String.format("Your account has an overdue balance of %s %s. Please make a payment to avoid service interruption.", 
                    overdueAccount.getOverdueAmount(), overdueAccount.getCurrency()),
                Map.of(
                    "overdueAmount", overdueAccount.getOverdueAmount(),
                    "currency", overdueAccount.getCurrency(),
                    "daysOverdue", overdueAccount.getDaysOverdue(),
                    "correlationId", correlationId
                )
            );
        } else if (overdueAccount.getDaysOverdue() <= 60) {
            // Urgent notice
            notificationService.sendUrgentOverdueNotice(
                overdueAccount.getUserId(),
                "Urgent: Payment Required",
                String.format("Your account is %d days overdue with a balance of %s %s. Immediate payment is required.", 
                    overdueAccount.getDaysOverdue(), overdueAccount.getOverdueAmount(), overdueAccount.getCurrency()),
                Map.of(
                    "overdueAmount", overdueAccount.getOverdueAmount(),
                    "currency", overdueAccount.getCurrency(),
                    "daysOverdue", overdueAccount.getDaysOverdue(),
                    "correlationId", correlationId
                )
            );
        } else {
            // Final notice
            notificationService.sendFinalOverdueNotice(
                overdueAccount.getUserId(),
                "Final Notice: Account Suspension",
                String.format("This is your final notice. Your account is %d days overdue. Pay %s %s immediately to avoid account closure.", 
                    overdueAccount.getDaysOverdue(), overdueAccount.getOverdueAmount(), overdueAccount.getCurrency()),
                Map.of(
                    "overdueAmount", overdueAccount.getOverdueAmount(),
                    "currency", overdueAccount.getCurrency(),
                    "daysOverdue", overdueAccount.getDaysOverdue(),
                    "correlationId", correlationId
                )
            );
        }
    }

    private void applyAccountRestrictions(OverdueAccount overdueAccount, String correlationId) {
        // Apply restrictions based on days overdue
        if (overdueAccount.getDaysOverdue() > 30) {
            kafkaTemplate.send("account-restrictions", Map.of(
                "accountId", overdueAccount.getAccountId(),
                "userId", overdueAccount.getUserId(),
                "restrictionType", "TRANSACTION_LIMIT",
                "reason", "OVERDUE_PAYMENT",
                "daysOverdue", overdueAccount.getDaysOverdue(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        if (overdueAccount.getDaysOverdue() > 60) {
            kafkaTemplate.send("account-restrictions", Map.of(
                "accountId", overdueAccount.getAccountId(),
                "userId", overdueAccount.getUserId(),
                "restrictionType", "SERVICE_SUSPENSION",
                "reason", "OVERDUE_PAYMENT",
                "daysOverdue", overdueAccount.getDaysOverdue(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    private void updateRetentionStrategies(OverdueAccount overdueAccount, String correlationId) {
        // Update customer retention strategies for high-value customers
        if ("PREMIUM".equals(overdueAccount.getCustomerTier().toString()) ||
            "VIP".equals(overdueAccount.getCustomerTier().toString())) {
            
            customerRetentionService.updateRetentionStrategy(
                overdueAccount.getUserId(),
                "OVERDUE_ACCOUNT",
                overdueAccount.getOverdueAmount(),
                overdueAccount.getDaysOverdue(),
                correlationId
            );
        }
    }

    private void generateCollectionWorkflows(OverdueAccount overdueAccount, String correlationId) {
        // Generate collection workflows based on business rules
        kafkaTemplate.send("collection-workflows", Map.of(
            "workflowType", "OVERDUE_COLLECTION",
            "accountId", overdueAccount.getAccountId(),
            "userId", overdueAccount.getUserId(),
            "overdueAmount", overdueAccount.getOverdueAmount(),
            "daysOverdue", overdueAccount.getDaysOverdue(),
            "customerTier", overdueAccount.getCustomerTier().toString(),
            "priority", determineCollectionPriority(overdueAccount),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private String determineCollectionPriority(OverdueAccount overdueAccount) {
        if (overdueAccount.getOverdueAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return "HIGH";
        } else if (overdueAccount.getDaysOverdue() > 60) {
            return "HIGH";
        } else if (overdueAccount.getDaysOverdue() > 30) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private void updateBillingMetrics(OverdueAccount overdueAccount, String correlationId) {
        // Update billing metrics
        kafkaTemplate.send("billing-metrics-update", Map.of(
            "metricType", "OVERDUE_ACCOUNT",
            "accountId", overdueAccount.getAccountId(),
            "overdueAmount", overdueAccount.getOverdueAmount(),
            "currency", overdueAccount.getCurrency(),
            "daysOverdue", overdueAccount.getDaysOverdue(),
            "customerTier", overdueAccount.getCustomerTier().toString(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void publishOverdueAnalytics(OverdueAccount overdueAccount, String correlationId) {
        // Publish overdue analytics for business intelligence
        kafkaTemplate.send("billing-overdue-analytics", Map.of(
            "overdueId", overdueAccount.getId(),
            "accountId", overdueAccount.getAccountId(),
            "userId", overdueAccount.getUserId(),
            "overdueAmount", overdueAccount.getOverdueAmount(),
            "currency", overdueAccount.getCurrency(),
            "daysOverdue", overdueAccount.getDaysOverdue(),
            "overdueReason", overdueAccount.getOverdueReason(),
            "customerTier", overdueAccount.getCustomerTier().toString(),
            "totalOutstanding", overdueAccount.getTotalOutstanding(),
            "overdueCount", overdueAccount.getOverdueCount(),
            "hasPaymentMethod", overdueAccount.getHasPaymentMethod(),
            "isBusinessAccount", overdueAccount.getIsBusinessAccount(),
            "eventType", "OVERDUE_ACCOUNT_CREATED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processBillingOverdueAccountFallback(
            String accountId,
            String userId,
            String invoiceId,
            BigDecimal overdueAmount,
            String currency,
            Integer daysOverdue,
            String overdueReason,
            String customerTier,
            String billingCycle,
            BigDecimal totalOutstanding,
            Integer overdueCount,
            Boolean hasPaymentMethod,
            Boolean isBusinessAccount,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for billing overdue account - accountId: {}, correlationId: {}, error: {}", 
            accountId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("accountId", accountId);
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("invoiceId", invoiceId);
        fallbackEvent.put("overdueAmount", overdueAmount);
        fallbackEvent.put("currency", currency);
        fallbackEvent.put("daysOverdue", daysOverdue);
        fallbackEvent.put("overdueReason", overdueReason);
        fallbackEvent.put("customerTier", customerTier);
        fallbackEvent.put("billingCycle", billingCycle);
        fallbackEvent.put("totalOutstanding", totalOutstanding);
        fallbackEvent.put("overdueCount", overdueCount);
        fallbackEvent.put("hasPaymentMethod", hasPaymentMethod);
        fallbackEvent.put("isBusinessAccount", isBusinessAccount);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("billing-overdue-accounts-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Billing overdue account message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("billing-overdue-accounts-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Billing Overdue Account Processing Failed",
                String.format("CRITICAL: Failed to process overdue account after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        // Cleanup old entries
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}