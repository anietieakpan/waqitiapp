package com.waqiti.monitoring.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.monitoring.service.ProviderHealthTrackingService;
import com.waqiti.monitoring.service.ProviderAvailabilityService;
import com.waqiti.monitoring.service.AlertNotificationService;
import com.waqiti.monitoring.service.ProviderRoutingOptimizationService;
import com.waqiti.monitoring.domain.ProviderHealthEvent;
import com.waqiti.monitoring.repository.ProviderHealthEventRepository;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class PaymentProviderStatusChangesConsumer {

    private final ProviderHealthTrackingService healthTrackingService;
    private final ProviderAvailabilityService availabilityService;
    private final AlertNotificationService alertService;
    private final ProviderRoutingOptimizationService routingService;
    private final ProviderHealthEventRepository eventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter providerDownCounter;
    private final Counter providerRecoveredCounter;
    private final Counter criticalProviderDownCounter;
    private final Timer eventProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public PaymentProviderStatusChangesConsumer(
            ProviderHealthTrackingService healthTrackingService,
            ProviderAvailabilityService availabilityService,
            AlertNotificationService alertService,
            ProviderRoutingOptimizationService routingService,
            ProviderHealthEventRepository eventRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.healthTrackingService = healthTrackingService;
        this.availabilityService = availabilityService;
        this.alertService = alertService;
        this.routingService = routingService;
        this.eventRepository = eventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.providerDownCounter = Counter.builder("payment.provider.down.events")
            .description("Count of provider down events")
            .register(meterRegistry);
        
        this.providerRecoveredCounter = Counter.builder("payment.provider.recovered.events")
            .description("Count of provider recovered events")
            .register(meterRegistry);
        
        this.criticalProviderDownCounter = Counter.builder("payment.provider.critical.down.events")
            .description("Count of critical provider down events")
            .register(meterRegistry);
        
        this.eventProcessingTimer = Timer.builder("payment.provider.status.processing.duration")
            .description("Time taken to process provider status change events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "payment-provider-status-changes",
        groupId = "monitoring-provider-status-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "payment-provider-status-changes-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleProviderStatusChange(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received payment provider status change event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String provider = (String) eventData.get("provider");
            Boolean isHealthy = (Boolean) eventData.get("healthy");
            String eventType = (String) eventData.get("event_type");
            String timestamp = (String) eventData.get("timestamp");
            
            String correlationId = String.format("provider-status-%s-%s-%d", 
                provider, eventType, System.currentTimeMillis());
            
            log.info("Processing provider status change: provider={}, isHealthy={}, eventType={}, correlationId={}", 
                provider, isHealthy, eventType, correlationId);
            
            processProviderStatusChange(provider, isHealthy, eventType, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(eventProcessingTimer);
            
            log.info("Successfully processed provider status change event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process provider status change event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Provider status change processing failed", e);
        }
    }

    @CircuitBreaker(name = "monitoring", fallbackMethod = "processProviderStatusChangeFallback")
    @Retry(name = "monitoring")
    private void processProviderStatusChange(
            String provider,
            Boolean isHealthy,
            String eventType,
            Map<String, Object> eventData,
            String correlationId) {
        
        ProviderHealthEvent healthEvent = ProviderHealthEvent.builder()
            .provider(provider)
            .isHealthy(isHealthy)
            .eventType(eventType)
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        eventRepository.save(healthEvent);
        
        healthTrackingService.recordProviderHealthChange(
            provider,
            isHealthy,
            eventType,
            correlationId
        );
        
        availabilityService.updateProviderAvailability(
            provider,
            isHealthy,
            eventData,
            correlationId
        );
        
        if ("PROVIDER_DOWN".equals(eventType) || !isHealthy) {
            handleProviderDown(provider, eventData, correlationId);
            providerDownCounter.increment();
            
            if (isCriticalProvider(provider)) {
                criticalProviderDownCounter.increment();
                handleCriticalProviderDown(provider, eventData, correlationId);
            }
            
        } else if ("PROVIDER_RECOVERED".equals(eventType) || isHealthy) {
            handleProviderRecovered(provider, eventData, correlationId);
            providerRecoveredCounter.increment();
        }
        
        routingService.optimizeProviderRouting(
            provider,
            isHealthy,
            eventData,
            correlationId
        );
        
        publishProviderStatusAlert(provider, isHealthy, eventType, correlationId);
    }

    private void handleProviderDown(String provider, Map<String, Object> eventData, String correlationId) {
        log.error("ALERT: Payment provider {} is DOWN - correlationId: {}", provider, correlationId);
        
        healthTrackingService.trackProviderDowntime(provider, LocalDateTime.now(), correlationId);
        
        alertService.sendCriticalAlert(
            "Payment Provider Down",
            String.format("Payment provider %s is currently unavailable. Payments may be affected.", provider),
            Map.of(
                "provider", provider,
                "severity", "CRITICAL",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        routingService.disableProviderRouting(provider, correlationId);
        
        availabilityService.calculateProviderAvailabilitySLA(provider, correlationId);
    }

    private void handleProviderRecovered(String provider, Map<String, Object> eventData, String correlationId) {
        log.info("✅ Payment provider {} has RECOVERED - correlationId: {}", provider, correlationId);
        
        Duration downtime = healthTrackingService.recordProviderRecovery(provider, LocalDateTime.now(), correlationId);
        
        alertService.sendAlert(
            "Payment Provider Recovered",
            String.format("Payment provider %s is now available again. Downtime: %s", 
                provider, formatDuration(downtime)),
            Map.of(
                "provider", provider,
                "severity", "INFO",
                "downtime", downtime.toString(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        routingService.enableProviderRouting(provider, correlationId);
        
        availabilityService.updateProviderSLA(provider, downtime, correlationId);
    }

    private void handleCriticalProviderDown(String provider, Map<String, Object> eventData, String correlationId) {
        log.error("CRITICAL ALERT: Critical payment provider {} is DOWN - correlationId: {}", provider, correlationId);
        
        alertService.sendCriticalAlert(
            "CRITICAL: Payment Provider Down",
            String.format("CRITICAL payment provider %s is DOWN. Immediate action required!", provider),
            Map.of(
                "provider", provider,
                "severity", "CRITICAL",
                "isCriticalProvider", true,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "requiresImmediateAction", true
            )
        );
        
        healthTrackingService.escalateCriticalProviderDown(provider, eventData, correlationId);
        
        routingService.activateFailoverStrategy(provider, correlationId);
        
        kafkaTemplate.send("critical-provider-down-alerts", Map.of(
            "provider", provider,
            "eventType", "CRITICAL_PROVIDER_DOWN",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString(),
            "actionRequired", true
        ));
    }

    private void publishProviderStatusAlert(String provider, Boolean isHealthy, String eventType, String correlationId) {
        try {
            Map<String, Object> alert = Map.of(
                "provider", provider,
                "isHealthy", isHealthy,
                "eventType", eventType,
                "alertType", "PROVIDER_STATUS_CHANGE",
                "severity", isHealthy ? "INFO" : "CRITICAL",
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            );
            
            kafkaTemplate.send("provider-health-alerts", alert);
            
        } catch (Exception e) {
            log.error("Failed to publish provider status alert for {}: {}", provider, e.getMessage(), e);
        }
    }

    private boolean isCriticalProvider(String provider) {
        return "stripe".equalsIgnoreCase(provider) || 
               "paypal".equalsIgnoreCase(provider) || 
               "adyen".equalsIgnoreCase(provider);
    }

    private String formatDuration(Duration duration) {
        if (duration == null) return "unknown";
        
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private void processProviderStatusChangeFallback(
            String provider,
            Boolean isHealthy,
            String eventType,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for provider status change - provider: {}, correlationId: {}, error: {}", 
            provider, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("provider", provider);
        fallbackEvent.put("isHealthy", isHealthy);
        fallbackEvent.put("eventType", eventType);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("provider-status-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("Message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
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
            
            kafkaTemplate.send("provider-status-processing-failures", dltEvent);
            
            alertService.sendCriticalAlert(
                "Provider Status Change Processing Failed",
                String.format("Failed to process provider status change after max retries. Error: %s", exceptionMessage),
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
