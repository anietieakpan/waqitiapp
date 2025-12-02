package com.waqiti.kyc.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.model.*;
import com.waqiti.kyc.service.*;
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
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DigitalIdentityVerificationEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(DigitalIdentityVerificationEventConsumer.class);
    
    private static final String TOPIC = "waqiti.kyc.digital-identity-verification";
    private static final String CONSUMER_GROUP = "digital-identity-verification-consumer-group";
    private static final String DLQ_TOPIC = "waqiti.kyc.digital-identity-verification.dlq";
    
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final DlqService dlqService;
    private final MeterRegistry meterRegistry;
    private final DigitalIdentityService identityService;
    private final BiometricVerificationService biometricService;
    private final DocumentVerificationService documentService;
    private final LivenessDetectionService livenessService;
    
    private CircuitBreaker circuitBreaker;
    private Retry retryConfig;
    private Counter messagesProcessedCounter;
    private Counter identityVerificationsCounter;
    private Timer messageProcessingTimer;
    
    private final ConcurrentHashMap<String, DigitalIdentityVerification> verifications = new ConcurrentHashMap<>();
    
    public DigitalIdentityVerificationEventConsumer(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            DlqService dlqService,
            MeterRegistry meterRegistry,
            DigitalIdentityService identityService,
            BiometricVerificationService biometricService,
            DocumentVerificationService documentService,
            LivenessDetectionService livenessService) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.dlqService = dlqService;
        this.meterRegistry = meterRegistry;
        this.identityService = identityService;
        this.biometricService = biometricService;
        this.documentService = documentService;
        this.livenessService = livenessService;
    }
    
    @PostConstruct
    public void init() {
        initializeCircuitBreaker();
        initializeRetry();
        initializeMetrics();
        logger.info("DigitalIdentityVerificationEventConsumer initialized successfully");
    }
    
    private void initializeCircuitBreaker() {
        circuitBreaker = CircuitBreaker.of("digital-identity-verification-circuit-breaker",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .minimumNumberOfCalls(10)
                .build());
    }
    
    private void initializeRetry() {
        retryConfig = Retry.of("digital-identity-verification-retry",
            RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1000))
                .exponentialBackoffMultiplier(2.0)
                .build());
    }
    
    private void initializeMetrics() {
        messagesProcessedCounter = Counter.builder("digital_identity_verification_messages_processed_total")
            .register(meterRegistry);
        identityVerificationsCounter = Counter.builder("digital_identity_verifications_total")
            .register(meterRegistry);
        messageProcessingTimer = Timer.builder("digital_identity_verification_message_processing_duration")
            .register(meterRegistry);
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processDigitalIdentityVerification(@Payload String message,
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
            
            boolean processed = circuitBreaker.executeSupplier(() ->
                retryConfig.executeSupplier(() -> {
                    return executeProcessingStep(eventType, messageNode, requestId);
                })
            );
            
            if (processed) {
                messagesProcessedCounter.increment();
                acknowledgment.acknowledge();
                logger.info("Successfully processed digital identity verification message: eventType={}", eventType);
            } else {
                throw new RuntimeException("Failed to process message: " + eventType);
            }
            
        } catch (Exception e) {
            logger.error("Error processing digital identity verification message", e);
            dlqService.sendToDlq(DLQ_TOPIC, message, e.getMessage(), requestId);
            acknowledgment.acknowledge();
        } finally {
            sample.stop(messageProcessingTimer);
        }
    }
    
    private boolean executeProcessingStep(String eventType, JsonNode messageNode, String requestId) {
        switch (eventType) {
            case "IDENTITY_VERIFICATION_INITIATED":
                return processIdentityVerificationInitiated(messageNode, requestId);
            case "BIOMETRIC_CAPTURE_COMPLETED":
                return processBiometricCaptureCompleted(messageNode, requestId);
            case "DOCUMENT_VERIFICATION_REQUEST":
                return processDocumentVerificationRequest(messageNode, requestId);
            case "LIVENESS_CHECK_PERFORMED":
                return processLivenessCheckPerformed(messageNode, requestId);
            case "IDENTITY_VERIFICATION_COMPLETED":
                return processIdentityVerificationCompleted(messageNode, requestId);
            case "VERIFICATION_CHALLENGE_ISSUED":
                return processVerificationChallengeIssued(messageNode, requestId);
            case "IDENTITY_FRAUD_DETECTED":
                return processIdentityFraudDetected(messageNode, requestId);
            default:
                logger.warn("Unknown event type: {}", eventType);
                return false;
        }
    }
    
    private boolean processIdentityVerificationInitiated(JsonNode messageNode, String requestId) {
        try {
            String customerId = messageNode.path("customerId").asText();
            String verificationType = messageNode.path("verificationType").asText();
            String verificationLevel = messageNode.path("verificationLevel").asText();
            JsonNode requiredDocuments = messageNode.path("requiredDocuments");
            
            DigitalIdentityVerification verification = DigitalIdentityVerification.builder()
                .id(UUID.randomUUID().toString())
                .customerId(customerId)
                .verificationType(verificationType)
                .verificationLevel(verificationLevel)
                .requiredDocuments(extractStringList(requiredDocuments))
                .status("INITIATED")
                .initiatedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            verifications.put(verification.getId(), verification);
            identityService.initiateVerification(verification);
            identityVerificationsCounter.increment();
            
            logger.info("Initiated digital identity verification: id={}, customerId={}, type={}", 
                verification.getId(), customerId, verificationType);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error initiating identity verification", e);
            return false;
        }
    }
    
    private boolean processBiometricCaptureCompleted(JsonNode messageNode, String requestId) {
        try {
            String verificationId = messageNode.path("verificationId").asText();
            String biometricType = messageNode.path("biometricType").asText();
            String biometricData = messageNode.path("biometricData").asText();
            String captureQuality = messageNode.path("captureQuality").asText();
            
            BiometricCapture capture = BiometricCapture.builder()
                .id(UUID.randomUUID().toString())
                .verificationId(verificationId)
                .biometricType(biometricType)
                .biometricData(biometricData)
                .captureQuality(captureQuality)
                .capturedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            BiometricVerificationResult result = biometricService.verifyBiometric(capture);
            
            DigitalIdentityVerification verification = verifications.get(verificationId);
            if (verification != null) {
                verification.setBiometricVerified(result.isVerified());
                verification.setBiometricScore(result.getConfidenceScore());
                identityService.updateVerification(verification);
            }
            
            logger.info("Processed biometric capture: verificationId={}, type={}, verified={}", 
                verificationId, biometricType, result.isVerified());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing biometric capture", e);
            return false;
        }
    }
    
    private boolean processDocumentVerificationRequest(JsonNode messageNode, String requestId) {
        try {
            String verificationId = messageNode.path("verificationId").asText();
            String documentType = messageNode.path("documentType").asText();
            String documentImageData = messageNode.path("documentImageData").asText();
            String issuerCountry = messageNode.path("issuerCountry").asText();
            
            DocumentVerificationRequest docRequest = DocumentVerificationRequest.builder()
                .id(UUID.randomUUID().toString())
                .verificationId(verificationId)
                .documentType(documentType)
                .documentImageData(documentImageData)
                .issuerCountry(issuerCountry)
                .submittedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            DocumentVerificationResult result = documentService.verifyDocument(docRequest);
            
            DigitalIdentityVerification verification = verifications.get(verificationId);
            if (verification != null) {
                verification.setDocumentVerified(result.isVerified());
                verification.setDocumentScore(result.getConfidenceScore());
                verification.setExtractedData(result.getExtractedData());
                identityService.updateVerification(verification);
            }
            
            logger.info("Processed document verification: verificationId={}, type={}, verified={}", 
                verificationId, documentType, result.isVerified());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing document verification", e);
            return false;
        }
    }
    
    private boolean processLivenessCheckPerformed(JsonNode messageNode, String requestId) {
        try {
            String verificationId = messageNode.path("verificationId").asText();
            String livenessType = messageNode.path("livenessType").asText();
            String videoData = messageNode.path("videoData").asText();
            JsonNode challengeResponses = messageNode.path("challengeResponses");
            
            LivenessCheck livenessCheck = LivenessCheck.builder()
                .id(UUID.randomUUID().toString())
                .verificationId(verificationId)
                .livenessType(livenessType)
                .videoData(videoData)
                .challengeResponses(extractStringList(challengeResponses))
                .performedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            LivenessDetectionResult result = livenessService.detectLiveness(livenessCheck);
            
            DigitalIdentityVerification verification = verifications.get(verificationId);
            if (verification != null) {
                verification.setLivenessVerified(result.isLive());
                verification.setLivenessScore(result.getConfidenceScore());
                identityService.updateVerification(verification);
            }
            
            logger.info("Processed liveness check: verificationId={}, type={}, live={}", 
                verificationId, livenessType, result.isLive());
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing liveness check", e);
            return false;
        }
    }
    
    private boolean processIdentityVerificationCompleted(JsonNode messageNode, String requestId) {
        try {
            String verificationId = messageNode.path("verificationId").asText();
            String finalStatus = messageNode.path("finalStatus").asText();
            String overallScore = messageNode.path("overallScore").asText();
            
            DigitalIdentityVerification verification = verifications.get(verificationId);
            if (verification != null) {
                verification.setStatus(finalStatus);
                verification.setOverallScore(Double.parseDouble(overallScore));
                verification.setCompletedAt(LocalDateTime.now());
                
                identityService.completeVerification(verification);
                
                if ("VERIFIED".equals(finalStatus)) {
                    identityService.activateDigitalIdentity(verification.getCustomerId());
                }
            }
            
            logger.info("Completed identity verification: verificationId={}, status={}, score={}", 
                verificationId, finalStatus, overallScore);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error completing identity verification", e);
            return false;
        }
    }
    
    private boolean processVerificationChallengeIssued(JsonNode messageNode, String requestId) {
        try {
            String verificationId = messageNode.path("verificationId").asText();
            String challengeType = messageNode.path("challengeType").asText();
            String challengeData = messageNode.path("challengeData").asText();
            
            VerificationChallenge challenge = VerificationChallenge.builder()
                .id(UUID.randomUUID().toString())
                .verificationId(verificationId)
                .challengeType(challengeType)
                .challengeData(challengeData)
                .status("ISSUED")
                .issuedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            identityService.issueChallenge(challenge);
            
            logger.info("Issued verification challenge: verificationId={}, type={}", 
                verificationId, challengeType);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error issuing verification challenge", e);
            return false;
        }
    }
    
    private boolean processIdentityFraudDetected(JsonNode messageNode, String requestId) {
        try {
            String verificationId = messageNode.path("verificationId").asText();
            String fraudType = messageNode.path("fraudType").asText();
            String fraudIndicators = messageNode.path("fraudIndicators").asText();
            String riskScore = messageNode.path("riskScore").asText();
            
            IdentityFraudAlert alert = IdentityFraudAlert.builder()
                .id(UUID.randomUUID().toString())
                .verificationId(verificationId)
                .fraudType(fraudType)
                .fraudIndicators(fraudIndicators)
                .riskScore(Double.parseDouble(riskScore))
                .detectedAt(LocalDateTime.now())
                .requestId(requestId)
                .build();
            
            identityService.handleFraudAlert(alert);
            
            DigitalIdentityVerification verification = verifications.get(verificationId);
            if (verification != null) {
                verification.setStatus("FRAUD_DETECTED");
                verification.setFraudAlerted(true);
                identityService.updateVerification(verification);
            }
            
            logger.warn("Detected identity fraud: verificationId={}, type={}, riskScore={}", 
                verificationId, fraudType, riskScore);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing identity fraud detection", e);
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