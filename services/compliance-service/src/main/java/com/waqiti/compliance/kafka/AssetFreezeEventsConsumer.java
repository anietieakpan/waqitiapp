package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.domain.AssetFreeze;
import com.waqiti.compliance.domain.FreezeReason;
import com.waqiti.compliance.domain.FreezeStatus;
import com.waqiti.compliance.repository.AssetFreezeRepository;
import com.waqiti.compliance.service.AssetFreezeService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.service.LegalComplianceService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AssetFreezeEventsConsumer {

    private final AssetFreezeRepository freezeRepository;
    private final AssetFreezeService freezeService;
    private final RegulatoryReportingService regulatoryService;
    private final ComplianceNotificationService notificationService;
    private final LegalComplianceService legalService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter assetFreezeCounter;
    private final Counter sanctionsFreezeCounter;
    private final Counter legalFreezeCounter;
    private final Timer freezeProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AssetFreezeEventsConsumer(
            AssetFreezeRepository freezeRepository,
            AssetFreezeService freezeService,
            RegulatoryReportingService regulatoryService,
            ComplianceNotificationService notificationService,
            LegalComplianceService legalService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.freezeRepository = freezeRepository;
        this.freezeService = freezeService;
        this.regulatoryService = regulatoryService;
        this.notificationService = notificationService;
        this.legalService = legalService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.assetFreezeCounter = Counter.builder("asset.freeze.events")
            .description("Count of asset freeze events")
            .register(meterRegistry);
        
        this.sanctionsFreezeCounter = Counter.builder("asset.freeze.sanctions.events")
            .description("Count of sanctions-related asset freezes")
            .register(meterRegistry);
        
        this.legalFreezeCounter = Counter.builder("asset.freeze.legal.events")
            .description("Count of legal-related asset freezes")
            .register(meterRegistry);
        
        this.freezeProcessingTimer = Timer.builder("asset.freeze.processing.duration")
            .description("Time taken to process asset freeze events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "asset-freeze-events",
        groupId = "asset-freeze-events-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "asset-freeze-events-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAssetFreezeEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received asset freeze event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String userId = (String) eventData.get("userId");
            String reason = (String) eventData.get("reason");
            Object timestampObj = eventData.get("timestamp");
            String freezeId = (String) eventData.get("freezeId");
            String severity = (String) eventData.getOrDefault("severity", "HIGH");
            String description = (String) eventData.get("description");
            String legalOrder = (String) eventData.get("legalOrder");
            String requestingAuthority = (String) eventData.get("requestingAuthority");
            
            String correlationId = String.format("asset-freeze-%s-%s-%d", 
                userId, reason, System.currentTimeMillis());
            
            log.warn("CRITICAL: Processing asset freeze - userId: {}, reason: {}, correlationId: {}", 
                userId, reason, correlationId);
            
            assetFreezeCounter.increment();
            
            processAssetFreeze(userId, reason, freezeId, severity, description, 
                legalOrder, requestingAuthority, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(freezeProcessingTimer);
            
            log.info("Successfully processed asset freeze event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process asset freeze event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Asset freeze processing failed", e);
        }
    }

    @CircuitBreaker(name = "compliance", fallbackMethod = "processAssetFreezeFallback")
    @Retry(name = "compliance")
    private void processAssetFreeze(
            String userId,
            String reason,
            String freezeId,
            String severity,
            String description,
            String legalOrder,
            String requestingAuthority,
            Map<String, Object> eventData,
            String correlationId) {
        
        FreezeReason freezeReason = FreezeReason.valueOf(reason);
        FreezeStatus status = FreezeStatus.ACTIVE;
        
        AssetFreeze freeze = AssetFreeze.builder()
            .freezeId(freezeId)
            .userId(userId)
            .reason(freezeReason)
            .status(status)
            .severity(severity)
            .description(description)
            .legalOrder(legalOrder)
            .requestingAuthority(requestingAuthority)
            .frozenAt(LocalDateTime.now())
            .isActive(true)
            .correlationId(correlationId)
            .build();
        
        freezeRepository.save(freeze);
        
        freezeService.applyAssetFreeze(freeze, correlationId);
        
        switch (freezeReason) {
            case SANCTIONS_VIOLATION -> {
                sanctionsFreezeCounter.increment();
                handleSanctionsFreeze(freeze, correlationId);
            }
            case LEGAL_ORDER -> {
                legalFreezeCounter.increment();
                handleLegalFreeze(freeze, correlationId);
            }
            case COURT_ORDER -> {
                legalFreezeCounter.increment();
                handleCourtOrderFreeze(freeze, correlationId);
            }
            case REGULATORY_REQUEST -> {
                handleRegulatoryFreeze(freeze, correlationId);
            }
            case INVESTIGATION -> {
                handleInvestigationFreeze(freeze, correlationId);
            }
            default -> {
                log.warn("Unknown freeze reason: {}", reason);
            }
        }
        
        notificationService.sendAssetFreezeNotification(
            userId,
            freezeReason,
            description,
            correlationId
        );
        
        kafkaTemplate.send("asset-freeze-applied", Map.of(
            "freezeId", freezeId,
            "userId", userId,
            "reason", reason,
            "status", status.toString(),
            "severity", severity,
            "legalOrder", legalOrder,
            "requestingAuthority", requestingAuthority,
            "eventType", "ASSET_FREEZE_APPLIED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logComplianceEvent(
            "ASSET_FREEZE_APPLIED",
            freezeId,
            Map.of(
                "userId", userId,
                "reason", reason,
                "status", status.toString(),
                "severity", severity,
                "description", description,
                "legalOrder", legalOrder,
                "requestingAuthority", requestingAuthority,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.error("CRITICAL: Asset freeze applied - userId: {}, reason: {}, freezeId: {}, correlationId: {}", 
            userId, reason, freezeId, correlationId);
    }

    private void handleSanctionsFreeze(AssetFreeze freeze, String correlationId) {
        log.error("COMPLIANCE ALERT: Sanctions asset freeze applied - userId: {}, freezeId: {}, correlationId: {}", 
            freeze.getUserId(), freeze.getFreezeId(), correlationId);
        
        regulatoryService.reportSanctionsAssetFreeze(freeze, correlationId);
        
        kafkaTemplate.send("sanctions-asset-freeze-alerts", Map.of(
            "freezeId", freeze.getFreezeId(),
            "userId", freeze.getUserId(),
            "reason", freeze.getReason().toString(),
            "severity", "CRITICAL",
            "regulatoryNotificationRequired", true,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendRegulatoryAlert(
            "Sanctions Asset Freeze Applied",
            String.format("Assets frozen for user %s due to sanctions violation", freeze.getUserId()),
            Map.of(
                "freezeId", freeze.getFreezeId(),
                "userId", freeze.getUserId(),
                "reason", freeze.getReason().toString(),
                "correlationId", correlationId
            )
        );
    }

    private void handleLegalFreeze(AssetFreeze freeze, String correlationId) {
        log.error("LEGAL ALERT: Legal order asset freeze applied - userId: {}, freezeId: {}, correlationId: {}", 
            freeze.getUserId(), freeze.getFreezeId(), correlationId);
        
        legalService.processLegalAssetFreeze(freeze, correlationId);
        
        kafkaTemplate.send("legal-asset-freeze-alerts", Map.of(
            "freezeId", freeze.getFreezeId(),
            "userId", freeze.getUserId(),
            "reason", freeze.getReason().toString(),
            "legalOrder", freeze.getLegalOrder(),
            "requestingAuthority", freeze.getRequestingAuthority(),
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleCourtOrderFreeze(AssetFreeze freeze, String correlationId) {
        log.error("LEGAL ALERT: Court order asset freeze applied - userId: {}, freezeId: {}, correlationId: {}", 
            freeze.getUserId(), freeze.getFreezeId(), correlationId);
        
        legalService.processCourtOrderAssetFreeze(freeze, correlationId);
        
        kafkaTemplate.send("court-order-asset-freeze-alerts", Map.of(
            "freezeId", freeze.getFreezeId(),
            "userId", freeze.getUserId(),
            "reason", freeze.getReason().toString(),
            "legalOrder", freeze.getLegalOrder(),
            "requestingAuthority", freeze.getRequestingAuthority(),
            "severity", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleRegulatoryFreeze(AssetFreeze freeze, String correlationId) {
        log.warn("REGULATORY ALERT: Regulatory asset freeze applied - userId: {}, freezeId: {}, correlationId: {}", 
            freeze.getUserId(), freeze.getFreezeId(), correlationId);
        
        regulatoryService.processRegulatoryAssetFreeze(freeze, correlationId);
        
        kafkaTemplate.send("regulatory-asset-freeze-alerts", Map.of(
            "freezeId", freeze.getFreezeId(),
            "userId", freeze.getUserId(),
            "reason", freeze.getReason().toString(),
            "requestingAuthority", freeze.getRequestingAuthority(),
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleInvestigationFreeze(AssetFreeze freeze, String correlationId) {
        log.warn("INVESTIGATION ALERT: Investigation asset freeze applied - userId: {}, freezeId: {}, correlationId: {}", 
            freeze.getUserId(), freeze.getFreezeId(), correlationId);
        
        kafkaTemplate.send("investigation-asset-freeze-alerts", Map.of(
            "freezeId", freeze.getFreezeId(),
            "userId", freeze.getUserId(),
            "reason", freeze.getReason().toString(),
            "severity", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void processAssetFreezeFallback(
            String userId,
            String reason,
            String freezeId,
            String severity,
            String description,
            String legalOrder,
            String requestingAuthority,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for asset freeze - userId: {}, reason: {}, correlationId: {}, error: {}", 
            userId, reason, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("userId", userId);
        fallbackEvent.put("reason", reason);
        fallbackEvent.put("freezeId", freezeId);
        fallbackEvent.put("severity", severity);
        fallbackEvent.put("description", description);
        fallbackEvent.put("legalOrder", legalOrder);
        fallbackEvent.put("requestingAuthority", requestingAuthority);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("asset-freeze-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Asset freeze event sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("asset-freeze-processing-failures", dltEvent);
            
            notificationService.sendCriticalComplianceAlert(
                "Asset Freeze Processing Failed",
                String.format("CRITICAL: Failed to process asset freeze event after max retries. Error: %s", exceptionMessage),
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