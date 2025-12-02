package com.waqiti.common.client;

import com.waqiti.common.api.StandardApiResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Wallet Service Client
 * 
 * Provides standardized communication with the Wallet Service
 */
@Component
@Slf4j
public class WalletServiceClient extends ServiceClient {

    public WalletServiceClient(RestTemplate restTemplate, 
                             @Value("${services.wallet-service.url}") String baseUrl) {
        super(restTemplate, baseUrl, "wallet-service");
    }

    /**
     * Get wallet by user ID
     */
    public CompletableFuture<ServiceResponse<WalletDTO>> getWalletByUserId(UUID userId) {
        return get("/api/v1/wallets/user/" + userId, WalletDTO.class, null);
    }

    /**
     * Get wallet by ID
     */
    public CompletableFuture<ServiceResponse<WalletDTO>> getWalletById(UUID walletId) {
        return get("/api/v1/wallets/" + walletId, WalletDTO.class, null);
    }

    /**
     * Create wallet for user
     */
    public CompletableFuture<ServiceResponse<WalletDTO>> createWallet(CreateWalletRequest request) {
        return post("/api/v1/wallets", request, WalletDTO.class);
    }

    /**
     * Get wallet balance
     */
    public CompletableFuture<ServiceResponse<BalanceDTO>> getBalance(UUID walletId) {
        return get("/api/v1/wallets/" + walletId + "/balance", BalanceDTO.class, null);
    }

    /**
     * Get wallet balance by currency
     */
    public CompletableFuture<ServiceResponse<BalanceDTO>> getBalance(UUID walletId, String currency) {
        Map<String, Object> queryParams = Map.of("currency", currency);
        return get("/api/v1/wallets/" + walletId + "/balance", BalanceDTO.class, queryParams);
    }

    /**
     * Credit wallet
     */
    public CompletableFuture<ServiceResponse<TransactionResultDTO>> creditWallet(UUID walletId, CreditRequest request) {
        return post("/api/v1/wallets/" + walletId + "/credit", request, TransactionResultDTO.class);
    }

    /**
     * Debit wallet
     */
    public CompletableFuture<ServiceResponse<TransactionResultDTO>> debitWallet(UUID walletId, DebitRequest request) {
        return post("/api/v1/wallets/" + walletId + "/debit", request, TransactionResultDTO.class);
    }

    /**
     * Transfer between wallets
     */
    public CompletableFuture<ServiceResponse<TransferResultDTO>> transferFunds(TransferRequest request) {
        return post("/api/v1/wallets/transfer", request, TransferResultDTO.class);
    }

    /**
     * Reserve funds
     */
    public CompletableFuture<ServiceResponse<ReservationDTO>> reserveFunds(UUID walletId, ReservationRequest request) {
        return post("/api/v1/wallets/" + walletId + "/reserve", request, ReservationDTO.class);
    }

    /**
     * Release reservation
     */
    public CompletableFuture<ServiceResponse<Void>> releaseReservation(UUID walletId, UUID reservationId) {
        return post("/api/v1/wallets/" + walletId + "/reservations/" + reservationId + "/release", null, Void.class);
    }

    /**
     * Get wallet balance (synchronous for resource ownership checks)
     */
    public Map<String, Object> getWalletBalance(String walletId) {
        try {
            log.debug("Fetching wallet balance for: {}", walletId);
            String url = baseUrl + "/api/internal/wallets/" + walletId + "/balance";

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : Map.of("balance", BigDecimal.ZERO);

        } catch (Exception e) {
            log.error("Failed to fetch wallet balance for {}", walletId, e);
            return Map.of("balance", BigDecimal.ZERO, "error", e.getMessage());
        }
    }

    /**
     * Confirm reservation
     */
    public CompletableFuture<ServiceResponse<TransactionResultDTO>> confirmReservation(UUID walletId, UUID reservationId) {
        return post("/api/v1/wallets/" + walletId + "/reservations/" + reservationId + "/confirm", null, TransactionResultDTO.class);
    }

    /**
     * Get transaction history
     */
    public CompletableFuture<ServiceResponse<List<WalletTransactionDTO>>> getTransactionHistory(UUID walletId, TransactionHistoryRequest request) {
        Map<String, Object> queryParams = Map.of(
            "fromDate", request.getFromDate() != null ? request.getFromDate().toString() : "",
            "toDate", request.getToDate() != null ? request.getToDate().toString() : "",
            "type", request.getType() != null ? request.getType() : "",
            "status", request.getStatus() != null ? request.getStatus() : "",
            "page", request.getPage(),
            "size", request.getSize()
        );
        return getList("/api/v1/wallets/" + walletId + "/transactions", 
            new ParameterizedTypeReference<StandardApiResponse<List<WalletTransactionDTO>>>() {}, 
            queryParams);
    }

    /**
     * Get specific transaction
     */
    public CompletableFuture<ServiceResponse<WalletTransactionDTO>> getTransaction(UUID walletId, UUID transactionId) {
        return get("/api/v1/wallets/" + walletId + "/transactions/" + transactionId, WalletTransactionDTO.class, null);
    }

    /**
     * Freeze wallet
     */
    public CompletableFuture<ServiceResponse<Void>> freezeWallet(UUID walletId, FreezeRequest request) {
        return post("/api/v1/wallets/" + walletId + "/freeze", request, Void.class);
    }

    /**
     * Unfreeze wallet
     */
    public CompletableFuture<ServiceResponse<Void>> unfreezeWallet(UUID walletId, String reason) {
        Map<String, Object> request = Map.of("reason", reason);
        return post("/api/v1/wallets/" + walletId + "/unfreeze", request, Void.class);
    }

    /**
     * Get wallet limits
     */
    public CompletableFuture<ServiceResponse<WalletLimitsDTO>> getWalletLimits(UUID walletId) {
        return get("/api/v1/wallets/" + walletId + "/limits", WalletLimitsDTO.class, null);
    }

    /**
     * Update wallet limits
     */
    public CompletableFuture<ServiceResponse<WalletLimitsDTO>> updateWalletLimits(UUID walletId, UpdateLimitsRequest request) {
        return put("/api/v1/wallets/" + walletId + "/limits", request, WalletLimitsDTO.class);
    }

    /**
     * Get wallet statements
     */
    public CompletableFuture<ServiceResponse<WalletStatementDTO>> getStatement(UUID walletId, StatementRequest request) {
        Map<String, Object> queryParams = Map.of(
            "fromDate", request.getFromDate().toString(),
            "toDate", request.getToDate().toString(),
            "format", request.getFormat()
        );
        return get("/api/v1/wallets/" + walletId + "/statement", WalletStatementDTO.class, queryParams);
    }

    /**
     * Validate wallet operation
     */
    public CompletableFuture<ServiceResponse<ValidationResultDTO>> validateOperation(UUID walletId, OperationValidationRequest request) {
        return post("/api/v1/wallets/" + walletId + "/validate", request, ValidationResultDTO.class);
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

    // DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WalletDTO {
        private UUID id;
        private UUID userId;
        private String walletNumber;
        private String status;
        private String type;
        private String currency;
        private BigDecimal balance;
        private BigDecimal availableBalance;
        private BigDecimal reservedBalance;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private boolean frozen;
        private String freezeReason;
        private LocalDateTime frozenAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BalanceDTO {
        private UUID walletId;
        private String currency;
        private BigDecimal totalBalance;
        private BigDecimal availableBalance;
        private BigDecimal reservedBalance;
        private BigDecimal pendingBalance;
        private LocalDateTime lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionResultDTO {
        private UUID transactionId;
        private UUID walletId;
        private String type;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String reference;
        private BigDecimal balanceAfter;
        private LocalDateTime processedAt;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransferResultDTO {
        private UUID transferId;
        private UUID fromWalletId;
        private UUID toWalletId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String reference;
        private BigDecimal fromBalanceAfter;
        private BigDecimal toBalanceAfter;
        private LocalDateTime processedAt;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReservationDTO {
        private UUID reservationId;
        private UUID walletId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String reference;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WalletTransactionDTO {
        private UUID id;
        private UUID walletId;
        private String type;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String reference;
        private String description;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private LocalDateTime createdAt;
        private LocalDateTime processedAt;
        private Map<String, Object> metadata;
        private UUID relatedTransactionId;
        private String category;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WalletLimitsDTO {
        private UUID walletId;
        private BigDecimal dailyTransactionLimit;
        private BigDecimal monthlyTransactionLimit;
        private BigDecimal maxTransactionAmount;
        private BigDecimal maxBalance;
        private int dailyTransactionCount;
        private int monthlyTransactionCount;
        private LocalDateTime lastUpdated;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WalletStatementDTO {
        private UUID walletId;
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private BigDecimal openingBalance;
        private BigDecimal closingBalance;
        private List<WalletTransactionDTO> transactions;
        private String format;
        private byte[] statementData;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationResultDTO {
        private boolean valid;
        private String status;
        private List<String> violations;
        private Map<String, Object> details;
    }

    // Request DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateWalletRequest {
        private UUID userId;
        private String type;
        private String currency;
        private String description;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreditRequest {
        private BigDecimal amount;
        private String currency;
        private String reference;
        private String description;
        private String idempotencyKey;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DebitRequest {
        private BigDecimal amount;
        private String currency;
        private String reference;
        private String description;
        private String idempotencyKey;
        private boolean allowOverdraft;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransferRequest {
        private UUID fromWalletId;
        private UUID toWalletId;
        private BigDecimal amount;
        private String currency;
        private String reference;
        private String description;
        private String idempotencyKey;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReservationRequest {
        private BigDecimal amount;
        private String currency;
        private String reference;
        private String description;
        private LocalDateTime expiresAt;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransactionHistoryRequest {
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String type;
        private String status;
        private String category;
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 20;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FreezeRequest {
        private String reason;
        private String notes;
        private boolean partial;
        private BigDecimal allowedAmount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UpdateLimitsRequest {
        private BigDecimal dailyTransactionLimit;
        private BigDecimal monthlyTransactionLimit;
        private BigDecimal maxTransactionAmount;
        private BigDecimal maxBalance;
        private int dailyTransactionCount;
        private int monthlyTransactionCount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StatementRequest {
        private LocalDateTime fromDate;
        private LocalDateTime toDate;
        private String format; // PDF, CSV, JSON
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OperationValidationRequest {
        private String operation;
        private BigDecimal amount;
        private String currency;
        private Map<String, Object> parameters;
    }
}