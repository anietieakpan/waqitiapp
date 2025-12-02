package com.waqiti.business.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.business.model.*;
import com.waqiti.business.repository.BusinessTransactionRepository;
import com.waqiti.business.service.*;
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
 * Production-grade Kafka consumer for business account transactions
 * Processes business account specific transactions with enterprise patterns
 * 
 * Critical for: Business operations, compliance, financial reporting
 * SLA: Must process business transactions within 45 seconds for compliance
 */
@Component
@Slf4j
public class BusinessAccountTransactionsConsumer {

    private final BusinessTransactionRepository businessTransactionRepository;
    private final BusinessAccountService businessAccountService;
    private final BusinessComplianceService complianceService;
    private final BusinessAnalyticsService analyticsService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter businessTransactionCounter;
    private final Counter highValueBusinessTransactionCounter;
    private final Counter complianceBusinessTransactionCounter;
    private final Timer businessTransactionProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100000.00");

    public BusinessAccountTransactionsConsumer(
            BusinessTransactionRepository businessTransactionRepository,
            BusinessAccountService businessAccountService,
            BusinessComplianceService complianceService,
            BusinessAnalyticsService analyticsService,
            NotificationService notificationService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.businessTransactionRepository = businessTransactionRepository;
        this.businessAccountService = businessAccountService;
        this.complianceService = complianceService;
        this.analyticsService = analyticsService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.businessTransactionCounter = Counter.builder("business.account.transactions.events")
            .description("Count of business account transaction events")
            .register(meterRegistry);
        
        this.highValueBusinessTransactionCounter = Counter.builder("business.account.transactions.high.value.events")
            .description("Count of high-value business transactions")
            .register(meterRegistry);
        
        this.complianceBusinessTransactionCounter = Counter.builder("business.account.transactions.compliance.events")
            .description("Count of compliance-related business transactions")
            .register(meterRegistry);
        
        this.businessTransactionProcessingTimer = Timer.builder("business.account.transactions.processing.duration")
            .description("Time taken to process business transaction events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "business-account-transactions",
        groupId = "business-account-transactions-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "business-account-transactions-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleBusinessAccountTransactionEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received business account transaction event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String transactionId = (String) eventData.get("transactionId");
            String businessAccountId = (String) eventData.get("businessAccountId");
            String businessId = (String) eventData.get("businessId");
            String transactionType = (String) eventData.get("transactionType");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            String currency = (String) eventData.get("currency");
            String counterpartyType = (String) eventData.get("counterpartyType");
            String counterpartyId = (String) eventData.get("counterpartyId");
            String description = (String) eventData.get("description");
            String reference = (String) eventData.get("reference");
            Boolean requiresApproval = (Boolean) eventData.getOrDefault("requiresApproval", false);
            Boolean isInternational = (Boolean) eventData.getOrDefault("isInternational", false);
            String businessCategory = (String) eventData.get("businessCategory");
            String industryCode = (String) eventData.get("industryCode");
            
            String correlationId = String.format("business-transaction-%s-%d", 
                transactionId, System.currentTimeMillis());
            
            log.info("Processing business account transaction - transactionId: {}, businessId: {}, amount: {}, correlationId: {}", 
                transactionId, businessId, amount, correlationId);
            
            businessTransactionCounter.increment();
            
            processBusinessAccountTransaction(transactionId, businessAccountId, businessId, transactionType,
                amount, currency, counterpartyType, counterpartyId, description, reference,
                requiresApproval, isInternational, businessCategory, industryCode, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(businessTransactionProcessingTimer);
            
            log.info("Successfully processed business account transaction event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process business account transaction event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Business account transaction processing failed", e);
        }
    }

    @CircuitBreaker(name = "business", fallbackMethod = "processBusinessAccountTransactionFallback")
    @Retry(name = "business")
    private void processBusinessAccountTransaction(
            String transactionId,
            String businessAccountId,
            String businessId,
            String transactionType,
            BigDecimal amount,
            String currency,
            String counterpartyType,
            String counterpartyId,
            String description,
            String reference,
            Boolean requiresApproval,
            Boolean isInternational,
            String businessCategory,
            String industryCode,
            Map<String, Object> eventData,
            String correlationId) {
        
        // Create business transaction record
        BusinessTransaction businessTransaction = BusinessTransaction.builder()
            .id(transactionId)
            .businessAccountId(businessAccountId)
            .businessId(businessId)
            .transactionType(BusinessTransactionType.fromString(transactionType))
            .amount(amount)
            .currency(currency)
            .counterpartyType(counterpartyType)
            .counterpartyId(counterpartyId)
            .description(description)
            .reference(reference)
            .requiresApproval(requiresApproval)
            .isInternational(isInternational)
            .businessCategory(businessCategory)
            .industryCode(industryCode)
            .status(BusinessTransactionStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        businessTransactionRepository.save(businessTransaction);
        
        // Handle high-value transactions
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            handleHighValueBusinessTransaction(businessTransaction, correlationId);
        }
        
        // Perform compliance checks
        performComplianceChecks(businessTransaction, correlationId);
        
        // Update business analytics
        updateBusinessAnalytics(businessTransaction, correlationId);
        
        // Handle approval workflow if required
        if (requiresApproval) {
            initiateApprovalWorkflow(businessTransaction, correlationId);
        }
        
        // Publish business transaction events
        publishBusinessTransactionEvents(businessTransaction, correlationId);
        
        // Audit the business transaction
        auditService.logBusinessEvent(
            "BUSINESS_ACCOUNT_TRANSACTION_PROCESSED",
            businessTransaction.getId(),
            Map.of(
                "businessAccountId", businessAccountId,
                "businessId", businessId,
                "transactionType", transactionType,
                "amount", amount,
                "currency", currency,
                "counterpartyType", counterpartyType,
                "description", description,
                "requiresApproval", requiresApproval,
                "isInternational", isInternational,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.info("Business account transaction processed - transactionId: {}, businessId: {}, amount: {}, correlationId: {}", 
            transactionId, businessId, amount, correlationId);
    }

    private void handleHighValueBusinessTransaction(BusinessTransaction businessTransaction, String correlationId) {
        highValueBusinessTransactionCounter.increment();
        
        log.warn("HIGH VALUE BUSINESS TRANSACTION: High-value business transaction - transactionId: {}, amount: {}, correlationId: {}", 
            businessTransaction.getId(), businessTransaction.getAmount(), correlationId);
        
        // Send high-value alert
        kafkaTemplate.send("high-value-business-transaction-alerts", Map.of(
            "transactionId", businessTransaction.getId(),
            "businessAccountId", businessTransaction.getBusinessAccountId(),
            "businessId", businessTransaction.getBusinessId(),
            "amount", businessTransaction.getAmount(),
            "currency", businessTransaction.getCurrency(),
            "transactionType", businessTransaction.getTransactionType().toString(),
            "counterpartyType", businessTransaction.getCounterpartyType(),
            "isInternational", businessTransaction.getIsInternational(),
            "priority", "HIGH",
            "requiresReview", true,
            "alertType", "HIGH_VALUE_BUSINESS_TRANSACTION",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Notify business operations team
        notificationService.sendBusinessOperationsAlert(
            "High-Value Business Transaction",
            String.format("High-value business transaction: %s %s for business %s", 
                businessTransaction.getAmount(), businessTransaction.getCurrency(), businessTransaction.getBusinessId()),
            Map.of(
                "transactionId", businessTransaction.getId(),
                "businessId", businessTransaction.getBusinessId(),
                "amount", businessTransaction.getAmount(),
                "currency", businessTransaction.getCurrency(),
                "correlationId", correlationId
            )
        );
    }

    private void performComplianceChecks(BusinessTransaction businessTransaction, String correlationId) {
        // Perform business-specific compliance checks
        ComplianceCheckResult complianceResult = complianceService.performBusinessTransactionCompliance(
            businessTransaction.getBusinessId(),
            businessTransaction.getAmount(),
            businessTransaction.getCurrency(),
            businessTransaction.getCounterpartyType(),
            businessTransaction.getIsInternational(),
            businessTransaction.getIndustryCode()
        );
        
        if (complianceResult.requiresReview()) {
            complianceBusinessTransactionCounter.increment();
            
            kafkaTemplate.send("business-compliance-review-required", Map.of(
                "reviewType", "BUSINESS_TRANSACTION_COMPLIANCE",
                "transactionId", businessTransaction.getId(),
                "businessId", businessTransaction.getBusinessId(),
                "complianceFlags", complianceResult.getFlags(),
                "riskScore", complianceResult.getRiskScore(),
                "priority", complianceResult.getPriority(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
            
            // Update transaction status
            businessTransaction.setComplianceStatus(ComplianceStatus.UNDER_REVIEW);
            businessTransaction.setComplianceFlags(complianceResult.getFlags());
            businessTransactionRepository.save(businessTransaction);
        } else {
            businessTransaction.setComplianceStatus(ComplianceStatus.APPROVED);
            businessTransactionRepository.save(businessTransaction);
        }
    }

    private void updateBusinessAnalytics(BusinessTransaction businessTransaction, String correlationId) {
        // Update business analytics and metrics
        analyticsService.updateBusinessTransactionMetrics(
            businessTransaction.getBusinessId(),
            businessTransaction.getTransactionType(),
            businessTransaction.getAmount(),
            businessTransaction.getCurrency(),
            businessTransaction.getBusinessCategory()
        );
        
        // Publish analytics event
        kafkaTemplate.send("business-transaction-analytics", Map.of(
            "transactionId", businessTransaction.getId(),
            "businessId", businessTransaction.getBusinessId(),
            "transactionType", businessTransaction.getTransactionType().toString(),
            "amount", businessTransaction.getAmount(),
            "currency", businessTransaction.getCurrency(),
            "businessCategory", businessTransaction.getBusinessCategory(),
            "industryCode", businessTransaction.getIndustryCode(),
            "isInternational", businessTransaction.getIsInternational(),
            "eventType", "BUSINESS_TRANSACTION_ANALYTICS",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void initiateApprovalWorkflow(BusinessTransaction businessTransaction, String correlationId) {
        // Create approval workflow for business transactions requiring approval
        kafkaTemplate.send("business-transaction-approval-required", Map.of(
            "workflowType", "BUSINESS_TRANSACTION_APPROVAL",
            "transactionId", businessTransaction.getId(),
            "businessId", businessTransaction.getBusinessId(),
            "amount", businessTransaction.getAmount(),
            "currency", businessTransaction.getCurrency(),
            "transactionType", businessTransaction.getTransactionType().toString(),
            "description", businessTransaction.getDescription(),
            "priority", determineApprovalPriority(businessTransaction),
            "assigneeRole", "BUSINESS_APPROVER",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Update transaction status
        businessTransaction.setStatus(BusinessTransactionStatus.PENDING_APPROVAL);
        businessTransactionRepository.save(businessTransaction);
    }

    private String determineApprovalPriority(BusinessTransaction businessTransaction) {
        if (businessTransaction.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return "URGENT";
        } else if (businessTransaction.getIsInternational()) {
            return "HIGH";
        } else {
            return "MEDIUM";
        }
    }

    private void publishBusinessTransactionEvents(BusinessTransaction businessTransaction, String correlationId) {
        // Publish business transaction event for downstream consumers
        kafkaTemplate.send("business-transaction-events", Map.of(
            "eventType", "BUSINESS_TRANSACTION_CREATED",
            "transactionId", businessTransaction.getId(),
            "businessAccountId", businessTransaction.getBusinessAccountId(),
            "businessId", businessTransaction.getBusinessId(),
            "transactionType", businessTransaction.getTransactionType().toString(),
            "amount", businessTransaction.getAmount(),
            "currency", businessTransaction.getCurrency(),
            "counterpartyType", businessTransaction.getCounterpartyType(),
            "counterpartyId", businessTransaction.getCounterpartyId(),
            "description", businessTransaction.getDescription(),
            "reference", businessTransaction.getReference(),
            "status", businessTransaction.getStatus().toString(),
            "requiresApproval", businessTransaction.getRequiresApproval(),
            "isInternational", businessTransaction.getIsInternational(),
            "businessCategory", businessTransaction.getBusinessCategory(),
            "industryCode", businessTransaction.getIndustryCode(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processBusinessAccountTransactionFallback(
            String transactionId,
            String businessAccountId,
            String businessId,
            String transactionType,
            BigDecimal amount,
            String currency,
            String counterpartyType,
            String counterpartyId,
            String description,
            String reference,
            Boolean requiresApproval,
            Boolean isInternational,
            String businessCategory,
            String industryCode,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for business account transaction - transactionId: {}, correlationId: {}, error: {}", 
            transactionId, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("transactionId", transactionId);
        fallbackEvent.put("businessAccountId", businessAccountId);
        fallbackEvent.put("businessId", businessId);
        fallbackEvent.put("transactionType", transactionType);
        fallbackEvent.put("amount", amount);
        fallbackEvent.put("currency", currency);
        fallbackEvent.put("counterpartyType", counterpartyType);
        fallbackEvent.put("counterpartyId", counterpartyId);
        fallbackEvent.put("description", description);
        fallbackEvent.put("reference", reference);
        fallbackEvent.put("requiresApproval", requiresApproval);
        fallbackEvent.put("isInternational", isInternational);
        fallbackEvent.put("businessCategory", businessCategory);
        fallbackEvent.put("industryCode", industryCode);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("business-account-transactions-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Business account transaction message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("business-account-transactions-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Business Account Transaction Processing Failed",
                String.format("CRITICAL: Failed to process business transaction after max retries. Error: %s", exceptionMessage),
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