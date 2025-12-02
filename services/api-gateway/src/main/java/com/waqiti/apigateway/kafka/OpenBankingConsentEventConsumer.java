package com.waqiti.apigateway.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.gateway.model.*;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.service.DlqService;
import com.waqiti.common.utils.MDCUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OpenBankingConsentEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(OpenBankingConsentEventConsumer.class);
    
    private static final String TOPIC = "waqiti.gateway.open-banking-consent";
    private static final String CONSUMER_GROUP = "open-banking-consent-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.gateway.open-banking-consent.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final MeterRegistry meterRegistry;
    private final OpenBankingConsentService consentService;
    private final OpenBankingComplianceService complianceService;
    
    private Counter messagesProcessedCounter;
    private Counter consentsCreatedCounter;
    private Counter consentsRevokedCounter;
    private Timer messageProcessingTimer;
    
    private final ConcurrentHashMap<String, OpenBankingConsent> consents = new ConcurrentHashMap<>();
    
    public OpenBankingConsentEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            MeterRegistry meterRegistry,
            OpenBankingConsentService consentService,
            OpenBankingComplianceService complianceService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.meterRegistry = meterRegistry;
        this.consentService = consentService;
        this.complianceService = complianceService;
        initializeMetrics();
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("open_banking_consent_messages_processed_total")
            .register(meterRegistry);
        consentsCreatedCounter = Counter.builder("open_banking_consents_created_total")
            .register(meterRegistry);
        consentsRevokedCounter = Counter.builder("open_banking_consents_revoked_total")
            .register(meterRegistry);
        messageProcessingTimer = Timer.builder("open_banking_consent_message_processing_duration")
            .register(meterRegistry);
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processOpenBankingConsent(@Payload String message,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset,
                                        Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String requestId = UUID.randomUUID().toString();
        
        try {
            MDCUtil.setRequestId(requestId);
            
            JsonNode messageNode = objectMapper.readTree(message);
            String eventType = messageNode.path("eventType").asText();
            
            boolean processed = executeProcessingStep(eventType, messageNode, requestId);
            
            if (processed) {
                messagesProcessedCounter.increment();
                acknowledgment.acknowledge();
                logger.info("Successfully processed open banking consent message: eventType={}", eventType);
            } else {
                throw new RuntimeException("Failed to process message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing open banking consent message", e);
            dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
            acknowledgment.acknowledge();
        } finally {
            sample.stop(messageProcessingTimer);
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "CONSENT_CREATION_REQUEST":
                return processConsentCreation(messageNode, requestId);
            case "CONSENT_AUTHORIZATION":
                return processConsentAuthorization(messageNode, requestId);
            case "CONSENT_REVOCATION":
                return processConsentRevocation(messageNode, requestId);
            case "CONSENT_RENEWAL":
                return processConsentRenewal(messageNode, requestId);
            case "CONSENT_STATUS_UPDATE":
                return processConsentStatusUpdate(messageNode, requestId);
            case "API_ACCESS_REQUEST":
                return processApiAccessRequest(messageNode, requestId);
            case "COMPLIANCE_CHECK":
                return processComplianceCheck(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processConsentCreation(JsonNode messageNode, String requestId) {
        try {
            String customerId = messageNode.path("customerId").asText();
            String tppId = messageNode.path("tppId").asText();
            String consentType = messageNode.path("consentType").asText();
            JsonNode permissions = messageNode.path("permissions");
            String expiryDate = messageNode.path("expiryDate").asText();
            
            OpenBankingConsent consent = OpenBankingConsent.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .tppId(tppId)
                .consentType(consentType)
                .permissions(extractStringList(permissions))
                .expiryDate(LocalDateTime.parse(expiryDate))
                .status("AWAITING_AUTHORIZATION")
                .createdAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            consents.put(consent.getId(), consent);
            consentService.createConsent(consent);
            consentsCreatedCounter.increment();
            
            logger.info("Created open banking consent: id={}, customerId={}, tppId={}", 
                consent.getId(), customerId, tppId);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error creating consent", e);
            return false;
        }
    }
    
    private boolean processConsentAuthorization(JsonNode messageNode, String requestId) {
        try {
            String consentId = messageNode.path("consentId").asText();
            String authorizationCode = messageNode.path("authorizationCode").asText();
            String authorizedBy = messageNode.path("authorizedBy").asText();
            
            OpenBankingConsent consent = consents.get(consentId);
            if (consent != null) {
                consent.setStatus("AUTHORIZED");
                consent.setAuthorizationCode(authorizationCode);
                consent.setAuthorizedBy(authorizedBy);
                consent.setAuthorizedAt(LocalDateTime.now());
                
                consentService.updateConsent(consent);
            }
            
            logger.info("Authorized open banking consent: id={}, authorizedBy={}", consentId, authorizedBy);
            return true;
            
        } catch (Exception e) {
            logger.error("Error authorizing consent", e);
            return false;
        }
    }
    
    private boolean processConsentRevocation(JsonNode messageNode, String requestId) {
        try {
            String consentId = messageNode.path("consentId").asText();
            String revocationReason = messageNode.path("revocationReason").asText();
            
            OpenBankingConsent consent = consents.get(consentId);
            if (consent != null) {
                consent.setStatus("REVOKED");
                consent.setRevocationReason(revocationReason);
                consent.setRevokedAt(LocalDateTime.now());
                
                consentService.updateConsent(consent);
                consents.remove(consentId);
                consentsRevokedCounter.increment();
            }
            
            logger.info("Revoked open banking consent: id={}, reason={}", consentId, revocationReason);
            return true;
            
        } catch (Exception e) {
            logger.error("Error revoking consent", e);
            return false;
        }
    }
    
    private boolean processConsentRenewal(JsonNode messageNode, String requestId) {
        try {
            String consentId = messageNode.path("consentId").asText();
            String newExpiryDate = messageNode.path("newExpiryDate").asText();
            
            OpenBankingConsent consent = consents.get(consentId);
            if (consent != null) {
                consent.setExpiryDate(LocalDateTime.parse(newExpiryDate));
                consent.setRenewedAt(LocalDateTime.now());
                
                consentService.updateConsent(consent);
            }
            
            logger.info("Renewed open banking consent: id={}, newExpiryDate={}", consentId, newExpiryDate);
            return true;
            
        } catch (Exception e) {
            logger.error("Error renewing consent", e);
            return false;
        }
    }
    
    private boolean processConsentStatusUpdate(JsonNode messageNode, String requestId) {
        try {
            String consentId = messageNode.path("consentId").asText();
            String newStatus = messageNode.path("newStatus").asText();
            String statusReason = messageNode.path("statusReason").asText();
            
            OpenBankingConsent consent = consents.get(consentId);
            if (consent != null) {
                consent.setStatus(newStatus);
                consent.setStatusReason(statusReason);
                consent.setStatusUpdatedAt(LocalDateTime.now());
                
                consentService.updateConsent(consent);
            }
            
            logger.info("Updated consent status: id={}, status={}, reason={}", consentId, newStatus, statusReason);
            return true;
            
        } catch (Exception e) {
            logger.error("Error updating consent status", e);
            return false;
        }
    }
    
    private boolean processApiAccessRequest(JsonNode messageNode, String requestId) {
        try {
            String consentId = messageNode.path("consentId").asText();
            String apiEndpoint = messageNode.path("apiEndpoint").asText();
            String accessMethod = messageNode.path("accessMethod").asText();
            
            OpenBankingConsent consent = consents.get(consentId);
            if (consent == null || !"AUTHORIZED".equals(consent.getStatus())) {
                logger.warn("Unauthorized API access request: consentId={}, endpoint={}", consentId, apiEndpoint);
                return false;
            }
            
            boolean accessGranted = consentService.validateApiAccess(consent, apiEndpoint, accessMethod);
            
            logger.info("API access request: consentId={}, endpoint={}, granted={}", 
                consentId, apiEndpoint, accessGranted);
            
            return accessGranted;
            
        } catch (Exception e) {
            logger.error("Error processing API access request", e);
            return false;
        }
    }
    
    private boolean processComplianceCheck(JsonNode messageNode, String requestId) {
        try {
            String consentId = messageNode.path("consentId").asText();
            JsonNode complianceRules = messageNode.path("complianceRules");
            
            OpenBankingConsent consent = consents.get(consentId);
            if (consent != null) {
                boolean compliant = complianceService.checkCompliance(consent, complianceRules);
                
                if (!compliant) {
                    consent.setStatus("COMPLIANCE_FAILED");
                    consentService.updateConsent(consent);
                }
                
                logger.info("Compliance check: consentId={}, compliant={}", consentId, compliant);
                return compliant;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Error processing compliance check", e);
            return false;
        }
    }
    
    private List<String> extractStringList(JsonNode arrayNode) {
        List<String> list = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> list.add(node.asText()));
        }
        return list;
    }
}