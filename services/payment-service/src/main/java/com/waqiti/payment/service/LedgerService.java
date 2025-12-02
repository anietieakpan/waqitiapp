package com.waqiti.payment.service;

import com.waqiti.common.client.LedgerServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Service for interacting with the Ledger service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {
    
    private final LedgerServiceClient ledgerClient;
    private final WebClient.Builder webClientBuilder;
    
    @Value("${ledger-service.url:http://localhost:8083}")
    private String ledgerServiceUrl;
    
    private WebClient webClient;
    
    private WebClient getWebClient() {
        if (webClient == null) {
            webClient = webClientBuilder.baseUrl(ledgerServiceUrl).build();
        }
        return webClient;
    }
    
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordBatchSettlementFallback")
    @Retry(name = "ledger-service")
    public Object recordBatchSettlement(String batchId, BigDecimal totalAmount, Map<String, Object> details) {
        log.info("Recording batch settlement in ledger: batchId={} amount={}", batchId, totalAmount);
        
        try {
            Map<String, Object> request = Map.of(
                    "batchId", batchId,
                    "amount", totalAmount.toString(),
                    "details", details,
                    "timestamp", System.currentTimeMillis()
            );
            
            Object result = getWebClient().post()
                    .uri("/api/v1/ledger/batch-settlement")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            log.info("Successfully recorded batch settlement in ledger");
            return result;
            
        } catch (Exception e) {
            log.error("Failed to record batch settlement in ledger", e);
            return recordBatchSettlementFallback(batchId, totalAmount, details, e);
        }
    }
    
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordPaymentEntryFallback")
    @Retry(name = "ledger-service")
    public void recordPaymentEntry(String paymentId, String fromAccount, String toAccount, BigDecimal amount) {
        log.debug("Recording payment entry in ledger: paymentId={}", paymentId);
        
        try {
            Map<String, Object> request = Map.of(
                    "paymentId", paymentId,
                    "fromAccount", fromAccount,
                    "toAccount", toAccount,
                    "amount", amount.toString()
            );
            
            getWebClient().post()
                    .uri("/api/v1/ledger/payment-entry")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();
            
            log.debug("Successfully recorded payment entry in ledger");
            
        } catch (Exception e) {
            log.error("Failed to record payment entry in ledger", e);
            recordPaymentEntryFallback(paymentId, fromAccount, toAccount, amount, e);
        }
    }
    
    private Object recordBatchSettlementFallback(String batchId, BigDecimal totalAmount, 
                                                Map<String, Object> details, Exception e) {
        log.warn("Ledger service unavailable - batch settlement not recorded (fallback): {}", batchId);
        return Map.of("status", "PENDING_LEDGER", "batchId", batchId);
    }
    
    private void recordPaymentEntryFallback(String paymentId, String fromAccount, 
                                          String toAccount, BigDecimal amount, Exception e) {
        log.warn("Ledger service unavailable - payment entry not recorded (fallback): {}", paymentId);
    }
    
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "createCompensatingEntriesFallback")
    @Retry(name = "ledger-service")
    public void createCompensatingEntries(String paymentId, BigDecimal amount, String description,
                                          String senderAccountId, String recipientAccountId) {
        log.info("Creating compensating ledger entries for payment: {}", paymentId);
        
        try {
            Map<String, Object> request = Map.of(
                "paymentId", paymentId,
                "amount", amount.toString(),
                "description", description,
                "senderAccountId", senderAccountId != null ? senderAccountId : "",
                "recipientAccountId", recipientAccountId != null ? recipientAccountId : "",
                "entryType", "COMPENSATING"
            );
            
            getWebClient().post()
                .uri("/api/v1/ledger/compensating-entry")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(15))
                .block();
            
            log.info("Successfully created compensating ledger entries");
            
        } catch (Exception e) {
            log.error("Failed to create compensating ledger entries", e);
            createCompensatingEntriesFallback(paymentId, amount, description, senderAccountId, recipientAccountId, e);
        }
    }
    
    private void createCompensatingEntriesFallback(String paymentId, BigDecimal amount, String description,
                                                   String senderAccountId, String recipientAccountId, Exception e) {
        log.warn("Ledger service unavailable - compensating entries not created (fallback): {}", paymentId);
    }
}