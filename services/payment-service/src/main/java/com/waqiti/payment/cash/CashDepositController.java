package com.waqiti.payment.cash;

import com.waqiti.payment.cash.dto.*;
import com.waqiti.payment.cash.service.CashDepositService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.ValidUUID;
import com.waqiti.common.ratelimiting.RateLimited;
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
import java.math.BigDecimal;
import java.util.List;

/**
 * Production-ready Cash Deposit Network Integration Controller
 * 
 * Provides comprehensive cash deposit functionality including:
 * - Multi-network support (MoneyGram, Western Union, PayPal, CashApp, Venmo)
 * - Reference generation with QR/barcode support
 * - Location finder for nearby deposit locations
 * - Real-time status tracking and webhooks
 * - Fee calculation and limit management
 * - Admin dashboard functionality
 * - Comprehensive analytics and reporting
 * 
 * Security Features:
 * - Rate limiting on all endpoints
 * - User ownership validation
 * - Admin-only operations
 * - Webhook authentication
 * 
 * @author Waqiti Payment Team
 * @version 2.0.0
 * @since 2025-01-16
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/cash-deposits")
@RequiredArgsConstructor
@Validated
@Tag(name = "Cash Deposits", description = "Multi-network cash deposit integration with MoneyGram, Western Union, and digital wallets")
public class CashDepositController {

    private final CashDepositService cashDepositService;
    private final SecurityContext securityContext;

    @Operation(
        summary = "Generate deposit reference",
        description = "Generate a cash deposit reference code for depositing money at partner locations"
    )
    @ApiResponse(responseCode = "201", description = "Deposit reference generated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request or amount limits exceeded")
    @ApiResponse(responseCode = "403", description = "User verification required")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @PostMapping("/generate-reference")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 5, refillTokens = 5, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositReferenceDto> generateDepositReference(
            @Valid @RequestBody GenerateReferenceRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Generating cash deposit reference for user: {}, amount: {}, network: {}", 
            userId, request.getAmount(), request.getPreferredNetwork());
        
        CashDepositReferenceDto reference = cashDepositService.generateDepositReference(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reference);
    }

    @Operation(
        summary = "Get deposit status",
        description = "Get the current status of a cash deposit with real-time updates"
    )
    @ApiResponse(responseCode = "200", description = "Deposit status retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Deposit not found")
    @GetMapping("/{depositId}")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositDto> getDepositStatus(
            @Parameter(description = "Deposit ID") @PathVariable @ValidUUID String depositId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting deposit status: {}, user: {}", depositId, userId);
        
        CashDepositDto deposit = cashDepositService.getDepositStatus(depositId, userId);
        return ResponseEntity.ok(deposit);
    }

    @Operation(
        summary = "Get deposit history",
        description = "Get paginated list of cash deposits for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Deposit history retrieved successfully")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    public ResponseEntity<Page<CashDepositDto>> getDepositHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting deposit history for user: {}", userId);
        
        Page<CashDepositDto> deposits = cashDepositService.getDepositHistory(userId, pageable);
        return ResponseEntity.ok(deposits);
    }

    @Operation(
        summary = "Cancel pending deposit",
        description = "Cancel a pending cash deposit reference before completion"
    )
    @ApiResponse(responseCode = "204", description = "Deposit cancelled successfully")
    @ApiResponse(responseCode = "400", description = "Cannot cancel non-pending deposit")
    @DeleteMapping("/{depositId}")
    @PreAuthorize("hasAuthority('CASH_DEPOSIT_CANCEL') and @depositOwnershipValidator.canCancelDeposit(authentication.name, #depositId)")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 1)
    @AuditLog(action = "CASH_DEPOSIT_CANCELLED", level = AuditLevel.MEDIUM)
    public ResponseEntity<Void> cancelDeposit(
            @Parameter(description = "Deposit ID") @PathVariable @ValidUUID String depositId,
            @RequestParam(defaultValue = "User requested cancellation") String reason) {
        String userId = securityContext.getCurrentUserId();
        log.info("Cancelling deposit: {} for user: {}, reason: {}", depositId, userId, reason);
        
        cashDepositService.cancelDeposit(depositId, userId, reason);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Find nearby locations",
        description = "Find nearby cash deposit locations for specified networks with real-time availability"
    )
    @ApiResponse(responseCode = "200", description = "Nearby locations retrieved successfully")
    @PostMapping("/locations/nearby")
    @PreAuthorize("hasAuthority('LOCATION_SEARCH')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    public ResponseEntity<List<CashDepositLocationDto>> findNearbyLocations(
            @Valid @RequestBody LocationRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Finding nearby locations for user: {}, lat: {}, lng: {}, radius: {}km", 
            userId, request.getLatitude(), request.getLongitude(), request.getRadiusKm());
        
        List<CashDepositLocationDto> locations = cashDepositService.findNearbyLocations(request);
        return ResponseEntity.ok(locations);
    }

    @Operation(
        summary = "Get supported networks",
        description = "Get list of supported cash deposit networks with availability and features"
    )
    @ApiResponse(responseCode = "200", description = "Supported networks retrieved successfully")
    @GetMapping("/networks")
    @PreAuthorize("hasAuthority('NETWORK_INFO_READ')")
    @Cacheable(value = "deposit-networks", unless = "#result.isEmpty()")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 1)
    public ResponseEntity<List<CashDepositNetworkDto>> getSupportedNetworks() {
        log.debug("Getting supported cash deposit networks");
        
        List<CashDepositNetworkDto> networks = cashDepositService.getSupportedNetworks();
        return ResponseEntity.ok(networks);
    }

    @Operation(
        summary = "Calculate deposit fee",
        description = "Calculate the fee for a cash deposit amount across all supported networks"
    )
    @ApiResponse(responseCode = "200", description = "Fee calculated successfully")
    @GetMapping("/calculate-fee")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositFeeDto> calculateDepositFee(
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) CashDepositNetwork network) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Calculating deposit fee for user: {}, amount: {}, network: {}", userId, amount, network);
        
        CashDepositFeeDto feeDto = cashDepositService.calculateDepositFee(amount, network);
        return ResponseEntity.ok(feeDto);
    }

    @Operation(
        summary = "Get deposit limits",
        description = "Get current deposit limits and usage for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Deposit limits retrieved successfully")
    @GetMapping("/limits")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositLimitsDto> getDepositLimits() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting deposit limits for user: {}", userId);
        
        CashDepositLimitsDto limits = cashDepositService.getDepositLimits(userId);
        return ResponseEntity.ok(limits);
    }

    @Operation(
        summary = "Get deposit by reference",
        description = "Get deposit details by reference number"
    )
    @ApiResponse(responseCode = "200", description = "Deposit retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Deposit not found")
    @GetMapping("/reference/{referenceNumber}")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositDto> getDepositByReference(
            @Parameter(description = "Reference Number") @PathVariable String referenceNumber) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting deposit by reference: {}, user: {}", referenceNumber, userId);
        
        CashDepositDto deposit = cashDepositService.getDepositByReference(referenceNumber, userId);
        return ResponseEntity.ok(deposit);
    }

    @Operation(
        summary = "Resend deposit instructions",
        description = "Resend deposit instructions and reference details to user via SMS/email"
    )
    @ApiResponse(responseCode = "204", description = "Instructions sent successfully")
    @PostMapping("/{depositId}/resend-instructions")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 3, refillTokens = 3, refillPeriodMinutes = 1)
    public ResponseEntity<Void> resendInstructions(
            @Parameter(description = "Deposit ID") @PathVariable @ValidUUID String depositId) {
        String userId = securityContext.getCurrentUserId();
        log.info("Resending instructions for deposit: {}, user: {}", depositId, userId);
        
        cashDepositService.resendInstructions(depositId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Process deposit confirmation webhook",
        description = "Process cash deposit confirmation from network provider (webhook endpoint)"
    )
    @ApiResponse(responseCode = "200", description = "Deposit confirmation processed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid webhook signature or data")
    @PostMapping("/webhook/{network}/confirmation")
    @PreAuthorize("@webhookAuthenticationService.isValidProviderWebhook(#network, #httpRequest)")
    public ResponseEntity<Void> processCashDepositConfirmation(
            @PathVariable CashDepositNetwork network,
            @Valid @RequestBody CashDepositConfirmationRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("Processing cash deposit confirmation for network: {}, reference: {}, amount: {}", 
            network, request.getReferenceNumber(), request.getActualAmount());
        
        cashDepositService.processCashDepositConfirmation(network, request, httpRequest);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Get location details",
        description = "Get detailed information about a specific cash deposit location"
    )
    @ApiResponse(responseCode = "200", description = "Location details retrieved successfully")
    @GetMapping("/locations/{locationId}")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositLocationDto> getLocationDetails(
            @Parameter(description = "Location ID") @PathVariable String locationId,
            @RequestParam CashDepositNetwork network) {
        log.debug("Getting location details: {}, network: {}", locationId, network);
        
        CashDepositLocationDto location = cashDepositService.getLocationDetails(locationId, network);
        return ResponseEntity.ok(location);
    }

    @Operation(
        summary = "Get deposit analytics",
        description = "Get comprehensive cash deposit analytics for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 10, refillTokens = 10, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositAnalyticsDto> getDepositAnalytics(
            @RequestParam(defaultValue = "30") int days) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting deposit analytics for user: {}, days: {}", userId, days);
        
        CashDepositAnalyticsDto analytics = cashDepositService.getDepositAnalytics(userId, days);
        return ResponseEntity.ok(analytics);
    }

    @Operation(
        summary = "Track deposit in real-time",
        description = "Get real-time tracking information for an active deposit"
    )
    @ApiResponse(responseCode = "200", description = "Tracking information retrieved successfully")
    @GetMapping("/{depositId}/tracking")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositTrackingDto> trackDeposit(
            @Parameter(description = "Deposit ID") @PathVariable @ValidUUID String depositId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Tracking deposit: {}, user: {}", depositId, userId);
        
        CashDepositTrackingDto tracking = cashDepositService.trackDeposit(depositId, userId);
        return ResponseEntity.ok(tracking);
    }

    @Operation(
        summary = "Get QR code for deposit",
        description = "Generate QR code containing deposit reference and instructions"
    )
    @ApiResponse(responseCode = "200", description = "QR code generated successfully")
    @GetMapping("/{depositId}/qr-code")
    @PreAuthorize("hasRole('USER')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositQRCodeDto> getDepositQRCode(
            @Parameter(description = "Deposit ID") @PathVariable @ValidUUID String depositId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Generating QR code for deposit: {}, user: {}", depositId, userId);
        
        CashDepositQRCodeDto qrCode = cashDepositService.generateQRCode(depositId, userId);
        return ResponseEntity.ok(qrCode);
    }

    // ========================================
    // ADMIN ENDPOINTS
    // ========================================

    @Operation(
        summary = "Admin: Get all deposits",
        description = "Get all cash deposits in the system with filtering (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "All deposits retrieved successfully")
    @GetMapping("/admin/deposits")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    public ResponseEntity<Page<CashDepositDto>> getAllDeposits(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String network,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 50) Pageable pageable) {
        log.info("Admin getting all deposits, status: {}, network: {}, user: {}", status, network, userId);
        
        Page<CashDepositDto> deposits = cashDepositService.getAllDeposits(
            status, network, startDate, endDate, userId, pageable);
        return ResponseEntity.ok(deposits);
    }

    @Operation(
        summary = "Admin: Get system metrics",
        description = "Get comprehensive system-wide cash deposit metrics (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "System metrics retrieved successfully")
    @GetMapping("/admin/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositSystemMetricsDto> getSystemMetrics(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting cash deposit system metrics for {} days", days);
        
        CashDepositSystemMetricsDto metrics = cashDepositService.getSystemMetrics(days);
        return ResponseEntity.ok(metrics);
    }

    @Operation(
        summary = "Admin: Force deposit status update",
        description = "Manually update deposit status with audit trail (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Status updated successfully")
    @PutMapping("/admin/deposits/{depositId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    public ResponseEntity<CashDepositDto> updateDepositStatus(
            @Parameter(description = "Deposit ID") @PathVariable @ValidUUID String depositId,
            @Valid @RequestBody UpdateDepositStatusRequest request) {
        String adminUserId = securityContext.getCurrentUserId();
        log.warn("Admin {} updating deposit {} status to {} with reason: {}", 
            adminUserId, depositId, request.getStatus(), request.getReason());
        
        CashDepositDto deposit = cashDepositService.updateDepositStatus(depositId, request, adminUserId);
        return ResponseEntity.ok(deposit);
    }

    @Operation(
        summary = "Admin: Get deposit audit trail",
        description = "Get complete audit trail for a deposit (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Audit trail retrieved successfully")
    @GetMapping("/admin/deposits/{depositId}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 50, refillTokens = 50, refillPeriodMinutes = 1)
    public ResponseEntity<List<CashDepositAuditDto>> getDepositAuditTrail(
            @Parameter(description = "Deposit ID") @PathVariable @ValidUUID String depositId) {
        log.info("Getting audit trail for deposit: {}", depositId);
        
        List<CashDepositAuditDto> auditTrail = cashDepositService.getDepositAuditTrail(depositId);
        return ResponseEntity.ok(auditTrail);
    }

    @Operation(
        summary = "Admin: Get network health status",
        description = "Get real-time health status of all cash deposit networks (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Network health status retrieved successfully")
    @GetMapping("/admin/network-health")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 20, refillTokens = 20, refillPeriodMinutes = 1)
    public ResponseEntity<List<CashDepositNetworkHealthDto>> getNetworkHealthStatus() {
        log.info("Getting network health status");
        
        List<CashDepositNetworkHealthDto> healthStatus = cashDepositService.getNetworkHealthStatus();
        return ResponseEntity.ok(healthStatus);
    }
}