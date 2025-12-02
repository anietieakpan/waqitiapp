package com.waqiti.account.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.account.domain.Account;
import com.waqiti.account.domain.AccountLimits;
import com.waqiti.account.domain.LimitType;
import com.waqiti.account.repository.AccountRepository;
import com.waqiti.account.repository.AccountLimitsRepository;
import com.waqiti.account.service.AccountLimitsService;
import com.waqiti.account.service.AccountNotificationService;
import com.waqiti.account.service.ComplianceReportingService;
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

@Component
@Slf4j
public class AccountLimitsUpdatedConsumer {

    private final AccountRepository accountRepository;
    private final AccountLimitsRepository limitsRepository;
    private final AccountLimitsService limitsService;
    private final AccountNotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter limitsUpdatedCounter;
    private final Counter tierUpgradeCounter;
    private final Counter kycLimitsCounter;
    private final Timer limitsProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AccountLimitsUpdatedConsumer(
            AccountRepository accountRepository,
            AccountLimitsRepository limitsRepository,
            AccountLimitsService limitsService,
            AccountNotificationService notificationService,
            ComplianceReportingService complianceService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.accountRepository = accountRepository;
        this.limitsRepository = limitsRepository;
        this.limitsService = limitsService;
        this.notificationService = notificationService;
        this.complianceService = complianceService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.limitsUpdatedCounter = Counter.builder("account.limits.updated.events")
            .description("Count of account limits updated events")
            .register(meterRegistry);
        
        this.tierUpgradeCounter = Counter.builder("account.tier.upgrade.events")
            .description("Count of tier upgrade events")
            .register(meterRegistry);
        
        this.kycLimitsCounter = Counter.builder("account.kyc.limits.events")
            .description("Count of KYC-related limits updates")
            .register(meterRegistry);
        
        this.limitsProcessingTimer = Timer.builder("account.limits.processing.duration")
            .description("Time taken to process account limits events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "account-limits-updated",
        groupId = "account-limits-updated-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "account-limits-updated-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAccountLimitsUpdatedEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received account limits updated event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String userId = (String) eventData.get("userId");
            Map<String, Object> limits = (Map<String, Object>) eventData.get("limits");
            Object timestampObj = eventData.get("timestamp");
            String reason = (String) eventData.getOrDefault("reason", "KYC_COMPLETED");
            String tier = (String) eventData.get("tier");
            
            String correlationId = String.format("account-limits-%s-%s-%d", 
                userId, reason, System.currentTimeMillis());
            
            log.info("Processing account limits update - userId: {}, reason: {}, correlationId: {}", 
                userId, reason, correlationId);
            
            limitsUpdatedCounter.increment();
            
            processAccountLimitsUpdate(userId, limits, reason, tier, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(limitsProcessingTimer);
            
            log.info("Successfully processed account limits updated event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process account limits updated event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Account limits processing failed", e);
        }
    }

    @CircuitBreaker(name = "account", fallbackMethod = "processAccountLimitsUpdateFallback")
    @Retry(name = "account")
    private void processAccountLimitsUpdate(
            String userId,
            Map<String, Object> limitsData,
            String reason,
            String tier,
            Map<String, Object> eventData,
            String correlationId) {
        
        Account account = accountRepository.findByUserId(userId)
            .orElseThrow(() -> new RuntimeException("Account not found for user: " + userId));
        
        AccountLimits previousLimits = limitsRepository.findByAccountId(account.getId())
            .orElse(null);
        
        AccountLimits newLimits = AccountLimits.builder()
            .accountId(account.getId())
            .userId(userId)
            .dailyTransactionLimit(new BigDecimal(limitsData.get("dailyTransactionLimit").toString()))
            .monthlyTransactionLimit(new BigDecimal(limitsData.get("monthlyTransactionLimit").toString()))
            .singleTransactionLimit(new BigDecimal(limitsData.get("singleTransactionLimit").toString()))
            .withdrawalDailyLimit(new BigDecimal(limitsData.get("withdrawalDailyLimit").toString()))
            .transferDailyLimit(new BigDecimal(limitsData.get("transferDailyLimit").toString()))
            .internationalTransferLimit(new BigDecimal(limitsData.get("internationalTransferLimit").toString()))
            .accountTier(tier)
            .limitType(LimitType.valueOf(reason))
            .effectiveFrom(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .isActive(true)
            .build();
        
        limitsRepository.save(newLimits);
        
        // Deactivate previous limits
        if (previousLimits != null) {
            previousLimits.setIsActive(false);
            previousLimits.setUpdatedAt(LocalDateTime.now());
            limitsRepository.save(previousLimits);
        }
        
        limitsService.applyAccountLimits(account, newLimits, correlationId);
        
        if ("KYC_COMPLETED".equals(reason)) {
            kycLimitsCounter.increment();
            handleKycLimitsUpdate(account, newLimits, previousLimits, correlationId);
        }
        
        if (tier != null && isHigherTier(previousLimits, tier)) {
            tierUpgradeCounter.increment();
            handleTierUpgrade(account, newLimits, previousLimits, correlationId);
        }
        
        notificationService.sendLimitsUpdatedNotification(
            userId,
            newLimits,
            previousLimits,
            reason,
            correlationId
        );
        
        kafkaTemplate.send("account-limits-applied", Map.of(
            "accountId", account.getId(),
            "userId", userId,
            "previousLimits", previousLimits != null ? previousLimits : "NONE",
            "newLimits", newLimits,
            "reason", reason,
            "tier", tier,
            "eventType", "ACCOUNT_LIMITS_APPLIED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logAccountEvent(
            "ACCOUNT_LIMITS_UPDATED",
            account.getId(),
            Map.of(
                "userId", userId,
                "reason", reason,
                "tier", tier,
                "previousLimits", previousLimits != null ? previousLimits.toString() : "NONE",
                "newLimits", newLimits.toString(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.info("Account limits updated - userId: {}, reason: {}, tier: {}, correlationId: {}", 
            userId, reason, tier, correlationId);
    }

    private void handleKycLimitsUpdate(Account account, AccountLimits newLimits, 
                                     AccountLimits previousLimits, String correlationId) {
        log.info("KYC limits update processed - userId: {}, correlationId: {}", 
            account.getUserId(), correlationId);
        
        complianceService.reportKycLimitsUpdate(account, newLimits, previousLimits, correlationId);
        
        kafkaTemplate.send("kyc-limits-updated", Map.of(
            "accountId", account.getId(),
            "userId", account.getUserId(),
            "limits", newLimits,
            "previousLimits", previousLimits,
            "kycStatus", "COMPLETED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleTierUpgrade(Account account, AccountLimits newLimits, 
                                 AccountLimits previousLimits, String correlationId) {
        log.info("Account tier upgrade processed - userId: {}, newTier: {}, correlationId: {}", 
            account.getUserId(), newLimits.getAccountTier(), correlationId);
        
        kafkaTemplate.send("account-tier-upgraded", Map.of(
            "accountId", account.getId(),
            "userId", account.getUserId(),
            "previousTier", previousLimits != null ? previousLimits.getAccountTier() : "BASIC",
            "newTier", newLimits.getAccountTier(),
            "limits", newLimits,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendTierUpgradeNotification(
            account.getUserId(),
            newLimits.getAccountTier(),
            previousLimits != null ? previousLimits.getAccountTier() : "BASIC",
            correlationId
        );
    }

    private boolean isHigherTier(AccountLimits previousLimits, String newTier) {
        if (previousLimits == null) return true;
        
        String previousTier = previousLimits.getAccountTier();
        if (previousTier == null) return true;
        
        // Define tier hierarchy
        Map<String, Integer> tierRank = Map.of(
            "BASIC", 1,
            "STANDARD", 2,
            "PREMIUM", 3,
            "VIP", 4,
            "BUSINESS", 5
        );
        
        return tierRank.getOrDefault(newTier, 0) > tierRank.getOrDefault(previousTier, 0);
    }

    private void processAccountLimitsUpdateFallback(
            String userId,
            Map<String, Object> limitsData,
            String reason,
            String tier,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for account limits update - userId: {}, reason: {}, correlationId: {}, error: {}", 
            userId, reason, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("limits", limitsData);
        fallbackEvent.put("reason", reason);
        fallbackEvent.put("tier", tier);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("account-limits-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Account limits updated message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("account-limits-processing-failures", dltEvent);
            
            notificationService.sendCriticalOperationalAlert(
                "Account Limits Processing Failed",
                String.format("CRITICAL: Failed to process account limits update after max retries. Error: %s", exceptionMessage),
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
        
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}