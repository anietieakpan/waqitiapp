package com.waqiti.payment.client.fallback;

import com.waqiti.common.response.ApiResponse;
import com.waqiti.payment.client.WalletServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fallback factory for WalletServiceClient providing resilient error handling
 * and graceful degradation when the wallet service is unavailable.
 * 
 * This factory implements circuit breaker patterns and provides safe fallback
 * responses to ensure payment processing can continue with appropriate risk
 * management when wallet services are temporarily unavailable.
 * 
 * For critical operations like debits and transfers, the fallback provides
 * conservative responses that prevent financial loss while maintaining
 * system availability.
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Component
@Slf4j
public class WalletServiceClientFallbackFactory implements FallbackFactory<WalletServiceClient> {

    @Override
    public WalletServiceClient create(Throwable cause) {
        log.error("Wallet service is unavailable, activating fallback", cause);
        return new WalletServiceClientFallback(cause);
    }

    /**
     * Fallback implementation providing safe default responses when wallet service is down
     */
    @Slf4j
    static class WalletServiceClientFallback implements WalletServiceClient {
        
        private final Throwable cause;

        public WalletServiceClientFallback(Throwable cause) {
            this.cause = cause;
        }

        @Override
        public ResponseEntity<ApiResponse<WalletBalance>> getBalance(String userId, String correlationId) {
            log.warn("Wallet balance fallback activated for user: {}", userId);
            
            // Return unavailable status to prevent any operations based on stale balance
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Wallet balance temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<CurrencyBalance>> getCurrencyBalance(
                String userId, String currency, String correlationId) {
            log.warn("Currency balance fallback activated for user: {}, currency: {}", userId, currency);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Currency balance temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<Map<String, WalletBalance>>> getBulkBalances(
                List<String> userIds, String correlationId) {
            log.warn("Bulk balance fallback activated for {} users", userIds.size());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Bulk balance inquiry temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<BalanceValidationResult>> validateSufficientBalance(
                String userId, BigDecimal amount, String currency, String correlationId) {
            log.warn("Balance validation fallback activated for user: {}, amount: {} {}", userId, amount, currency);
            
            // Conservative approach: assume insufficient balance when service is unavailable
            BalanceValidationResult fallbackValidation = new BalanceValidationResult(
                    false, // Conservative: assume insufficient balance
                    amount,
                    BigDecimal.ZERO, // Unknown available balance
                    amount, // Full amount as shortfall
                    currency,
                    List.of("Wallet service temporarily unavailable - cannot validate balance"),
                    Map.of("fallback_reason", "SERVICE_UNAVAILABLE")
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackValidation, "Balance validation unavailable - assume insufficient"));
        }

        @Override
        public ResponseEntity<ApiResponse<WalletOperationResult>> creditWallet(
                WalletCreditRequest request, String correlationId, String idempotencyKey) {
            log.warn("Wallet credit fallback activated for user: {}, amount: {} {}", 
                    request.userId(), request.amount(), request.currency());
            
            // Credit operations should fail safely - cannot credit without service
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Wallet credit temporarily unavailable - operation cancelled", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<BulkOperationResult>> bulkCreditWallets(
                List<WalletCreditRequest> requests, String correlationId) {
            log.warn("Bulk credit fallback activated for {} requests", requests.size());
            
            BulkOperationResult fallbackResult = new BulkOperationResult(
                    "fallback-" + System.currentTimeMillis(),
                    requests.size(),
                    0, // No successful operations
                    requests.size(), // All failed
                    "FAILED_SERVICE_UNAVAILABLE"
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackResult, "Bulk credit operations cancelled due to service unavailability"));
        }

        @Override
        public ResponseEntity<ApiResponse<WalletOperationResult>> debitWallet(
                WalletDebitRequest request, String correlationId, String idempotencyKey) {
            log.warn("Wallet debit fallback activated for user: {}, amount: {} {}", 
                    request.userId(), request.amount(), request.currency());
            
            // Debit operations must fail safely - cannot debit without confirming balance
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Wallet debit temporarily unavailable - operation cancelled for safety", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<BulkOperationResult>> bulkDebitWallets(
                List<WalletDebitRequest> requests, String correlationId) {
            log.warn("Bulk debit fallback activated for {} requests", requests.size());
            
            // All debit operations must fail when service is unavailable
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Bulk debit operations cancelled for safety", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<FundReservationResult>> reserveFunds(
                FundReservationRequest request, String correlationId, String idempotencyKey) {
            log.warn("Fund reservation fallback activated for user: {}, amount: {} {}", 
                    request.userId(), request.amount(), request.currency());
            
            // Fund reservations should fail when service is unavailable
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Fund reservation temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<FundReleaseResult>> releaseReservation(
                String userId, String reservationId, String correlationId) {
            log.warn("Fund release fallback activated for user: {}, reservation: {}", userId, reservationId);
            
            // Fund releases should fail when service is unavailable to maintain data consistency
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Fund release temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<WalletOperationResult>> convertReservationToDebit(
                String userId, String reservationId, ReservationConversionRequest request, String correlationId) {
            log.warn("Reservation conversion fallback activated for user: {}, reservation: {}", userId, reservationId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Reservation conversion temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<List<FundReservation>>> getActiveReservations(
                String userId, String correlationId) {
            log.warn("Active reservations fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Active reservations temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<WalletTransferResult>> transferFunds(
                WalletTransferRequest request, String correlationId, String idempotencyKey) {
            log.warn("Wallet transfer fallback activated from {} to {}, amount: {} {}", 
                    request.fromUserId(), request.toUserId(), request.amount(), request.currency());
            
            // Transfers must fail safely when service is unavailable
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Wallet transfer temporarily unavailable - operation cancelled for safety", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<CurrencyConversionTransferResult>> transferWithConversion(
                CurrencyConversionTransferRequest request, String correlationId, String idempotencyKey) {
            log.warn("Currency conversion transfer fallback activated from {} to {}", 
                    request.fromUserId(), request.toUserId());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Currency conversion transfer temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<WalletStatusResult>> freezeWallet(
                String userId, WalletFreezeRequest request, String correlationId) {
            log.warn("Wallet freeze fallback activated for user: {}", userId);
            
            // Wallet management operations should fail when service is unavailable
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Wallet freeze temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<WalletStatusResult>> unfreezeWallet(
                String userId, WalletUnfreezeRequest request, String correlationId) {
            log.warn("Wallet unfreeze fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Wallet unfreeze temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<WalletStatus>> getWalletStatus(String userId, String correlationId) {
            log.warn("Wallet status fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Wallet status temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<Page<WalletTransaction>>> getTransactionHistory(
                String userId, Pageable pageable, String correlationId) {
            log.warn("Transaction history fallback activated for user: {}", userId);
            
            Page<WalletTransaction> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Transaction history temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<Page<WalletTransaction>>> getFilteredTransactionHistory(
                String userId, TransactionFilter filter, Pageable pageable, String correlationId) {
            log.warn("Filtered transaction history fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Filtered transaction history temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<CurrencyAdditionResult>> addCurrency(
                String userId, String currency, String correlationId) {
            log.warn("Add currency fallback activated for user: {}, currency: {}", userId, currency);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Currency addition temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public ResponseEntity<ApiResponse<List<SupportedCurrency>>> getSupportedCurrencies(
                String userId, String correlationId) {
            log.warn("Supported currencies fallback activated for user: {}", userId);
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Supported currencies temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));
        }

        @Override
        public CompletableFuture<ResponseEntity<ApiResponse<WalletOperationResult>>> processComplexOperationAsync(
                ComplexWalletOperationRequest request, String correlationId) {
            log.warn("Complex operation fallback activated for operation: {}", request.operationType());
            
            ResponseEntity<ApiResponse<WalletOperationResult>> response = 
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(ApiResponse.error("Complex wallet operation temporarily unavailable", "WALLET_SERVICE_UNAVAILABLE"));

            return CompletableFuture.completedFuture(response);
        }

        @Override
        public ResponseEntity<ApiResponse<ServiceHealthStatus>> healthCheck() {
            log.warn("Wallet service health check fallback activated");
            
            ServiceHealthStatus fallbackHealth = new ServiceHealthStatus(
                    "DEGRADED",
                    Map.of(
                            "wallet-api", "UNAVAILABLE",
                            "balance-service", "UNKNOWN",
                            "transaction-service", "UNKNOWN",
                            "currency-service", "UNKNOWN"
                    ),
                    Instant.now(),
                    "fallback-1.0"
            );

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.success(fallbackHealth, "Wallet service degraded - fallback mode active"));
        }
    }
}