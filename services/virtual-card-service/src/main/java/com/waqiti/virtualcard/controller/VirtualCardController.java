package com.waqiti.virtualcard.controller;

import com.waqiti.common.ratelimit.RateLimited;
import com.waqiti.virtualcard.dto.*;
import com.waqiti.virtualcard.service.VirtualCardService;
import com.waqiti.virtualcard.security.VirtualCardDeviceMfaService;
import com.waqiti.virtualcard.security.VirtualCardDeviceMfaService.*;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.ValidUUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for virtual card management with device-based 2FA
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/virtual-cards")
@RequiredArgsConstructor
@Validated
@Tag(name = "Virtual Cards", description = "Virtual card management and transactions with device-based 2FA")
public class VirtualCardController {

    private final VirtualCardService virtualCardService;
    private final VirtualCardDeviceMfaService deviceMfaService;
    private final SecurityContext securityContext;

    @Operation(
        summary = "Create virtual card",
        description = "Create a new virtual card for the authenticated user"
    )
    @ApiResponse(responseCode = "201", description = "Virtual card created successfully")
    @ApiResponse(responseCode = "409", description = "Card limit exceeded")
    @ApiResponse(responseCode = "403", description = "KYC verification required")
    @PostMapping
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VirtualCardDto> createVirtualCard(
            @Valid @RequestBody CreateCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Creating virtual card for user: {}, type: {}", userId, request.getType());
        
        VirtualCardDto card = virtualCardService.createVirtualCard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(card);
    }

    @Operation(
        summary = "Get user's virtual cards",
        description = "Get all virtual cards for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Virtual cards retrieved successfully")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<VirtualCardDto>> getUserCards() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting virtual cards for user: {}", userId);
        
        List<VirtualCardDto> cards = virtualCardService.getUserCards(userId);
        return ResponseEntity.ok(cards);
    }

    @Operation(
        summary = "Get card details",
        description = "Get detailed information about a specific virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Card details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Card not found")
    @GetMapping("/{cardId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VirtualCardDto> getCardDetails(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @RequestParam(defaultValue = "false") boolean includeSensitive) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting card details for card: {}, user: {}, sensitive: {}", 
            cardId, userId, includeSensitive);
        
        VirtualCardDto card = virtualCardService.getCardDetails(cardId, includeSensitive);
        return ResponseEntity.ok(card);
    }

    @Operation(
        summary = "Get card secrets",
        description = "Get card number and CVV (requires device-based 2FA)"
    )
    @ApiResponse(responseCode = "200", description = "Card secrets retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Device authentication required")
    @PostMapping("/{cardId}/secrets")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 30)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardDetailsDto> getCardSecrets(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody GetCardSecretsRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.warn("Requesting card secrets for card: {}, user: {}", cardId, userId);
        
        // Verify device MFA session before revealing secrets
        if (request.getDeviceSessionToken() == null || !isValidDeviceSession(request.getDeviceSessionToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        CardDetailsDto secrets = virtualCardService.getCardSecrets(cardId, 
            request.getAuthenticationToken());
        return ResponseEntity.ok(secrets);
    }
    
    // Device-Based 2FA Endpoints
    
    @Operation(
        summary = "Register device for virtual card operations",
        description = "Register a new device for secure virtual card transactions"
    )
    @ApiResponse(responseCode = "201", description = "Device registered successfully")
    @ApiResponse(responseCode = "400", description = "Device does not meet security requirements")
    @PostMapping("/device/register")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 60)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DeviceRegistrationResult> registerDevice(
            @Valid @RequestBody DeviceInfo deviceInfo) {
        String userId = securityContext.getCurrentUserId();
        log.info("Registering device {} for user {}", deviceInfo.getDeviceId(), userId);
        
        DeviceRegistrationResult result = deviceMfaService.registerDevice(userId, deviceInfo);
        
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(result);
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
    
    @Operation(
        summary = "Initiate device MFA for transaction",
        description = "Initiate device-based multi-factor authentication for a virtual card transaction"
    )
    @ApiResponse(responseCode = "200", description = "MFA challenge initiated")
    @ApiResponse(responseCode = "403", description = "Device locked or blocked")
    @PostMapping("/device/mfa/initiate")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 5)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DeviceMfaChallenge> initiateDeviceMfa(
            @RequestParam String deviceId,
            @Valid @RequestBody TransactionContext transactionContext) {
        String userId = securityContext.getCurrentUserId();
        log.info("Initiating device MFA for user {} device {} transaction {}", 
            userId, deviceId, transactionContext.getTransactionId());
        
        DeviceMfaRequirement requirement = deviceMfaService.determineDeviceMfaRequirement(
            userId, deviceId, transactionContext);
        
        if (requirement.isBlocked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        if (requirement.isRequiresRegistration()) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).build();
        }
        
        if (!requirement.isRequired()) {
            // Trusted device, no additional MFA required
            return ResponseEntity.ok(DeviceMfaChallenge.builder()
                .challengeId("TRUSTED")
                .requiredMethods(List.of())
                .message("Device trusted - no additional authentication required")
                .build());
        }
        
        DeviceMfaChallenge challenge = deviceMfaService.generateDeviceMfaChallenge(
            userId, deviceId, transactionContext.getTransactionId(), requirement);
        
        return ResponseEntity.ok(challenge);
    }
    
    @Operation(
        summary = "Verify device MFA response",
        description = "Verify the device-based MFA response for a transaction"
    )
    @ApiResponse(responseCode = "200", description = "MFA verification successful")
    @ApiResponse(responseCode = "401", description = "MFA verification failed")
    @ApiResponse(responseCode = "423", description = "Device locked")
    @PostMapping("/device/mfa/verify")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DeviceMfaVerificationResult> verifyDeviceMfa(
            @RequestParam String challengeId,
            @Valid @RequestBody Map<MfaMethod, String> mfaResponses) {
        log.info("Verifying device MFA for challenge {}", challengeId);
        
        DeviceMfaVerificationResult result = deviceMfaService.verifyDeviceMfa(challengeId, mfaResponses);
        
        if (result.isDeviceLocked()) {
            return ResponseEntity.status(HttpStatus.LOCKED).body(result);
        }
        
        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
        
        return ResponseEntity.ok(result);
    }
    
    @Operation(
        summary = "Trust device",
        description = "Add device to trusted devices list (requires 2FA)"
    )
    @ApiResponse(responseCode = "200", description = "Device trusted successfully")
    @PostMapping("/device/trust")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 60)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TrustDeviceResult> trustDevice(
            @RequestParam String deviceId,
            @RequestParam String verificationCode) {
        String userId = securityContext.getCurrentUserId();
        log.info("Adding device {} to trusted devices for user {}", deviceId, userId);
        
        TrustDeviceResult result = deviceMfaService.trustDevice(userId, deviceId, verificationCode);
        
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(result);
        }
        
        return ResponseEntity.ok(result);
    }
    
    @Operation(
        summary = "Remove device",
        description = "Remove device from registered/trusted devices"
    )
    @ApiResponse(responseCode = "200", description = "Device removed successfully")
    @DeleteMapping("/device/{deviceId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RemoveDeviceResult> removeDevice(
            @PathVariable String deviceId,
            @RequestParam String reason) {
        String userId = securityContext.getCurrentUserId();
        log.info("Removing device {} for user {} - reason: {}", deviceId, userId, reason);
        
        RemoveDeviceResult result = deviceMfaService.removeDevice(userId, deviceId, reason);
        return ResponseEntity.ok(result);
    }
    
    @Operation(
        summary = "Get trusted devices",
        description = "Get list of user's trusted devices"
    )
    @ApiResponse(responseCode = "200", description = "Trusted devices retrieved successfully")
    @GetMapping("/device/trusted")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<TrustedDeviceInfo>> getTrustedDevices() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting trusted devices for user {}", userId);
        
        List<TrustedDeviceInfo> devices = deviceMfaService.getTrustedDevices(userId);
        return ResponseEntity.ok(devices);
    }
    
    @Operation(
        summary = "Get device security status",
        description = "Get security status and trust score for a device"
    )
    @ApiResponse(responseCode = "200", description = "Device status retrieved successfully")
    @GetMapping("/device/{deviceId}/status")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DeviceSecurityStatus> getDeviceStatus(
            @PathVariable String deviceId) {
        String userId = securityContext.getCurrentUserId();
        
        // This would be implemented to return device security status
        DeviceSecurityStatus status = DeviceSecurityStatus.builder()
            .deviceId(deviceId)
            .trusted(true)
            .trustScore(0.85)
            .lastUsed(java.time.LocalDateTime.now())
            .requiresBiometricSetup(false)
            .build();
        
        return ResponseEntity.ok(status);
    }
    
    private boolean isValidDeviceSession(String sessionToken) {
        if (sessionToken == null || sessionToken.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Parse and validate JWT session token
            Claims claims = jwtTokenValidator.validateToken(sessionToken);
            
            // Check token expiry
            if (claims.getExpiration().before(new Date())) {
                log.warn("Virtual card session token expired: {}", claims.getSubject());
                return false;
            }
            
            // Validate session scope
            String scope = claims.get("scope", String.class);
            if (!"virtual-card-access".equals(scope)) {
                log.warn("Invalid session scope for virtual card access: {}", scope);
                return false;
            }
            
            // Validate device fingerprint
            String deviceFingerprint = claims.get("deviceFingerprint", String.class);
            if (deviceFingerprint == null || !deviceSecurityService.isValidDeviceFingerprint(deviceFingerprint)) {
                log.warn("Invalid device fingerprint in session token");
                return false;
            }
            
            // Check if session is in blacklist (logout/revoked sessions)
            String sessionId = claims.get("sessionId", String.class);
            if (sessionBlacklistService.isSessionBlacklisted(sessionId)) {
                log.warn("Session is blacklisted: {}", sessionId);
                return false;
            }
            
            // Validate user status
            String userId = claims.getSubject();
            if (!userService.isUserActiveAndAuthorized(userId)) {
                log.warn("User is not active or authorized: {}", userId);
                return false;
            }
            
            // Check rate limiting for virtual card operations
            if (rateLimitingService.isRateLimited(userId, "virtual-card-ops")) {
                log.warn("Rate limit exceeded for user: {}", userId);
                return false;
            }
            
            return true;
            
        } catch (ExpiredJwtException e) {
            log.warn("Expired virtual card session token: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.error("Invalid virtual card session token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error validating virtual card session: {}", e.getMessage());
            return false;
        }
    }

    @Operation(
        summary = "Freeze/Unfreeze card",
        description = "Temporarily freeze or unfreeze a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Card status updated successfully")
    @PutMapping("/{cardId}/freeze")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VirtualCardDto> toggleCardFreeze(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @RequestParam boolean freeze) {
        String userId = securityContext.getCurrentUserId();
        log.info("User {} {} card {}", userId, freeze ? "freezing" : "unfreezing", cardId);
        
        VirtualCardDto card = virtualCardService.toggleCardFreeze(cardId, freeze);
        return ResponseEntity.ok(card);
    }

    @Operation(
        summary = "Update card limits",
        description = "Update spending limits for a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Card limits updated successfully")
    @PutMapping("/{cardId}/limits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardLimitsDto> updateCardLimits(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody UpdateLimitsRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Updating limits for card: {}, user: {}", cardId, userId);
        
        CardLimitsDto limits = virtualCardService.updateCardLimits(cardId, request);
        return ResponseEntity.ok(limits);
    }

    @Operation(
        summary = "Get card limits",
        description = "Get current spending limits for a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Card limits retrieved successfully")
    @GetMapping("/{cardId}/limits")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardLimitsDto> getCardLimits(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting limits for card: {}, user: {}", cardId, userId);
        
        CardLimitsDto limits = virtualCardService.getCardLimits(cardId);
        return ResponseEntity.ok(limits);
    }

    @Operation(
        summary = "Update card controls",
        description = "Update transaction controls and restrictions for a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Card controls updated successfully")
    @PutMapping("/{cardId}/controls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardControlsDto> updateCardControls(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody UpdateControlsRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Updating controls for card: {}, user: {}", cardId, userId);
        
        CardControlsDto controls = virtualCardService.updateCardControls(cardId, request);
        return ResponseEntity.ok(controls);
    }

    @Operation(
        summary = "Get card controls",
        description = "Get current transaction controls for a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Card controls retrieved successfully")
    @GetMapping("/{cardId}/controls")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardControlsDto> getCardControls(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting controls for card: {}, user: {}", cardId, userId);
        
        CardControlsDto controls = virtualCardService.getCardControls(cardId);
        return ResponseEntity.ok(controls);
    }

    @Operation(
        summary = "Fund virtual card",
        description = "Add funds to a virtual card from wallet balance"
    )
    @ApiResponse(responseCode = "200", description = "Card funded successfully")
    @ApiResponse(responseCode = "400", description = "Insufficient wallet balance")
    @PostMapping("/{cardId}/fund")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardTransactionDto> fundCard(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody FundCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Funding card: {} with amount: {} for user: {}", 
            cardId, request.getAmount(), userId);
        
        CardTransactionDto transaction = virtualCardService.fundCard(cardId, request.getAmount());
        return ResponseEntity.ok(transaction);
    }

    @Operation(
        summary = "Withdraw from virtual card",
        description = "Withdraw funds from a virtual card back to wallet"
    )
    @ApiResponse(responseCode = "200", description = "Withdrawal successful")
    @ApiResponse(responseCode = "400", description = "Insufficient card balance")
    @PostMapping("/{cardId}/withdraw")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 5)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardTransactionDto> withdrawFromCard(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody WithdrawCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Withdrawing from card: {} amount: {} for user: {}", 
            cardId, request.getAmount(), userId);
        
        CardTransactionDto transaction = virtualCardService.withdrawFromCard(
            cardId, request.getAmount());
        return ResponseEntity.ok(transaction);
    }

    @Operation(
        summary = "Get card transactions",
        description = "Get paginated list of transactions for a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Transactions retrieved successfully")
    @GetMapping("/{cardId}/transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<CardTransactionDto>> getCardTransactions(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String merchantCategory,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting transactions for card: {}, user: {}", cardId, userId);
        
        TransactionFilter filter = TransactionFilter.builder()
            .type(type)
            .status(status)
            .merchantCategory(merchantCategory)
            .startDate(startDate)
            .endDate(endDate)
            .build();
        
        Page<CardTransactionDto> transactions = virtualCardService.getCardTransactions(
            cardId, filter, pageable);
        return ResponseEntity.ok(transactions);
    }

    @Operation(
        summary = "Get spending analytics",
        description = "Get spending analytics and insights for a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @GetMapping("/{cardId}/analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<SpendingAnalyticsDto> getSpendingAnalytics(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @RequestParam(defaultValue = "MONTHLY") AnalyticsTimeframe timeframe) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting analytics for card: {}, user: {}, timeframe: {}", 
            cardId, userId, timeframe);
        
        SpendingAnalyticsDto analytics = virtualCardService.getSpendingAnalytics(
            cardId, timeframe);
        return ResponseEntity.ok(analytics);
    }

    @Operation(
        summary = "Update card nickname",
        description = "Update the display name/nickname for a virtual card"
    )
    @ApiResponse(responseCode = "200", description = "Card nickname updated successfully")
    @PutMapping("/{cardId}/nickname")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<VirtualCardDto> updateCardNickname(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody UpdateNicknameRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Updating nickname for card: {}, user: {}", cardId, userId);
        
        VirtualCardDto card = virtualCardService.updateCardNickname(cardId, request.getNickname());
        return ResponseEntity.ok(card);
    }

    @Operation(
        summary = "Delete virtual card",
        description = "Permanently delete/close a virtual card"
    )
    @ApiResponse(responseCode = "204", description = "Card deleted successfully")
    @ApiResponse(responseCode = "400", description = "Card has remaining balance")
    @DeleteMapping("/{cardId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteCard(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId) {
        String userId = securityContext.getCurrentUserId();
        log.warn("Deleting card: {} for user: {}", cardId, userId);
        
        virtualCardService.deleteCard(cardId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Process card transaction",
        description = "Process incoming card transaction from provider (webhook endpoint)"
    )
    @ApiResponse(responseCode = "200", description = "Transaction processed successfully")
    @PostMapping("/webhook/transaction")
    @RateLimited(keyType = RateLimited.KeyType.IP, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @PreAuthorize("hasRole('CARD_PROVIDER')")
    public ResponseEntity<Void> processCardTransaction(
            @Valid @RequestBody CardTransactionWebhook webhook) {
        log.info("Processing card transaction webhook: {}", webhook.getTransactionId());
        
        virtualCardService.processCardTransaction(webhook);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Get card summary",
        description = "Get summary information for all user's virtual cards"
    )
    @ApiResponse(responseCode = "200", description = "Card summary retrieved successfully")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardSummaryDto> getCardSummary() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting card summary for user: {}", userId);
        
        CardSummaryDto summary = virtualCardService.getCardSummary(userId);
        return ResponseEntity.ok(summary);
    }

    @Operation(
        summary = "Get supported currencies",
        description = "Get list of currencies supported for virtual cards"
    )
    @ApiResponse(responseCode = "200", description = "Supported currencies retrieved")
    @GetMapping("/currencies")
    public ResponseEntity<List<String>> getSupportedCurrencies() {
        List<String> currencies = virtualCardService.getSupportedCurrencies();
        return ResponseEntity.ok(currencies);
    }

    @Operation(
        summary = "Admin: Get user's virtual cards",
        description = "Get all virtual cards for any user (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "User cards retrieved successfully")
    @GetMapping("/admin/users/{userId}/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<VirtualCardDto>> getAdminUserCards(
            @Parameter(description = "User ID") @PathVariable @ValidUUID String userId) {
        log.info("Admin getting virtual cards for user: {}", userId);
        
        List<VirtualCardDto> cards = virtualCardService.getUserCards(userId);
        return ResponseEntity.ok(cards);
    }

    @Operation(
        summary = "Admin: Force card status change",
        description = "Forcibly change card status (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Card status updated successfully")
    @PutMapping("/admin/cards/{cardId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VirtualCardDto> updateCardStatus(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody UpdateCardStatusRequest request) {
        String adminUserId = securityContext.getCurrentUserId();
        log.warn("Admin {} updating status for card: {} to {}", 
            adminUserId, cardId, request.getStatus());
        
        VirtualCardDto card = virtualCardService.updateCardStatus(cardId, request, adminUserId);
        return ResponseEntity.ok(card);
    }

    @Operation(
        summary = "Admin: Get system metrics",
        description = "Get system-wide virtual card metrics (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "System metrics retrieved successfully")
    @GetMapping("/admin/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<VirtualCardMetricsDto> getSystemMetrics() {
        log.info("Getting virtual card system metrics");
        
        VirtualCardMetricsDto metrics = virtualCardService.getSystemMetrics();
        return ResponseEntity.ok(metrics);
    }
}