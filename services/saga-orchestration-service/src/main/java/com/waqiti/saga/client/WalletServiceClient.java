package com.waqiti.saga.client;

import com.waqiti.common.resilience.CircuitBreakerServiceEnhancer;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client for communicating with the wallet service
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletServiceClient {

    private final RestTemplate restTemplate;
    private final CircuitBreakerServiceEnhancer circuitBreakerEnhancer;

    @Value("${services.wallet-service.url:http://wallet-service}")
    private String walletServiceUrl;

    @Value("${wallet.operation.timeout:30000}")
    private int timeoutMs;

    /**
     * Reserve funds in wallet with circuit breaker protection
     */
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "reserveFundsFallback")
    public CompletableFuture<ReservationResponse> reserveFunds(String walletId, BigDecimal amount, 
                                                              String currency, String reservationId, String transactionId) {
        
        return circuitBreakerEnhancer.executeExternalCall(
            new CircuitBreakerServiceEnhancer.ExternalServiceCall<ReservationResponse>() {
                @Override
                public String getServiceName() {
                    return "wallet-service";
                }
                
                @Override
                public ReservationResponse execute() throws Exception {
                    log.debug("Reserving funds: walletId={}, amount={} {}, reservationId={}", 
                        walletId, amount, currency, reservationId);

                    String url = walletServiceUrl + "/api/v1/wallets/" + walletId + "/reserve";
                    
                    Map<String, Object> request = Map.of(
                        "reservationId", reservationId,
                        "amount", amount,
                        "currency", currency,
                        "transactionId", transactionId,
                        "reason", "SAGA_RESERVATION",
                        "expiresAt", LocalDateTime.now().plusMinutes(30)
                    );
                    
                    HttpHeaders headers = createHeaders();
                    HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);
                    
                    ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.POST, httpEntity, Map.class);
                    
                    Map<String, Object> result = response.getBody();
                    if (result != null) {
                        return ReservationResponse.builder()
                            .reservationId(reservationId)
                            .walletId(walletId)
                            .amount(amount)
                            .currency(currency)
                            .status((String) result.get("status"))
                            .success(Boolean.TRUE.equals(result.get("success")))
                            .availableBalance(new BigDecimal(result.getOrDefault("availableBalance", "0").toString()))
                            .reservedAt(LocalDateTime.now())
                            .build();
                    }
                    
                    throw new RuntimeException("Empty response from wallet service");
                }
                
                @Override
                public boolean hasFallback() {
                    return true;
                }
                
                @Override
                public ReservationResponse fallback(Throwable throwable) {
                    return reserveFundsFallback(walletId, amount, currency, reservationId, transactionId, throwable);
                }
            });
    }
    
    /**
     * Fallback method for fund reservation
     */
    public ReservationResponse reserveFundsFallback(String walletId, BigDecimal amount, 
                                                   String currency, String reservationId, 
                                                   String transactionId, Throwable throwable) {
        log.warn("Wallet service unavailable, using fallback for fund reservation: walletId={}, reservationId={}", 
                walletId, reservationId, throwable);
        
        // Return a fallback response indicating service unavailability
        return ReservationResponse.builder()
            .reservationId(reservationId)
            .walletId(walletId)
            .amount(amount)
            .currency(currency)
            .status("FAILED")
            .success(false)
            .availableBalance(BigDecimal.ZERO)
            .reservedAt(LocalDateTime.now())
            .errorMessage("Wallet service temporarily unavailable")
            .fallbackUsed(true)
            .build();
    }

    /**
     * Release reserved funds
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Boolean> releaseFunds(String walletId, String reservationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Releasing funds: walletId={}, reservationId={}", walletId, reservationId);

                String url = walletServiceUrl + "/api/v1/wallets/" + walletId + "/release/" + reservationId;
                
                HttpHeaders headers = createHeaders();
                HttpEntity<?> httpEntity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                boolean success = result != null && Boolean.TRUE.equals(result.get("success"));
                
                log.debug("Funds release result: walletId={}, reservationId={}, success={}", 
                    walletId, reservationId, success);
                
                return success;
                
            } catch (Exception e) {
                log.error("Failed to release funds: walletId={}, reservationId={}", walletId, reservationId, e);
                return false; // Return false instead of throwing to allow saga compensation to continue
            }
        });
    }

    /**
     * Debit wallet (final transaction)
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<DebitResponse> debitWallet(String walletId, BigDecimal amount, 
                                                       String currency, String transactionId, String reservationId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Debiting wallet: walletId={}, amount={} {}, transactionId={}", 
                    walletId, amount, currency, transactionId);

                String url = walletServiceUrl + "/api/v1/wallets/" + walletId + "/debit";
                
                Map<String, Object> request = Map.of(
                    "amount", amount,
                    "currency", currency,
                    "transactionId", transactionId,
                    "reservationId", reservationId,
                    "description", "Saga debit operation"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                if (result != null) {
                    return DebitResponse.builder()
                        .transactionId(transactionId)
                        .walletId(walletId)
                        .amount(amount)
                        .currency(currency)
                        .success(Boolean.TRUE.equals(result.get("success")))
                        .newBalance(new BigDecimal(result.getOrDefault("newBalance", "0").toString()))
                        .debitedAt(LocalDateTime.now())
                        .build();
                }
                
                throw new RuntimeException("Empty response from wallet service");
                
            } catch (Exception e) {
                log.error("Failed to debit wallet: walletId={}, amount={}", walletId, amount, e);
                throw new RuntimeException("Wallet debit failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Credit wallet
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<CreditResponse> creditWallet(String walletId, BigDecimal amount, 
                                                         String currency, String transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Crediting wallet: walletId={}, amount={} {}, transactionId={}", 
                    walletId, amount, currency, transactionId);

                String url = walletServiceUrl + "/api/v1/wallets/" + walletId + "/credit";
                
                Map<String, Object> request = Map.of(
                    "amount", amount,
                    "currency", currency,
                    "transactionId", transactionId,
                    "description", "Saga credit operation"
                );
                
                HttpHeaders headers = createHeaders();
                HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                if (result != null) {
                    return CreditResponse.builder()
                        .transactionId(transactionId)
                        .walletId(walletId)
                        .amount(amount)
                        .currency(currency)
                        .success(Boolean.TRUE.equals(result.get("success")))
                        .newBalance(new BigDecimal(result.getOrDefault("newBalance", "0").toString()))
                        .creditedAt(LocalDateTime.now())
                        .build();
                }
                
                throw new RuntimeException("Empty response from wallet service");
                
            } catch (Exception e) {
                log.error("Failed to credit wallet: walletId={}, amount={}", walletId, amount, e);
                throw new RuntimeException("Wallet credit failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Get wallet balance
     */
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 500))
    public CompletableFuture<WalletBalance> getWalletBalance(String walletId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Getting wallet balance: walletId={}", walletId);

                String url = walletServiceUrl + "/api/v1/wallets/" + walletId + "/balance";
                
                HttpHeaders headers = createHeaders();
                HttpEntity<?> httpEntity = new HttpEntity<>(headers);
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, httpEntity, Map.class);
                
                Map<String, Object> result = response.getBody();
                if (result != null) {
                    return WalletBalance.builder()
                        .walletId(walletId)
                        .availableBalance(new BigDecimal(result.get("availableBalance").toString()))
                        .totalBalance(new BigDecimal(result.get("totalBalance").toString()))
                        .reservedBalance(new BigDecimal(result.getOrDefault("reservedBalance", "0").toString()))
                        .currency((String) result.get("currency"))
                        .lastUpdated(LocalDateTime.now())
                        .build();
                }
                
                throw new RuntimeException("Empty response from wallet service");
                
            } catch (Exception e) {
                log.error("Failed to get wallet balance: walletId={}", walletId, e);
                throw new RuntimeException("Failed to get wallet balance: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Health check for wallet service
     */
    public boolean isWalletServiceHealthy() {
        try {
            String url = walletServiceUrl + "/actuator/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("Wallet service health check failed", e);
            return false;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Service-Name", "saga-orchestration-service");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        return headers;
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class ReservationResponse {
        private String reservationId;
        private String walletId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private boolean success;
        private BigDecimal availableBalance;
        private LocalDateTime reservedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class DebitResponse {
        private String transactionId;
        private String walletId;
        private BigDecimal amount;
        private String currency;
        private boolean success;
        private BigDecimal newBalance;
        private LocalDateTime debitedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class CreditResponse {
        private String transactionId;
        private String walletId;
        private BigDecimal amount;
        private String currency;
        private boolean success;
        private BigDecimal newBalance;
        private LocalDateTime creditedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class WalletBalance {
        private String walletId;
        private BigDecimal availableBalance;
        private BigDecimal totalBalance;
        private BigDecimal reservedBalance;
        private String currency;
        private LocalDateTime lastUpdated;
    }
}