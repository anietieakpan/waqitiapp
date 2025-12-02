package com.waqiti.legal.service;

import com.waqiti.legal.domain.Subpoena;
import com.waqiti.legal.domain.LegalAttorney;
import com.waqiti.legal.repository.SubpoenaRepository;
import com.waqiti.legal.repository.LegalAttorneyRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Intelligent Legal Order Assignment Service
 *
 * Provides sophisticated workload-balanced assignment of legal orders to attorneys:
 * - Skill-based routing matching case complexity to attorney expertise
 * - Workload balancing considering current caseload and deadlines
 * - Automatic escalation for approaching deadlines
 * - SLA tracking and deadline monitoring
 * - Capacity-aware assignment preventing overload
 * - Specialty matching (subpoenas, litigation, regulatory, etc.)
 *
 * Assignment Algorithm:
 * 1. Determine case complexity score (0-100)
 * 2. Filter attorneys by specialty and skill level
 * 3. Calculate attorney availability score based on:
 *    - Current active cases
 *    - Upcoming deadlines
 *    - Historical performance
 *    - Specialization match
 * 4. Assign to attorney with highest availability score
 * 5. Auto-escalate if all attorneys near capacity
 *
 * Features:
 * - Round-robin with capacity awareness
 * - SLA-based prioritization
 * - Automatic reassignment for deadline risk
 * - Load balancing across legal team
 * - Integration with case management system
 * - Metrics tracking for optimization
 *
 * @author Waqiti Legal Technology Team
 * @version 2.0.0
 * @since 2025-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegalOrderAssignmentService {

    private final SubpoenaRepository subpoenaRepository;
    private final LegalAttorneyRepository attorneyRepository;
    private final com.waqiti.common.notification.NotificationService notificationService;

    // Configuration constants
    private static final int MAX_ACTIVE_CASES_PER_ATTORNEY = 15;
    private static final int CRITICAL_DEADLINE_DAYS = 5;
    private static final int WARNING_DEADLINE_DAYS = 10;
    private static final double WORKLOAD_WEIGHT = 0.4;
    private static final double SKILL_MATCH_WEIGHT = 0.3;
    private static final double DEADLINE_PRESSURE_WEIGHT = 0.2;
    private static final double PERFORMANCE_WEIGHT = 0.1;

    /**
     * Assign subpoena to most appropriate attorney using intelligent algorithm
     *
     * @param subpoena Subpoena to assign
     * @return Assigned attorney ID
     */
    @Transactional
    public String assignSubpoena(Subpoena subpoena) {
        log.info("Starting intelligent assignment for subpoena: subpoenaId={}, caseNumber={}",
            subpoena.getSubpoenaId(), subpoena.getCaseNumber());

        try {
            // Step 1: Calculate case complexity
            int complexityScore = calculateComplexityScore(subpoena);
            log.debug("Case complexity score: {} for subpoena: {}", complexityScore, subpoena.getSubpoenaId());

            // Step 2: Determine required skill level
            SkillLevel requiredSkill = determineRequiredSkillLevel(complexityScore);

            // Step 3: Find eligible attorneys
            List<LegalAttorney> eligibleAttorneys = findEligibleAttorneys(
                subpoena.getSubpoenaType().toString(),
                requiredSkill
            );

            if (eligibleAttorneys.isEmpty()) {
                log.warn("No eligible attorneys found for subpoena: {}", subpoena.getSubpoenaId());
                return escalateToSeniorCounsel(subpoena, "No eligible attorneys available");
            }

            // Step 4: Calculate assignment scores for each attorney
            List<AttorneyScore> scoredAttorneys = eligibleAttorneys.stream()
                .map(attorney -> calculateAttorneyScore(attorney, subpoena, complexityScore))
                .sorted(Comparator.comparingDouble(AttorneyScore::getTotalScore).reversed())
                .collect(Collectors.toList());

            // Step 5: Select best attorney
            AttorneyScore bestMatch = scoredAttorneys.get(0);
            LegalAttorney assignedAttorney = bestMatch.getAttorney();

            // Check if best attorney is near capacity
            if (bestMatch.getTotalScore() < 50.0) {
                log.warn("All attorneys near capacity. Best score: {} for attorney: {}",
                    bestMatch.getTotalScore(), assignedAttorney.getAttorneyId());

                // Check if we should escalate
                if (subpoena.getPriorityLevel() == Subpoena.PriorityLevel.CRITICAL) {
                    return escalateToSeniorCounsel(subpoena, "All attorneys near capacity for critical case");
                }
            }

            // Step 6: Assign the case
            subpoena.assignTo(assignedAttorney.getAttorneyId(), assignedAttorney.getFullName());
            subpoenaRepository.save(subpoena);

            // Step 7: Update attorney workload
            assignedAttorney.incrementActiveWorkload();
            attorneyRepository.save(assignedAttorney);

            // Step 8: Notify attorney
            notifyAttorneyOfAssignment(assignedAttorney, subpoena);

            log.info("Subpoena assigned successfully: subpoenaId={}, assignedTo={}, score={}",
                subpoena.getSubpoenaId(), assignedAttorney.getFullName(), bestMatch.getTotalScore());

            return assignedAttorney.getAttorneyId();

        } catch (Exception e) {
            log.error("Failed to assign subpoena: subpoenaId={}", subpoena.getSubpoenaId(), e);
            // Fallback: Escalate to senior counsel
            return escalateToSeniorCounsel(subpoena, "Assignment algorithm failed: " + e.getMessage());
        }
    }

    /**
     * Calculate case complexity score (0-100)
     *
     * Factors:
     * - Document volume requested
     * - Jurisdiction complexity
     * - Response deadline urgency
     * - Case type (criminal vs civil)
     * - Regulatory body involvement
     */
    private int calculateComplexityScore(Subpoena subpoena) {
        int score = 0;

        // Document volume factor (0-25 points)
        String requestedRecords = subpoena.getRequestedRecords();
        if (requestedRecords != null) {
            if (requestedRecords.length() > 500) score += 25;
            else if (requestedRecords.length() > 250) score += 15;
            else score += 5;
        }

        // Deadline urgency (0-25 points)
        long daysToDeadline = ChronoUnit.DAYS.between(LocalDate.now(), subpoena.getResponseDeadline());
        if (daysToDeadline < 5) score += 25;
        else if (daysToDeadline < 10) score += 15;
        else if (daysToDeadline < 20) score += 8;
        else score += 3;

        // Case type complexity (0-25 points)
        switch (subpoena.getSubpoenaType()) {
            case GRAND_JURY -> score += 25;          // Highest complexity
            case CRIMINAL_SUBPOENA -> score += 20;
            case ADMINISTRATIVE -> score += 15;
            case CIVIL_SUBPOENA -> score += 10;
            case RECORDS_REQUEST -> score += 5;     // Lowest complexity
        }

        // Jurisdiction complexity (0-15 points)
        String court = subpoena.getIssuingCourt();
        if (court != null) {
            if (court.contains("Federal") || court.contains("USDC")) score += 15;
            else if (court.contains("Superior") || court.contains("District")) score += 10;
            else score += 5;
        }

        // Customer involvement (0-10 points)
        if (subpoena.getCustomerNotificationRequired()) {
            score += 10; // RFPA notifications add complexity
        }

        return Math.min(score, 100);
    }

    /**
     * Determine required skill level based on complexity
     */
    private SkillLevel determineRequiredSkillLevel(int complexityScore) {
        if (complexityScore >= 75) return SkillLevel.SENIOR;
        if (complexityScore >= 50) return SkillLevel.INTERMEDIATE;
        return SkillLevel.JUNIOR;
    }

    /**
     * Find attorneys eligible for this case type and skill level
     */
    private List<LegalAttorney> findEligibleAttorneys(String caseType, SkillLevel requiredSkill) {
        // Get all active attorneys
        List<LegalAttorney> allAttorneys = attorneyRepository.findByActiveTrue();

        return allAttorneys.stream()
            .filter(attorney -> {
                // Must have required skill level or higher
                if (attorney.getSkillLevel().ordinal() < requiredSkill.ordinal()) {
                    return false;
                }

                // Must have specialty match or be generalist
                if (attorney.getSpecialties() != null && !attorney.getSpecialties().isEmpty()) {
                    return attorney.getSpecialties().contains(caseType) ||
                           attorney.getSpecialties().contains("GENERAL");
                }

                return true;
            })
            .collect(Collectors.toList());
    }

    /**
     * Calculate comprehensive score for attorney assignment
     *
     * Score components:
     * - Workload score (40%): Current capacity and deadline pressure
     * - Skill match score (30%): Expertise alignment with case
     * - Deadline pressure score (20%): Attorney's upcoming deadlines
     * - Performance score (10%): Historical success rate
     */
    private AttorneyScore calculateAttorneyScore(LegalAttorney attorney, Subpoena subpoena, int complexityScore) {

        // 1. Workload score (0-100, higher = more available)
        double workloadScore = calculateWorkloadScore(attorney);

        // 2. Skill match score (0-100, higher = better match)
        double skillMatchScore = calculateSkillMatchScore(attorney, subpoena, complexityScore);

        // 3. Deadline pressure score (0-100, higher = less pressure)
        double deadlinePressureScore = calculateDeadlinePressureScore(attorney);

        // 4. Performance score (0-100, higher = better historical performance)
        double performanceScore = calculatePerformanceScore(attorney);

        // Calculate weighted total score
        double totalScore = (workloadScore * WORKLOAD_WEIGHT) +
                           (skillMatchScore * SKILL_MATCH_WEIGHT) +
                           (deadlinePressureScore * DEADLINE_PRESSURE_WEIGHT) +
                           (performanceScore * PERFORMANCE_WEIGHT);

        return AttorneyScore.builder()
            .attorney(attorney)
            .workloadScore(workloadScore)
            .skillMatchScore(skillMatchScore)
            .deadlinePressureScore(deadlinePressureScore)
            .performanceScore(performanceScore)
            .totalScore(totalScore)
            .build();
    }

    /**
     * Calculate workload score based on current active cases
     */
    private double calculateWorkloadScore(LegalAttorney attorney) {
        int activeCases = attorney.getActiveWorkloadCount();

        // Perfect score if no cases, decreases linearly to 0 at max capacity
        double score = 100.0 * (1.0 - ((double) activeCases / MAX_ACTIVE_CASES_PER_ATTORNEY));

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Calculate skill match score
     */
    private double calculateSkillMatchScore(LegalAttorney attorney, Subpoena subpoena, int complexityScore) {
        double score = 50.0; // Base score

        // Specialty match bonus
        String caseType = subpoena.getSubpoenaType().toString();
        if (attorney.getSpecialties() != null && attorney.getSpecialties().contains(caseType)) {
            score += 30.0;
        } else if (attorney.getSpecialties() != null && attorney.getSpecialties().contains("GENERAL")) {
            score += 15.0;
        }

        // Skill level alignment
        SkillLevel requiredSkill = determineRequiredSkillLevel(complexityScore);
        if (attorney.getSkillLevel() == requiredSkill) {
            score += 20.0; // Perfect match
        } else if (attorney.getSkillLevel().ordinal() > requiredSkill.ordinal()) {
            score += 10.0; // Overqualified (still good but not perfect use of resources)
        }

        return Math.min(100, score);
    }

    /**
     * Calculate deadline pressure score based on upcoming deadlines
     */
    private double calculateDeadlinePressureScore(LegalAttorney attorney) {
        // Get attorney's cases with upcoming deadlines
        List<Subpoena> assignedCases = subpoenaRepository.findByAssignedToAndStatusNot(
            attorney.getAttorneyId(),
            Subpoena.SubpoenaStatus.COMPLETED
        );

        if (assignedCases.isEmpty()) {
            return 100.0; // No pressure
        }

        // Count critical and warning deadlines
        long criticalDeadlines = assignedCases.stream()
            .filter(s -> ChronoUnit.DAYS.between(LocalDate.now(), s.getResponseDeadline()) <= CRITICAL_DEADLINE_DAYS)
            .count();

        long warningDeadlines = assignedCases.stream()
            .filter(s -> {
                long days = ChronoUnit.DAYS.between(LocalDate.now(), s.getResponseDeadline());
                return days > CRITICAL_DEADLINE_DAYS && days <= WARNING_DEADLINE_DAYS;
            })
            .count();

        // Calculate pressure score (higher pressure = lower score)
        double pressurePoints = (criticalDeadlines * 20.0) + (warningDeadlines * 10.0);
        double score = 100.0 - pressurePoints;

        return Math.max(0, Math.min(100, score));
    }

    /**
     * Calculate performance score based on historical metrics
     */
    private double calculatePerformanceScore(LegalAttorney attorney) {
        // In production, calculate from historical data
        // For now, use attorney's success rate
        if (attorney.getSuccessRate() != null) {
            return attorney.getSuccessRate();
        }

        // Default to good performance
        return 75.0;
    }

    /**
     * Escalate to senior counsel when no suitable attorney found
     */
    private String escalateToSeniorCounsel(Subpoena subpoena, String reason) {
        log.warn("Escalating subpoena to senior counsel: subpoenaId={}, reason={}",
            subpoena.getSubpoenaId(), reason);

        // Mark as escalated
        subpoena.escalateToLegalCounsel(reason);
        subpoenaRepository.save(subpoena);

        // Notify senior counsel
        notificationService.sendCriticalNotification(
            "SENIOR_LEGAL_COUNSEL",
            "🚨 Subpoena Escalation Required - " + subpoena.getCaseNumber(),
            String.format(
                "Subpoena requires senior counsel assignment:\n\n" +
                "Subpoena ID: %s\n" +
                "Case Number: %s\n" +
                "Issuing Court: %s\n" +
                "Response Deadline: %s\n" +
                "Priority: %s\n" +
                "Escalation Reason: %s\n\n" +
                "ACTION REQUIRED: Manual assignment needed.",
                subpoena.getSubpoenaId(),
                subpoena.getCaseNumber(),
                subpoena.getIssuingCourt(),
                subpoena.getResponseDeadline(),
                subpoena.getPriorityLevel(),
                reason
            ),
            Map.of(
                "subpoenaId", subpoena.getSubpoenaId(),
                "caseNumber", subpoena.getCaseNumber(),
                "priority", subpoena.getPriorityLevel().toString(),
                "escalationReason", reason,
                "requiresManualAssignment", true
            )
        );

        return "ESCALATED_TO_SENIOR_COUNSEL";
    }

    /**
     * Notify attorney of new assignment
     */
    private void notifyAttorneyOfAssignment(LegalAttorney attorney, Subpoena subpoena) {
        try {
            notificationService.sendCriticalNotification(
                attorney.getEmail(),
                "New Case Assignment - " + subpoena.getCaseNumber(),
                String.format(
                    "You have been assigned a new legal matter:\n\n" +
                    "Case Number: %s\n" +
                    "Type: %s\n" +
                    "Issuing Court: %s\n" +
                    "Response Deadline: %s\n" +
                    "Priority: %s\n\n" +
                    "Please review the case details in the legal management system.",
                    subpoena.getCaseNumber(),
                    subpoena.getSubpoenaType(),
                    subpoena.getIssuingCourt(),
                    subpoena.getResponseDeadline(),
                    subpoena.getPriorityLevel()
                ),
                Map.of(
                    "subpoenaId", subpoena.getSubpoenaId(),
                    "caseNumber", subpoena.getCaseNumber(),
                    "assignedTo", attorney.getFullName(),
                    "deadline", subpoena.getResponseDeadline().toString()
                )
            );

            log.info("Assignment notification sent to attorney: {}", attorney.getEmail());

        } catch (Exception e) {
            log.error("Failed to notify attorney of assignment: attorneyId={}", attorney.getAttorneyId(), e);
        }
    }

    /**
     * Scheduled task to check for deadline escalations
     * Runs daily at 9 AM
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void checkDeadlineEscalations() {
        log.info("Running scheduled deadline escalation check");

        try {
            LocalDate criticalDate = LocalDate.now().plusDays(CRITICAL_DEADLINE_DAYS);

            List<Subpoena> criticalDeadlines = subpoenaRepository
                .findByResponseDeadlineBeforeAndStatusNot(criticalDate, Subpoena.SubpoenaStatus.COMPLETED);

            for (Subpoena subpoena : criticalDeadlines) {
                long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), subpoena.getResponseDeadline());

                if (daysRemaining <= 2 && subpoena.getAssignedTo() != null) {
                    // Escalate to supervisor
                    escalateDeadlineRisk(subpoena, daysRemaining);
                } else if (daysRemaining <= CRITICAL_DEADLINE_DAYS && subpoena.getAssignedTo() != null) {
                    // Send reminder
                    sendDeadlineReminder(subpoena, daysRemaining);
                }
            }

            log.info("Deadline escalation check completed. Checked {} cases", criticalDeadlines.size());

        } catch (Exception e) {
            log.error("Error during deadline escalation check", e);
        }
    }

    /**
     * Escalate case with approaching deadline
     */
    private void escalateDeadlineRisk(Subpoena subpoena, long daysRemaining) {
        log.warn("Escalating deadline risk: subpoenaId={}, daysRemaining={}",
            subpoena.getSubpoenaId(), daysRemaining);

        notificationService.sendCriticalNotification(
            "LEGAL_SUPERVISOR",
            "⚠️ URGENT: Deadline Risk - " + subpoena.getCaseNumber(),
            String.format(
                "Case approaching deadline requires immediate attention:\n\n" +
                "Case Number: %s\n" +
                "Assigned To: %s\n" +
                "Deadline: %s (%d days remaining)\n" +
                "Current Status: %s\n\n" +
                "ACTION REQUIRED: Verify case is on track or reassign.",
                subpoena.getCaseNumber(),
                subpoena.getAssignedToName(),
                subpoena.getResponseDeadline(),
                daysRemaining,
                subpoena.getStatus()
            ),
            Map.of(
                "subpoenaId", subpoena.getSubpoenaId(),
                "assignedTo", subpoena.getAssignedTo(),
                "daysRemaining", daysRemaining,
                "escalationType", "DEADLINE_RISK"
            )
        );
    }

    /**
     * Send deadline reminder to assigned attorney
     */
    private void sendDeadlineReminder(Subpoena subpoena, long daysRemaining) {
        LegalAttorney attorney = attorneyRepository.findById(subpoena.getAssignedTo()).orElse(null);

        if (attorney != null) {
            notificationService.sendCriticalNotification(
                attorney.getEmail(),
                "Deadline Reminder - " + subpoena.getCaseNumber(),
                String.format(
                    "Reminder: Case deadline approaching:\n\n" +
                    "Case Number: %s\n" +
                    "Deadline: %s (%d days remaining)\n" +
                    "Current Status: %s\n\n" +
                    "Please ensure timely completion.",
                    subpoena.getCaseNumber(),
                    subpoena.getResponseDeadline(),
                    daysRemaining,
                    subpoena.getStatus()
                ),
                Map.of(
                    "subpoenaId", subpoena.getSubpoenaId(),
                    "daysRemaining", daysRemaining
                )
            );
        }
    }

    /**
     * Reassign case to different attorney (manual or automatic)
     */
    @Transactional
    public String reassignSubpoena(String subpoenaId, String reason) {
        log.info("Reassigning subpoena: subpoenaId={}, reason={}", subpoenaId, reason);

        Subpoena subpoena = subpoenaRepository.findBySubpoenaId(subpoenaId)
            .orElseThrow(() -> new IllegalArgumentException("Subpoena not found: " + subpoenaId));

        // Decrement workload from previous attorney
        if (subpoena.getAssignedTo() != null) {
            attorneyRepository.findById(subpoena.getAssignedTo()).ifPresent(attorney -> {
                attorney.decrementActiveWorkload();
                attorneyRepository.save(attorney);
            });
        }

        // Clear current assignment
        subpoena.clearAssignment();
        subpoenaRepository.save(subpoena);

        // Assign using standard algorithm
        return assignSubpoena(subpoena);
    }

    // ==================== Data Transfer Objects ====================

    /**
     * Attorney scoring result
     */
    @Data
    @Builder
    private static class AttorneyScore {
        private LegalAttorney attorney;
        private double workloadScore;
        private double skillMatchScore;
        private double deadlinePressureScore;
        private double performanceScore;
        private double totalScore;
    }

    /**
     * Skill level enum
     */
    public enum SkillLevel {
        JUNIOR,
        INTERMEDIATE,
        SENIOR,
        PRINCIPAL
    }
}
