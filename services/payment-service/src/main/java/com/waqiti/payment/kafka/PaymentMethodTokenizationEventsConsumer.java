package com.waqiti.payment.kafka;

import com.waqiti.common.events.TokenizationEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.TokenizedPaymentMethod;
import com.waqiti.payment.repository.TokenizedPaymentMethodRepository;
import com.waqiti.payment.service.TokenizationService;
import com.waqiti.payment.service.TokenVaultService;
import com.waqiti.payment.service.PaymentMethodVerificationService;
import com.waqiti.payment.metrics.TokenizationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentMethodTokenizationEventsConsumer {
    
    private final TokenizedPaymentMethodRepository tokenRepository;
    private final TokenizationService tokenizationService;
    private final TokenVaultService vaultService;
    private final PaymentMethodVerificationService verificationService;
    private final TokenizationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    private static final long TOKEN_EXPIRATION_DAYS = 1095;
    
    @KafkaListener(
        topics = {"payment-tokenization-events", "card-tokenization-events", "payment-method-tokenization"},
        groupId = "payment-tokenization-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleTokenizationEvent(
            @Payload TokenizationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("token-%s-%s-p%d-o%d", 
            event.getUserId(), maskToken(event.getToken()), partition, offset);
        
        log.info("Processing tokenization event: userId={}, paymentMethodType={}, status={}",
            event.getUserId(), event.getPaymentMethodType(), event.getStatus());
        
        try {
            switch (event.getStatus()) {
                case "TOKENIZATION_REQUESTED":
                    requestTokenization(event, correlationId);
                    break;
                    
                case "TOKEN_GENERATED":
                    generateToken(event, correlationId);
                    break;
                    
                case "TOKEN_STORED":
                    storeToken(event, correlationId);
                    break;
                    
                case "TOKEN_VERIFIED":
                    verifyToken(event, correlationId);
                    break;
                    
                case "TOKEN_ACTIVATED":
                    activateToken(event, correlationId);
                    break;
                    
                case "TOKEN_REVOKED":
                    revokeToken(event, correlationId);
                    break;
                    
                case "TOKEN_EXPIRED":
                    expireToken(event, correlationId);
                    break;
                    
                case "TOKEN_RENEWED":
                    renewToken(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown tokenization status: {}", event.getStatus());
                    break;
            }
            
            auditService.logPaymentEvent("TOKENIZATION_EVENT_PROCESSED", event.getUserId(),
                Map.of("paymentMethodType", event.getPaymentMethodType(), "status", event.getStatus(),
                    "tokenMasked", maskToken(event.getToken()), "correlationId", correlationId, 
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing tokenization event: partition={}, offset={}, error={}",
                partition, offset, e.getMessage(), e);

            kafkaTemplate.send("payment-tokenization-events-dlq", Map.of(
                "originalEvent", sanitizeEvent(event), "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now()));

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: offset={}, destination={}, category={}",
                        offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "partition={}, offset={}, error={}",
                            partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Tokenization event processing failed", e);
        }
    }
    
    private void requestTokenization(TokenizationEvent event, String correlationId) {
        boolean pciCompliant = tokenizationService.validatePciCompliance(event.getPaymentMethodData());
        
        if (!pciCompliant) {
            log.error("PCI compliance validation failed for tokenization request: userId={}", event.getUserId());
            notificationService.sendNotification("SECURITY_TEAM", "PCI Compliance Violation",
                String.format("Non-compliant payment data detected for user %s", event.getUserId()),
                correlationId);
            return;
        }
        
        kafkaTemplate.send("payment-tokenization-events", Map.of(
            "userId", event.getUserId(),
            "paymentMethodType", event.getPaymentMethodType(),
            "paymentMethodData", event.getPaymentMethodData(),
            "status", "TOKEN_GENERATED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordTokenizationRequested(event.getPaymentMethodType());
        
        log.info("Tokenization requested: userId={}, paymentMethodType={}", 
            event.getUserId(), event.getPaymentMethodType());
    }
    
    private void generateToken(TokenizationEvent event, String correlationId) {
        String token = tokenizationService.generateSecureToken(
            event.getUserId(),
            event.getPaymentMethodType(),
            event.getPaymentMethodData()
        );
        
        TokenizedPaymentMethod tokenizedMethod = TokenizedPaymentMethod.builder()
            .token(token)
            .userId(event.getUserId())
            .paymentMethodType(event.getPaymentMethodType())
            .last4Digits(extractLast4Digits(event.getPaymentMethodData()))
            .expiryMonth(event.getExpiryMonth())
            .expiryYear(event.getExpiryYear())
            .status("TOKEN_GENERATED")
            .tokenGeneratedAt(LocalDateTime.now())
            .tokenExpiresAt(LocalDateTime.now().plusDays(TOKEN_EXPIRATION_DAYS))
            .correlationId(correlationId)
            .build();
        tokenRepository.save(tokenizedMethod);
        
        kafkaTemplate.send("payment-tokenization-events", Map.of(
            "userId", event.getUserId(),
            "token", token,
            "paymentMethodType", event.getPaymentMethodType(),
            "status", "TOKEN_STORED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordTokenGenerated(event.getPaymentMethodType());
        
        log.info("Token generated: userId={}, paymentMethodType={}, tokenMasked={}", 
            event.getUserId(), event.getPaymentMethodType(), maskToken(token));
    }
    
    private void storeToken(TokenizationEvent event, String correlationId) {
        TokenizedPaymentMethod tokenizedMethod = tokenRepository.findByToken(event.getToken())
            .orElseThrow(() -> new RuntimeException("Tokenized payment method not found"));
        
        tokenizedMethod.setStatus("TOKEN_STORED");
        tokenizedMethod.setTokenStoredAt(LocalDateTime.now());
        tokenRepository.save(tokenizedMethod);
        
        String vaultId = vaultService.storeTokenSecurely(
            event.getToken(),
            event.getUserId(),
            event.getPaymentMethodData()
        );
        
        tokenizedMethod.setVaultId(vaultId);
        tokenRepository.save(tokenizedMethod);
        
        kafkaTemplate.send("payment-tokenization-events", Map.of(
            "userId", event.getUserId(),
            "token", event.getToken(),
            "paymentMethodType", event.getPaymentMethodType(),
            "status", "TOKEN_VERIFIED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordTokenStored(event.getPaymentMethodType());
        
        log.info("Token stored in vault: userId={}, vaultId={}, tokenMasked={}", 
            event.getUserId(), vaultId, maskToken(event.getToken()));
    }
    
    private void verifyToken(TokenizationEvent event, String correlationId) {
        TokenizedPaymentMethod tokenizedMethod = tokenRepository.findByToken(event.getToken())
            .orElseThrow(() -> new RuntimeException("Tokenized payment method not found"));
        
        tokenizedMethod.setStatus("TOKEN_VERIFIED");
        tokenizedMethod.setVerifiedAt(LocalDateTime.now());
        tokenRepository.save(tokenizedMethod);
        
        boolean isValid = verificationService.verifyPaymentMethod(
            event.getUserId(),
            event.getToken(),
            event.getPaymentMethodType()
        );
        
        if (isValid) {
            kafkaTemplate.send("payment-tokenization-events", Map.of(
                "userId", event.getUserId(),
                "token", event.getToken(),
                "paymentMethodType", event.getPaymentMethodType(),
                "status", "TOKEN_ACTIVATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("payment-tokenization-events", Map.of(
                "userId", event.getUserId(),
                "token", event.getToken(),
                "status", "TOKEN_REVOKED",
                "reason", "VERIFICATION_FAILED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordTokenVerified(event.getPaymentMethodType(), isValid);
        
        log.info("Token verification: userId={}, tokenMasked={}, valid={}", 
            event.getUserId(), maskToken(event.getToken()), isValid);
    }
    
    private void activateToken(TokenizationEvent event, String correlationId) {
        TokenizedPaymentMethod tokenizedMethod = tokenRepository.findByToken(event.getToken())
            .orElseThrow(() -> new RuntimeException("Tokenized payment method not found"));
        
        tokenizedMethod.setStatus("ACTIVE");
        tokenizedMethod.setActivatedAt(LocalDateTime.now());
        tokenRepository.save(tokenizedMethod);
        
        notificationService.sendNotification(event.getUserId(), "Payment Method Added",
            String.format("Your %s ending in %s has been securely added to your account.", 
                event.getPaymentMethodType(), tokenizedMethod.getLast4Digits()),
            correlationId);
        
        metricsService.recordTokenActivated(event.getPaymentMethodType());
        
        log.info("Token activated: userId={}, tokenMasked={}, paymentMethodType={}", 
            event.getUserId(), maskToken(event.getToken()), event.getPaymentMethodType());
    }
    
    private void revokeToken(TokenizationEvent event, String correlationId) {
        TokenizedPaymentMethod tokenizedMethod = tokenRepository.findByToken(event.getToken())
            .orElseThrow(() -> new RuntimeException("Tokenized payment method not found"));
        
        tokenizedMethod.setStatus("REVOKED");
        tokenizedMethod.setRevokedAt(LocalDateTime.now());
        tokenizedMethod.setRevocationReason(event.getReason());
        tokenRepository.save(tokenizedMethod);
        
        vaultService.revokeToken(event.getToken());
        
        metricsService.recordTokenRevoked(event.getPaymentMethodType(), event.getReason());
        
        log.warn("Token revoked: userId={}, tokenMasked={}, reason={}", 
            event.getUserId(), maskToken(event.getToken()), event.getReason());
    }
    
    private void expireToken(TokenizationEvent event, String correlationId) {
        TokenizedPaymentMethod tokenizedMethod = tokenRepository.findByToken(event.getToken())
            .orElseThrow(() -> new RuntimeException("Tokenized payment method not found"));
        
        tokenizedMethod.setStatus("EXPIRED");
        tokenizedMethod.setExpiredAt(LocalDateTime.now());
        tokenRepository.save(tokenizedMethod);
        
        vaultService.archiveExpiredToken(event.getToken());
        
        notificationService.sendNotification(event.getUserId(), "Payment Method Expired",
            String.format("Your %s ending in %s has expired. Please update your payment method.", 
                event.getPaymentMethodType(), tokenizedMethod.getLast4Digits()),
            correlationId);
        
        metricsService.recordTokenExpired(event.getPaymentMethodType());
        
        log.info("Token expired: userId={}, tokenMasked={}, expiresAt={}", 
            event.getUserId(), maskToken(event.getToken()), tokenizedMethod.getTokenExpiresAt());
    }
    
    private void renewToken(TokenizationEvent event, String correlationId) {
        TokenizedPaymentMethod tokenizedMethod = tokenRepository.findByToken(event.getToken())
            .orElseThrow(() -> new RuntimeException("Tokenized payment method not found"));
        
        String newToken = tokenizationService.renewToken(event.getToken(), event.getUserId());
        
        tokenizedMethod.setToken(newToken);
        tokenizedMethod.setStatus("ACTIVE");
        tokenizedMethod.setTokenExpiresAt(LocalDateTime.now().plusDays(TOKEN_EXPIRATION_DAYS));
        tokenizedMethod.setRenewedAt(LocalDateTime.now());
        tokenRepository.save(tokenizedMethod);
        
        vaultService.updateToken(event.getToken(), newToken);
        
        metricsService.recordTokenRenewed(event.getPaymentMethodType());
        
        log.info("Token renewed: userId={}, oldTokenMasked={}, newTokenMasked={}", 
            event.getUserId(), maskToken(event.getToken()), maskToken(newToken));
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
    
    private String extractLast4Digits(Map<String, String> paymentMethodData) {
        String cardNumber = paymentMethodData.get("cardNumber");
        if (cardNumber != null && cardNumber.length() >= 4) {
            return cardNumber.substring(cardNumber.length() - 4);
        }
        return "****";
    }
    
    private Map<String, Object> sanitizeEvent(TokenizationEvent event) {
        return Map.of(
            "userId", event.getUserId(),
            "paymentMethodType", event.getPaymentMethodType(),
            "status", event.getStatus(),
            "tokenMasked", maskToken(event.getToken())
        );
    }
}