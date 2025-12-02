package com.waqiti.rewards.controller;

import com.waqiti.rewards.dto.*;
import com.waqiti.rewards.service.RewardsService;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * REST API controller for rewards and loyalty program management
 */
@RestController
@Slf4j
@RestController
@RequestMapping("/api/v1/rewards")
@RequiredArgsConstructor
@Validated
@Tag(name = "Rewards", description = "Rewards and loyalty program management")
public class RewardsController {

    private final RewardsService rewardsService;
    private final SecurityContext securityContext;

    @Operation(
        summary = "Create rewards account",
        description = "Creates a new rewards account for the authenticated user"
    )
    @ApiResponse(responseCode = "201", description = "Rewards account created successfully")
    @ApiResponse(responseCode = "409", description = "Rewards account already exists")
    @PostMapping("/account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RewardsAccountDto> createRewardsAccount() {
        String userId = securityContext.getCurrentUserId();
        log.info("Creating rewards account for user: {}", userId);
        
        RewardsAccountDto account = rewardsService.createRewardsAccount(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @Operation(
        summary = "Get rewards summary",
        description = "Get comprehensive rewards summary including balances, recent activity, and tier information"
    )
    @ApiResponse(responseCode = "200", description = "Rewards summary retrieved successfully")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RewardsSummaryDto> getRewardsSummary() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting rewards summary for user: {}", userId);
        
        RewardsSummaryDto summary = rewardsService.getRewardsSummary(userId);
        return ResponseEntity.ok(summary);
    }

    @Operation(
        summary = "Get rewards account",
        description = "Get detailed information about the user's rewards account"
    )
    @ApiResponse(responseCode = "200", description = "Rewards account retrieved successfully")
    @GetMapping("/account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RewardsAccountDto> getRewardsAccount() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting rewards account for user: {}", userId);
        
        RewardsAccountDto account = rewardsService.getRewardsAccount(userId);
        return ResponseEntity.ok(account);
    }

    @Operation(
        summary = "Update rewards preferences",
        description = "Update user's rewards and notification preferences"
    )
    @ApiResponse(responseCode = "200", description = "Preferences updated successfully")
    @PutMapping("/preferences")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RewardsAccountDto> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Updating rewards preferences for user: {}", userId);
        
        RewardsAccountDto account = rewardsService.updatePreferences(userId, request);
        return ResponseEntity.ok(account);
    }

    @Operation(
        summary = "Process cashback",
        description = "Process cashback for a payment transaction (internal API)"
    )
    @ApiResponse(responseCode = "201", description = "Cashback processed successfully")
    @PostMapping("/cashback/process")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_SERVICE')")
    public ResponseEntity<CashbackTransactionDto> processCashback(
            @Valid @RequestBody ProcessCashbackRequest request) {
        log.info("Processing cashback for transaction: {}", request.getTransactionId());
        
        CashbackTransactionDto cashback = rewardsService.processCashback(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(cashback);
    }

    @Operation(
        summary = "Process points",
        description = "Process points for a transaction (internal API)"
    )
    @ApiResponse(responseCode = "201", description = "Points processed successfully")
    @PostMapping("/points/process")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('PAYMENT_SERVICE')")
    public ResponseEntity<PointsTransactionDto> processPoints(
            @Valid @RequestBody ProcessPointsRequest request) {
        log.info("Processing points for transaction: {}", request.getTransactionId());
        
        PointsTransactionDto points = rewardsService.processPoints(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(points);
    }

    @Operation(
        summary = "Redeem cashback",
        description = "Redeem accumulated cashback to wallet"
    )
    @ApiResponse(responseCode = "200", description = "Cashback redeemed successfully")
    @PostMapping("/cashback/redeem")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RedemptionTransactionDto> redeemCashback(
            @Valid @RequestBody RedeemCashbackRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Redeeming cashback for user: {}, amount: {}", userId, request.getAmount());
        
        RedemptionTransactionDto redemption = rewardsService.redeemCashback(userId, request);
        return ResponseEntity.ok(redemption);
    }

    @Operation(
        summary = "Redeem points",
        description = "Redeem points for various rewards (cashback, gift cards, etc.)"
    )
    @ApiResponse(responseCode = "200", description = "Points redeemed successfully")
    @PostMapping("/points/redeem")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RedemptionTransactionDto> redeemPoints(
            @Valid @RequestBody RedeemPointsRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Redeeming points for user: {}, points: {}, reward: {}", 
            userId, request.getPoints(), request.getRewardType());
        
        RedemptionTransactionDto redemption = rewardsService.redeemPoints(userId, request);
        return ResponseEntity.ok(redemption);
    }

    @Operation(
        summary = "Get cashback history",
        description = "Get paginated list of cashback transactions"
    )
    @ApiResponse(responseCode = "200", description = "Cashback history retrieved successfully")
    @GetMapping("/cashback")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<CashbackTransactionDto>> getCashbackHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting cashback history for user: {}", userId);
        
        Page<CashbackTransactionDto> cashback = rewardsService.getCashbackHistory(userId, pageable);
        return ResponseEntity.ok(cashback);
    }

    @Operation(
        summary = "Get points history",
        description = "Get paginated list of points transactions"
    )
    @ApiResponse(responseCode = "200", description = "Points history retrieved successfully")
    @GetMapping("/points")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<PointsTransactionDto>> getPointsHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting points history for user: {}", userId);
        
        Page<PointsTransactionDto> points = rewardsService.getPointsHistory(userId, pageable);
        return ResponseEntity.ok(points);
    }

    @Operation(
        summary = "Get redemption history",
        description = "Get paginated list of redemption transactions"
    )
    @ApiResponse(responseCode = "200", description = "Redemption history retrieved successfully")
    @GetMapping("/redemptions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<RedemptionTransactionDto>> getRedemptionHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting redemption history for user: {}", userId);
        
        Page<RedemptionTransactionDto> redemptions = rewardsService.getRedemptionHistory(userId, pageable);
        return ResponseEntity.ok(redemptions);
    }

    @Operation(
        summary = "Get available campaigns",
        description = "Get list of active reward campaigns available to the user"
    )
    @ApiResponse(responseCode = "200", description = "Available campaigns retrieved successfully")
    @GetMapping("/campaigns")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CampaignDto>> getAvailableCampaigns() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting available campaigns for user: {}", userId);
        
        List<CampaignDto> campaigns = rewardsService.getAvailableCampaigns(userId);
        return ResponseEntity.ok(campaigns);
    }

    @Operation(
        summary = "Get tier information",
        description = "Get detailed information about loyalty tiers and benefits"
    )
    @ApiResponse(responseCode = "200", description = "Tier information retrieved successfully")
    @GetMapping("/tiers")
    public ResponseEntity<List<LoyaltyTierDto>> getTierInformation() {
        log.debug("Getting loyalty tier information");
        
        List<LoyaltyTierDto> tiers = rewardsService.getTierInformation();
        return ResponseEntity.ok(tiers);
    }

    @Operation(
        summary = "Get merchant rewards",
        description = "Get cashback rates and offers for specific merchants"
    )
    @ApiResponse(responseCode = "200", description = "Merchant rewards retrieved successfully")
    @GetMapping("/merchants")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<MerchantRewardsDto>> getMerchantRewards(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 50) Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting merchant rewards for user: {}, category: {}, search: {}", 
            userId, category, search);
        
        Page<MerchantRewardsDto> merchants = rewardsService.getMerchantRewards(
            userId, category, search, pageable);
        return ResponseEntity.ok(merchants);
    }

    @Operation(
        summary = "Get cashback estimate",
        description = "Get estimated cashback for a potential transaction"
    )
    @ApiResponse(responseCode = "200", description = "Cashback estimate calculated successfully")
    @PostMapping("/estimate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CashbackEstimateDto> estimateCashback(
            @Valid @RequestBody CashbackEstimateRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Estimating cashback for user: {}, merchant: {}, amount: {}", 
            userId, request.getMerchantId(), request.getAmount());
        
        CashbackEstimateDto estimate = rewardsService.estimateCashback(userId, request);
        return ResponseEntity.ok(estimate);
    }

    @Operation(
        summary = "Get spending analytics",
        description = "Get spending and rewards analytics for the user"
    )
    @ApiResponse(responseCode = "200", description = "Analytics retrieved successfully")
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RewardsAnalyticsDto> getAnalytics(
            @RequestParam(defaultValue = "30") int days) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting rewards analytics for user: {}, days: {}", userId, days);
        
        RewardsAnalyticsDto analytics = rewardsService.getAnalytics(userId, days);
        return ResponseEntity.ok(analytics);
    }

    @Operation(
        summary = "Admin: Get user rewards account",
        description = "Get rewards account for any user (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "User rewards account retrieved successfully")
    @GetMapping("/admin/users/{userId}/account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RewardsAccountDto> getAdminUserAccount(
            @Parameter(description = "User ID") @PathVariable @ValidUUID String userId) {
        log.info("Admin getting rewards account for user: {}", userId);
        
        RewardsAccountDto account = rewardsService.getRewardsAccount(userId);
        return ResponseEntity.ok(account);
    }

    @Operation(
        summary = "Admin: Adjust user rewards",
        description = "Manually adjust user's rewards balance (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Rewards adjusted successfully")
    @PostMapping("/admin/users/{userId}/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RewardsAccountDto> adjustUserRewards(
            @Parameter(description = "User ID") @PathVariable @ValidUUID String userId,
            @Valid @RequestBody AdjustRewardsRequest request) {
        String adminUserId = securityContext.getCurrentUserId();
        log.warn("Admin {} adjusting rewards for user: {}, adjustment: {}", 
            adminUserId, userId, request);
        
        RewardsAccountDto account = rewardsService.adjustUserRewards(userId, request, adminUserId);
        return ResponseEntity.ok(account);
    }

    @Operation(
        summary = "Admin: Get system rewards metrics",
        description = "Get system-wide rewards program metrics (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "System metrics retrieved successfully")
    @GetMapping("/admin/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SystemRewardsMetricsDto> getSystemMetrics() {
        log.info("Getting system rewards metrics");
        
        SystemRewardsMetricsDto metrics = rewardsService.getSystemMetrics();
        return ResponseEntity.ok(metrics);
    }
}