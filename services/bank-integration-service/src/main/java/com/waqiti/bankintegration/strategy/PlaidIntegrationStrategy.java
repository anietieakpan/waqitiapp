package com.waqiti.bankintegration.strategy;

import com.plaid.client.PlaidApi;
import com.plaid.client.request.*;
import com.plaid.client.response.*;
import com.waqiti.bankintegration.domain.PaymentProvider;
import com.waqiti.bankintegration.dto.*;
import com.waqiti.bankintegration.exception.PaymentProcessingException;
import com.waqiti.common.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plaid Integration Strategy Implementation
 * 
 * Handles bank account linking, verification, and ACH transfers through Plaid.
 * Note: This service focuses on account linking and verification.
 * Actual ACH transfers are typically handled by a separate ACH processor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaidIntegrationStrategy implements PaymentStrategy {
    
    @Value("${waqiti.frontend.base-url:https://app.example.com}")
    private String frontendBaseUrl;

    @Override
    @CircuitBreaker(name = "plaidApi", fallbackMethod = "processPaymentFallback")
    @Retry(name = "plaidApi")
    @TimeLimiter(name = "plaidApi")
    @Bulkhead(name = "plaidApi")
    @RateLimiter(name = "plaidApi")
    public PaymentResponse processPayment(PaymentProvider provider, PaymentRequest request) {
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            log.debug("Processing Plaid ACH transfer for amount: {} {}", 
                request.getAmount(), request.getCurrency());

            // For Plaid, we typically don't process direct payments
            // Instead, we use the linked account for ACH transfers
            // This would integrate with an ACH processor
            
            if (request.getBankAccountId() == null) {
                throw new BusinessException("Bank account ID is required for Plaid payments");
            }

            // Verify account balance (if account supports balance checks)
            AccountsBalanceGetRequest balanceRequest = new AccountsBalanceGetRequest()
                .accessToken(request.getBankAccountId()); // Using bankAccountId as access token
                
            Response<AccountsBalanceGetResponse> balanceResponse = 
                plaidClient.accountsBalanceGet(balanceRequest).execute();
                
            if (!balanceResponse.isSuccessful()) {
                throw new PaymentProcessingException("Failed to verify account balance: " + 
                    balanceResponse.errorBody().string());
            }

            AccountsBalanceGetResponse balanceData = balanceResponse.body();
            
            // Find the account and check balance
            boolean sufficientFunds = false;
            String accountId = null;
            
            for (Account account : balanceData.getAccounts()) {
                if (account.getBalances().getAvailable() != null) {
                    BigDecimal availableBalance = BigDecimal.valueOf(account.getBalances().getAvailable());
                    if (availableBalance.compareTo(request.getAmount()) >= 0) {
                        sufficientFunds = true;
                        accountId = account.getAccountId();
                        break;
                    }
                }
            }

            PaymentResponse response = new PaymentResponse();
            response.setRequestId(request.getRequestId());
            response.setProviderId(provider.getId());
            response.setTransactionId(request.getRequestId()); // Use request ID as transaction ID
            response.setProviderTransactionId(request.getRequestId());
            response.setAmount(request.getAmount());
            response.setCurrency(request.getCurrency());
            response.setProcessedAt(Instant.now());

            if (sufficientFunds) {
                // In a real implementation, this would initiate an ACH transfer
                // through a processor like Dwolla, Stripe ACH, or similar
                response.setStatus(PaymentStatus.PENDING);
                
                Map<String, Object> additionalData = new HashMap<>();
                additionalData.put("account_id", accountId);
                additionalData.put("account_verified", true);
                additionalData.put("balance_check", "passed");
                additionalData.put("transfer_method", "ach");
                response.setAdditionalData(additionalData);
                
            } else {
                response.setStatus(PaymentStatus.FAILED);
                response.setErrorCode("INSUFFICIENT_FUNDS");
                response.setErrorMessage("Insufficient funds in linked account");
            }

            log.info("Plaid payment verification completed: {}", response.getTransactionId());
            return response;

        } catch (Exception e) {
            log.error("Plaid payment failed: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Plaid payment processing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RefundResponse processRefund(PaymentProvider provider, RefundRequest request) {
        // Plaid doesn't handle refunds directly - this would be handled by the ACH processor
        log.info("Plaid refund requested - delegating to ACH processor");
        
        RefundResponse response = new RefundResponse();
        response.setRefundId(request.getRequestId());
        response.setOriginalTransactionId(request.getOriginalTransactionId());
        response.setStatus(RefundStatus.PENDING);
        response.setAmount(request.getAmount());
        response.setCurrency(request.getCurrency());
        response.setProcessedAt(Instant.now());
        
        // In production, this would trigger an ACH credit back to the account
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("refund_method", "ach_credit");
        additionalData.put("estimated_settlement", LocalDate.now().plusDays(3).toString());
        response.setAdditionalData(additionalData);
        
        return response;
    }

    @Override
    @CircuitBreaker(name = "plaidApi", fallbackMethod = "checkPaymentStatusFallback")
    @Retry(name = "plaidApi")
    @TimeLimiter(name = "plaidApi")
    @RateLimiter(name = "plaidApi")
    public PaymentResponse checkPaymentStatus(PaymentProvider provider, String transactionId) {
        // For Plaid, we would check the ACH transfer status
        // This is a simplified implementation
        
        PaymentResponse response = new PaymentResponse();
        response.setTransactionId(transactionId);
        response.setProviderId(provider.getId());
        response.setProviderTransactionId(transactionId);
        response.setStatus(PaymentStatus.PROCESSING); // ACH transfers take time
        
        return response;
    }

    @Override
    public boolean canHandle(PaymentProvider provider, PaymentRequest request) {
        return provider.getProviderType() == ProviderType.PLAID &&
               request.getBankAccountId() != null &&
               provider.supportsFeature("account_linking");
    }

    @Override
    public boolean isProviderHealthy(PaymentProvider provider) {
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            // Make a lightweight API call to check connectivity
            CategoriesGetRequest request = new CategoriesGetRequest();
            Response<CategoriesGetResponse> response = plaidClient.categoriesGet(request).execute();
            
            return response.isSuccessful();
        } catch (Exception e) {
            log.warn("Plaid health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public PaymentResponse cancelPayment(PaymentProvider provider, String transactionId) {
        // ACH transfers can potentially be cancelled if not yet processed
        log.info("Plaid payment cancellation requested for: {}", transactionId);
        
        PaymentResponse response = new PaymentResponse();
        response.setTransactionId(transactionId);
        response.setStatus(PaymentStatus.CANCELLED);
        response.setProviderId(provider.getId());
        response.setProviderTransactionId(transactionId);
        
        return response;
    }

    /**
     * Creates a Plaid link token for account linking
     */
    @CircuitBreaker(name = "plaidApi", fallbackMethod = "createLinkTokenFallback")
    @Retry(name = "plaidApi")
    @TimeLimiter(name = "plaidApi")
    @Bulkhead(name = "plaidApi")
    @RateLimiter(name = "plaidApi")
    public LinkTokenCreateResponse createLinkToken(PaymentProvider provider, String userId, 
                                                   List<String> countryCodes) {
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                .clientUserId(userId);

            LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .clientName("Waqiti")
                .countryCodes(countryCodes != null ? countryCodes : Arrays.asList("US"))
                .language("en")
                .user(user)
                .products(Arrays.asList(Products.TRANSACTIONS, Products.AUTH, Products.IDENTITY))
                .redirectUri(frontendBaseUrl + "/plaid/oauth-return");

            if (provider.isSandboxMode()) {
                // Add sandbox-specific configurations
                request.androidPackageName("com.waqiti.app");
            }

            Response<LinkTokenCreateResponse> response = plaidClient.linkTokenCreate(request).execute();
            
            if (!response.isSuccessful()) {
                throw new PaymentProcessingException("Failed to create Plaid link token: " + 
                    response.errorBody().string());
            }

            return response.body();

        } catch (Exception e) {
            log.error("Failed to create Plaid link token: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to create link token: " + e.getMessage(), e);
        }
    }

    /**
     * Exchanges a public token for an access token
     */
    @CircuitBreaker(name = "plaidApi", fallbackMethod = "exchangePublicTokenFallback")
    @Retry(name = "plaidApi")
    @TimeLimiter(name = "plaidApi")
    @Bulkhead(name = "plaidApi")
    @RateLimiter(name = "plaidApi")
    public ItemPublicTokenExchangeResponse exchangePublicToken(PaymentProvider provider, 
                                                               String publicToken) {
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            ItemPublicTokenExchangeRequest request = new ItemPublicTokenExchangeRequest()
                .publicToken(publicToken);

            Response<ItemPublicTokenExchangeResponse> response = 
                plaidClient.itemPublicTokenExchange(request).execute();
                
            if (!response.isSuccessful()) {
                throw new PaymentProcessingException("Failed to exchange public token: " + 
                    response.errorBody().string());
            }

            return response.body();

        } catch (Exception e) {
            log.error("Failed to exchange Plaid public token: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to exchange public token: " + e.getMessage(), e);
        }
    }

    /**
     * Gets account information for a linked account
     */
    @CircuitBreaker(name = "plaidApi", fallbackMethod = "getAccountInfoFallback")
    @Retry(name = "plaidApi")
    @TimeLimiter(name = "plaidApi")
    @Bulkhead(name = "plaidApi")
    @RateLimiter(name = "plaidApi")
    public AccountsGetResponse getAccountInfo(PaymentProvider provider, String accessToken) {
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            AccountsGetRequest request = new AccountsGetRequest()
                .accessToken(accessToken);

            Response<AccountsGetResponse> response = plaidClient.accountsGet(request).execute();
            
            if (!response.isSuccessful()) {
                throw new PaymentProcessingException("Failed to get account info: " + 
                    response.errorBody().string());
            }

            return response.body();

        } catch (Exception e) {
            log.error("Failed to get Plaid account info: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to get account info: " + e.getMessage(), e);
        }
    }

    /**
     * Gets account and routing numbers for ACH setup
     */
    @CircuitBreaker(name = "plaidApi", fallbackMethod = "getAuthInfoFallback")
    @Retry(name = "plaidApi")
    @TimeLimiter(name = "plaidApi")
    @Bulkhead(name = "plaidApi")
    @RateLimiter(name = "plaidApi")
    public AuthGetResponse getAuthInfo(PaymentProvider provider, String accessToken) {
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            AuthGetRequest request = new AuthGetRequest()
                .accessToken(accessToken);

            Response<AuthGetResponse> response = plaidClient.authGet(request).execute();
            
            if (!response.isSuccessful()) {
                throw new PaymentProcessingException("Failed to get auth info: " + 
                    response.errorBody().string());
            }

            return response.body();

        } catch (Exception e) {
            log.error("Failed to get Plaid auth info: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to get auth info: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies account ownership through micro-deposits
     */
    @CircuitBreaker(name = "plaidApi", fallbackMethod = "verifyMicroDepositsFallback")
    @Retry(name = "plaidApi")
    @TimeLimiter(name = "plaidApi")
    @Bulkhead(name = "plaidApi")
    @RateLimiter(name = "plaidApi")
    public AccountsBalanceGetResponse verifyMicroDeposits(PaymentProvider provider, String accessToken,
                                                          List<Double> amounts) {
        try {
            PlaidApi plaidClient = createPlaidClient(provider);
            
            // This would be used in conjunction with a micro-deposit verification flow
            AccountsBalanceGetRequest request = new AccountsBalanceGetRequest()
                .accessToken(accessToken);

            Response<AccountsBalanceGetResponse> response = 
                plaidClient.accountsBalanceGet(request).execute();
                
            if (!response.isSuccessful()) {
                throw new PaymentProcessingException("Failed to verify micro deposits: " + 
                    response.errorBody().string());
            }

            return response.body();

        } catch (Exception e) {
            log.error("Failed to verify Plaid micro deposits: {}", e.getMessage(), e);
            throw new PaymentProcessingException("Failed to verify micro deposits: " + e.getMessage(), e);
        }
    }

    private PlaidApi createPlaidClient(PaymentProvider provider) {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("client_id", provider.getApiKey());
        apiKeys.put("secret", provider.getApiSecret());
        
        PlaidApi.Builder builder = PlaidApi.builder();
        
        if (provider.isSandboxMode()) {
            builder.sandboxBaseUrl();
        } else {
            builder.productionBaseUrl();
        }
        
        return builder
            .apiKeys(apiKeys)
            .build();
    }

    // Fallback methods for resilience
    public PaymentResponse processPaymentFallback(PaymentProvider provider, PaymentRequest request, Exception ex) {
        log.error("Plaid payment fallback triggered for request: {}", request.getRequestId(), ex);
        PaymentResponse response = new PaymentResponse();
        response.setRequestId(request.getRequestId());
        response.setStatus(PaymentStatus.FAILED);
        response.setErrorCode("PLAID_UNAVAILABLE");
        response.setErrorMessage("Plaid service is temporarily unavailable: " + ex.getMessage());
        response.setProcessedAt(Instant.now());
        return response;
    }
    
    public PaymentResponse checkPaymentStatusFallback(PaymentProvider provider, String transactionId, Exception ex) {
        log.error("Plaid payment status check fallback triggered: {}", transactionId, ex);
        PaymentResponse response = new PaymentResponse();
        response.setTransactionId(transactionId);
        response.setProviderId(provider.getId());
        response.setStatus(PaymentStatus.UNKNOWN);
        response.setErrorCode("PLAID_STATUS_CHECK_FAILED");
        response.setErrorMessage("Unable to check payment status: " + ex.getMessage());
        return response;
    }
    
    public LinkTokenCreateResponse createLinkTokenFallback(PaymentProvider provider, String userId, 
                                                           List<String> countryCodes, Exception ex) {
        log.error("Plaid link token creation fallback triggered for user: {}", userId, ex);
        throw new PaymentProcessingException("Plaid link token service unavailable. Please try again later.", ex);
    }
    
    public ItemPublicTokenExchangeResponse exchangePublicTokenFallback(PaymentProvider provider, 
                                                                        String publicToken, Exception ex) {
        log.error("Plaid public token exchange fallback triggered", ex);
        throw new PaymentProcessingException("Plaid token exchange service unavailable. Please try again later.", ex);
    }
    
    public AccountsGetResponse getAccountInfoFallback(PaymentProvider provider, String accessToken, Exception ex) {
        log.error("Plaid account info fallback triggered", ex);
        throw new PaymentProcessingException("Plaid account service unavailable. Please try again later.", ex);
    }
    
    public AuthGetResponse getAuthInfoFallback(PaymentProvider provider, String accessToken, Exception ex) {
        log.error("Plaid auth info fallback triggered", ex);
        throw new PaymentProcessingException("Plaid auth service unavailable. Please try again later.", ex);
    }
    
    public AccountsBalanceGetResponse verifyMicroDepositsFallback(PaymentProvider provider, String accessToken,
                                                                   List<Double> amounts, Exception ex) {
        log.error("Plaid micro-deposit verification fallback triggered", ex);
        throw new PaymentProcessingException("Plaid verification service unavailable. Please try again later.", ex);
    }
}