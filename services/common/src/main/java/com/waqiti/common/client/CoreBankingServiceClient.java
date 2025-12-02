package com.waqiti.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core Banking Service Client
 * 
 * Provides standardized communication with the Core Banking Service
 */
@Component
@Slf4j
public class CoreBankingServiceClient extends ServiceClient {

    public CoreBankingServiceClient(RestTemplate restTemplate, 
                                   @Value("${services.core-banking-service.url}") String baseUrl) {
        super(restTemplate, baseUrl, "core-banking-service");
    }

    /**
     * Get account balance
     */
    public CompletableFuture<ServiceResponse<AccountBalanceDTO>> getAccountBalance(String accountId) {
        return get("/api/v1/accounts/" + accountId + "/balance", AccountBalanceDTO.class, null);
    }

    /**
     * Get account balance for wallet
     */
    public BigDecimal getWalletBalance(String walletId) {
        try {
            ServiceResponse<AccountBalanceDTO> response = getAccountBalance(walletId).get();
            if (response.isSuccess() && response.getData() != null) {
                return response.getData().getCurrentBalance();
            }
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Failed to get balance for wallet: {}", walletId, e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Account Balance DTO
     */
    public static class AccountBalanceDTO {
        private String accountId;
        private BigDecimal currentBalance;
        private BigDecimal availableBalance;
        private BigDecimal reservedBalance;
        private String currency;
        private String status;

        // Getters and setters
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
        
        public BigDecimal getAvailableBalance() { return availableBalance; }
        public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }
        
        public BigDecimal getReservedBalance() { return reservedBalance; }
        public void setReservedBalance(BigDecimal reservedBalance) { this.reservedBalance = reservedBalance; }
        
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    @Override
    protected String getCurrentCorrelationId() {
        return org.slf4j.MDC.get("correlationId");
    }
    
    @Override
    protected String getCurrentAuthToken() {
        return org.springframework.security.core.context.SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getCredentials()
            .toString();
    }
}