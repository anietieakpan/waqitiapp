package com.waqiti.rewards.controller;

import com.waqiti.common.security.SecurityContext;
import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.dto.referral.CreateReferralProgramRequest;
import com.waqiti.rewards.dto.referral.ReferralProgramDto;
import com.waqiti.rewards.service.ReferralProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API controller for referral program management
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/referrals/programs")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Referral Programs", description = "Referral program configuration and management")
public class ReferralProgramController {

    private final ReferralProgramService programService;
    private final SecurityContext securityContext;

    @Operation(
            summary = "Get active referral programs",
            description = "Retrieve all currently active referral programs available to users"
    )
    @ApiResponse(responseCode = "200", description = "Active programs retrieved successfully")
    @GetMapping("/active")
    public ResponseEntity<List<ReferralProgramDto>> getActivePrograms() {
        log.debug("Retrieving active referral programs");

        List<ReferralProgram> programs = programService.getActivePrograms();
        List<ReferralProgramDto> dtos = programs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get program by ID",
            description = "Retrieve detailed information about a specific referral program"
    )
    @ApiResponse(responseCode = "200", description = "Program retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @GetMapping("/{programId}")
    public ResponseEntity<ReferralProgramDto> getProgramById(
            @Parameter(description = "Program ID", example = "REF-PROG-001")
            @PathVariable String programId) {
        log.debug("Retrieving program: {}", programId);

        ReferralProgram program = programService.getProgramByProgramId(programId);
        return ResponseEntity.ok(toDto(program));
    }

    @Operation(
            summary = "Create referral program",
            description = "Create a new referral program (admin only)"
    )
    @ApiResponse(responseCode = "201", description = "Program created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "409", description = "Program ID already exists")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralProgramDto> createProgram(
            @Valid @RequestBody CreateReferralProgramRequest request) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} creating referral program: {}", adminUserId, request.getProgramId());

        ReferralProgram program = ReferralProgram.builder()
                .programId(request.getProgramId())
                .programName(request.getProgramName())
                .programType(request.getProgramType())
                .description(request.getDescription())
                .isActive(false) // New programs start inactive
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .referrerRewardType(request.getReferrerRewardType())
                .referrerRewardAmount(request.getReferrerRewardAmount())
                .referrerRewardPoints(request.getReferrerRewardPoints())
                .refereeRewardType(request.getRefereeRewardType())
                .refereeRewardAmount(request.getRefereeRewardAmount())
                .refereeRewardPoints(request.getRefereeRewardPoints())
                .minTransactionAmount(request.getMinTransactionAmount())
                .rewardExpiryDays(request.getRewardExpiryDays())
                .maxReferralsPerUser(request.getMaxReferralsPerUser())
                .requiresFirstTransaction(request.getRequiresFirstTransaction())
                .maxProgramBudget(request.getMaxProgramBudget())
                .metadata(request.getMetadata())
                .build();

        ReferralProgram created = programService.createProgram(program);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @Operation(
            summary = "Update referral program",
            description = "Update an existing referral program (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Program updated successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @PutMapping("/{programId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralProgramDto> updateProgram(
            @Parameter(description = "Program ID") @PathVariable String programId,
            @Valid @RequestBody CreateReferralProgramRequest request) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} updating referral program: {}", adminUserId, programId);

        ReferralProgram existing = programService.getProgramByProgramId(programId);

        // Update fields
        existing.setProgramName(request.getProgramName());
        existing.setProgramType(request.getProgramType());
        existing.setDescription(request.getDescription());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        existing.setReferrerRewardType(request.getReferrerRewardType());
        existing.setReferrerRewardAmount(request.getReferrerRewardAmount());
        existing.setReferrerRewardPoints(request.getReferrerRewardPoints());
        existing.setRefereeRewardType(request.getRefereeRewardType());
        existing.setRefereeRewardAmount(request.getRefereeRewardAmount());
        existing.setRefereeRewardPoints(request.getRefereeRewardPoints());
        existing.setMinTransactionAmount(request.getMinTransactionAmount());
        existing.setRewardExpiryDays(request.getRewardExpiryDays());
        existing.setMaxReferralsPerUser(request.getMaxReferralsPerUser());
        existing.setRequiresFirstTransaction(request.getRequiresFirstTransaction());
        existing.setMaxProgramBudget(request.getMaxProgramBudget());
        existing.setMetadata(request.getMetadata());

        ReferralProgram updated = programService.updateProgram(existing);
        return ResponseEntity.ok(toDto(updated));
    }

    @Operation(
            summary = "Activate program",
            description = "Activate a referral program (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Program activated successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @PostMapping("/{programId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralProgramDto> activateProgram(
            @Parameter(description = "Program ID") @PathVariable String programId) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} activating referral program: {}", adminUserId, programId);

        programService.activateProgram(programId);
        ReferralProgram program = programService.getProgramByProgramId(programId);
        return ResponseEntity.ok(toDto(program));
    }

    @Operation(
            summary = "Deactivate program",
            description = "Deactivate a referral program (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Program deactivated successfully")
    @ApiResponse(responseCode = "404", description = "Program not found")
    @PostMapping("/{programId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralProgramDto> deactivateProgram(
            @Parameter(description = "Program ID") @PathVariable String programId) {
        String adminUserId = securityContext.getCurrentUserId();
        log.info("Admin {} deactivating referral program: {}", adminUserId, programId);

        programService.deactivateProgram(programId);
        ReferralProgram program = programService.getProgramByProgramId(programId);
        return ResponseEntity.ok(toDto(program));
    }

    @Operation(
            summary = "Get all programs (admin)",
            description = "Get paginated list of all referral programs (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Programs retrieved successfully")
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReferralProgramDto>> getAllPrograms(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.info("Admin retrieving all referral programs");

        Page<ReferralProgram> programs = programService.getAllPrograms(pageable);
        Page<ReferralProgramDto> dtos = programs.map(this::toDto);

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get programs exceeding budget",
            description = "Get programs that have exceeded 90% of their budget (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Programs retrieved successfully")
    @GetMapping("/admin/exceeding-budget")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReferralProgramDto>> getProgramsExceedingBudget() {
        log.info("Admin retrieving programs exceeding budget");

        BigDecimal threshold = new BigDecimal("0.90");
        List<ReferralProgram> programs = programService.getProgramsExceedingBudget(threshold);
        List<ReferralProgramDto> dtos = programs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @Operation(
            summary = "Get program performance metrics",
            description = "Get detailed performance metrics for a program (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    @GetMapping("/{programId}/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReferralProgramDto> getProgramMetrics(
            @Parameter(description = "Program ID") @PathVariable String programId) {
        log.debug("Retrieving metrics for program: {}", programId);

        ReferralProgram program = programService.getProgramByProgramId(programId);
        return ResponseEntity.ok(toDto(program));
    }

    /**
     * Convert entity to DTO
     */
    private ReferralProgramDto toDto(ReferralProgram program) {
        BigDecimal remainingBudget = null;
        if (program.getMaxProgramBudget() != null) {
            remainingBudget = program.getMaxProgramBudget()
                    .subtract(program.getTotalRewardsIssued() != null
                            ? program.getTotalRewardsIssued()
                            : BigDecimal.ZERO);
        }

        return ReferralProgramDto.builder()
                .programId(program.getProgramId())
                .programName(program.getProgramName())
                .programType(program.getProgramType())
                .description(program.getDescription())
                .isActive(program.getIsActive())
                .startDate(program.getStartDate())
                .endDate(program.getEndDate())
                .referrerRewardType(program.getReferrerRewardType())
                .referrerRewardAmount(program.getReferrerRewardAmount())
                .referrerRewardPoints(program.getReferrerRewardPoints())
                .refereeRewardType(program.getRefereeRewardType())
                .refereeRewardAmount(program.getRefereeRewardAmount())
                .refereeRewardPoints(program.getRefereeRewardPoints())
                .minTransactionAmount(program.getMinTransactionAmount())
                .rewardExpiryDays(program.getRewardExpiryDays())
                .maxReferralsPerUser(program.getMaxReferralsPerUser())
                .requiresFirstTransaction(program.getRequiresFirstTransaction())
                .maxProgramBudget(program.getMaxProgramBudget())
                .totalRewardsIssued(program.getTotalRewardsIssued())
                .remainingBudget(remainingBudget)
                .totalReferrals(program.getTotalReferrals())
                .successfulReferrals(program.getSuccessfulReferrals())
                .conversionRate(program.getConversionRate())
                .averageTimeToConversion(program.getAverageTimeToConversion())
                .metadata(program.getMetadata())
                .createdAt(program.getCreatedAt())
                .updatedAt(program.getUpdatedAt())
                .build();
    }
}
