package com.waqiti.rewards.controller;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.rewards.domain.*;
import com.waqiti.rewards.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API controller for administrative referral operations
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/referrals/admin")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Admin Referral Operations", description = "Administrative referral management")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReferralController {

    private final ReferralProgramService programService;
    private final ReferralLinkService linkService;
    private final ReferralRewardService rewardService;
    private final ReferralFraudDetectionService fraudService;
    private final ReferralMilestoneService milestoneService;
    private final ReferralCampaignService campaignService;
    private final ReferralTierService tierService;
    private final SecurityContext securityContext;

    @Operation(
            summary = "Get dashboard metrics",
            description = "Get comprehensive dashboard metrics for referral programs"
    )
    @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> getDashboard() {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} retrieving referral dashboard", adminUserId);

        // Get all programs
        List<ReferralProgram> allPrograms = programService.getAllPrograms(
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<ReferralProgram> activePrograms = programService.getActivePrograms();

        // Calculate totals
        long totalReferrals = allPrograms.stream()
                .mapToLong(p -> p.getTotalReferrals() != null ? p.getTotalReferrals() : 0)
                .sum();

        long successfulReferrals = allPrograms.stream()
                .mapToLong(p -> p.getSuccessfulReferrals() != null ? p.getSuccessfulReferrals() : 0)
                .sum();

        BigDecimal totalRewardsIssued = allPrograms.stream()
                .map(p -> p.getTotalRewardsIssued() != null ? p.getTotalRewardsIssued() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get pending approvals
        List<ReferralReward> pendingApprovals = rewardService.getRewardsRequiringApproval();

        // Get recent fraud checks
        List<ReferralFraudCheck> recentFraud = fraudService.getRecentFailedChecks(24);

        // Get programs exceeding budget
        List<ReferralProgram> overBudget = programService.getProgramsExceedingBudget(
                new BigDecimal("0.90"));

        // Calculate average conversion rate
        double avgConversionRate = allPrograms.stream()
                .filter(p -> p.getConversionRate() != null)
                .mapToDouble(p -> p.getConversionRate().doubleValue())
                .average()
                .orElse(0.0);

        AdminDashboardDto dashboard = AdminDashboardDto.builder()
                .totalPrograms(allPrograms.size())
                .activePrograms(activePrograms.size())
                .totalReferrals(totalReferrals)
                .successfulReferrals(successfulReferrals)
                .totalRewardsIssued(totalRewardsIssued)
                .avgConversionRate(avgConversionRate)
                .pendingApprovalsCount(pendingApprovals.size())
                .recentFraudAlertsCount(recentFraud.size())
                .programsNearBudgetCount(overBudget.size())
                .generatedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(dashboard);
    }

    @Operation(
            summary = "Get fraud alerts",
            description = "Get recent fraud detection alerts"
    )
    @ApiResponse(responseCode = "200", description = "Fraud alerts retrieved successfully")
    @GetMapping("/fraud-alerts")
    public ResponseEntity<List<FraudAlertDto>> getFraudAlerts(
            @Parameter(description = "Hours to look back") @RequestParam(defaultValue = "24") int hours) {
        log.info("Admin retrieving fraud alerts for last {} hours", hours);

        List<ReferralFraudCheck> fraudChecks = fraudService.getRecentFailedChecks(hours);

        List<FraudAlertDto> alerts = fraudChecks.stream()
                .map(check -> FraudAlertDto.builder()
                        .checkId(check.getCheckId())
                        .referralId(check.getReferralId())
                        .riskScore(check.getRiskScore())
                        .checkStatus(check.getCheckStatus())
                        .fraudIndicators(check.getFraudIndicators())
                        .detectionRules(check.getDetectionRulesTriggered())
                        .createdAt(check.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(alerts);
    }

    @Operation(
            summary = "Get fraud check details",
            description = "Get detailed information about a fraud check"
    )
    @ApiResponse(responseCode = "200", description = "Fraud check retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Fraud check not found")
    @GetMapping("/fraud-checks/{checkId}")
    public ResponseEntity<FraudAlertDto> getFraudCheck(
            @Parameter(description = "Check ID") @PathVariable String checkId) {
        log.info("Admin retrieving fraud check: {}", checkId);

        ReferralFraudCheck check = fraudService.getFraudCheck(checkId);

        FraudAlertDto alert = FraudAlertDto.builder()
                .checkId(check.getCheckId())
                .referralId(check.getReferralId())
                .riskScore(check.getRiskScore())
                .checkStatus(check.getCheckStatus())
                .fraudIndicators(check.getFraudIndicators())
                .detectionRules(check.getDetectionRulesTriggered())
                .reviewNotes(check.getReviewNotes())
                .reviewedAt(check.getReviewedAt())
                .reviewedBy(check.getReviewedBy())
                .createdAt(check.getCreatedAt())
                .build();

        return ResponseEntity.ok(alert);
    }

    @Operation(
            summary = "Review fraud check",
            description = "Mark a fraud check as reviewed with notes"
    )
    @ApiResponse(responseCode = "200", description = "Fraud check reviewed successfully")
    @PostMapping("/fraud-checks/{checkId}/review")
    public ResponseEntity<Void> reviewFraudCheck(
            @Parameter(description = "Check ID") @PathVariable String checkId,
            @Parameter(description = "Review notes") @RequestParam String notes) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} reviewing fraud check: {}", adminUserId, checkId);

        fraudService.markAsReviewed(checkId, UUID.fromString(adminUserId), notes);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get milestone achievements",
            description = "Get achievements that need reward issuance"
    )
    @ApiResponse(responseCode = "200", description = "Achievements retrieved successfully")
    @GetMapping("/milestone-achievements/pending")
    public ResponseEntity<List<MilestoneAchievementDto>> getPendingMilestoneRewards() {
        log.info("Admin retrieving pending milestone achievements");

        List<ReferralMilestoneAchievement> achievements =
                milestoneService.getPendingRewardIssuances();

        List<MilestoneAchievementDto> dtos = achievements.stream()
                .map(a -> MilestoneAchievementDto.builder()
                        .achievementId(a.getAchievementId())
                        .userId(a.getUserId())
                        .milestoneName(a.getMilestone().getMilestoneName())
                        .achievedAt(a.getAchievedAt())
                        .rewardType(a.getMilestone().getRewardType())
                        .rewardAmount(a.getMilestone().getRewardAmount())
                        .rewardPoints(a.getMilestone().getRewardPoints())
                        .rewardIssued(a.getRewardIssued())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Issue milestone reward",
            description = "Mark a milestone achievement reward as issued"
    )
    @ApiResponse(responseCode = "200", description = "Reward marked as issued")
    @PostMapping("/milestone-achievements/{achievementId}/issue-reward")
    public ResponseEntity<Void> issueMilestoneReward(
            @Parameter(description = "Achievement ID") @PathVariable String achievementId,
            @Parameter(description = "Reward ID") @RequestParam String rewardId) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} issuing milestone reward: achievement={}, reward={}",
                adminUserId, achievementId, rewardId);

        milestoneService.markRewardIssued(achievementId, rewardId);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get campaigns",
            description = "Get all referral campaigns"
    )
    @ApiResponse(responseCode = "200", description = "Campaigns retrieved successfully")
    @GetMapping("/campaigns")
    public ResponseEntity<List<CampaignDto>> getCampaigns() {
        log.info("Admin retrieving all campaigns");

        List<ReferralCampaign> campaigns = campaignService.getActiveCampaigns();

        List<CampaignDto> dtos = campaigns.stream()
                .map(c -> CampaignDto.builder()
                        .campaignId(c.getCampaignId())
                        .campaignName(c.getCampaignName())
                        .startDate(c.getStartDate())
                        .endDate(c.getEndDate())
                        .status(c.getStatus())
                        .totalReferrals(c.getTotalReferrals())
                        .targetReferrals(c.getTargetReferrals())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Activate campaign",
            description = "Activate a referral campaign"
    )
    @ApiResponse(responseCode = "200", description = "Campaign activated successfully")
    @PostMapping("/campaigns/{campaignId}/activate")
    public ResponseEntity<Void> activateCampaign(
            @Parameter(description = "Campaign ID") @PathVariable String campaignId) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} activating campaign: {}", adminUserId, campaignId);

        campaignService.activateCampaign(campaignId);

        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Get tiers",
            description = "Get all referral tiers for a program"
    )
    @ApiResponse(responseCode = "200", description = "Tiers retrieved successfully")
    @GetMapping("/programs/{programId}/tiers")
    public ResponseEntity<List<TierDto>> getProgramTiers(
            @Parameter(description = "Program ID") @PathVariable String programId) {
        log.info("Admin retrieving tiers for program: {}", programId);

        List<ReferralTier> tiers = tierService.getTiersForProgram(programId);

        List<TierDto> dtos = tiers.stream()
                .sorted(Comparator.comparing(ReferralTier::getMinReferrals))
                .map(t -> TierDto.builder()
                        .tierId(t.getTierId())
                        .tierName(t.getTierName())
                        .minReferrals(t.getMinReferrals())
                        .multiplier(t.getMultiplier())
                        .bonusRewardAmount(t.getBonusRewardAmount())
                        .bonusRewardPoints(t.getBonusRewardPoints())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get system health",
            description = "Get health status of referral system components"
    )
    @ApiResponse(responseCode = "200", description = "Health status retrieved successfully")
    @GetMapping("/health")
    public ResponseEntity<SystemHealthDto> getSystemHealth() {
        log.info("Admin retrieving system health");

        // Check various system components
        boolean programsHealthy = checkProgramsHealth();
        boolean rewardsHealthy = checkRewardsHealth();
        boolean fraudHealthy = checkFraudHealth();

        String overallStatus = (programsHealthy && rewardsHealthy && fraudHealthy)
                ? "HEALTHY" : "DEGRADED";

        SystemHealthDto health = SystemHealthDto.builder()
                .overallStatus(overallStatus)
                .programsHealthy(programsHealthy)
                .rewardsHealthy(rewardsHealthy)
                .fraudDetectionHealthy(fraudHealthy)
                .checkedAt(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(health);
    }

    // ========== HELPER METHODS ==========

    private boolean checkProgramsHealth() {
        try {
            List<ReferralProgram> programs = programService.getActivePrograms();
            // Check if any programs are significantly over budget
            long overBudget = programs.stream()
                    .filter(p -> {
                        if (p.getMaxProgramBudget() == null) return false;
                        BigDecimal used = p.getTotalRewardsIssued() != null
                                ? p.getTotalRewardsIssued() : BigDecimal.ZERO;
                        return used.compareTo(p.getMaxProgramBudget()) > 0;
                    })
                    .count();
            return overBudget == 0;
        } catch (Exception e) {
            log.error("Error checking programs health", e);
            return false;
        }
    }

    private boolean checkRewardsHealth() {
        try {
            List<ReferralReward> pending = rewardService.getRewardsRequiringApproval();
            // Alert if too many pending approvals (> 100)
            return pending.size() < 100;
        } catch (Exception e) {
            log.error("Error checking rewards health", e);
            return false;
        }
    }

    private boolean checkFraudHealth() {
        try {
            List<ReferralFraudCheck> recent = fraudService.getRecentFailedChecks(1);
            // Alert if too many fraud failures in last hour (> 50)
            return recent.size() < 50;
        } catch (Exception e) {
            log.error("Error checking fraud health", e);
            return false;
        }
    }

    // ========== DTOs ==========

    @Data
    @Builder
    @AllArgsConstructor
    private static class AdminDashboardDto {
        private Integer totalPrograms;
        private Integer activePrograms;
        private Long totalReferrals;
        private Long successfulReferrals;
        private BigDecimal totalRewardsIssued;
        private Double avgConversionRate;
        private Integer pendingApprovalsCount;
        private Integer recentFraudAlertsCount;
        private Integer programsNearBudgetCount;
        private LocalDateTime generatedAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class FraudAlertDto {
        private String checkId;
        private String referralId;
        private BigDecimal riskScore;
        private String checkStatus;
        private Map<String, Object> fraudIndicators;
        private List<String> detectionRules;
        private String reviewNotes;
        private LocalDateTime reviewedAt;
        private UUID reviewedBy;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class MilestoneAchievementDto {
        private String achievementId;
        private UUID userId;
        private String milestoneName;
        private LocalDateTime achievedAt;
        private com.waqiti.rewards.enums.RewardType rewardType;
        private BigDecimal rewardAmount;
        private Long rewardPoints;
        private Boolean rewardIssued;
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class CampaignDto {
        private String campaignId;
        private String campaignName;
        private java.time.LocalDate startDate;
        private java.time.LocalDate endDate;
        private String status;
        private Integer totalReferrals;
        private Integer targetReferrals;
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class TierDto {
        private String tierId;
        private String tierName;
        private Integer minReferrals;
        private BigDecimal multiplier;
        private BigDecimal bonusRewardAmount;
        private Long bonusRewardPoints;
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class SystemHealthDto {
        private String overallStatus;
        private Boolean programsHealthy;
        private Boolean rewardsHealthy;
        private Boolean fraudDetectionHealthy;
        private LocalDateTime checkedAt;
    }
}
