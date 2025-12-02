package com.waqiti.rewards.controller;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.rewards.domain.ReferralLeaderboard;
import com.waqiti.rewards.dto.referral.ReferralLeaderboardDto;
import com.waqiti.rewards.service.ReferralLeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API controller for referral leaderboards
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/referrals/leaderboard")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Referral Leaderboard", description = "Referral program leaderboards and rankings")
public class ReferralLeaderboardController {

    private final ReferralLeaderboardService leaderboardService;
    private final SecurityContext securityContext;

    @Operation(
            summary = "Get current leaderboard",
            description = "Get the current leaderboard for a specific period type"
    )
    @ApiResponse(responseCode = "200", description = "Leaderboard retrieved successfully")
    @GetMapping("/current")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ReferralLeaderboardDto>> getCurrentLeaderboard(
            @Parameter(description = "Program ID") @RequestParam String programId,
            @Parameter(description = "Period type: DAILY, WEEKLY, MONTHLY, ALL_TIME")
            @RequestParam(defaultValue = "WEEKLY") String periodType,
            @PageableDefault(size = 50, sort = "rank") Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving leaderboard: program={}, period={}",
                userId, programId, periodType);

        Page<ReferralLeaderboard> leaderboard =
                leaderboardService.getLatestLeaderboard(programId, periodType, pageable);

        UUID currentUserUuid = UUID.fromString(userId);
        Page<ReferralLeaderboardDto> dtos = leaderboard.map(entry ->
                toDto(entry, entry.getUserId().equals(currentUserUuid)));

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get leaderboard for period",
            description = "Get the leaderboard for a specific time period"
    )
    @ApiResponse(responseCode = "200", description = "Leaderboard retrieved successfully")
    @GetMapping("/period")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ReferralLeaderboardDto>> getLeaderboardForPeriod(
            @Parameter(description = "Program ID") @RequestParam String programId,
            @Parameter(description = "Period type") @RequestParam String periodType,
            @Parameter(description = "Period start date") @RequestParam LocalDate periodStart,
            @Parameter(description = "Period end date") @RequestParam LocalDate periodEnd,
            @PageableDefault(size = 50, sort = "rank") Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving leaderboard for period: {} to {}",
                userId, periodStart, periodEnd);

        Page<ReferralLeaderboard> leaderboard = leaderboardService.getLeaderboard(
                programId, periodType, periodStart, periodEnd, pageable);

        UUID currentUserUuid = UUID.fromString(userId);
        Page<ReferralLeaderboardDto> dtos = leaderboard.map(entry ->
                toDto(entry, entry.getUserId().equals(currentUserUuid)));

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get my leaderboard position",
            description = "Get the current user's position in the leaderboard"
    )
    @ApiResponse(responseCode = "200", description = "Position retrieved successfully")
    @GetMapping("/my-position")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ReferralLeaderboardDto> getMyPosition(
            @Parameter(description = "Program ID") @RequestParam String programId,
            @Parameter(description = "Period type")
            @RequestParam(defaultValue = "WEEKLY") String periodType) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving leaderboard position: program={}, period={}",
                userId, programId, periodType);

        // Get current period dates
        PeriodDates dates = calculatePeriodDates(periodType);

        // Get or create user's entry
        ReferralLeaderboard entry = leaderboardService.getOrCreateEntry(
                UUID.fromString(userId),
                programId,
                periodType,
                dates.start,
                dates.end
        );

        return ResponseEntity.ok(toDto(entry, true));
    }

    @Operation(
            summary = "Get my leaderboard history",
            description = "Get the user's historical leaderboard positions"
    )
    @ApiResponse(responseCode = "200", description = "History retrieved successfully")
    @GetMapping("/my-history")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ReferralLeaderboardDto>> getMyHistory(
            @Parameter(description = "Program ID") @RequestParam String programId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving leaderboard history for program: {}", userId, programId);

        List<ReferralLeaderboard> history = leaderboardService.getUserHistory(
                UUID.fromString(userId),
                programId
        );

        List<ReferralLeaderboardDto> dtos = history.stream()
                .map(entry -> toDto(entry, true))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get weekly leaderboard",
            description = "Get this week's leaderboard (Monday-Sunday)"
    )
    @ApiResponse(responseCode = "200", description = "Weekly leaderboard retrieved successfully")
    @GetMapping("/weekly")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ReferralLeaderboardDto>> getWeeklyLeaderboard(
            @Parameter(description = "Program ID") @RequestParam String programId,
            @PageableDefault(size = 50, sort = "rank") Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving weekly leaderboard for program: {}", userId, programId);

        Page<ReferralLeaderboard> leaderboard =
                leaderboardService.getLatestLeaderboard(programId, "WEEKLY", pageable);

        UUID currentUserUuid = UUID.fromString(userId);
        Page<ReferralLeaderboardDto> dtos = leaderboard.map(entry ->
                toDto(entry, entry.getUserId().equals(currentUserUuid)));

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get monthly leaderboard",
            description = "Get this month's leaderboard"
    )
    @ApiResponse(responseCode = "200", description = "Monthly leaderboard retrieved successfully")
    @GetMapping("/monthly")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ReferralLeaderboardDto>> getMonthlyLeaderboard(
            @Parameter(description = "Program ID") @RequestParam String programId,
            @PageableDefault(size = 50, sort = "rank") Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving monthly leaderboard for program: {}", userId, programId);

        Page<ReferralLeaderboard> leaderboard =
                leaderboardService.getLatestLeaderboard(programId, "MONTHLY", pageable);

        UUID currentUserUuid = UUID.fromString(userId);
        Page<ReferralLeaderboardDto> dtos = leaderboard.map(entry ->
                toDto(entry, entry.getUserId().equals(currentUserUuid)));

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get all-time leaderboard",
            description = "Get the all-time leaderboard"
    )
    @ApiResponse(responseCode = "200", description = "All-time leaderboard retrieved successfully")
    @GetMapping("/all-time")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<ReferralLeaderboardDto>> getAllTimeLeaderboard(
            @Parameter(description = "Program ID") @RequestParam String programId,
            @PageableDefault(size = 100, sort = "rank") Pageable pageable) {
        String userId = securityContext.getCurrentUserId();
        log.debug("User {} retrieving all-time leaderboard for program: {}", userId, programId);

        Page<ReferralLeaderboard> leaderboard =
                leaderboardService.getLatestLeaderboard(programId, "ALL_TIME", pageable);

        UUID currentUserUuid = UUID.fromString(userId);
        Page<ReferralLeaderboardDto> dtos = leaderboard.map(entry ->
                toDto(entry, entry.getUserId().equals(currentUserUuid)));

        return ResponseEntity.ok(dtos);
    }

    // ========== ADMIN ENDPOINTS ==========

    @Operation(
            summary = "Admin: Update rankings",
            description = "Manually trigger ranking calculation for a period (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Rankings updated successfully")
    @PostMapping("/admin/update-rankings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateRankings(
            @Parameter(description = "Program ID") @RequestParam String programId,
            @Parameter(description = "Period type") @RequestParam String periodType) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} updating rankings: program={}, period={}",
                adminUserId, programId, periodType);

        PeriodDates dates = calculatePeriodDates(periodType);
        leaderboardService.updateRankings(programId, periodType, dates.start, dates.end);

        return ResponseEntity.ok().build();
    }

    /**
     * Convert entity to DTO
     */
    private ReferralLeaderboardDto toDto(ReferralLeaderboard entry, boolean isCurrentUser) {
        // Mask username for privacy (show first 3 chars + ***)
        String maskedName = entry.getUserId().toString().substring(0, 8) + "***";

        // Determine badge based on rank
        String badge = null;
        if (entry.getRank() != null) {
            if (entry.getRank() == 1) {
                badge = "GOLD";
            } else if (entry.getRank() == 2) {
                badge = "SILVER";
            } else if (entry.getRank() == 3) {
                badge = "BRONZE";
            } else if (entry.getRank() <= 10) {
                badge = "TOP_10";
            }
        }

        // Calculate conversion rate
        Double conversionRate = null;
        if (entry.getTotalReferrals() != null && entry.getTotalReferrals() > 0) {
            conversionRate = (entry.getSuccessfulReferrals() * 100.0) / entry.getTotalReferrals();
        }

        return ReferralLeaderboardDto.builder()
                .rank(entry.getRank())
                .previousRank(entry.getPreviousRank())
                .userId(entry.getUserId())
                .userName(isCurrentUser ? "You" : maskedName)
                .programId(entry.getProgramId())
                .periodType(entry.getPeriodType())
                .periodStart(entry.getPeriodStart())
                .periodEnd(entry.getPeriodEnd())
                .totalReferrals(entry.getTotalReferrals())
                .successfulReferrals(entry.getSuccessfulReferrals())
                .totalPointsEarned(entry.getTotalPointsEarned())
                .totalCashbackEarned(entry.getTotalCashbackEarned())
                .conversionRate(conversionRate)
                .isCurrentUser(isCurrentUser)
                .badge(badge)
                .build();
    }

    /**
     * Calculate period start/end dates based on period type
     */
    private PeriodDates calculatePeriodDates(String periodType) {
        LocalDate now = LocalDate.now();
        LocalDate start;
        LocalDate end;

        switch (periodType.toUpperCase()) {
            case "DAILY":
                start = now;
                end = now;
                break;
            case "WEEKLY":
                start = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                break;
            case "MONTHLY":
                start = now.withDayOfMonth(1);
                end = now.with(TemporalAdjusters.lastDayOfMonth());
                break;
            case "ALL_TIME":
                start = LocalDate.of(2020, 1, 1); // Platform inception date
                end = now;
                break;
            default:
                throw new IllegalArgumentException("Invalid period type: " + periodType);
        }

        return new PeriodDates(start, end);
    }

    /**
     * Helper class for period dates
     */
    private static class PeriodDates {
        final LocalDate start;
        final LocalDate end;

        PeriodDates(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }
    }
}
