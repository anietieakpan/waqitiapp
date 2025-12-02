package com.waqiti.payment.client;

import com.waqiti.payment.merchant.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Client for communication with merchant-payment-service.
 * Handles merchant payment operations, settlements, holds, and reserves.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantPaymentServiceClient {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${merchant.payment.service.base-url:http://merchant-payment-service:8080}")
    private String merchantPaymentServiceBaseUrl;
    
    @Value("${merchant.payment.service.timeout.seconds:10}")
    private int timeoutSeconds;
    
    @Value("${merchant.payment.service.api-key:}")
    private String apiKey;
    
    private WebClient webClient;
    
    @PostConstruct
    public void init() {
        this.webClient = webClientBuilder
            .baseUrl(merchantPaymentServiceBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-Service-Name", "payment-service")
            .defaultHeader("X-API-Key", apiKey)
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024)) // 10MB buffer
            .build();
        
        log.info("MerchantPaymentServiceClient initialized with base URL: {}", merchantPaymentServiceBaseUrl);
    }
    
    /**
     * Get merchant balance
     */
    @CircuitBreaker(name = "merchant-payment-service", fallbackMethod = "getMerchantBalanceFallback")
    @Retry(name = "merchant-payment-service")
    public MerchantBalance getMerchantBalance(String merchantId) {
        log.debug("Fetching balance for merchant: {}", merchantId);
        
        return webClient.get()
            .uri("/api/v1/merchants/{merchantId}/balance", merchantId)
            .retrieve()
            .bodyToMono(MerchantBalance.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(balance -> log.debug("Retrieved balance for merchant {}: {}", 
                merchantId, balance.getAvailableBalance()))
            .doOnError(error -> log.error("Error fetching balance for merchant {}: {}", 
                merchantId, error.getMessage()))
            .block();
    }
    
    /**
     * Debit merchant account
     */
    @CircuitBreaker(name = "merchant-payment-service")
    @Retry(name = "merchant-payment-service")
    @TimeLimiter(name = "merchant-payment-service")
    public CompletableFuture<DebitMerchantResponse> debitMerchantAsync(DebitMerchantRequest request) {
        log.info("Processing debit for merchant {} amount: {}", 
            request.getMerchantId(), request.getAmount());
        
        return webClient.post()
            .uri("/api/v1/merchants/{merchantId}/debit", request.getMerchantId())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(DebitMerchantResponse.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(response -> {
                if (response.isSuccess()) {
                    log.info("Successfully debited merchant {} transaction: {}", 
                        request.getMerchantId(), response.getTransactionId());
                } else {
                    log.error("Failed to debit merchant {}: {}", 
                        request.getMerchantId(), response.getError());
                }
            })
            .toFuture();
    }
    
    /**
     * Debit merchant account (synchronous)
     */
    public DebitMerchantResponse debitMerchant(DebitMerchantRequest request) {
        return debitMerchantAsync(request).join();
    }
    
    /**
     * Credit merchant account
     */
    @CircuitBreaker(name = "merchant-payment-service")
    @Retry(name = "merchant-payment-service")
    public CreditMerchantResponse creditMerchant(CreditMerchantRequest request) {
        log.info("Processing credit for merchant {} amount: {}", 
            request.getMerchantId(), request.getAmount());
        
        return webClient.post()
            .uri("/api/v1/merchants/{merchantId}/credit", request.getMerchantId())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CreditMerchantResponse.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(response -> log.info("Credited merchant {} transaction: {}", 
                request.getMerchantId(), response.getTransactionId()))
            .block();
    }
    
    /**
     * Place hold on merchant funds
     */
    @CircuitBreaker(name = "merchant-payment-service")
    @Retry(name = "merchant-payment-service")
    public PlaceHoldResponse placeHold(PlaceHoldRequest request) {
        log.debug("Placing hold on merchant {} amount: {} reason: {}", 
            request.getMerchantId(), request.getAmount(), request.getReason());
        
        return webClient.post()
            .uri("/api/v1/merchants/{merchantId}/holds", request.getMerchantId())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(PlaceHoldResponse.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(response -> log.info("Hold placed: {} for merchant {}", 
                response.getHoldId(), request.getMerchantId()))
            .onErrorResume(WebClientResponseException.class, e -> {
                log.error("Failed to place hold for merchant {}: {} - {}", 
                    request.getMerchantId(), e.getStatusCode(), e.getResponseBodyAsString());
                return Mono.error(new MerchantPaymentException("Failed to place hold", e));
            })
            .block();
    }
    
    /**
     * Release hold on merchant funds
     */
    @Retry(name = "merchant-payment-service")
    public void releaseHold(String merchantId, String holdId) {
        log.debug("Releasing hold {} for merchant {}", holdId, merchantId);
        
        try {
            webClient.delete()
                .uri("/api/v1/merchants/{merchantId}/holds/{holdId}", merchantId, holdId)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(v -> log.info("Released hold {} for merchant {}", holdId, merchantId))
                .block();
        } catch (Exception e) {
            log.error("Failed to release hold {} for merchant {}: {}", 
                holdId, merchantId, e.getMessage());
            // Don't throw - hold release is not critical
        }
    }
    
    /**
     * Create reserve
     */
    @CircuitBreaker(name = "merchant-payment-service")
    public CreateReserveResponse createReserve(CreateReserveRequest request) {
        log.info("Creating reserve for merchant {} amount: {} type: {}", 
            request.getMerchantId(), request.getAmount(), request.getType());
        
        return webClient.post()
            .uri("/api/v1/merchants/{merchantId}/reserves", request.getMerchantId())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CreateReserveResponse.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(response -> log.info("Reserve created: {} for merchant {}", 
                response.getReserveId(), request.getMerchantId()))
            .block();
    }
    
    /**
     * Release reserve
     */
    public void releaseReserve(String merchantId, String reserveId) {
        log.debug("Releasing reserve {} for merchant {}", reserveId, merchantId);
        
        try {
            webClient.delete()
                .uri("/api/v1/merchants/{merchantId}/reserves/{reserveId}", merchantId, reserveId)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(v -> log.info("Released reserve {} for merchant {}", reserveId, merchantId))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to release reserve {} for merchant {}: {}", 
                reserveId, merchantId, e.getMessage());
        }
    }
    
    /**
     * Create dispute record
     */
    @CircuitBreaker(name = "merchant-payment-service")
    @Retry(name = "merchant-payment-service")
    public DisputeResponse createDispute(CreateDisputeRequest request) {
        log.info("Creating dispute for merchant {} dispute: {} amount: {}", 
            request.getMerchantId(), request.getDisputeId(), request.getAmount());
        
        return webClient.post()
            .uri("/api/v1/merchants/{merchantId}/disputes", request.getMerchantId())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(DisputeResponse.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(response -> log.info("Dispute created: {} for merchant {}", 
                response.getDisputeRecordId(), request.getMerchantId()))
            .block();
    }
    
    /**
     * Resolve dispute
     */
    @Retry(name = "merchant-payment-service")
    public void resolveDispute(String merchantId, String disputeId, ResolveDisputeRequest request) {
        log.info("Resolving dispute {} for merchant {} outcome: {}", 
            disputeId, merchantId, request.getOutcome());
        
        webClient.put()
            .uri("/api/v1/merchants/{merchantId}/disputes/{disputeId}/resolve", merchantId, disputeId)
            .bodyValue(request)
            .retrieve()
            .toBodilessEntity()
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .doOnSuccess(v -> log.info("Resolved dispute {} for merchant {}", disputeId, merchantId))
            .subscribe();
    }
    
    /**
     * Create negative balance record
     */
    public void createNegativeBalance(CreateNegativeBalanceRequest request) {
        log.warn("Creating negative balance record for merchant {} amount: {}", 
            request.getMerchantId(), request.getAmount());
        
        try {
            webClient.post()
                .uri("/api/v1/merchants/{merchantId}/negative-balance", request.getMerchantId())
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(v -> log.info("Negative balance recorded for merchant {}", 
                    request.getMerchantId()))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to create negative balance for merchant {}: {}", 
                request.getMerchantId(), e.getMessage());
        }
    }
    
    /**
     * Get merchant settlement details
     */
    @Cacheable(value = "merchantSettlement", key = "#merchantId")
    public MerchantSettlementInfo getSettlementInfo(String merchantId) {
        log.debug("Fetching settlement info for merchant: {}", merchantId);
        
        return webClient.get()
            .uri("/api/v1/merchants/{merchantId}/settlement-info", merchantId)
            .retrieve()
            .bodyToMono(MerchantSettlementInfo.class)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .onErrorResume(e -> {
                log.error("Error fetching settlement info for merchant {}: {}", 
                    merchantId, e.getMessage());
                return Mono.just(MerchantSettlementInfo.defaultInfo());
            })
            .block();
    }
    
    /**
     * Process settlement
     */
    @CircuitBreaker(name = "merchant-payment-service")
    public SettlementResponse processSettlement(ProcessSettlementRequest request) {
        log.info("Processing settlement for merchant {} amount: {}", 
            request.getMerchantId(), request.getAmount());
        
        return webClient.post()
            .uri("/api/v1/merchants/{merchantId}/settlements", request.getMerchantId())
            .bodyValue(request)
            .retrieve()
            .bodyToMono(SettlementResponse.class)
            .timeout(Duration.ofSeconds(timeoutSeconds * 2)) // Longer timeout for settlements
            .doOnSuccess(response -> log.info("Settlement processed: {} for merchant {}", 
                response.getSettlementId(), request.getMerchantId()))
            .block();
    }
    
    /**
     * Reverse a debit transaction
     */
    public void reverseMerchantDebit(String merchantId, String transactionId) {
        log.info("Reversing debit transaction {} for merchant {}", transactionId, merchantId);
        
        try {
            ReverseDebitRequest request = ReverseDebitRequest.builder()
                .merchantId(merchantId)
                .originalTransactionId(transactionId)
                .reason("SAGA_COMPENSATION")
                .build();
            
            webClient.post()
                .uri("/api/v1/merchants/{merchantId}/reverse-debit", merchantId)
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .doOnSuccess(v -> log.info("Successfully reversed debit {} for merchant {}", 
                    transactionId, merchantId))
                .subscribe();
        } catch (Exception e) {
            log.error("Failed to reverse debit {} for merchant {}: {}", 
                transactionId, merchantId, e.getMessage());
        }
    }
    
    /**
     * Fallback method for getMerchantBalance
     */
    public MerchantBalance getMerchantBalanceFallback(String merchantId, Exception ex) {
        log.warn("Using fallback for merchant balance {}: {}", merchantId, ex.getMessage());
        
        // Return a safe fallback balance that prevents operations
        return MerchantBalance.builder()
            .merchantId(merchantId)
            .totalBalance(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .heldAmount(BigDecimal.ZERO)
            .reservedAmount(BigDecimal.ZERO)
            .isStale(true)
            .build();
    }
    
    /**
     * Custom exception for merchant payment service errors
     */
    public static class MerchantPaymentException extends RuntimeException {
        public MerchantPaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}