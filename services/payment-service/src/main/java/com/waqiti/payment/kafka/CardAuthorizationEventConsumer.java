package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.compliance.ComplianceEngine;
import com.waqiti.common.fraud.FraudDetectionEngine;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.security.AuditLogger;
import com.waqiti.payment.model.CardAuthorization;
import com.waqiti.payment.model.CardTransaction;
import com.waqiti.payment.service.CardProcessingService;
import com.waqiti.payment.service.PCIComplianceService;
import com.waqiti.payment.service.NotificationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Consumer #149: Card Authorization Event Consumer
 * Handles real-time card authorization with PCI DSS compliance, fraud detection, and risk scoring
 * Implements zero-tolerance 12-step processing pattern with SERIALIZABLE transaction isolation
 */
@Component
public class CardAuthorizationEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(CardAuthorizationEventConsumer.class);
    
    private static final String TOPIC = "card-authorization";
    private static final String DLQ_TOPIC = "card-authorization-dlq";
    private static final String CONSUMER_GROUP = "card-authorization-consumer-group";
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private CardProcessingService cardProcessingService;
    
    @Autowired
    private PCIComplianceService pciComplianceService;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private ComplianceEngine complianceEngine;
    
    @Autowired
    private FraudDetectionEngine fraudDetectionEngine;
    
    @Autowired
    private AuditLogger auditLogger;

    @Autowired
    private UniversalDLQHandler dlqHandler;

    @Value("${payment.card.authorization.timeout:5}")
    private int authorizationTimeoutSeconds;
    
    @Value("${payment.card.fraud.score.threshold:75}")
    private int fraudScoreThreshold;
    
    @Value("${payment.card.velocity.max-transactions:10}")
    private int maxTransactionsPerMinute;
    
    @Value("${payment.card.amount.daily-limit:10000}")
    private BigDecimal dailyTransactionLimit;
    
    private Counter authorizationsProcessedCounter;
    private Counter authorizationsApprovedCounter;
    private Counter authorizationsDeclinedCounter;
    private Counter fraudDetectedCounter;
    private Counter pciViolationsCounter;
    private Counter errorCounter;
    private Timer processingTimer;
    
    private CircuitBreaker circuitBreaker;
    private Retry retry;
    
    private final Map<String, CardAuthorizationState> authorizationStates = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeResilience();
        logger.info("CardAuthorizationEventConsumer initialized with real-time processing capabilities");
    }
    
    private void initializeMetrics() {
        authorizationsProcessedCounter = Counter.builder("card.authorizations.processed")
                .description("Total number of card authorizations processed")
                .register(meterRegistry);
                
        authorizationsApprovedCounter = Counter.builder("card.authorizations.approved")
                .description("Total number of card authorizations approved")
                .register(meterRegistry);
                
        authorizationsDeclinedCounter = Counter.builder("card.authorizations.declined")
                .description("Total number of card authorizations declined")
                .register(meterRegistry);
                
        fraudDetectedCounter = Counter.builder("card.authorizations.fraud.detected")
                .description("Number of fraudulent card authorizations detected")
                .register(meterRegistry);
                
        pciViolationsCounter = Counter.builder("card.authorizations.pci.violations")
                .description("Number of PCI DSS violations in card authorizations")
                .register(meterRegistry);
                
        errorCounter = Counter.builder("card.authorizations.errors")
                .description("Number of card authorization processing errors")
                .register(meterRegistry);
                
        processingTimer = Timer.builder("card.authorizations.processing.time")
                .description("Time taken to process card authorizations")
                .register(meterRegistry);
    }
    
    private void initializeResilience() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(100)
                .permittedNumberOfCallsInHalfOpenState(10)
                .build();
                
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("card-authorization-processor");
        
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2) // Minimal retry for real-time authorization
                .waitDuration(Duration.ofMilliseconds(500))
                .retryExceptions(Exception.class)
                .build();
                
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);
        retry = retryRegistry.retry("card-authorization-retry");
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 5)
    public void processCardAuthorizationEvent(@Payload String message,
                                            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                            @Header(KafkaHeaders.OFFSET) long offset,
                                            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = UUID.randomUUID().toString();
        
        // Step 1: Initialize MDC and logging context
        MDC.put("correlation.id", correlationId);
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        MDC.put("processor", "CardAuthorizationEventConsumer");
        
        try {
            logger.info("Step 1: Processing card authorization event - correlation: {}", correlationId);
            
            // Step 2: Parse and validate message structure
            Map<String, Object> eventData = parseAndValidateMessage(message);
            if (eventData == null) {
                sendAuthorizationResponse(message, "DECLINED", "Invalid message format", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            String merchantId = (String) eventData.get("merchantId");
            
            MDC.put("authorization.id", authorizationId);
            MDC.put("card.token", cardToken.substring(0, 8) + "****"); // Masked for logging
            MDC.put("merchant.id", merchantId);
            
            logger.info("Step 2: Message validated - Authorization ID: {}", authorizationId);
            
            // Step 3: PCI DSS compliance validation
            if (!performPCIComplianceCheck(eventData, correlationId)) {
                logger.warn("Step 3: PCI compliance violation for authorization: {}", authorizationId);
                pciViolationsCounter.increment();
                sendAuthorizationResponse(message, "DECLINED", "PCI compliance violation", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 3: PCI compliance check passed");
            
            // Step 4: Real-time fraud detection
            if (!performRealTimeFraudDetection(eventData, correlationId)) {
                logger.warn("Step 4: Fraud detected for authorization: {}", authorizationId);
                fraudDetectedCounter.increment();
                sendAuthorizationResponse(message, "DECLINED", "Fraud detected", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 4: Fraud detection passed");
            
            // Step 5: Card validation and risk assessment
            if (!performCardValidation(eventData, correlationId)) {
                logger.warn("Step 5: Card validation failed for authorization: {}", authorizationId);
                sendAuthorizationResponse(message, "DECLINED", "Card validation failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 5: Card validation passed");
            
            // Step 6: Velocity and limit checks
            if (!performVelocityAndLimitChecks(eventData, correlationId)) {
                logger.warn("Step 6: Velocity/limit check failed for authorization: {}", authorizationId);
                sendAuthorizationResponse(message, "DECLINED", "Velocity or limit exceeded", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 6: Velocity and limit checks passed");
            
            // Step 7: Authorization processing with circuit breaker
            Supplier<Boolean> processor = () -> processCardAuthorizationBusinessLogic(eventData, correlationId);
            Boolean result = Retry.decorateSupplier(retry,
                    CircuitBreaker.decorateSupplier(circuitBreaker, processor)).get();
            
            if (!result) {
                logger.error("Step 7: Authorization processing failed for: {}", authorizationId);
                sendAuthorizationResponse(message, "DECLINED", "Processing failed", correlationId);
                acknowledgment.acknowledge();
                return;
            }
            
            logger.info("Step 7: Authorization processing completed");
            
            // Step 8: Account balance and availability check
            performBalanceCheck(eventData, correlationId);
            logger.info("Step 8: Balance check completed");
            
            // Step 9: Generate authorization response
            generateAuthorizationResponse(eventData, correlationId);
            logger.info("Step 9: Authorization response generated");
            
            // Step 10: Regulatory and compliance reporting
            generateComplianceReports(eventData, correlationId);
            logger.info("Step 10: Compliance reports generated");
            
            // Step 11: Audit trail and security logging
            createSecurityAuditTrail(eventData, correlationId);
            logger.info("Step 11: Security audit trail created");
            
            // Step 12: Final acknowledgment and metrics
            authorizationsProcessedCounter.increment();
            CardAuthorizationState state = authorizationStates.get(authorizationId);
            if (state != null && "APPROVED".equals(state.getAuthorizationResult())) {
                authorizationsApprovedCounter.increment();
            } else {
                authorizationsDeclinedCounter.increment();
            }
            
            acknowledgment.acknowledge();
            logger.info("Step 12: Card authorization processing completed for: {}", authorizationId);
            
        } catch (Exception e) {
            logger.error("Error processing card authorization event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);
            errorCounter.increment();

            // Send to DLQ with ConsumerRecord wrapper
            ConsumerRecord<String, String> record =
                new ConsumerRecord<>(topic, partition, offset, correlationId, message);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> {
                    logger.info("Card authorization sent to DLQ: topic={}, offset={}, destination={}, category={}",
                            topic, offset, result.getDestinationTopic(), result.getFailureCategory());
                })
                .exceptionally(dlqError -> {
                    logger.error("CRITICAL: DLQ handling failed for card authorization - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);

                    // Trigger critical PagerDuty alert for message loss
                    try {
                        pagerDutyAlertingService.alertDLQFailure(topic, partition, offset, dlqError.getMessage());
                    } catch (Exception alertEx) {
                        logger.error("Failed to send PagerDuty alert for DLQ failure", alertEx);
                    }

                    return null;
                });

            // Decline authorization and do NOT acknowledge
            sendAuthorizationResponse(message, "DECLINED", "System error", correlationId);

            // Re-throw to prevent offset commit
            throw new RuntimeException("Card authorization processing failed", e);
        } finally {
            processingTimer.stop(sample);
            MDC.clear();
        }
    }
    
    private Map<String, Object> parseAndValidateMessage(String message) {
        try {
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            // Validate required fields for card authorization
            List<String> requiredFields = Arrays.asList(
                "authorizationId", "cardToken", "merchantId", "amount", "currency",
                "transactionType", "merchantCategoryCode", "terminalId", "timestamp"
            );
            
            for (String field : requiredFields) {
                if (!eventData.containsKey(field) || eventData.get(field) == null) {
                    logger.error("Missing required field: {}", field);
                    return null;
                }
            }
            
            // Validate amount
            Object amountObj = eventData.get("amount");
            if (!(amountObj instanceof Number)) {
                logger.error("Invalid amount: {}", amountObj);
                return null;
            }
            
            BigDecimal amount = new BigDecimal(amountObj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                logger.error("Invalid amount value: {}", amount);
                return null;
            }
            
            // Validate currency
            String currency = (String) eventData.get("currency");
            if (!isValidCurrency(currency)) {
                logger.error("Invalid currency: {}", currency);
                return null;
            }
            
            return eventData;
        } catch (Exception e) {
            logger.error("Failed to parse message", e);
            return null;
        }
    }
    
    private boolean performPCIComplianceCheck(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            String merchantId = (String) eventData.get("merchantId");
            
            // Validate card token format and encryption
            if (!pciComplianceService.validateCardTokenFormat(cardToken, correlationId)) {
                logger.warn("Invalid card token format for authorization: {}", authorizationId);
                auditLogger.logSecurityEvent("INVALID_CARD_TOKEN_FORMAT", correlationId,
                    Map.of("authorizationId", authorizationId, "merchantId", merchantId));
                return false;
            }
            
            // Verify merchant PCI compliance status
            if (!pciComplianceService.isMerchantPCICompliant(merchantId, correlationId)) {
                logger.warn("Merchant not PCI compliant: {}", merchantId);
                auditLogger.logComplianceViolation("MERCHANT_NOT_PCI_COMPLIANT", correlationId,
                    Map.of("merchantId", merchantId, "authorizationId", authorizationId));
                return false;
            }
            
            // Validate secure transmission
            if (!pciComplianceService.validateSecureTransmission(eventData, correlationId)) {
                logger.warn("Insecure transmission detected for authorization: {}", authorizationId);
                auditLogger.logSecurityEvent("INSECURE_TRANSMISSION", correlationId,
                    Map.of("authorizationId", authorizationId, "merchantId", merchantId));
                return false;
            }
            
            // Check for sensitive data exposure
            if (pciComplianceService.detectSensitiveDataExposure(eventData, correlationId)) {
                logger.warn("Sensitive data exposure detected for authorization: {}", authorizationId);
                auditLogger.logSecurityEvent("SENSITIVE_DATA_EXPOSURE", correlationId,
                    Map.of("authorizationId", authorizationId, "merchantId", merchantId));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("PCI compliance check failed", e);
            return false;
        }
    }
    
    private boolean performRealTimeFraudDetection(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            String merchantId = (String) eventData.get("merchantId");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            String terminalId = (String) eventData.get("terminalId");
            
            // Calculate fraud risk score
            int fraudScore = fraudDetectionEngine.calculateRealTimeFraudScore(
                cardToken, merchantId, amount, terminalId, eventData, correlationId);
            
            if (fraudScore >= fraudScoreThreshold) {
                logger.warn("High fraud score detected: {} for authorization: {}", fraudScore, authorizationId);
                auditLogger.logSecurityEvent("HIGH_FRAUD_SCORE", correlationId,
                    Map.of("authorizationId", authorizationId, "fraudScore", fraudScore, "threshold", fraudScoreThreshold));
                return false;
            }
            
            // Check for suspicious merchant patterns
            if (fraudDetectionEngine.detectSuspiciousMerchantPattern(merchantId, correlationId)) {
                logger.warn("Suspicious merchant pattern detected: {}", merchantId);
                auditLogger.logSecurityEvent("SUSPICIOUS_MERCHANT_PATTERN", correlationId,
                    Map.of("merchantId", merchantId, "authorizationId", authorizationId));
                return false;
            }
            
            // Check for card testing patterns
            if (fraudDetectionEngine.detectCardTestingPattern(cardToken, correlationId)) {
                logger.warn("Card testing pattern detected for token: {}", cardToken.substring(0, 8) + "****");
                auditLogger.logSecurityEvent("CARD_TESTING_PATTERN", correlationId,
                    Map.of("authorizationId", authorizationId, "cardToken", cardToken.substring(0, 8) + "****"));
                return false;
            }
            
            // Check for geographic anomalies
            if (eventData.containsKey("merchantCountry") && eventData.containsKey("customerCountry")) {
                String merchantCountry = (String) eventData.get("merchantCountry");
                String customerCountry = (String) eventData.get("customerCountry");
                
                if (fraudDetectionEngine.detectGeographicAnomaly(customerCountry, merchantCountry, correlationId)) {
                    logger.warn("Geographic anomaly detected: customer {} merchant {}", customerCountry, merchantCountry);
                    auditLogger.logSecurityEvent("GEOGRAPHIC_ANOMALY", correlationId,
                        Map.of("authorizationId", authorizationId, "customerCountry", customerCountry, "merchantCountry", merchantCountry));
                    return false;
                }
            }
            
            // Store fraud score for reference
            eventData.put("fraudScore", fraudScore);
            
            return true;
        } catch (Exception e) {
            logger.error("Real-time fraud detection failed", e);
            return false;
        }
    }
    
    private boolean performCardValidation(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            
            // Validate card status and activation
            if (!cardProcessingService.isCardActive(cardToken, correlationId)) {
                logger.warn("Card not active for authorization: {}", authorizationId);
                auditLogger.logBusinessEvent("CARD_NOT_ACTIVE", correlationId,
                    Map.of("authorizationId", authorizationId, "cardToken", cardToken.substring(0, 8) + "****"));
                return false;
            }
            
            // Check card expiration
            if (cardProcessingService.isCardExpired(cardToken, correlationId)) {
                logger.warn("Card expired for authorization: {}", authorizationId);
                auditLogger.logBusinessEvent("CARD_EXPIRED", correlationId,
                    Map.of("authorizationId", authorizationId, "cardToken", cardToken.substring(0, 8) + "****"));
                return false;
            }
            
            // Check card blocks and restrictions
            if (cardProcessingService.isCardBlocked(cardToken, correlationId)) {
                logger.warn("Card blocked for authorization: {}", authorizationId);
                auditLogger.logBusinessEvent("CARD_BLOCKED", correlationId,
                    Map.of("authorizationId", authorizationId, "cardToken", cardToken.substring(0, 8) + "****"));
                return false;
            }
            
            // Validate transaction type permissions
            String transactionType = (String) eventData.get("transactionType");
            if (!cardProcessingService.isTransactionTypeAllowed(cardToken, transactionType, correlationId)) {
                logger.warn("Transaction type not allowed: {} for card: {}", transactionType, cardToken.substring(0, 8) + "****");
                auditLogger.logBusinessEvent("TRANSACTION_TYPE_NOT_ALLOWED", correlationId,
                    Map.of("authorizationId", authorizationId, "transactionType", transactionType));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Card validation failed", e);
            return false;
        }
    }
    
    private boolean performVelocityAndLimitChecks(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            
            // Check transaction velocity
            if (!cardProcessingService.checkTransactionVelocity(cardToken, maxTransactionsPerMinute, correlationId)) {
                logger.warn("Transaction velocity exceeded for card: {}", cardToken.substring(0, 8) + "****");
                auditLogger.logBusinessEvent("TRANSACTION_VELOCITY_EXCEEDED", correlationId,
                    Map.of("authorizationId", authorizationId, "cardToken", cardToken.substring(0, 8) + "****"));
                return false;
            }
            
            // Check daily spending limit
            BigDecimal dailySpend = cardProcessingService.getDailySpendAmount(cardToken, correlationId);
            if (dailySpend.add(amount).compareTo(dailyTransactionLimit) > 0) {
                logger.warn("Daily spending limit exceeded for card: {}", cardToken.substring(0, 8) + "****");
                auditLogger.logBusinessEvent("DAILY_LIMIT_EXCEEDED", correlationId,
                    Map.of("authorizationId", authorizationId, "currentSpend", dailySpend.toString(), 
                           "attemptedAmount", amount.toString(), "limit", dailyTransactionLimit.toString()));
                return false;
            }
            
            // Check single transaction limit
            BigDecimal singleTransactionLimit = cardProcessingService.getSingleTransactionLimit(cardToken, correlationId);
            if (amount.compareTo(singleTransactionLimit) > 0) {
                logger.warn("Single transaction limit exceeded for card: {}", cardToken.substring(0, 8) + "****");
                auditLogger.logBusinessEvent("SINGLE_TRANSACTION_LIMIT_EXCEEDED", correlationId,
                    Map.of("authorizationId", authorizationId, "amount", amount.toString(), 
                           "limit", singleTransactionLimit.toString()));
                return false;
            }
            
            return true;
        } catch (Exception e) {
            logger.error("Velocity and limit checks failed", e);
            return false;
        }
    }
    
    private boolean processCardAuthorizationBusinessLogic(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            String merchantId = (String) eventData.get("merchantId");
            
            logger.info("Processing card authorization business logic for: {}", authorizationId);
            
            // Create authorization record
            CardAuthorization authorization = new CardAuthorization();
            authorization.setAuthorizationId(authorizationId);
            authorization.setCardToken(cardToken);
            authorization.setMerchantId(merchantId);
            authorization.setAmount(new BigDecimal(eventData.get("amount").toString()));
            authorization.setCurrency((String) eventData.get("currency"));
            authorization.setTransactionType((String) eventData.get("transactionType"));
            authorization.setMerchantCategoryCode((String) eventData.get("merchantCategoryCode"));
            authorization.setTerminalId((String) eventData.get("terminalId"));
            authorization.setAuthorizationDate(LocalDateTime.now());
            authorization.setStatus("PROCESSING");
            authorization.setCorrelationId(correlationId);
            
            // Add optional fields
            if (eventData.containsKey("merchantName")) {
                authorization.setMerchantName((String) eventData.get("merchantName"));
            }
            if (eventData.containsKey("fraudScore")) {
                authorization.setFraudScore((Integer) eventData.get("fraudScore"));
            }
            
            // Save authorization
            cardProcessingService.saveCardAuthorization(authorization);
            
            // Initialize authorization state
            CardAuthorizationState state = new CardAuthorizationState();
            state.setAuthorizationId(authorizationId);
            state.setStatus("PROCESSING");
            state.setProcessedAt(Instant.now());
            state.setCorrelationId(correlationId);
            authorizationStates.put(authorizationId, state);
            
            logger.info("Card authorization business logic processing completed for: {}", authorizationId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to process card authorization business logic", e);
            return false;
        }
    }
    
    private void performBalanceCheck(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            
            // Get account balance
            BigDecimal availableBalance = cardProcessingService.getAvailableBalance(cardToken, correlationId);
            
            CardAuthorizationState state = authorizationStates.get(authorizationId);
            if (state != null) {
                if (availableBalance.compareTo(amount) >= 0) {
                    state.setAuthorizationResult("APPROVED");
                    state.setResponseCode("00"); // Approved
                    state.setResponseMessage("Transaction approved");
                    
                    // Reserve funds
                    cardProcessingService.reserveFunds(cardToken, amount, authorizationId, correlationId);
                } else {
                    state.setAuthorizationResult("DECLINED");
                    state.setResponseCode("51"); // Insufficient funds
                    state.setResponseMessage("Insufficient funds");
                }
            }
            
            logger.info("Balance check completed for authorization: {} - result: {}", 
                authorizationId, state != null ? state.getAuthorizationResult() : "UNKNOWN");
                
        } catch (Exception e) {
            logger.error("Balance check failed", e);
            throw new RuntimeException("Balance check failed", e);
        }
    }
    
    private void generateAuthorizationResponse(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            CardAuthorizationState state = authorizationStates.get(authorizationId);
            
            if (state != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("authorizationId", authorizationId);
                response.put("result", state.getAuthorizationResult());
                response.put("responseCode", state.getResponseCode());
                response.put("responseMessage", state.getResponseMessage());
                response.put("timestamp", Instant.now().toString());
                response.put("correlationId", correlationId);
                
                // Send response back to requesting system
                String responseJson = objectMapper.writeValueAsString(response);
                kafkaTemplate.send("card-authorization-response", authorizationId, responseJson);
                
                logger.info("Authorization response sent for: {} - result: {}", 
                    authorizationId, state.getAuthorizationResult());
            }
            
        } catch (Exception e) {
            logger.error("Failed to generate authorization response", e);
            throw new RuntimeException("Authorization response generation failed", e);
        }
    }
    
    private void generateComplianceReports(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String merchantId = (String) eventData.get("merchantId");
            BigDecimal amount = new BigDecimal(eventData.get("amount").toString());
            
            // Generate PCI DSS compliance report
            pciComplianceService.generatePCIComplianceReport(authorizationId, merchantId, correlationId);
            
            // Generate card transaction report
            complianceEngine.generateCardTransactionReport(authorizationId, amount, correlationId);
            
            logger.info("Compliance reports generated for authorization: {}", authorizationId);
        } catch (Exception e) {
            logger.error("Failed to generate compliance reports", e);
            throw new RuntimeException("Compliance reporting failed", e);
        }
    }
    
    private void createSecurityAuditTrail(Map<String, Object> eventData, String correlationId) {
        try {
            String authorizationId = (String) eventData.get("authorizationId");
            String cardToken = (String) eventData.get("cardToken");
            String merchantId = (String) eventData.get("merchantId");
            
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("eventType", "CARD_AUTHORIZATION_PROCESSED");
            auditData.put("authorizationId", authorizationId);
            auditData.put("cardToken", cardToken.substring(0, 8) + "****"); // Masked
            auditData.put("merchantId", merchantId);
            auditData.put("processingTimestamp", Instant.now().toString());
            auditData.put("correlationId", correlationId);
            
            // Add result information
            CardAuthorizationState state = authorizationStates.get(authorizationId);
            if (state != null) {
                auditData.put("authorizationResult", state.getAuthorizationResult());
                auditData.put("responseCode", state.getResponseCode());
            }
            
            // Remove sensitive data and add safe fields
            auditData.put("amount", eventData.get("amount"));
            auditData.put("currency", eventData.get("currency"));
            auditData.put("transactionType", eventData.get("transactionType"));
            auditData.put("merchantCategoryCode", eventData.get("merchantCategoryCode"));
            
            auditLogger.logBusinessEvent("CARD_AUTHORIZATION_PROCESSED", correlationId, auditData);
            
            logger.info("Security audit trail created for authorization: {}", authorizationId);
        } catch (Exception e) {
            logger.error("Failed to create security audit trail", e);
            throw new RuntimeException("Security audit trail creation failed", e);
        }
    }
    
    private void sendAuthorizationResponse(String originalMessage, String result, String reason, String correlationId) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("result", result);
            response.put("reason", reason);
            response.put("timestamp", Instant.now().toString());
            response.put("correlationId", correlationId);
            
            String responseJson = objectMapper.writeValueAsString(response);
            kafkaTemplate.send("card-authorization-response", responseJson);
            
            if ("DECLINED".equals(result)) {
                sendToDlq(originalMessage, reason, correlationId);
            }
            
        } catch (Exception e) {
            logger.error("Failed to send authorization response", e);
        }
    }
    
    private boolean isValidCurrency(String currency) {
        return currency != null && currency.length() == 3 && 
               Arrays.asList("USD", "EUR", "GBP", "CAD", "AUD").contains(currency);
    }
    
    private void sendToDlq(String message, String reason, String correlationId) {
        try {
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(DLQ_TOPIC, message);
            dlqRecord.headers().add("failure_reason", reason.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("correlation_id", correlationId.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("original_topic", TOPIC.getBytes(StandardCharsets.UTF_8));
            dlqRecord.headers().add("failed_at", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
            
            kafkaTemplate.send(dlqRecord);
            logger.warn("Message sent to DLQ with reason: {} - correlation: {}", reason, correlationId);
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down CardAuthorizationEventConsumer...");
        authorizationStates.clear();
        logger.info("CardAuthorizationEventConsumer shutdown complete");
    }
    
    private static class CardAuthorizationState {
        private String authorizationId;
        private String status;
        private Instant processedAt;
        private String correlationId;
        private String authorizationResult;
        private String responseCode;
        private String responseMessage;
        
        // Getters and setters
        public String getAuthorizationId() { return authorizationId; }
        public void setAuthorizationId(String authorizationId) { this.authorizationId = authorizationId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Instant getProcessedAt() { return processedAt; }
        public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public String getAuthorizationResult() { return authorizationResult; }
        public void setAuthorizationResult(String authorizationResult) { this.authorizationResult = authorizationResult; }
        public String getResponseCode() { return responseCode; }
        public void setResponseCode(String responseCode) { this.responseCode = responseCode; }
        public String getResponseMessage() { return responseMessage; }
        public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }
    }
}