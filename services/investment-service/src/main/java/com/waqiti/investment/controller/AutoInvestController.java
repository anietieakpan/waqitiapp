package com.waqiti.investment.controller;

import com.waqiti.investment.dto.request.CreateAutoInvestRequest;
import com.waqiti.investment.dto.response.AutoInvestDto;
import com.waqiti.investment.service.AutoInvestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST controller for auto-invest operations
 */
@RestController
@RequestMapping("/api/v1/auto-invest")
@Tag(name = "Auto-Invest", description = "Automated investment plan management API")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {
    "${cors.allowed-origins:http://localhost:3000,https://app.example.com,https://admin.example.com}"
})
public class AutoInvestController {

    private final AutoInvestService autoInvestService;

    @PostMapping
    @Operation(summary = "Create auto-invest plan", description = "Create a new automated investment plan")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#request.accountId)")
    public ResponseEntity<AutoInvestDto> createAutoInvestPlan(
            @Parameter(description = "Auto-invest plan details", required = true)
            @RequestBody @Valid CreateAutoInvestRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} creating auto-invest plan: {}", userDetails.getUsername(), request.getName());
        AutoInvestDto plan = autoInvestService.createAutoInvestPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Update auto-invest plan", description = "Update an existing auto-invest plan")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<AutoInvestDto> updateAutoInvestPlan(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId,
            @Parameter(description = "Updated plan details", required = true)
            @RequestBody @Valid CreateAutoInvestRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} updating auto-invest plan: {}", userDetails.getUsername(), planId);
        AutoInvestDto plan = autoInvestService.updateAutoInvestPlan(planId, request);
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{planId}/pause")
    @Operation(summary = "Pause auto-invest plan", description = "Pause an active auto-invest plan")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<AutoInvestDto> pauseAutoInvestPlan(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} pausing auto-invest plan: {}", userDetails.getUsername(), planId);
        AutoInvestDto plan = autoInvestService.pauseAutoInvestPlan(planId);
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{planId}/resume")
    @Operation(summary = "Resume auto-invest plan", description = "Resume a paused auto-invest plan")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<AutoInvestDto> resumeAutoInvestPlan(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} resuming auto-invest plan: {}", userDetails.getUsername(), planId);
        AutoInvestDto plan = autoInvestService.resumeAutoInvestPlan(planId);
        return ResponseEntity.ok(plan);
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "Delete auto-invest plan", description = "Delete an auto-invest plan")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<Void> deleteAutoInvestPlan(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} deleting auto-invest plan: {}", userDetails.getUsername(), planId);
        autoInvestService.deleteAutoInvestPlan(planId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get auto-invest plan", description = "Get auto-invest plan details")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<AutoInvestDto> getAutoInvestPlan(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId) {
        
        log.info("Getting auto-invest plan: {}", planId);
        AutoInvestDto plan = autoInvestService.getAutoInvestPlan(planId);
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get account auto-invest plans", 
              description = "Get all auto-invest plans for an account")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<List<AutoInvestDto>> getAccountAutoInvestPlans(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId) {
        
        log.info("Getting auto-invest plans for account: {}", accountId);
        List<AutoInvestDto> plans = autoInvestService.getAccountAutoInvestPlans(accountId);
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/{planId}/performance")
    @Operation(summary = "Get auto-invest performance", 
              description = "Calculate performance metrics for auto-invest plan")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<AutoInvestService.AutoInvestPerformanceDto> getAutoInvestPerformance(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId) {
        
        log.info("Calculating performance for auto-invest plan: {}", planId);
        AutoInvestService.AutoInvestPerformanceDto performance = 
                autoInvestService.calculateAutoInvestPerformance(planId);
        return ResponseEntity.ok(performance);
    }

    @PostMapping("/{planId}/rebalance")
    @Operation(summary = "Get rebalance recommendations", 
              description = "Generate rebalance recommendations based on auto-invest allocations")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<List<AutoInvestService.RebalanceRecommendationDto>> rebalancePortfolio(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} requesting rebalance for auto-invest plan: {}", 
                userDetails.getUsername(), planId);
        List<AutoInvestService.RebalanceRecommendationDto> recommendations = 
                autoInvestService.rebalancePortfolio(planId);
        return ResponseEntity.ok(recommendations);
    }

    @PostMapping("/{planId}/execute")
    @Operation(summary = "Execute auto-invest plan", 
              description = "Manually trigger execution of auto-invest plan")
    @PreAuthorize("@investmentSecurityService.canAccessAutoInvestPlan(#planId)")
    public ResponseEntity<Void> executeAutoInvestPlan(
            @Parameter(description = "Auto-invest plan ID", required = true)
            @PathVariable Long planId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("User {} manually executing auto-invest plan: {}", 
                userDetails.getUsername(), planId);
        
        try {
            autoInvestService.executeAutoInvestPlanManually(planId);
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            log.error("Error executing auto-invest plan {} manually", planId, e);
            return ResponseEntity.status(500).build();
        }
    }
}