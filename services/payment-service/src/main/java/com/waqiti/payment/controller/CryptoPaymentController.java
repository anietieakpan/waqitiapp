package com.waqiti.payment.controller;

import com.waqiti.payment.dto.*;
import com.waqiti.payment.service.CryptoPaymentService;
import com.waqiti.payment.model.*;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.security.SecurityContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Cryptocurrency Payments
 *
 * SECURITY FIX: This controller now extracts user ID from SecurityContext (JWT token)
 * instead of accepting it from X-User-ID header, preventing IDOR vulnerabilities.
 *
 * Provides comprehensive API for crypto payment processing, wallet management, and blockchain interactions
 */
@RestController
@RequestMapping("/api/v1/payments/crypto")
@Tag(name = "Crypto Payments", description = "APIs for cryptocurrency payment processing")
@RequiredArgsConstructor
@Validated
@Slf4j
public class CryptoPaymentController {

    private final CryptoPaymentService cryptoPaymentService;

    /**
     * Process cryptocurrency payment
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of header
     */
    @PostMapping("/send")
    @Operation(summary = "Send crypto payment", description = "Process cryptocurrency payment to blockchain address")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment initiated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid payment request"),
        @ApiResponse(responseCode = "402", description = "Insufficient balance"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    @PreAuthorize("hasRole('USER')")
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.ENHANCED)
    public ResponseEntity<CryptoPaymentResponse> sendCryptoPayment(
            @Valid @RequestBody CryptoPaymentRequest request) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Processing crypto payment for user: {} to address: {} amount: {} {}",
                userId, request.getRecipientAddress(), request.getAmount(), request.getCurrency());

        try {
            request.setSenderUserId(userId.toString());
            CryptoPaymentResponse response = cryptoPaymentService.processCryptoPayment(request);

            log.info("Crypto payment initiated successfully. Transaction ID: {}", response.getTransactionId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error processing crypto payment for user: {}", userId, e);
            throw e;
        }
    }

    /**
     * Get supported cryptocurrencies
     *
     * SECURITY: Intentionally public - informational endpoint
     */
    @GetMapping("/currencies")
    @Operation(summary = "Get supported cryptocurrencies", description = "Retrieve list of supported cryptocurrencies")
    @PreAuthorize("permitAll()")
    public ResponseEntity<List<SupportedCryptoCurrency>> getSupportedCurrencies() {
        log.debug("Fetching supported cryptocurrencies");

        List<SupportedCryptoCurrency> currencies = cryptoPaymentService.getSupportedCurrencies();
        return ResponseEntity.ok(currencies);
    }

    /**
     * Get real-time crypto prices
     *
     * SECURITY: Intentionally public - market data endpoint
     */
    @GetMapping("/prices")
    @Operation(summary = "Get crypto prices", description = "Get real-time cryptocurrency prices")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Prices retrieved successfully"),
        @ApiResponse(responseCode = "503", description = "Price service unavailable")
    })
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, CryptoPriceInfo>> getCryptoPrices(
            @RequestParam(required = false) List<String> currencies,
            @RequestParam(defaultValue = "USD") String baseCurrency) {

        log.debug("Fetching crypto prices for currencies: {} in base: {}", currencies, baseCurrency);

        try {
            Map<String, CryptoPriceInfo> prices = cryptoPaymentService.getCurrentPrices(currencies, baseCurrency);
            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            log.error("Error fetching crypto prices", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Generate crypto wallet address
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of header
     */
    @PostMapping("/wallet/address")
    @Operation(summary = "Generate wallet address", description = "Generate new cryptocurrency wallet address for user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Wallet address generated"),
        @ApiResponse(responseCode = "400", description = "Invalid currency or request"),
        @ApiResponse(responseCode = "429", description = "Address generation limit exceeded")
    })
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CryptoWalletAddress> generateWalletAddress(
            @Valid @RequestBody GenerateAddressRequest request) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Generating {} wallet address for user: {}", request.getCurrency(), userId);

        try {
            request.setUserId(userId.toString());
            CryptoWalletAddress address = cryptoPaymentService.generateWalletAddress(request);

            log.info("Wallet address generated successfully: {} for user: {}",
                    address.getAddress(), userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(address);

        } catch (Exception e) {
            log.error("Error generating wallet address for user: {}", userId, e);
            throw e;
        }
    }

    /**
     * Get user's crypto wallets
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of header
     */
    @GetMapping("/wallet/addresses")
    @Operation(summary = "Get wallet addresses", description = "Retrieve user's cryptocurrency wallet addresses")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CryptoWalletAddress>> getUserWalletAddresses(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false, defaultValue = "true") boolean includeBalance) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching wallet addresses for user: {} currency: {}", userId, currency);

        List<CryptoWalletAddress> addresses = cryptoPaymentService.getUserWalletAddresses(
                userId.toString(), currency, includeBalance);
        return ResponseEntity.ok(addresses);
    }

    /**
     * Get crypto wallet balance
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of header
     */
    @GetMapping("/wallet/balance")
    @Operation(summary = "Get wallet balance", description = "Get cryptocurrency wallet balance for user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, CryptoBalance>> getWalletBalance(
            @RequestParam(required = false) List<String> currencies) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching wallet balance for user: {} currencies: {}", userId, currencies);

        Map<String, CryptoBalance> balances = cryptoPaymentService.getWalletBalance(userId.toString(), currencies);
        return ResponseEntity.ok(balances);
    }

    /**
     * Validate cryptocurrency address
     *
     * SECURITY: Intentionally public - utility endpoint for address validation
     */
    @PostMapping("/validate-address")
    @Operation(summary = "Validate crypto address", description = "Validate cryptocurrency address format")
    @PreAuthorize("permitAll()")
    public ResponseEntity<AddressValidationResult> validateAddress(
            @Valid @RequestBody ValidateAddressRequest request) {

        log.debug("Validating {} address: {}", request.getCurrency(), request.getAddress());

        AddressValidationResult result = cryptoPaymentService.validateAddress(
                request.getAddress(), request.getCurrency());

        return ResponseEntity.ok(result);
    }

    /**
     * Get transaction status by hash
     *
     * SECURITY: Intentionally public - blockchain data is publicly accessible
     */
    @GetMapping("/transaction/{txHash}")
    @Operation(summary = "Get transaction status", description = "Get blockchain transaction status by hash")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaction found"),
        @ApiResponse(responseCode = "404", description = "Transaction not found"),
        @ApiResponse(responseCode = "503", description = "Blockchain service unavailable")
    })
    @PreAuthorize("permitAll()")
    public ResponseEntity<CryptoTransactionStatus> getTransactionStatus(
            @PathVariable @NotBlank String txHash,
            @RequestParam @NotBlank String currency) {

        log.debug("Fetching transaction status for hash: {} currency: {}", txHash, currency);

        try {
            CryptoTransactionStatus status = cryptoPaymentService.getTransactionStatus(txHash, currency);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error fetching transaction status for hash: {}", txHash, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Get user's crypto transaction history
     *
     * SECURITY FIX: User ID now extracted from SecurityContext (JWT) instead of header
     */
    @GetMapping("/transactions")
    @Operation(summary = "Get transaction history", description = "Get user's cryptocurrency transaction history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<CryptoTransactionDTO>> getCryptoTransactions(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) CryptoTransactionType type,
            @RequestParam(required = false) CryptoTransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching crypto transactions for user: {} currency: {} type: {}",
                userId, currency, type);

        CryptoTransactionFilter filter = CryptoTransactionFilter.builder()
                .userId(userId.toString())
                .currency(currency)
                .type(type)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        Page<CryptoTransactionDTO> transactions = cryptoPaymentService.getCryptoTransactions(filter, pageable);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Estimate network fees
     */
    @PostMapping("/estimate-fee")
    @Operation(summary = "Estimate network fee", description = "Estimate blockchain network fee for transaction")
    public ResponseEntity<NetworkFeeEstimate> estimateNetworkFee(
            @Valid @RequestBody EstimateFeeRequest request) {
        
        log.debug("Estimating network fee for {} transaction: {} {}", 
                request.getCurrency(), request.getAmount(), request.getCurrency());
        
        try {
            NetworkFeeEstimate estimate = cryptoPaymentService.estimateNetworkFee(request);
            return ResponseEntity.ok(estimate);
        } catch (Exception e) {
            log.error("Error estimating network fee", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Convert cryptocurrency amounts
     */
    @PostMapping("/convert")
    @Operation(summary = "Convert crypto amounts", description = "Convert between cryptocurrencies and fiat")
    public ResponseEntity<CurrencyConversionResult> convertCurrency(
            @Valid @RequestBody CurrencyConversionRequest request) {
        
        log.debug("Converting {} {} to {}", request.getAmount(), 
                request.getFromCurrency(), request.getToCurrency());
        
        try {
            CurrencyConversionResult result = cryptoPaymentService.convertCurrency(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error converting currency", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Set up recurring crypto payments
     */
    @PostMapping("/recurring")
    @Operation(summary = "Setup recurring payment", description = "Setup recurring cryptocurrency payment")
    @PreAuthorize("hasRole('USER')")
    @RequireKYCVerification(level = RequireKYCVerification.VerificationLevel.ENHANCED)
    public ResponseEntity<RecurringCryptoPaymentResponse> setupRecurringPayment(
            @Valid @RequestBody RecurringCryptoPaymentRequest request) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Setting up recurring crypto payment for user: {} to address: {}",
                userId, request.getRecipientAddress());

        try {
            request.setUserId(userId.toString());
            RecurringCryptoPaymentResponse response = cryptoPaymentService.setupRecurringPayment(request);

            log.info("Recurring crypto payment setup successfully. ID: {}", response.getRecurringPaymentId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error setting up recurring crypto payment for user: {}", userId, e);
            throw e;
        }
    }

    /**
     * Get crypto market data
     */
    @GetMapping("/market-data")
    @Operation(summary = "Get market data", description = "Get cryptocurrency market data and charts")
    public ResponseEntity<CryptoMarketData> getMarketData(
            @RequestParam @NotBlank String currency,
            @RequestParam(defaultValue = "USD") String baseCurrency,
            @RequestParam(defaultValue = "24h") String timeframe) {
        
        log.debug("Fetching market data for {} in {} timeframe: {}", 
                currency, baseCurrency, timeframe);
        
        try {
            CryptoMarketData marketData = cryptoPaymentService.getMarketData(currency, baseCurrency, timeframe);
            return ResponseEntity.ok(marketData);
        } catch (Exception e) {
            log.error("Error fetching market data for {}", currency, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Cancel pending crypto transaction
     */
    @PostMapping("/transaction/{transactionId}/cancel")
    @Operation(summary = "Cancel transaction", description = "Cancel pending crypto transaction (if possible)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CryptoTransactionCancellationResult> cancelTransaction(
            @PathVariable @NotBlank String transactionId,
            @RequestParam(required = false) String reason) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.info("Attempting to cancel crypto transaction: {} for user: {}", transactionId, userId);

        try {
            CryptoTransactionCancellationResult result = cryptoPaymentService.cancelTransaction(
                    transactionId, userId.toString(), reason);

            if (result.isSuccess()) {
                log.info("Crypto transaction cancelled successfully: {}", transactionId);
                return ResponseEntity.ok(result);
            } else {
                log.warn("Failed to cancel crypto transaction: {} - {}", transactionId, result.getReason());
                return ResponseEntity.badRequest().body(result);
            }

        } catch (Exception e) {
            log.error("Error cancelling crypto transaction: {}", transactionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get crypto payment analytics
     */
    @GetMapping("/analytics")
    @Operation(summary = "Get payment analytics", description = "Get cryptocurrency payment analytics for user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CryptoPaymentAnalytics> getPaymentAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) List<String> currencies) {

        // SECURITY FIX: Extract user ID from SecurityContext (JWT token) instead of header
        UUID userId = SecurityContextUtil.getAuthenticatedUserId();

        log.debug("Fetching crypto payment analytics for user: {}", userId);

        if (startDate == null) {
            startDate = LocalDateTime.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }

        CryptoPaymentAnalytics analytics = cryptoPaymentService.getPaymentAnalytics(
                userId.toString(), startDate, endDate, currencies);
        return ResponseEntity.ok(analytics);
    }

    /**
     * Get network status
     */
    @GetMapping("/network-status")
    @Operation(summary = "Get network status", description = "Get blockchain network status and health")
    public ResponseEntity<Map<String, NetworkStatus>> getNetworkStatus() {
        log.debug("Fetching blockchain network status");
        
        try {
            Map<String, NetworkStatus> networkStatus = cryptoPaymentService.getNetworkStatus();
            return ResponseEntity.ok(networkStatus);
        } catch (Exception e) {
            log.error("Error fetching network status", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Emergency crypto transaction recovery
     *
     * SECURITY FIX: Admin ID now extracted from SecurityContext (JWT) instead of X-User-ID header
     * to prevent IDOR vulnerability where attackers could impersonate admins.
     */
    @PostMapping("/recovery/transaction")
    @Operation(summary = "Recover transaction", description = "Attempt emergency recovery of stuck crypto transaction")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPPORT')")
    public ResponseEntity<CryptoRecoveryResult> recoverTransaction(
            @Valid @RequestBody CryptoRecoveryRequest request) {

        // SECURITY FIX: Extract admin ID from SecurityContext (JWT token) instead of header
        UUID adminId = SecurityContextUtil.getAuthenticatedUserId();

        log.warn("Emergency crypto transaction recovery initiated by admin: {} for transaction: {}",
                adminId, request.getTransactionId());

        try {
            request.setInitiatedBy(adminId.toString());
            CryptoRecoveryResult result = cryptoPaymentService.attemptTransactionRecovery(request);

            log.info("Crypto recovery attempt completed: {} - Success: {} - Admin: {}",
                    request.getTransactionId(), result.isSuccess(), adminId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error during crypto transaction recovery - Admin: {} Transaction: {}",
                    adminId, request.getTransactionId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Health check for crypto payment service
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check crypto payment service health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "crypto-payment-service",
                "timestamp", LocalDateTime.now().toString(),
                "blockchain_connections", cryptoPaymentService.getBlockchainConnectionStatus(),
                "supported_currencies", cryptoPaymentService.getSupportedCurrencies().size()
        );
        
        return ResponseEntity.ok(health);
    }
}