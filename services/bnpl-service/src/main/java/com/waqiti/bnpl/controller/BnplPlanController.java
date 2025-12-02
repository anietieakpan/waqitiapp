package com.waqiti.bnpl.controller;

import com.waqiti.bnpl.dto.request.*;
import com.waqiti.bnpl.dto.response.*;
import com.waqiti.bnpl.service.BnplPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for BNPL plan operations
 */
@RestController
@RequestMapping("/api/v1/bnpl/plans")
@Tag(name = "BNPL Plans", description = "BNPL plan management endpoints")
@Slf4j
@RequiredArgsConstructor
public class BnplPlanController {

    private final BnplPlanService bnplPlanService;

    @PostMapping
    @Operation(summary = "Create a new BNPL plan")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BnplPlanDto> createBnplPlan(
            @Valid @RequestBody CreateBnplPlanRequest request) {
        log.info("Creating BNPL plan for user: {}", request.getUserId());
        BnplPlanDto plan = bnplPlanService.createBnplPlan(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(plan);
    }

    @PutMapping("/{planId}/approve")
    @Operation(summary = "Approve a BNPL plan")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BnplPlanDto> approvePlan(
            @PathVariable Long planId,
            @Valid @RequestBody ApprovePlanRequest request) {
        log.info("Approving BNPL plan: {}", planId);
        BnplPlanDto plan = bnplPlanService.approvePlan(planId, request);
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{planId}/activate")
    @Operation(summary = "Activate a BNPL plan")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BnplPlanDto> activatePlan(@PathVariable Long planId) {
        log.info("Activating BNPL plan: {}", planId);
        BnplPlanDto plan = bnplPlanService.activatePlan(planId);
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{planId}/cancel")
    @Operation(summary = "Cancel a BNPL plan")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BnplPlanDto> cancelPlan(
            @PathVariable Long planId,
            @RequestParam String reason) {
        log.info("Cancelling BNPL plan: {} with reason: {}", planId, reason);
        BnplPlanDto plan = bnplPlanService.cancelPlan(planId, reason);
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/{planId}")
    @Operation(summary = "Get BNPL plan by ID")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BnplPlanDto> getBnplPlan(@PathVariable Long planId) {
        log.info("Getting BNPL plan: {}", planId);
        BnplPlanDto plan = bnplPlanService.getBnplPlan(planId);
        return ResponseEntity.ok(plan);
    }

    @GetMapping("/{planId}/details")
    @Operation(summary = "Get BNPL plan details including installments and transactions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BnplPlanDetailsDto> getBnplPlanDetails(@PathVariable Long planId) {
        log.info("Getting BNPL plan details: {}", planId);
        BnplPlanDetailsDto details = bnplPlanService.getBnplPlanDetails(planId);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get BNPL plans for a user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Page<BnplPlanDto>> getUserBnplPlans(
            @PathVariable String userId,
            Pageable pageable) {
        log.info("Getting BNPL plans for user: {}", userId);
        Page<BnplPlanDto> plans = bnplPlanService.getUserBnplPlans(userId, pageable);
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/user/{userId}/active")
    @Operation(summary = "Get active BNPL plans for a user")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<BnplPlanDto>> getActiveUserPlans(@PathVariable String userId) {
        log.info("Getting active BNPL plans for user: {}", userId);
        List<BnplPlanDto> plans = bnplPlanService.getActiveUserPlans(userId);
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/overdue")
    @Operation(summary = "Get overdue BNPL plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BnplPlanDto>> getOverduePlans() {
        log.info("Getting overdue BNPL plans");
        List<BnplPlanDto> plans = bnplPlanService.getOverduePlans();
        return ResponseEntity.ok(plans);
    }
}