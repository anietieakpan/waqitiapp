package com.waqiti.wallet.integration.banking;

import com.waqiti.wallet.dto.*;
import com.waqiti.wallet.domain.PaymentMethod;
import com.waqiti.wallet.client.*;
import com.waqiti.common.resilience.PaymentResilience;
import com.waqiti.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Enterprise-grade banking integration service that orchestrates wallet operations
 * between core-banking-service and bank-integration-service using Feign clients.
 * 
 * This service acts as an orchestrator, coordinating between:
 * - core-banking-service: For bank account domain management
 * - bank-integration-service: For external bank API integration
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankingIntegrationService {
    
    private final CoreBankingServiceClient coreBankingClient;
    private final BankIntegrationServiceClient bankIntegrationClient;
    
    /**
     * Creates a new bank account by orchestrating between core-banking and bank-integration services
     */
    @PaymentResilience
    public Map<String, Object> createBankAccount(BankAccountCreateRequest request) {
        log.info("Orchestrating bank account creation for wallet: {}, user: {}", 
            request.getWalletId(), request.getUserId());
        
        try {
            String authToken = getAuthorizationToken();
            
            // Step 1: Link with external bank via bank-integration-service
            LinkBankAccountRequest linkRequest = LinkBankAccountRequest.builder()
                .userId(request.getUserId().toString())
                .accountNumber(request.getAccountNumber())
                .routingNumber(request.getRoutingNumber())
                .bankName(request.getBankName())
                .accountType(request.getAccountType().name().toLowerCase())
                .accountHolderName(request.getAccountHolderName())
                .build();
            
            ResponseEntity<Map<String, Object>> linkResponse = bankIntegrationClient.linkBankAccount(linkRequest);
            
            if (!linkResponse.getStatusCode().is2xxSuccessful() || linkResponse.getBody() == null) {
                throw new ServiceException("Failed to link bank account with external service");
            }
            
            Map<String, Object> linkResponseBody = linkResponse.getBody();
            Boolean linkSuccess = (Boolean) linkResponseBody.get("success");
            
            if (linkSuccess == null || !linkSuccess) {
                String error = (String) linkResponseBody.get("error");
                throw new ServiceException("Bank account linking failed: " + error);
            }
            
            String externalAccountId = (String) linkResponseBody.get("accountId");
            
            // Step 2: Get the core account ID for this wallet
            ResponseEntity<List<Map<String, Object>>> userAccountsResponse = 
                coreBankingClient.getUserAccounts(request.getUserId(), authToken);
            
            UUID coreAccountId = null;
            if (userAccountsResponse.getBody() != null && !userAccountsResponse.getBody().isEmpty()) {
                // Use the first account or find matching currency
                Map<String, Object> coreAccount = userAccountsResponse.getBody().stream()
                    .filter(acc -> request.getCurrency().equals(acc.get("currency")))
                    .findFirst()
                    .orElse(userAccountsResponse.getBody().get(0));
                
                coreAccountId = UUID.fromString((String) coreAccount.get("accountId"));
            }
            
            if (coreAccountId == null) {
                // Create a new core account if none exists
                CoreBankingAccountRequest coreAccountRequest = CoreBankingAccountRequest.builder()
                    .userId(request.getUserId())
                    .accountName("Linked Bank Account - " + request.getBankName())
                    .accountType("EXTERNAL_BANK")
                    .accountCategory("ASSET")
                    .currency(request.getCurrency())
                    .initialBalance(BigDecimal.ZERO)
                    .dailyTransactionLimit(request.getDailyLimit())
                    .monthlyTransactionLimit(request.getMonthlyLimit())
                    .build();
                
                ResponseEntity<Map<String, Object>> coreResponse = 
                    coreBankingClient.createAccount(authToken, coreAccountRequest);
                
                if (coreResponse.getBody() != null) {
                    coreAccountId = UUID.fromString((String) coreResponse.getBody().get("accountId"));
                }
            }
            
            // Step 3: Create bank account in core-banking-service
            CreateBankAccountRequest coreBankRequest = CreateBankAccountRequest.builder()
                .userId(request.getUserId())
                .coreAccountId(coreAccountId)
                .accountNumber(request.getAccountNumber())
                .routingNumber(request.getRoutingNumber())
                .accountHolderName(request.getAccountHolderName())
                .accountType(request.getAccountType().name())
                .currency(request.getCurrency())
                .bankName(request.getBankName())
                .branchCode(request.getBranchCode())
                .swiftCode(request.getSwiftCode())
                .externalBankId(request.getExternalBankId())
                .providerAccountId(externalAccountId)
                .providerName("bank-integration-service")
                .build();
            
            ResponseEntity<Map<String, Object>> coreBankResponse = 
                coreBankingClient.createBankAccount(authToken, coreBankRequest);
            
            if (!coreBankResponse.getStatusCode().is2xxSuccessful() || coreBankResponse.getBody() == null) {
                throw new ServiceException("Failed to create bank account in core banking");
            }
            
            Map<String, Object> result = coreBankResponse.getBody();
            result.put("externalAccountId", externalAccountId);
            result.put("walletId", request.getWalletId());
            
            log.info("Successfully orchestrated bank account creation: core account {}, external ID {}", 
                result.get("id"), externalAccountId);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error orchestrating bank account creation for wallet: {}", request.getWalletId(), e);
            throw new ServiceException("Failed to create bank account", e);
        }
    }
    
    /**
     * Processes deposit by orchestrating between core-banking and bank-integration services
     */
    @PaymentResilience
    public DepositProcessingResult processDeposit(DepositRequest request) {
        log.info("Orchestrating bank deposit: wallet={}, amount={}", 
            request.getWalletId(), request.getAmount());
        
        try {
            String authToken = getAuthorizationToken();
            
            // Validate payment method
            if (!isValidBankPaymentMethod(request.getPaymentMethod())) {
                return DepositProcessingResult.failed("INVALID_PAYMENT_METHOD", 
                    "Invalid payment method for bank deposit");
            }
            
            // Get bank account from core-banking-service
            ResponseEntity<Map<String, Object>> bankAccountResponse = 
                coreBankingClient.getBankAccount(UUID.fromString(request.getPaymentMethodId()), authToken);
            
            if (!bankAccountResponse.getStatusCode().is2xxSuccessful() || bankAccountResponse.getBody() == null) {
                return DepositProcessingResult.failed("BANK_ACCOUNT_NOT_FOUND", 
                    "Bank account not found");
            }
            
            Map<String, Object> bankAccount = bankAccountResponse.getBody();
            String providerAccountId = (String) bankAccount.get("providerAccountId");
            
            // Initiate transfer via bank-integration-service
            BankTransferRequest transferRequest = BankTransferRequest.builder()
                .userId(request.getUserId().toString())
                .toAccount("WAQITI_WALLET_" + request.getWalletId())
                .amount(request.getAmount())
                .currency(request.getCurrency().toString())
                .description(request.getDescription())
                .transferType("ach")
                .build();
            
            ResponseEntity<Map<String, Object>> transferResponse = 
                bankIntegrationClient.initiateTransfer(providerAccountId, transferRequest);
            
            if (!transferResponse.getStatusCode().is2xxSuccessful() || transferResponse.getBody() == null) {
                return DepositProcessingResult.failed("TRANSFER_REQUEST_FAILED", 
                    "Failed to initiate transfer");
            }
            
            Map<String, Object> transferResult = transferResponse.getBody();
            Boolean success = (Boolean) transferResult.get("success");
            
            if (success == null || !success) {
                String error = (String) transferResult.get("error");
                return DepositProcessingResult.failed("TRANSFER_FAILED", error);
            }
            
            String transferId = (String) transferResult.get("transferId");
            
            // Update core-banking account balance
            AccountBalanceUpdateRequest balanceUpdate = AccountBalanceUpdateRequest.builder()
                .amount(request.getAmount())
                .operation("CREDIT")
                .description("Deposit from bank account")
                .transactionReference(transferId)
                .build();
            
            String coreAccountId = (String) bankAccount.get("coreAccountId");
            coreBankingClient.updateAccountBalance(coreAccountId, authToken, balanceUpdate);
            
            log.info("Bank deposit orchestrated successfully: {}", transferId);
            return DepositProcessingResult.successful(transferId);
            
        } catch (Exception e) {
            log.error("Error orchestrating bank deposit", e);
            return DepositProcessingResult.failed("PROCESSING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Processes withdrawal by orchestrating between core-banking and bank-integration services
     */
    @PaymentResilience
    public WithdrawProcessingResult processWithdrawal(WithdrawRequest request) {
        log.info("Orchestrating bank withdrawal: wallet={}, amount={}", 
            request.getWalletId(), request.getAmount());
        
        try {
            String authToken = getAuthorizationToken();
            
            // Validate payment method
            if (!isValidBankPaymentMethod(request.getPaymentMethod())) {
                return WithdrawProcessingResult.failed("INVALID_PAYMENT_METHOD", 
                    "Invalid payment method for bank withdrawal");
            }
            
            // Get bank account from core-banking-service
            ResponseEntity<Map<String, Object>> bankAccountResponse = 
                coreBankingClient.getBankAccount(UUID.fromString(request.getPaymentMethodId()), authToken);
            
            if (!bankAccountResponse.getStatusCode().is2xxSuccessful() || bankAccountResponse.getBody() == null) {
                return WithdrawProcessingResult.failed("BANK_ACCOUNT_NOT_FOUND", 
                    "Bank account not found");
            }
            
            Map<String, Object> bankAccount = bankAccountResponse.getBody();
            String providerAccountId = (String) bankAccount.get("providerAccountId");
            
            // Check if account can process transaction
            Boolean isVerified = (Boolean) bankAccount.get("isVerified");
            String status = (String) bankAccount.get("status");
            
            if (!Boolean.TRUE.equals(isVerified) || !"ACTIVE".equals(status)) {
                return WithdrawProcessingResult.failed("ACCOUNT_NOT_ACTIVE", 
                    "Bank account is not active or verified");
            }
            
            // Initiate transfer via bank-integration-service
            BankTransferRequest transferRequest = BankTransferRequest.builder()
                .userId(request.getUserId().toString())
                .toAccount(providerAccountId)
                .amount(request.getAmount())
                .currency(request.getCurrency().toString())
                .description(request.getDescription())
                .transferType("ach")
                .build();
            
            ResponseEntity<Map<String, Object>> transferResponse = 
                bankIntegrationClient.initiateTransfer("WAQITI_MASTER_ACCOUNT", transferRequest);
            
            if (!transferResponse.getStatusCode().is2xxSuccessful() || transferResponse.getBody() == null) {
                return WithdrawProcessingResult.failed("TRANSFER_REQUEST_FAILED", 
                    "Failed to initiate transfer");
            }
            
            Map<String, Object> transferResult = transferResponse.getBody();
            Boolean success = (Boolean) transferResult.get("success");
            
            if (success == null || !success) {
                String error = (String) transferResult.get("error");
                return WithdrawProcessingResult.failed("TRANSFER_FAILED", error);
            }
            
            String transferId = (String) transferResult.get("transferId");
            
            // Update core-banking account balance
            AccountBalanceUpdateRequest balanceUpdate = AccountBalanceUpdateRequest.builder()
                .amount(request.getAmount())
                .operation("DEBIT")
                .description("Withdrawal to bank account")
                .transactionReference(transferId)
                .build();
            
            String coreAccountId = (String) bankAccount.get("coreAccountId");
            coreBankingClient.updateAccountBalance(coreAccountId, authToken, balanceUpdate);
            
            log.info("Bank withdrawal orchestrated successfully: {}", transferId);
            return WithdrawProcessingResult.successful(transferId);
            
        } catch (Exception e) {
            log.error("Error orchestrating bank withdrawal", e);
            return WithdrawProcessingResult.failed("PROCESSING_ERROR", e.getMessage());
        }
    }
    
    /**
     * Verifies bank account by orchestrating between services
     */
    @PaymentResilience
    public boolean verifyBankAccount(UUID bankAccountId, BigDecimal amount1, BigDecimal amount2) {
        log.info("Orchestrating bank account verification: {}", bankAccountId);
        
        try {
            String authToken = getAuthorizationToken();
            
            // Verify with core-banking-service
            BankAccountVerificationRequest verifyRequest = BankAccountVerificationRequest.builder()
                .microDepositAmount1(amount1)
                .microDepositAmount2(amount2)
                .build();
            
            ResponseEntity<Map<String, Object>> coreResponse = 
                coreBankingClient.verifyBankAccount(bankAccountId, authToken, verifyRequest);
            
            if (!coreResponse.getStatusCode().is2xxSuccessful() || coreResponse.getBody() == null) {
                return false;
            }
            
            Map<String, Object> result = coreResponse.getBody();
            Boolean verified = (Boolean) result.get("verified");
            
            if (Boolean.TRUE.equals(verified)) {
                // Also verify with bank-integration-service if needed
                String providerAccountId = (String) result.get("providerAccountId");
                if (providerAccountId != null) {
                    VerifyBankAccountRequest externalVerifyRequest = VerifyBankAccountRequest.builder()
                        .accountId(providerAccountId)
                        .deposit1(amount1)
                        .deposit2(amount2)
                        .build();
                    
                    bankIntegrationClient.verifyBankAccount(externalVerifyRequest);
                }
                
                log.info("Bank account {} verification successful", bankAccountId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error orchestrating bank account verification: {}", bankAccountId, e);
            return false;
        }
    }
    
    /**
     * Gets account balance by orchestrating between services
     */
    @PaymentResilience
    public BigDecimal getExternalAccountBalance(UUID bankAccountId) {
        try {
            String authToken = getAuthorizationToken();
            
            // Get bank account from core-banking
            ResponseEntity<Map<String, Object>> bankAccountResponse = 
                coreBankingClient.getBankAccount(bankAccountId, authToken);
            
            if (bankAccountResponse.getStatusCode().is2xxSuccessful() && bankAccountResponse.getBody() != null) {
                Map<String, Object> bankAccount = bankAccountResponse.getBody();
                String providerAccountId = (String) bankAccount.get("providerAccountId");
                
                // Get balance from bank-integration-service
                ResponseEntity<Map<String, Object>> balanceResponse = 
                    bankIntegrationClient.getAccountBalance(providerAccountId);
                
                if (balanceResponse.getStatusCode().is2xxSuccessful() && balanceResponse.getBody() != null) {
                    Object balanceObj = balanceResponse.getBody().get("availableBalance");
                    if (balanceObj instanceof Number) {
                        return new BigDecimal(balanceObj.toString());
                    }
                }
            }
            
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Error getting external account balance: {}", bankAccountId, e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * Gets supported banks from bank-integration-service
     */
    public List<Map<String, Object>> getSupportedBanks(String country) {
        try {
            ResponseEntity<List<Map<String, Object>>> response = bankIntegrationClient.getSupportedBanks(country);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            
            return List.of();
            
        } catch (Exception e) {
            log.error("Error getting supported banks for country: {}", country, e);
            return List.of();
        }
    }
    
    // Private helper methods
    
    private boolean isValidBankPaymentMethod(PaymentMethod method) {
        return method == PaymentMethod.BANK_TRANSFER || 
               method == PaymentMethod.ACH || 
               method == PaymentMethod.WIRE_TRANSFER;
    }
    
    private String getAuthorizationToken() {
        // Get the current authentication token from security context
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() != null) {
            return "Bearer " + authentication.getCredentials().toString();
        }
        // Fallback to service-to-service token if needed
        return "Bearer service-token";
    }
}