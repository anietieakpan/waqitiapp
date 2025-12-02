package com.waqiti.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when a wallet cannot be found
 */
@Getter
@ResponseStatus(HttpStatus.NOT_FOUND)
public class WalletNotFoundException extends WalletServiceException {
    
    private final String walletId;
    private final String userId;
    private final SearchCriteria searchCriteria;
    private final LocalDateTime attemptedAt;
    
    public enum SearchCriteria {
        WALLET_ID,
        USER_ID,
        PHONE_NUMBER,
        EMAIL,
        ACCOUNT_NUMBER,
        EXTERNAL_ID
    }
    
    public WalletNotFoundException(String walletId) {
        super(String.format("Wallet not found: %s", walletId),
              "WALLET_NOT_FOUND",
              HttpStatus.NOT_FOUND,
              walletId,
              ErrorCategory.WALLET_NOT_FOUND);
        this.walletId = walletId;
        this.userId = null;
        this.searchCriteria = SearchCriteria.WALLET_ID;
        this.attemptedAt = LocalDateTime.now();
    }
    
    public WalletNotFoundException(String identifier, SearchCriteria criteria) {
        super(String.format("Wallet not found by %s: %s", criteria.name().toLowerCase().replace('_', ' '), identifier),
              "WALLET_NOT_FOUND",
              HttpStatus.NOT_FOUND,
              criteria == SearchCriteria.WALLET_ID ? identifier : null,
              ErrorCategory.WALLET_NOT_FOUND);
        this.walletId = criteria == SearchCriteria.WALLET_ID ? identifier : null;
        this.userId = criteria == SearchCriteria.USER_ID ? identifier : null;
        this.searchCriteria = criteria;
        this.attemptedAt = LocalDateTime.now();
        
        withDetail("searchCriteria", criteria.name());
        withDetail("searchValue", identifier);
    }
    
    public WalletNotFoundException(String message, String walletId, String userId) {
        super(message,
              "WALLET_NOT_FOUND",
              HttpStatus.NOT_FOUND,
              walletId,
              ErrorCategory.WALLET_NOT_FOUND);
        this.walletId = walletId;
        this.userId = userId;
        this.searchCriteria = walletId != null ? SearchCriteria.WALLET_ID : SearchCriteria.USER_ID;
        this.attemptedAt = LocalDateTime.now();
    }
    
    @Override
    public Map<String, Object> toErrorResponse() {
        Map<String, Object> response = super.toErrorResponse();
        
        Map<String, Object> notFoundDetails = new HashMap<>();
        notFoundDetails.put("walletId", walletId);
        notFoundDetails.put("userId", userId);
        notFoundDetails.put("searchCriteria", searchCriteria);
        notFoundDetails.put("attemptedAt", attemptedAt);
        
        response.put("notFoundDetails", notFoundDetails);
        
        return response;
    }
}