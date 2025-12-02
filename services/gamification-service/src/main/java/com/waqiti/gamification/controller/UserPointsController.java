package com.waqiti.gamification.controller;

import com.waqiti.gamification.domain.UserPoints;
import com.waqiti.gamification.dto.request.AddPointsRequest;
import com.waqiti.gamification.dto.request.RedeemPointsRequest;
import com.waqiti.gamification.dto.response.UserPointsResponse;
import com.waqiti.gamification.dto.response.LeaderboardResponse;
import com.waqiti.gamification.service.UserPointsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
@Tag(name = "User Points", description = "User points management and tracking")
public class UserPointsController {
    
    private final UserPointsService userPointsService;
    
    @GetMapping("/{userId}")
    @Operation(summary = "Get user points", description = "Retrieve points information for a specific user")
    @PreAuthorize("hasRole('USER') and (@securityService.isCurrentUser(#userId) or hasRole('ADMIN'))")
    public ResponseEntity<UserPointsResponse> getUserPoints(
            @Parameter(description = "User ID") @PathVariable String userId) {
        
        Optional<UserPoints> userPoints = userPointsService.findByUserId(userId);
        
        if (userPoints.isEmpty()) {
            // Create new user points if they don't exist
            UserPoints newUserPoints = userPointsService.createUserPoints(userId);
            return ResponseEntity.ok(UserPointsResponse.from(newUserPoints));
        }
        
        return ResponseEntity.ok(UserPointsResponse.from(userPoints.get()));
    }
    
    @PostMapping("/{userId}/add")
    @Operation(summary = "Add points", description = "Add points to a user's account")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<UserPointsResponse> addPoints(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Valid @RequestBody AddPointsRequest request) {
        
        UserPoints userPoints = userPointsService.addPoints(
            userId, 
            request.getPoints(), 
            request.getEventType(), 
            request.getDescription(), 
            request.getReferenceId()
        );
        
        return ResponseEntity.ok(UserPointsResponse.from(userPoints));
    }
    
    @PostMapping("/{userId}/redeem")
    @Operation(summary = "Redeem points", description = "Redeem points from a user's account")
    @PreAuthorize("hasRole('USER') and @securityService.isCurrentUser(#userId)")
    public ResponseEntity<UserPointsResponse> redeemPoints(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Valid @RequestBody RedeemPointsRequest request) {
        
        UserPoints userPoints = userPointsService.redeemPoints(
            userId, 
            request.getPoints(), 
            request.getDescription(), 
            request.getReferenceId()
        );
        
        return ResponseEntity.ok(UserPointsResponse.from(userPoints));
    }
    
    @GetMapping("/{userId}/rank")
    @Operation(summary = "Get user rank", description = "Get the user's current ranking position")
    @PreAuthorize("hasRole('USER') and (@securityService.isCurrentUser(#userId) or hasRole('ADMIN'))")
    public ResponseEntity<Long> getUserRank(
            @Parameter(description = "User ID") @PathVariable String userId) {
        
        Long rank = userPointsService.getUserRank(userId);
        return ResponseEntity.ok(rank != null ? rank : 0L);
    }
    
    @GetMapping("/leaderboard")
    @Operation(summary = "Get leaderboard", description = "Retrieve the global points leaderboard")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<LeaderboardResponse>> getLeaderboard(
            @PageableDefault(size = 20) Pageable pageable) {
        
        Page<UserPoints> leaderboard = userPointsService.getLeaderboard(pageable);
        Page<LeaderboardResponse> response = leaderboard.map(LeaderboardResponse::from);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/leaderboard/active")
    @Operation(summary = "Get active users leaderboard", description = "Retrieve leaderboard for recently active users")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<LeaderboardResponse>> getActiveUsersLeaderboard(
            @RequestParam(defaultValue = "30") int daysSince,
            @PageableDefault(size = 20) Pageable pageable) {
        
        LocalDateTime since = LocalDateTime.now().minusDays(daysSince);
        Page<UserPoints> leaderboard = userPointsService.getActiveUsersLeaderboard(since, pageable);
        Page<LeaderboardResponse> response = leaderboard.map(LeaderboardResponse::from);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/{userId}/multiplier")
    @Operation(summary = "Activate points multiplier", description = "Activate a temporary points multiplier for a user")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<UserPointsResponse> activateMultiplier(
            @Parameter(description = "User ID") @PathVariable String userId,
            @RequestParam Double multiplier,
            @RequestParam int durationMinutes) {
        
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(durationMinutes);
        UserPoints userPoints = userPointsService.activateMultiplier(
            userId, 
            java.math.BigDecimal.valueOf(multiplier), 
            expiresAt
        );
        
        return ResponseEntity.ok(UserPointsResponse.from(userPoints));
    }
    
    @PostMapping("/{userId}/streak")
    @Operation(summary = "Update streak", description = "Update the user's activity streak")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<Void> updateStreak(
            @Parameter(description = "User ID") @PathVariable String userId,
            @RequestParam Integer streakDays) {
        
        userPointsService.updateStreak(userId, streakDays);
        return ResponseEntity.ok().build();
    }
}