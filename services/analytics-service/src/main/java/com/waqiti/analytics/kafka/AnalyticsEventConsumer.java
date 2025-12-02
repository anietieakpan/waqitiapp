package com.waqiti.analytics.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.analytics.service.AnalyticsService;
import com.waqiti.analytics.service.DataAggregationService;
import com.waqiti.analytics.service.ReportingService;
import com.waqiti.analytics.service.MetricsCalculationService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {
    
    private final AnalyticsService analyticsService;
    private final DataAggregationService dataAggregationService;
    private final ReportingService reportingService;
    private final MetricsCalculationService metricsCalculationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler universalDLQHandler;
    
    @KafkaListener(
        topics = {"analytics-events", "user-activity"},
        groupId = "analytics-service-event-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleAnalyticsEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("ANALYTICS EVENT: Processing analytics event - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID eventId = null;
        UUID userId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            eventId = UUID.fromString((String) event.get("eventId"));
            userId = event.containsKey("userId") ? UUID.fromString((String) event.get("userId")) : null;
            eventType = (String) event.get("eventType");
            String eventCategory = (String) event.get("eventCategory");
            @SuppressWarnings("unchecked")
            Map<String, Object> eventProperties = (Map<String, Object>) event.getOrDefault("eventProperties", Map.of());
            LocalDateTime eventTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String sessionId = (String) event.get("sessionId");
            String deviceType = (String) event.get("deviceType");
            String platform = (String) event.get("platform");
            String appVersion = (String) event.get("appVersion");
            String country = (String) event.get("country");
            String city = (String) event.get("city");
            
            log.info("Analytics event - EventId: {}, UserId: {}, Type: {}, Category: {}, Device: {}, Platform: {}", 
                    eventId, userId, eventType, eventCategory, deviceType, platform);
            
            validateAnalyticsEvent(eventId, eventType, eventCategory, eventTimestamp);
            
            processEventByType(eventId, userId, eventType, eventCategory, eventProperties, 
                    eventTimestamp, sessionId, deviceType, platform, appVersion, country, city);
            
            aggregateEventData(eventType, eventCategory, eventProperties, userId, eventTimestamp, 
                    deviceType, platform, country);
            
            updateRealTimeMetrics(eventType, eventCategory, userId, eventTimestamp);
            
            triggerReportGeneration(eventType, eventCategory, eventTimestamp);
            
            calculateUserMetrics(userId, eventType, eventCategory, eventTimestamp);
            
            auditAnalyticsEvent(eventId, userId, eventType, eventCategory, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("Analytics event processed - EventId: {}, Type: {}, ProcessingTime: {}ms", 
                    eventId, eventType, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Analytics event processing failed - EventId: {}, UserId: {}, Type: {}, Error: {}",
                    eventId, userId, eventType, e.getMessage(), e);

            if (eventId != null) {
                handleEventFailure(eventId, userId, eventType, e);
            }

            // Send to DLQ via UniversalDLQHandler
            try {
                org.apache.kafka.clients.consumer.ConsumerRecord<String, String> consumerRecord =
                    new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                        topic, partition, offset, null, eventJson
                    );
                universalDLQHandler.handleFailedMessage(consumerRecord, e);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ: {}", dlqException.getMessage());
            }

            throw new RuntimeException("Analytics event processing failed", e);
        }
    }
    
    private void validateAnalyticsEvent(UUID eventId, String eventType, String eventCategory,
                                       LocalDateTime eventTimestamp) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (eventCategory == null || eventCategory.trim().isEmpty()) {
            throw new IllegalArgumentException("Event category is required");
        }
        
        if (eventTimestamp == null) {
            throw new IllegalArgumentException("Event timestamp is required");
        }
        
        log.debug("Analytics event validation passed - EventId: {}", eventId);
    }
    
    private void processEventByType(UUID eventId, UUID userId, String eventType, String eventCategory,
                                   Map<String, Object> eventProperties, LocalDateTime eventTimestamp,
                                   String sessionId, String deviceType, String platform,
                                   String appVersion, String country, String city) {
        try {
            switch (eventType) {
                case "PAGE_VIEW" -> processPageViewEvent(eventId, userId, eventProperties, 
                        eventTimestamp, sessionId, deviceType, platform);
                
                case "USER_LOGIN" -> processUserLoginEvent(eventId, userId, eventTimestamp, 
                        deviceType, platform, country, city);
                
                case "TRANSACTION_COMPLETED" -> processTransactionEvent(eventId, userId, 
                        eventProperties, eventTimestamp);
                
                case "FEATURE_USED" -> processFeatureUsageEvent(eventId, userId, eventProperties, 
                        eventTimestamp, appVersion);
                
                case "ERROR_OCCURRED" -> processErrorEvent(eventId, userId, eventProperties, 
                        eventTimestamp, appVersion, platform);
                
                case "CONVERSION" -> processConversionEvent(eventId, userId, eventProperties, 
                        eventTimestamp);
                
                case "USER_REGISTRATION" -> processRegistrationEvent(eventId, userId, eventTimestamp, 
                        deviceType, platform, country);
                
                default -> {
                    log.warn("Unknown analytics event type: {}", eventType);
                    processGenericEvent(eventId, userId, eventType);
                }
            }
            
            log.debug("Event type processing completed - EventId: {}, Type: {}", eventId, eventType);
            
        } catch (Exception e) {
            log.error("Failed to process event by type - EventId: {}, Type: {}", eventId, eventType, e);
            throw new RuntimeException("Event type processing failed", e);
        }
    }
    
    private void processPageViewEvent(UUID eventId, UUID userId, Map<String, Object> eventProperties,
                                     LocalDateTime eventTimestamp, String sessionId, String deviceType,
                                     String platform) {
        log.info("Processing PAGE VIEW event - EventId: {}, UserId: {}, Page: {}, Device: {}", 
                eventId, userId, eventProperties.get("page"), deviceType);
        
        analyticsService.processPageView(eventId, userId, eventProperties, eventTimestamp, sessionId, 
                deviceType, platform);
    }
    
    private void processUserLoginEvent(UUID eventId, UUID userId, LocalDateTime eventTimestamp,
                                      String deviceType, String platform, String country, String city) {
        log.info("Processing USER LOGIN event - EventId: {}, UserId: {}, Device: {}, Location: {}, {}", 
                eventId, userId, deviceType, city, country);
        
        analyticsService.processUserLogin(eventId, userId, eventTimestamp, deviceType, platform, 
                country, city);
    }
    
    private void processTransactionEvent(UUID eventId, UUID userId, Map<String, Object> eventProperties,
                                        LocalDateTime eventTimestamp) {
        BigDecimal amount = eventProperties.containsKey("amount") ? 
                new BigDecimal(eventProperties.get("amount").toString()) : BigDecimal.ZERO;
        String currency = (String) eventProperties.get("currency");
        
        log.info("Processing TRANSACTION COMPLETED event - EventId: {}, UserId: {}, Amount: {} {}", 
                eventId, userId, amount, currency);
        
        analyticsService.processTransaction(eventId, userId, eventProperties, eventTimestamp);
    }
    
    private void processFeatureUsageEvent(UUID eventId, UUID userId, Map<String, Object> eventProperties,
                                         LocalDateTime eventTimestamp, String appVersion) {
        String featureName = (String) eventProperties.get("featureName");
        
        log.info("Processing FEATURE USED event - EventId: {}, UserId: {}, Feature: {}, Version: {}", 
                eventId, userId, featureName, appVersion);
        
        analyticsService.processFeatureUsage(eventId, userId, eventProperties, eventTimestamp, 
                appVersion);
    }
    
    private void processErrorEvent(UUID eventId, UUID userId, Map<String, Object> eventProperties,
                                  LocalDateTime eventTimestamp, String appVersion, String platform) {
        String errorCode = (String) eventProperties.get("errorCode");
        String errorMessage = (String) eventProperties.get("errorMessage");
        
        log.error("Processing ERROR OCCURRED event - EventId: {}, UserId: {}, Error: {} - {}, Version: {}", 
                eventId, userId, errorCode, errorMessage, appVersion);
        
        analyticsService.processError(eventId, userId, eventProperties, eventTimestamp, appVersion, 
                platform);
    }
    
    private void processConversionEvent(UUID eventId, UUID userId, Map<String, Object> eventProperties,
                                       LocalDateTime eventTimestamp) {
        String conversionType = (String) eventProperties.get("conversionType");
        BigDecimal value = eventProperties.containsKey("value") ? 
                new BigDecimal(eventProperties.get("value").toString()) : BigDecimal.ZERO;
        
        log.info("Processing CONVERSION event - EventId: {}, UserId: {}, Type: {}, Value: {}", 
                eventId, userId, conversionType, value);
        
        analyticsService.processConversion(eventId, userId, eventProperties, eventTimestamp);
    }
    
    private void processRegistrationEvent(UUID eventId, UUID userId, LocalDateTime eventTimestamp,
                                         String deviceType, String platform, String country) {
        log.info("Processing USER REGISTRATION event - EventId: {}, UserId: {}, Device: {}, Platform: {}, Country: {}", 
                eventId, userId, deviceType, platform, country);
        
        analyticsService.processRegistration(eventId, userId, eventTimestamp, deviceType, platform, 
                country);
    }
    
    private void processGenericEvent(UUID eventId, UUID userId, String eventType) {
        log.info("Processing generic analytics event - EventId: {}, Type: {}", eventId, eventType);
        
        analyticsService.processGeneric(eventId, userId, eventType);
    }
    
    private void aggregateEventData(String eventType, String eventCategory, 
                                   Map<String, Object> eventProperties, UUID userId,
                                   LocalDateTime eventTimestamp, String deviceType, String platform,
                                   String country) {
        try {
            dataAggregationService.aggregateEvent(eventType, eventCategory, eventProperties, userId, 
                    eventTimestamp, deviceType, platform, country);
            
            log.debug("Event data aggregated - Type: {}, Category: {}", eventType, eventCategory);
            
        } catch (Exception e) {
            log.error("Failed to aggregate event data - Type: {}", eventType, e);
        }
    }
    
    private void updateRealTimeMetrics(String eventType, String eventCategory, UUID userId,
                                      LocalDateTime eventTimestamp) {
        try {
            metricsCalculationService.updateRealTimeMetrics(eventType, eventCategory, userId, 
                    eventTimestamp);
            
            log.debug("Real-time metrics updated - Type: {}, Category: {}", eventType, eventCategory);
            
        } catch (Exception e) {
            log.error("Failed to update real-time metrics - Type: {}", eventType, e);
        }
    }
    
    private void triggerReportGeneration(String eventType, String eventCategory,
                                        LocalDateTime eventTimestamp) {
        try {
            reportingService.triggerReportGeneration(eventType, eventCategory, eventTimestamp);
            
            log.debug("Report generation triggered - Type: {}, Category: {}", eventType, eventCategory);
            
        } catch (Exception e) {
            log.error("Failed to trigger report generation - Type: {}", eventType, e);
        }
    }
    
    private void calculateUserMetrics(UUID userId, String eventType, String eventCategory,
                                     LocalDateTime eventTimestamp) {
        try {
            if (userId != null) {
                metricsCalculationService.calculateUserMetrics(userId, eventType, eventCategory, 
                        eventTimestamp);
                
                log.debug("User metrics calculated - UserId: {}, Type: {}", userId, eventType);
            }
            
        } catch (Exception e) {
            log.error("Failed to calculate user metrics - UserId: {}", userId, e);
        }
    }
    
    private void auditAnalyticsEvent(UUID eventId, UUID userId, String eventType, String eventCategory,
                                    LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditEvent(
                    "ANALYTICS_EVENT_PROCESSED",
                    userId != null ? userId.toString() : "ANONYMOUS",
                    String.format("Analytics event processed - Type: %s, Category: %s", eventType, eventCategory),
                    Map.of(
                            "eventId", eventId.toString(),
                            "userId", userId != null ? userId.toString() : "ANONYMOUS",
                            "eventType", eventType,
                            "eventCategory", eventCategory,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit analytics event - EventId: {}", eventId, e);
        }
    }
    
    private void handleEventFailure(UUID eventId, UUID userId, String eventType, Exception error) {
        try {
            analyticsService.handleEventFailure(eventId, userId, eventType, error.getMessage());
            
            auditService.auditEvent(
                    "ANALYTICS_EVENT_PROCESSING_FAILED",
                    userId != null ? userId.toString() : "SYSTEM",
                    "Failed to process analytics event: " + error.getMessage(),
                    Map.of(
                            "eventId", eventId.toString(),
                            "userId", userId != null ? userId.toString() : "ANONYMOUS",
                            "eventType", eventType != null ? eventType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle event failure - EventId: {}", eventId, e);
        }
    }
    
    @KafkaListener(
        topics = {"analytics-events.DLQ", "user-activity.DLQ"},
        groupId = "analytics-service-event-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Analytics event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID eventId = event.containsKey("eventId") ? 
                    UUID.fromString((String) event.get("eventId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String eventType = (String) event.get("eventType");
            
            log.error("DLQ: Analytics event failed permanently - EventId: {}, UserId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    eventId, userId, eventType);
            
            if (eventId != null) {
                analyticsService.markForManualReview(eventId, userId, eventType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse analytics event DLQ: {}", eventJson, e);
        }
    }
}