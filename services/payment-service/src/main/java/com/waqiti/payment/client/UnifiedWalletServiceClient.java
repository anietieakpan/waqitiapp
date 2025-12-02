package com.waqiti.payment.client;

import com.waqiti.payment.dto.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class UnifiedWalletServiceClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${services.wallet-service.url:http://wallet-service}")
    private String walletServiceUrl;
    
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "getBalanceFallback")
    @Retry(name = "wallet-service")
    public WalletBalanceResponse getBalance(String userId) {
        try {
            log.debug("Getting wallet balance for user: {}", userId);
            
            String url = walletServiceUrl + "/api/v1/wallets/" + userId + "/balance";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "payment-service");
            headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
            
            HttpEntity<?> httpEntity = new HttpEntity<>(headers);
            
            ResponseEntity<WalletBalanceResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                httpEntity,
                WalletBalanceResponse.class
            );
            
            log.debug("Retrieved wallet balance for user: {}", userId);
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to get wallet balance for user: {}", userId, e);
            throw new RuntimeException("Failed to get wallet balance", e);
        }
    }
    
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "debitWalletFallback")
    @Retry(name = "wallet-service")
    public BalanceTransactionResult debitWallet(WalletDebitRequest request) {
        try {
            log.info("Debiting wallet: userId={}, amount={}", request.getUserId(), request.getAmount());
            
            String url = walletServiceUrl + "/api/v1/wallets/debit";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "payment-service");
            headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
            
            HttpEntity<WalletDebitRequest> httpEntity = new HttpEntity<>(request, headers);
            
            ResponseEntity<BalanceTransactionResult> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                BalanceTransactionResult.class
            );
            
            log.info("Wallet debited successfully: userId={}", request.getUserId());
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to debit wallet: userId={}", request.getUserId(), e);
            throw new RuntimeException("Failed to debit wallet", e);
        }
    }
    
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "creditWalletFallback")
    @Retry(name = "wallet-service")
    public BalanceTransactionResult creditWallet(WalletCreditRequest request) {
        try {
            log.info("Crediting wallet: userId={}, amount={}", request.getUserId(), request.getAmount());
            
            String url = walletServiceUrl + "/api/v1/wallets/credit";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "payment-service");
            headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
            
            HttpEntity<WalletCreditRequest> httpEntity = new HttpEntity<>(request, headers);
            
            ResponseEntity<BalanceTransactionResult> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                BalanceTransactionResult.class
            );
            
            log.info("Wallet credited successfully: userId={}", request.getUserId());
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to credit wallet: userId={}", request.getUserId(), e);
            throw new RuntimeException("Failed to credit wallet", e);
        }
    }
    
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "transferFundsFallback")
    @Retry(name = "wallet-service")
    public BalanceTransferResult transferFunds(BalanceTransferRequest request) {
        try {
            log.info("Transferring funds: from={}, to={}, amount={}", 
                request.getFromUserId(), request.getToUserId(), request.getAmount());
            
            String url = walletServiceUrl + "/api/v1/wallets/transfer";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "payment-service");
            headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
            
            HttpEntity<BalanceTransferRequest> httpEntity = new HttpEntity<>(request, headers);
            
            ResponseEntity<BalanceTransferResult> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                BalanceTransferResult.class
            );
            
            log.info("Funds transferred successfully");
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to transfer funds", e);
            throw new RuntimeException("Failed to transfer funds", e);
        }
    }
    
    @CircuitBreaker(name = "wallet-service", fallbackMethod = "reserveBalanceFallback")
    @Retry(name = "wallet-service")
    public BalanceReservationResult reserveBalance(BalanceReservationRequest request) {
        try {
            log.info("Reserving balance: userId={}, amount={}", request.getUserId(), request.getAmount());
            
            String url = walletServiceUrl + "/api/v1/wallets/reserve";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Service-Name", "payment-service");
            headers.set("X-Request-ID", java.util.UUID.randomUUID().toString());
            
            HttpEntity<BalanceReservationRequest> httpEntity = new HttpEntity<>(request, headers);
            
            ResponseEntity<BalanceReservationResult> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                httpEntity,
                BalanceReservationResult.class
            );
            
            log.info("Balance reserved successfully: userId={}", request.getUserId());
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to reserve balance: userId={}", request.getUserId(), e);
            throw new RuntimeException("Failed to reserve balance", e);
        }
    }
    
    public WalletBalanceResponse getBalanceFallback(String userId, Exception ex) {
        log.error("Wallet service unavailable, using fallback for getBalance: userId={}", userId, ex);
        
        return WalletBalanceResponse.builder()
            .userId(userId)
            .availableBalance(BigDecimal.ZERO)
            .totalBalance(BigDecimal.ZERO)
            .currency("USD")
            .serviceUnavailable(true)
            .build();
    }
    
    public BalanceTransactionResult debitWalletFallback(WalletDebitRequest request, Exception ex) {
        log.error("Wallet service unavailable, using fallback for debitWallet: userId={}", request.getUserId(), ex);
        
        return BalanceTransactionResult.builder()
            .success(false)
            .transactionId(null)
            .errorMessage("Wallet service unavailable")
            .build();
    }
    
    public BalanceTransactionResult creditWalletFallback(WalletCreditRequest request, Exception ex) {
        log.error("Wallet service unavailable, using fallback for creditWallet: userId={}", request.getUserId(), ex);
        
        return BalanceTransactionResult.builder()
            .success(false)
            .transactionId(null)
            .errorMessage("Wallet service unavailable")
            .build();
    }
    
    public BalanceTransferResult transferFundsFallback(BalanceTransferRequest request, Exception ex) {
        log.error("Wallet service unavailable, using fallback for transferFunds", ex);
        
        return BalanceTransferResult.builder()
            .success(false)
            .transactionId(null)
            .errorMessage("Wallet service unavailable")
            .build();
    }
    
    public BalanceReservationResult reserveBalanceFallback(BalanceReservationRequest request, Exception ex) {
        log.error("Wallet service unavailable, using fallback for reserveBalance: userId={}", request.getUserId(), ex);
        
        return BalanceReservationResult.builder()
            .success(false)
            .reservationId(null)
            .errorMessage("Wallet service unavailable")
            .build();
    }
}