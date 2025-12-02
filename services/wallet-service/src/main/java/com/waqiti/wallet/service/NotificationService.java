package com.waqiti.wallet.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${notification-service.url:http://localhost:8085}")
    private String notificationServiceUrl;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(notificationServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendLimitExceededNotificationFallback")
    @Retry(name = "notification-service")
    public void sendLimitExceededNotification(String userId, String walletId, String limitType, 
                                             BigDecimal currentAmount, BigDecimal limitAmount) {
        log.info("Sending limit exceeded notification: userId={} walletId={} limitType={}", 
                userId, walletId, limitType);
        
        try {
            Map<String, Object> notification = Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "notificationType", "LIMIT_EXCEEDED",
                    "limitType", limitType,
                    "currentAmount", currentAmount.toString(),
                    "limitAmount", limitAmount.toString(),
                    "timestamp", LocalDateTime.now().toString()
            );
            
            getWebClient().post()
                    .uri("/api/v1/notifications/send")
                    .bodyValue(notification)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            log.debug("Limit exceeded notification sent successfully");
            
        } catch (Exception e) {
            log.error("Failed to send limit exceeded notification", e);
            sendLimitExceededNotificationFallback(userId, walletId, limitType, currentAmount, limitAmount, e);
        }
    }
    
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendRestrictionNotificationFallback")
    @Retry(name = "notification-service")
    public void sendRestrictionNotification(String userId, String walletId, String restrictionType, 
                                          String reason, LocalDateTime expiresAt) {
        log.info("Sending restriction notification: userId={} walletId={} restrictionType={}", 
                userId, walletId, restrictionType);
        
        try {
            Map<String, Object> notification = Map.of(
                    "userId", userId,
                    "walletId", walletId,
                    "notificationType", "RESTRICTION_APPLIED",
                    "restrictionType", restrictionType,
                    "reason", reason,
                    "expiresAt", expiresAt != null ? expiresAt.toString() : "INDEFINITE",
                    "timestamp", LocalDateTime.now().toString()
            );
            
            getWebClient().post()
                    .uri("/api/v1/notifications/send")
                    .bodyValue(notification)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            log.debug("Restriction notification sent successfully");
            
        } catch (Exception e) {
            log.error("Failed to send restriction notification", e);
            sendRestrictionNotificationFallback(userId, walletId, restrictionType, reason, expiresAt, e);
        }
    }
    
    private void sendLimitExceededNotificationFallback(String userId, String walletId, String limitType, 
                                                      BigDecimal currentAmount, BigDecimal limitAmount, Exception e) {
        log.error("Notification service unavailable - limit exceeded notification not sent (fallback): userId={}", userId);
    }
    
    private void sendRestrictionNotificationFallback(String userId, String walletId, String restrictionType,
                                                    String reason, LocalDateTime expiresAt, Exception e) {
        log.error("Notification service unavailable - restriction notification not sent (fallback): userId={}", userId);
    }

    /**
     * Send wallet freeze notification (used by WalletFreezeService).
     */
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendWalletFreezeNotificationFallback")
    @Retry(name = "notification-service")
    public void sendWalletFreezeNotification(java.util.UUID userId, java.util.List<String> walletIds, String freezeReason) {
        log.info("Sending wallet freeze notification: userId={}, wallets={}", userId, walletIds.size());

        try {
            Map<String, Object> notification = Map.of(
                "userId", userId.toString(),
                "walletIds", walletIds,
                "notificationType", "WALLET_FROZEN",
                "reason", freezeReason,
                "timestamp", LocalDateTime.now().toString()
            );

            getWebClient().post()
                .uri("/api/v1/notifications/send")
                .bodyValue(notification)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(5))
                .block();

            log.debug("Wallet freeze notification sent successfully");

        } catch (Exception e) {
            log.error("Failed to send wallet freeze notification", e);
            sendWalletFreezeNotificationFallback(userId, walletIds, freezeReason, e);
        }
    }

    private void sendWalletFreezeNotificationFallback(java.util.UUID userId, java.util.List<String> walletIds,
                                                     String freezeReason, Exception e) {
        log.error("Notification service unavailable - wallet freeze notification not sent (fallback): userId={}", userId);
    }
}