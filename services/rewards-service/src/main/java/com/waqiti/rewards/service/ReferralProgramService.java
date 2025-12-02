package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.enums.ProgramType;
import com.waqiti.rewards.exception.ReferralProgramNotFoundException;
import com.waqiti.rewards.exception.ReferralBudgetExceededException;
import com.waqiti.rewards.repository.ReferralProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing referral programs
 *
 * Handles CRUD operations, program lifecycle, and budget management
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralProgramService {

    private final ReferralProgramRepository programRepository;

    /**
     * Creates a new referral program
     */
    @Transactional
    public ReferralProgram createProgram(ReferralProgram program) {
        log.info("Creating referral program: {}", program.getProgramName());

        // Validate program ID uniqueness
        if (programRepository.existsByProgramId(program.getProgramId())) {
            throw new IllegalArgumentException("Program ID already exists: " + program.getProgramId());
        }

        // Validate dates
        validateProgramDates(program);

        // Validate reward configuration
        validateRewardConfiguration(program);

        ReferralProgram savedProgram = programRepository.save(program);

        log.info("Created referral program: id={}, programId={}",
                savedProgram.getId(), savedProgram.getProgramId());

        return savedProgram;
    }

    /**
     * Updates an existing referral program
     */
    @Transactional
    public ReferralProgram updateProgram(String programId, ReferralProgram updatedProgram) {
        log.info("Updating referral program: {}", programId);

        ReferralProgram existingProgram = getProgramByProgramId(programId);

        // Update allowed fields
        existingProgram.setProgramName(updatedProgram.getProgramName());
        existingProgram.setDescription(updatedProgram.getDescription());
        existingProgram.setProgramType(updatedProgram.getProgramType());
        existingProgram.setEndDate(updatedProgram.getEndDate());
        existingProgram.setIsPublic(updatedProgram.getIsPublic());
        existingProgram.setTargetAudience(updatedProgram.getTargetAudience());
        existingProgram.setEligibleProducts(updatedProgram.getEligibleProducts());
        existingProgram.setMaxReferralsPerUser(updatedProgram.getMaxReferralsPerUser());
        existingProgram.setMaxRewardsPerUser(updatedProgram.getMaxRewardsPerUser());
        existingProgram.setMaxProgramBudget(updatedProgram.getMaxProgramBudget());
        existingProgram.setTermsAndConditions(updatedProgram.getTermsAndConditions());
        existingProgram.setLastModifiedBy(updatedProgram.getLastModifiedBy());

        validateProgramDates(existingProgram);

        ReferralProgram saved = programRepository.save(existingProgram);

        log.info("Updated referral program: {}", programId);

        return saved;
    }

    /**
     * Activates a referral program
     */
    @Transactional
    public void activateProgram(String programId) {
        log.info("Activating referral program: {}", programId);

        ReferralProgram program = getProgramByProgramId(programId);
        program.setIsActive(true);
        programRepository.save(program);

        log.info("Activated referral program: {}", programId);
    }

    /**
     * Deactivates a referral program
     */
    @Transactional
    public void deactivateProgram(String programId) {
        log.info("Deactivating referral program: {}", programId);

        ReferralProgram program = getProgramByProgramId(programId);
        program.setIsActive(false);
        programRepository.save(program);

        log.info("Deactivated referral program: {}", programId);
    }

    /**
     * Gets a program by ID
     */
    public ReferralProgram getProgram(UUID id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new ReferralProgramNotFoundException("Program not found: " + id));
    }

    /**
     * Gets a program by program ID
     */
    public ReferralProgram getProgramByProgramId(String programId) {
        return programRepository.findByProgramId(programId)
                .orElseThrow(() -> new ReferralProgramNotFoundException("Program not found: " + programId));
    }

    /**
     * Gets all active programs
     */
    public List<ReferralProgram> getActivePrograms() {
        return programRepository.findByIsActiveTrue();
    }

    /**
     * Gets all currently active programs (within date range)
     */
    public List<ReferralProgram> getCurrentlyActivePrograms() {
        return programRepository.findCurrentlyActivePrograms(LocalDate.now());
    }

    /**
     * Gets programs by type
     */
    public List<ReferralProgram> getProgramsByType(ProgramType type) {
        return programRepository.findByProgramTypeAndIsActiveTrue(type);
    }

    /**
     * Gets programs by creator
     */
    public Page<ReferralProgram> getProgramsByCreator(String creator, Pageable pageable) {
        return programRepository.findByCreatedBy(creator, pageable);
    }

    /**
     * Searches programs by name
     */
    public Page<ReferralProgram> searchProgramsByName(String name, Pageable pageable) {
        return programRepository.findByProgramNameContainingIgnoreCase(name, pageable);
    }

    /**
     * Gets programs ending soon (within next N days)
     */
    public List<ReferralProgram> getProgramsEndingSoon(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(daysAhead);
        return programRepository.findProgramsEndingSoon(today, endDate);
    }

    /**
     * Gets high-performing programs (above conversion rate threshold)
     */
    public List<ReferralProgram> getHighPerformingPrograms(BigDecimal minConversionRate) {
        return programRepository.findHighPerformingPrograms(minConversionRate);
    }

    /**
     * Checks if user can make more referrals in a program
     */
    public boolean canUserMakeMoreReferrals(String programId, int currentReferralCount) {
        ReferralProgram program = getProgramByProgramId(programId);
        return program.canAcceptMoreReferrals(currentReferralCount);
    }

    /**
     * Increments referral count (atomic operation)
     */
    @Transactional
    public void incrementReferralCount(String programId) {
        ReferralProgram program = getProgramByProgramId(programId);
        program.incrementTotalReferrals();
        programRepository.save(program);

        log.debug("Incremented referral count for program: {}, total: {}",
                programId, program.getTotalReferrals());
    }

    /**
     * Increments successful referral count (atomic operation)
     */
    @Transactional
    public void incrementSuccessfulReferralCount(String programId) {
        ReferralProgram program = getProgramByProgramId(programId);
        program.incrementSuccessfulReferrals();
        programRepository.save(program);

        log.debug("Incremented successful referral count for program: {}, total: {}",
                programId, program.getSuccessfulReferrals());
    }

    /**
     * Adds reward amount to program total (with budget check)
     */
    @Transactional
    public void addRewardAmount(String programId, BigDecimal amount) {
        ReferralProgram program = getProgramByProgramId(programId);

        // Check budget before adding
        BigDecimal newTotal = program.getTotalRewardsIssued().add(amount);
        if (program.getMaxProgramBudget() != null &&
            newTotal.compareTo(program.getMaxProgramBudget()) > 0) {
            throw new ReferralBudgetExceededException(
                "Program budget exceeded: " + programId +
                ", budget: " + program.getMaxProgramBudget() +
                ", attempted: " + newTotal);
        }

        program.addRewardAmount(amount);
        programRepository.save(program);

        log.debug("Added reward amount to program: {}, amount: {}, total: {}",
                programId, amount, program.getTotalRewardsIssued());
    }

    /**
     * Checks if program budget is exceeded
     */
    public boolean isProgramBudgetExceeded(String programId) {
        ReferralProgram program = getProgramByProgramId(programId);
        return program.isBudgetExceeded();
    }

    /**
     * Gets total rewards issued across all programs
     */
    public BigDecimal getTotalRewardsIssuedAllPrograms() {
        return programRepository.getTotalRewardsIssuedAllPrograms();
    }

    /**
     * Gets total referrals across all programs
     */
    public Long getTotalReferralsAllPrograms() {
        return programRepository.getTotalReferralsAllPrograms();
    }

    /**
     * Deletes a program (soft delete by deactivating)
     */
    @Transactional
    public void deleteProgram(String programId) {
        log.warn("Deleting referral program: {}", programId);
        deactivateProgram(programId);
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    /**
     * Validates program dates
     */
    private void validateProgramDates(ReferralProgram program) {
        if (program.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }

        if (program.getEndDate() != null &&
            program.getEndDate().isBefore(program.getStartDate())) {
            throw new IllegalArgumentException(
                "End date must be after start date: start=" + program.getStartDate() +
                ", end=" + program.getEndDate());
        }
    }

    /**
     * Validates reward configuration
     */
    private void validateRewardConfiguration(ReferralProgram program) {
        if (program.getReferrerRewardType() == null) {
            throw new IllegalArgumentException("Referrer reward type is required");
        }

        // Validate referrer rewards
        switch (program.getReferrerRewardType()) {
            case POINTS:
                if (program.getReferrerRewardPoints() == null || program.getReferrerRewardPoints() <= 0) {
                    throw new IllegalArgumentException("Referrer reward points must be positive");
                }
                break;
            case CASHBACK:
            case FIXED_AMOUNT:
            case BONUS:
                if (program.getReferrerRewardAmount() == null ||
                    program.getReferrerRewardAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Referrer reward amount must be positive");
                }
                break;
            case PERCENTAGE:
            case COMMISSION:
                if (program.getReferrerRewardPercentage() == null ||
                    program.getReferrerRewardPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Referrer reward percentage must be positive");
                }
                break;
        }

        // Validate referee rewards (if configured)
        if (program.getRefereeRewardType() != null) {
            switch (program.getRefereeRewardType()) {
                case POINTS:
                    if (program.getRefereeRewardPoints() == null || program.getRefereeRewardPoints() <= 0) {
                        throw new IllegalArgumentException("Referee reward points must be positive");
                    }
                    break;
                case CASHBACK:
                case FIXED_AMOUNT:
                case BONUS:
                    if (program.getRefereeRewardAmount() == null ||
                        program.getRefereeRewardAmount().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Referee reward amount must be positive");
                    }
                    break;
                case PERCENTAGE:
                case COMMISSION:
                    if (program.getRefereeRewardPercentage() == null ||
                        program.getRefereeRewardPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Referee reward percentage must be positive");
                    }
                    break;
            }
        }
    }
}
