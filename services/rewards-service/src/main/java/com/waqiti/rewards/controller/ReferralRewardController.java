package com.waqiti.rewards.controller;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.rewards.domain.ReferralReward;
import com.waqiti.rewards.dto.referral.ReferralRewardDto;
import com.waqiti.rewards.enums.RewardStatus;
import com.waqiti.rewards.service.ReferralRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for referral reward management
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/referrals/rewards")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Referral Rewards", description = "Referral reward management and redemption")
public class ReferralRewardController {

    private final ReferralRewardService rewardService;
    private final SecurityContext securityContext;

    @Operation(
            summary = "Get my rewards",
            description = "Get all referral rewards for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Rewards retrieved successfully")
    @GetMapping("/my-rewards")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ReferralRewardDto>> getMyRewards(
            @RequestParam(required = false) RewardStatus status,
            @RequestParam(required = false) String programId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving referral rewards", userId);

        Page<ReferralReward> rewards;
        if (status != null) {
            rewards = rewardService.getUserRewardsByStatus(
                    UUID.fromString(userId),
                    status,
                    pageable
            );
        } else if (programId != null) {
            rewards = rewardService.getUserRewardsByProgram(
                    UUID.fromString(userId),
                    programId,
                    pageable
            );
        } else {
            rewards = rewardService.getUserRewards(UUID.fromString(userId), pageable);
        }

        Page<ReferralRewardDto> dtos = rewards.map(this::toDto);
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get reward by ID",
            description = "Get detailed information about a specific reward"
    )
    @ApiResponse(responseCode = "200", description = "Reward retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Reward not found")
    @GetMapping("/{rewardId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralRewardDto> getRewardById(
            @Parameter(description = "Reward ID", example = "RWD-ABC12345")
            @PathVariable String rewardId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving reward: {}", userId, rewardId);

        ReferralReward reward = rewardService.getRewardByRewardId(rewardId);

        // Verify ownership
        if (!reward.getRecipientUserId().toString().equals(userId)) {
            log.warn("User {} attempted to access reward {} owned by {}",
                    userId, rewardId, reward.getRecipientUserId());
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(toDto(reward));
    }

    @Operation(
            summary = "Get pending rewards",
            description = "Get all pending (not yet issued) rewards for the user"
    )
    @ApiResponse(responseCode = "200", description = "Pending rewards retrieved successfully")
    @GetMapping("/pending")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ReferralRewardDto>> getPendingRewards() {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving pending rewards", userId);

        List<ReferralReward> rewards = rewardService.getPendingRewardsForUser(
                UUID.fromString(userId)
        );

        List<ReferralRewardDto> dtos = rewards.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get reward summary",
            description = "Get summary statistics of user's referral rewards"
    )
    @ApiResponse(responseCode = "200", description = "Summary retrieved successfully")
    @GetMapping("/summary")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<RewardSummaryDto> getRewardSummary() {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving reward summary", userId);

        UUID userUuid = UUID.fromString(userId);

        // Get various metrics
        BigDecimal totalCashback = rewardService.getTotalCashbackEarned(userUuid);
        Long totalPoints = rewardService.getTotalPointsEarned(userUuid);
        List<ReferralReward> pending = rewardService.getPendingRewardsForUser(userUuid);
        List<ReferralReward> expiringSoon = rewardService.getExpiringSoonRewards(userUuid, 30);

        BigDecimal pendingCashback = pending.stream()
                .filter(r -> r.getCashbackAmount() != null)
                .map(ReferralReward::getCashbackAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Long pendingPoints = pending.stream()
                .filter(r -> r.getPointsAmount() != null)
                .map(ReferralReward::getPointsAmount)
                .reduce(0L, Long::sum);

        RewardSummaryDto summary = RewardSummaryDto.builder()
                .totalCashbackEarned(totalCashback)
                .totalPointsEarned(totalPoints)
                .pendingRewardsCount(pending.size())
                .pendingCashback(pendingCashback)
                .pendingPoints(pendingPoints)
                .expiringSoonCount(expiringSoon.size())
                .build();

        return ResponseEntity.ok(summary);
    }

    @Operation(
            summary = "Redeem reward",
            description = "Redeem an issued referral reward"
    )
    @ApiResponse(responseCode = "200", description = "Reward redeemed successfully")
    @ApiResponse(responseCode = "400", description = "Reward cannot be redeemed")
    @ApiResponse(responseCode = "404", description = "Reward not found")
    @PostMapping("/{rewardId}/redeem")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralRewardDto> redeemReward(
            @Parameter(description = "Reward ID") @PathVariable String rewardId,
            @Parameter(description = "Account ID for redemption") @RequestParam @NotBlank String accountId) {
        String userId = securityContext.getCurrentUserId();
        log.info("User {} redeeming reward: {} to account: {}", userId, rewardId, accountId);

        ReferralReward reward = rewardService.getRewardByRewardId(rewardId);

        // Verify ownership
        if (!reward.getRecipientUserId().toString().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        // Generate transaction ID
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        rewardService.redeemReward(rewardId, accountId, transactionId);
        ReferralReward updated = rewardService.getRewardByRewardId(rewardId);

        return ResponseEntity.ok(toDto(updated));
    }

    // ========== ADMIN ENDPOINTS ==========

    @Operation(
            summary = "Admin: Get all rewards",
            description = "Get all referral rewards in the system (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Rewards retrieved successfully")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReferralRewardDto>> getAllRewards(
            @RequestParam(required = false) RewardStatus status,
            @RequestParam(required = false) String programId,
            @RequestParam(required = false) String userId,
            @PageableDefault(size = 50, sort = "createdAt") Pageable pageable) {
        log.info("Admin retrieving all referral rewards");

        Page<ReferralReward> rewards;
        if (userId != null && status != null) {
            rewards = rewardService.getUserRewardsByStatus(
                    UUID.fromString(userId),
                    status,
                    pageable
            );
        } else if (userId != null) {
            rewards = rewardService.getUserRewards(UUID.fromString(userId), pageable);
        } else if (status != null) {
            rewards = rewardService.getRewardsByStatus(status, pageable);
        } else {
            rewards = rewardService.getAllRewards(pageable);
        }

        Page<ReferralRewardDto> dtos = rewards.map(this::toDto);
        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Admin: Get rewards requiring approval",
            description = "Get all rewards that require manual approval (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Rewards retrieved successfully")
    @GetMapping("/admin/pending-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReferralRewardDto>> getRewardsRequiringApproval() {
        log.info("Admin retrieving rewards requiring approval");

        List<ReferralReward> rewards = rewardService.getRewardsRequiringApproval();
        List<ReferralRewardDto> dtos = rewards.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Admin: Approve reward",
            description = "Manually approve a reward (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Reward approved successfully")
    @ApiResponse(responseCode = "404", description = "Reward not found")
    @PostMapping("/admin/{rewardId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralRewardDto> approveReward(
            @Parameter(description = "Reward ID") @PathVariable String rewardId) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} approving reward: {}", adminUserId, rewardId);

        rewardService.approveReward(rewardId, UUID.fromString(adminUserId));
        ReferralReward updated = rewardService.getRewardByRewardId(rewardId);

        return ResponseEntity.ok(toDto(updated));
    }

    @Operation(
            summary = "Admin: Reject reward",
            description = "Manually reject a reward (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Reward rejected successfully")
    @ApiResponse(responseCode = "404", description = "Reward not found")
    @PostMapping("/admin/{rewardId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralRewardDto> rejectReward(
            @Parameter(description = "Reward ID") @PathVariable String rewardId,
            @Parameter(description = "Rejection reason") @RequestParam @NotBlank String reason) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} rejecting reward: {} - reason: {}", adminUserId, rewardId, reason);

        rewardService.rejectReward(rewardId, UUID.fromString(adminUserId), reason);
        ReferralReward updated = rewardService.getRewardByRewardId(rewardId);

        return ResponseEntity.ok(toDto(updated));
    }

    @Operation(
            summary = "Admin: Issue reward",
            description = "Manually issue an approved reward (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Reward issued successfully")
    @ApiResponse(responseCode = "404", description = "Reward not found")
    @PostMapping("/admin/{rewardId}/issue")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralRewardDto> issueReward(
            @Parameter(description = "Reward ID") @PathVariable String rewardId) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} issuing reward: {}", adminUserId, rewardId);

        rewardService.issueReward(rewardId);
        ReferralReward updated = rewardService.getRewardByRewardId(rewardId);

        return ResponseEntity.ok(toDto(updated));
    }

    @Operation(
            summary = "Admin: Get expired rewards",
            description = "Get all expired, unredeemed rewards (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Expired rewards retrieved successfully")
    @GetMapping("/admin/expired")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReferralRewardDto>> getExpiredRewards() {
        log.info("Admin retrieving expired rewards");

        List<ReferralReward> rewards = rewardService.getExpiredRewards();
        List<ReferralRewardDto> dtos = rewards.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Admin: Process expired rewards",
            description = "Mark all expired rewards as expired (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Expired rewards processed")
    @PostMapping("/admin/process-expired")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProcessExpiredResponse> processExpiredRewards() {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} processing expired rewards", adminUserId);

        int count = rewardService.processExpiredRewards();

        ProcessExpiredResponse response = ProcessExpiredResponse.builder()
                .processedCount(count)
                .processedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Convert entity to DTO
     */
    private ReferralRewardDto toDto(ReferralReward reward) {
        boolean isExpired = reward.getExpiresAt() != null &&
                reward.getExpiresAt().isBefore(LocalDateTime.now());

        return ReferralRewardDto.builder()
                .rewardId(reward.getRewardId())
                .referralId(reward.getReferralId())
                .programId(reward.getProgramId())
                .programName(reward.getProgramName())
                .recipientUserId(reward.getRecipientUserId())
                .recipientType(reward.getRecipientType())
                .rewardType(reward.getRewardType())
                .pointsAmount(reward.getPointsAmount())
                .cashbackAmount(reward.getCashbackAmount())
                .status(reward.getStatus())
                .requiresApproval(reward.getRequiresApproval())
                .approvedAt(reward.getApprovedAt())
                .approvedBy(reward.getApprovedBy())
                .issuedAt(reward.getIssuedAt())
                .redeemedAt(reward.getRedeemedAt())
                .expiresAt(reward.getExpiresAt())
                .isExpired(isExpired)
                .redemptionAccountId(reward.getRedemptionAccountId())
                .redemptionTransactionId(reward.getRedemptionTransactionId())
                .rejectionReason(reward.getRejectionReason())
                .rejectedAt(reward.getRejectedAt())
                .rejectedBy(reward.getRejectedBy())
                .createdAt(reward.getCreatedAt())
                .updatedAt(reward.getUpdatedAt())
                .build();
    }

    // ========== INNER DTOs ==========

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class RewardSummaryDto {
        private BigDecimal totalCashbackEarned;
        private Long totalPointsEarned;
        private Integer pendingRewardsCount;
        private BigDecimal pendingCashback;
        private Long pendingPoints;
        private Integer expiringSoonCount;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class ProcessExpiredResponse {
        private Integer processedCount;
        private LocalDateTime processedAt;
    }
}
