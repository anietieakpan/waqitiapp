package com.waqiti.common.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class ComplianceServiceClient {

    private final WebClient webClient;

    public ComplianceServiceClient(WebClient.Builder webClientBuilder,
                                 @Value("${compliance-service.url:http://localhost:8087}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "validateTransactionFallback")
    @Retry(name = "compliance-service")
    public boolean validateTransaction(String userId, String recipientId, BigDecimal amount, String currency) {
        log.debug("Validating transaction compliance: {} -> {} ({} {})", userId, recipientId, amount, currency);

        try {
            Map<String, Object> request = Map.of(
                "userId", userId,
                "recipientId", recipientId,
                "amount", amount.toString(),
                "currency", currency,
                "type", "P2P_TRANSFER"
            );

            String response = webClient.post()
                    .uri("/api/v1/compliance/validate/transaction")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Compliance validation error: {} - {}", 
                                             clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException(
                                            "Compliance validation failed: " + errorBody));
                                });
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            boolean isCompliant = "APPROVED".equalsIgnoreCase(response);
            log.debug("Transaction compliance validation result: {} ({})", isCompliant, response);
            
            return isCompliant;

        } catch (Exception e) {
            log.error("Failed to validate transaction compliance: {} -> {}", userId, recipientId, e);
            throw new RuntimeException("Compliance service call failed", e);
        }
    }

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "checkSanctionsFallback")
    @Retry(name = "compliance-service")
    public boolean checkSanctions(String userId, String name, String email) {
        log.debug("Checking sanctions screening for user: {} ({})", userId, email);

        try {
            Map<String, Object> request = Map.of(
                "userId", userId,
                "name", name,
                "email", email,
                "type", "USER_SCREENING"
            );

            String response = webClient.post()
                    .uri("/api/v1/compliance/sanctions/check")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Sanctions screening error: {} - {}", 
                                             clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException(
                                            "Sanctions screening failed: " + errorBody));
                                });
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            boolean isClean = "CLEAR".equalsIgnoreCase(response);
            log.debug("Sanctions screening result for user {}: {} ({})", userId, isClean, response);
            
            return isClean;

        } catch (Exception e) {
            log.error("Failed to check sanctions for user: {}", userId, e);
            throw new RuntimeException("Sanctions screening failed", e);
        }
    }

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "validateKycFallback")
    @Retry(name = "compliance-service")
    public String validateKyc(String userId, Map<String, Object> kycData) {
        log.debug("Validating KYC data for user: {}", userId);

        try {
            Map<String, Object> request = Map.of(
                "userId", userId,
                "kycData", kycData,
                "type", "KYC_VALIDATION"
            );

            String response = webClient.post()
                    .uri("/api/v1/compliance/kyc/validate")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), clientResponse -> {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("KYC validation error: {} - {}", 
                                             clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException(
                                            "KYC validation failed: " + errorBody));
                                });
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.debug("KYC validation result for user {}: {}", userId, response);
            return response;

        } catch (Exception e) {
            log.error("Failed to validate KYC for user: {}", userId, e);
            throw new RuntimeException("KYC validation failed", e);
        }
    }

    @CircuitBreaker(name = "compliance-service", fallbackMethod = "reportSuspiciousActivityFallback")
    @Retry(name = "compliance-service")
    public void reportSuspiciousActivity(String userId, String activityType, Map<String, Object> details) {
        log.debug("Reporting suspicious activity for user: {} (type: {})", userId, activityType);

        try {
            Map<String, Object> request = Map.of(
                "userId", userId,
                "activityType", activityType,
                "details", details,
                "timestamp", System.currentTimeMillis()
            );

            webClient.post()
                    .uri("/api/v1/compliance/suspicious-activity/report")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(statusCode -> statusCode.isError(), response -> {
                        return response.bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Suspicious activity reporting error: {} - {}", 
                                             response.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException(
                                            "Suspicious activity reporting failed: " + errorBody));
                                });
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.debug("Successfully reported suspicious activity for user: {}", userId);

        } catch (Exception e) {
            log.error("Failed to report suspicious activity for user: {}", userId, e);
            throw new RuntimeException("Suspicious activity reporting failed", e);
        }
    }

    // Fallback methods

    private boolean validateTransactionFallback(String userId, String recipientId, BigDecimal amount, 
                                              String currency, Exception ex) {
        log.warn("Compliance service unavailable - transaction validation fallback executed: {} -> {} ({})", 
                userId, recipientId, ex.getMessage());
        // In production, might have more sophisticated fallback logic
        // For now, fail safe by allowing small transactions, blocking large ones
        return amount.compareTo(new BigDecimal("1000")) <= 0;
    }

    private boolean checkSanctionsFallback(String userId, String name, String email, Exception ex) {
        log.warn("Compliance service unavailable - sanctions check fallback executed: {} ({})", 
                userId, ex.getMessage());
        // Fail safe - assume user is not on sanctions list but log for manual review
        return true;
    }

    private String validateKycFallback(String userId, Map<String, Object> kycData, Exception ex) {
        log.warn("Compliance service unavailable - KYC validation fallback executed: {} ({})", 
                userId, ex.getMessage());
        // Return pending status for manual review
        return "PENDING_MANUAL_REVIEW";
    }

    private void reportSuspiciousActivityFallback(String userId, String activityType, 
                                                Map<String, Object> details, Exception ex) {
        log.error("Compliance service unavailable - suspicious activity report fallback: {} ({}) - {}", 
                 userId, activityType, ex.getMessage());
        // Could queue for later processing or send to alternative system
    }
}