package com.waqiti.legal.service;

import com.waqiti.legal.domain.BankruptcyCase;
import com.waqiti.legal.repository.BankruptcyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Automatic Stay Service
 *
 * Complete production-ready automatic stay enforcement service with:
 * - 11 U.S.C. � 362 automatic stay enforcement
 * - Collection activity cessation
 * - Litigation hold enforcement
 * - Foreclosure and repossession suspension
 * - Department-wide notification
 * - Stay violation detection and reporting
 * - Stay relief motion handling
 * - Stay lift processing
 *
 * The automatic stay is one of the most powerful protections in bankruptcy law,
 * immediately stopping virtually all creditor collection actions.
 *
 * Stay prohibits:
 * - Collection calls, letters, lawsuits
 * - Foreclosures and repossessions
 * - Wage garnishments
 * - Utility disconnections
 * - IRS seizures
 * - Evictions (with exceptions)
 *
 * Stay exceptions (actions that CAN continue):
 * - Criminal proceedings
 * - Child support collection
 * - Tax audits
 * - Certain domestic relations proceedings
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomaticStayService {

    private final BankruptcyRepository bankruptcyRepository;
    private final com.waqiti.common.notification.NotificationService notificationService;
    private final com.waqiti.legal.client.CollectionServiceClient collectionServiceClient;
    private final com.waqiti.legal.client.LitigationServiceClient litigationServiceClient;
    private final com.waqiti.legal.client.ForeclosureServiceClient foreclosureServiceClient;
    private final com.waqiti.legal.client.GarnishmentServiceClient garnishmentServiceClient;

    /**
     * Enforce automatic stay upon bankruptcy filing
     *
     * Per 11 U.S.C. � 362(a), the automatic stay takes effect immediately
     * upon the filing of a bankruptcy petition.
     *
     * @param bankruptcyId Bankruptcy case ID
     * @param customerId Customer ID
     * @param filingDate Filing date
     * @param stayDate Stay enforcement date
     * @return Stay enforcement confirmation
     */
    @Transactional
    public Map<String, Object> enforceAutomaticStay(
            String bankruptcyId,
            String customerId,
            LocalDate filingDate,
            LocalDate stayDate) {

        log.info("AUTOMATIC_STAY_ENFORCE: Enforcing automatic stay for case {} - Customer: {}",
                bankruptcyId, customerId);

        BankruptcyCase bankruptcyCase = getBankruptcyById(bankruptcyId);

        // Enforce stay on bankruptcy case
        bankruptcyCase.enforceAutomaticStay(stayDate);

        // Stop all collection activities
        StayEnforcementResult collectionResult = stopAllCollectionActivities(customerId, bankruptcyId);

        // Suspend all pending litigation
        StayEnforcementResult litigationResult = suspendAllLitigation(customerId, bankruptcyId);

        // Halt foreclosure and repossession proceedings
        StayEnforcementResult foreclosureResult = haltForeclosureProceedings(customerId, bankruptcyId);

        // Stop wage garnishments
        StayEnforcementResult garnishmentResult = stopWageGarnishments(customerId, bankruptcyId);

        // Update case status
        bankruptcyCase.setCaseStatus(BankruptcyCase.BankruptcyStatus.AUTOMATIC_STAY_ACTIVE);
        bankruptcyRepository.save(bankruptcyCase);

        // Build enforcement confirmation
        Map<String, Object> confirmation = new HashMap<>();
        confirmation.put("bankruptcyId", bankruptcyId);
        confirmation.put("customerId", customerId);
        confirmation.put("stayEnforced", true);
        confirmation.put("enforcementDate", stayDate);
        confirmation.put("collectionActivitiesStopped", collectionResult.getActionCount());
        confirmation.put("litigationSuspended", litigationResult.getActionCount());
        confirmation.put("foreclosuresHalted", foreclosureResult.getActionCount());
        confirmation.put("garnishmentsStopped", garnishmentResult.getActionCount());
        confirmation.put("totalActionsHalted",
                collectionResult.getActionCount() +
                litigationResult.getActionCount() +
                foreclosureResult.getActionCount() +
                garnishmentResult.getActionCount());

        log.info("AUTOMATIC_STAY_ENFORCED: Stay enforced for case {} - {} total actions halted",
                bankruptcyId, confirmation.get("totalActionsHalted"));

        return confirmation;
    }

    /**
     * Notify all departments of automatic stay
     *
     * Critical: All departments must immediately cease collection activities
     * to avoid stay violations (sanctions up to $500K+ per violation)
     *
     * @param bankruptcyId Bankruptcy case ID
     * @param customerId Customer ID
     * @param caseNumber Court case number
     * @param chapter Bankruptcy chapter
     */
    @Transactional
    public void notifyAllDepartments(
            String bankruptcyId,
            String customerId,
            String caseNumber,
            String chapter) {

        log.info("AUTOMATIC_STAY_NOTIFY: Notifying all departments for case {} - Customer: {}",
                bankruptcyId, customerId);

        BankruptcyCase bankruptcyCase = getBankruptcyById(bankruptcyId);

        // Critical departments that MUST be notified immediately
        List<String> departments = Arrays.asList(
                "COLLECTIONS",
                "LEGAL",
                "CUSTOMER_SERVICE",
                "LOAN_SERVICING",
                "CARD_SERVICES",
                "ACCOUNT_MANAGEMENT",
                "FRAUD_PREVENTION",
                "CREDIT_REPORTING",
                "FORECLOSURE",
                "REPOSSESSION",
                "LITIGATION",
                "EXECUTIVE_TEAM"
        );

        int notificationsSent = 0;

        for (String department : departments) {
            try {
                sendDepartmentNotification(department, bankruptcyCase, customerId, caseNumber, chapter);
                notificationsSent++;
                log.debug("STAY_NOTIFICATION_SENT: {} notified for case {}", department, bankruptcyId);
            } catch (Exception e) {
                log.error("STAY_NOTIFICATION_FAILED: Failed to notify {} for case {}",
                        department, bankruptcyId, e);
            }
        }

        // Mark departments as notified
        bankruptcyCase.markDepartmentsNotified(LocalDateTime.now());
        bankruptcyRepository.save(bankruptcyCase);

        log.info("AUTOMATIC_STAY_NOTIFICATIONS_COMPLETE: Notified {}/{} departments for case {}",
                notificationsSent, departments.size(), bankruptcyId);
    }

    /**
     * Process motion for relief from automatic stay
     *
     * Creditors may file motions to lift the stay for specific purposes
     * (e.g., foreclosure on property with no equity)
     *
     * @param bankruptcyId Bankruptcy case ID
     * @param motionType Type of motion
     * @param creditorName Creditor filing motion
     * @param reason Reason for stay relief
     * @param hearingDate Scheduled hearing date
     * @return Motion processing result
     */
    @Transactional
    public Map<String, Object> processStayReliefMotion(
            String bankruptcyId,
            String motionType,
            String creditorName,
            String reason,
            LocalDateTime hearingDate) {

        log.info("STAY_RELIEF_MOTION: Processing stay relief motion for case {} - Creditor: {}",
                bankruptcyId, creditorName);

        BankruptcyCase bankruptcyCase = getBankruptcyById(bankruptcyId);

        // Record motion filing
        Map<String, Object> motion = new HashMap<>();
        motion.put("motionType", motionType);
        motion.put("creditor", creditorName);
        motion.put("reason", reason);
        motion.put("filingDate", LocalDate.now().toString());
        motion.put("hearingDate", hearingDate != null ? hearingDate.toString() : null);
        motion.put("status", "PENDING_HEARING");

        bankruptcyCase.getMotionsFiled().add(motion);
        bankruptcyCase.setStayReliefMotionFiled(true);
        bankruptcyRepository.save(bankruptcyCase);

        Map<String, Object> result = new HashMap<>();
        result.put("motionRecorded", true);
        result.put("motionType", motionType);
        result.put("hearingDate", hearingDate);
        result.put("bankruptcyId", bankruptcyId);

        log.info("STAY_RELIEF_MOTION_RECORDED: Motion from {} recorded for case {}",
                creditorName, bankruptcyId);

        return result;
    }

    /**
     * Lift automatic stay (after court order)
     *
     * Only the bankruptcy court can lift the automatic stay.
     * This method should only be called after receiving a court order.
     *
     * @param bankruptcyId Bankruptcy case ID
     * @param liftDate Date stay is lifted
     * @param reason Reason for lift
     * @param courtOrderNumber Court order number
     * @param scopeOfLift Scope (FULL, PARTIAL, SPECIFIC_ASSET)
     * @return Stay lift confirmation
     */
    @Transactional
    public Map<String, Object> liftStay(
            String bankruptcyId,
            LocalDate liftDate,
            String reason,
            String courtOrderNumber,
            String scopeOfLift) {

        log.info("AUTOMATIC_STAY_LIFT: Lifting automatic stay for case {} - Reason: {}",
                bankruptcyId, reason);

        BankruptcyCase bankruptcyCase = getBankruptcyById(bankruptcyId);

        // Record court order
        Map<String, Object> courtOrder = new HashMap<>();
        courtOrder.put("orderType", "RELIEF_FROM_STAY");
        courtOrder.put("orderNumber", courtOrderNumber);
        courtOrder.put("orderDate", liftDate.toString());
        courtOrder.put("reason", reason);
        courtOrder.put("scope", scopeOfLift);

        bankruptcyCase.addCourtOrder("RELIEF_FROM_STAY", reason, liftDate);

        // Lift stay
        bankruptcyCase.liftAutomaticStay(liftDate, reason);

        // If FULL lift, resume collection activities
        if ("FULL".equals(scopeOfLift)) {
            resumeCollectionActivities(bankruptcyCase.getCustomerId(), bankruptcyId);
        }

        bankruptcyRepository.save(bankruptcyCase);

        Map<String, Object> confirmation = new HashMap<>();
        confirmation.put("bankruptcyId", bankruptcyId);
        confirmation.put("stayLifted", true);
        confirmation.put("liftDate", liftDate);
        confirmation.put("scope", scopeOfLift);
        confirmation.put("courtOrderNumber", courtOrderNumber);

        log.info("AUTOMATIC_STAY_LIFTED: Stay lifted for case {} - Scope: {}",
                bankruptcyId, scopeOfLift);

        return confirmation;
    }

    /**
     * Verify stay compliance
     *
     * Audit all activities to ensure no stay violations
     *
     * @param bankruptcyId Bankruptcy case ID
     * @param customerId Customer ID
     * @return Compliance report
     */
    @Transactional(readOnly = true)
    public Map<String, Object> verifyStayCompliance(String bankruptcyId, String customerId) {
        log.info("STAY_COMPLIANCE_VERIFY: Verifying stay compliance for case {} - Customer: {}",
                bankruptcyId, customerId);

        BankruptcyCase bankruptcyCase = getBankruptcyById(bankruptcyId);

        if (!bankruptcyCase.isAutomaticStayInEffect()) {
            log.warn("STAY_NOT_ACTIVE: Stay is not active for case {}", bankruptcyId);
        }

        // Check for any violations
        List<String> violations = new ArrayList<>();

        try {
            // Check collection system for post-filing activities
            List<com.waqiti.legal.client.CollectionActivityDto> postFilingActivities =
                collectionServiceClient.getActivitiesSinceDate(customerId, bankruptcyCase.getFilingDate());

            if (postFilingActivities != null && !postFilingActivities.isEmpty()) {
                violations.add(String.format("VIOLATION: %d collection activities detected after filing date",
                    postFilingActivities.size()));
                log.error("STAY_VIOLATION: {} collection activities found after filing for case {}",
                    postFilingActivities.size(), bankruptcyId);
            }
        } catch (Exception e) {
            log.error("Failed to check collection activities for stay compliance: {}", e.getMessage());
            violations.add("WARNING: Unable to verify collection activity compliance - manual review required");
        }

        try {
            // Check for post-filing lawsuits
            List<com.waqiti.legal.client.LawsuitDto> postFilingLawsuits =
                litigationServiceClient.getLawsuitsSinceDate(customerId, bankruptcyCase.getFilingDate());

            if (postFilingLawsuits != null && !postFilingLawsuits.isEmpty()) {
                violations.add(String.format("VIOLATION: %d lawsuits filed after stay",
                    postFilingLawsuits.size()));
                log.error("STAY_VIOLATION: {} lawsuits found after filing for case {}",
                    postFilingLawsuits.size(), bankruptcyId);
            }
        } catch (Exception e) {
            log.error("Failed to check litigation for stay compliance: {}", e.getMessage());
            violations.add("WARNING: Unable to verify litigation compliance - manual review required");
        }

        boolean compliant = violations.stream()
            .noneMatch(v -> v.startsWith("VIOLATION:"));

        Map<String, Object> report = new HashMap<>();
        report.put("bankruptcyId", bankruptcyId);
        report.put("customerId", customerId);
        report.put("stayActive", bankruptcyCase.isAutomaticStayInEffect());
        report.put("compliant", compliant);
        report.put("violations", violations);
        report.put("violationCount", violations.size());
        report.put("auditDate", LocalDate.now());

        if (!compliant) {
            log.error("STAY_VIOLATIONS_DETECTED: {} violations found for case {}",
                    violations.size(), bankruptcyId);
        } else {
            log.info("STAY_COMPLIANCE_VERIFIED: No violations for case {}", bankruptcyId);
        }

        return report;
    }

    // ========== Private Helper Methods ==========

    /**
     * Get bankruptcy case by ID
     */
    private BankruptcyCase getBankruptcyById(String bankruptcyId) {
        return bankruptcyRepository.findByBankruptcyId(bankruptcyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Bankruptcy case not found: " + bankruptcyId));
    }

    /**
     * Stop all collection activities
     */
    private StayEnforcementResult stopAllCollectionActivities(String customerId, String bankruptcyId) {
        log.debug("STAY_STOP_COLLECTIONS: Stopping collections for customer {}", customerId);

        try {
            // Call CollectionServiceClient to stop all activities
            Map<String, Object> result = collectionServiceClient.stopAllActivities(
                customerId, "AUTOMATIC_STAY", bankruptcyId);

            boolean success = (boolean) result.getOrDefault("success", false);
            int activitiesStopped = (int) result.getOrDefault("activitiesStopped", 0);

            if (!success) {
                log.error("CRITICAL: Failed to stop collections for customer {} - {}",
                    customerId, result.get("error"));

                // Escalate immediately if fallback was triggered
                if (Boolean.TRUE.equals(result.get("fallback"))) {
                    notificationService.sendCriticalNotification(
                        "LEGAL_TEAM",
                        "CRITICAL: Bankruptcy Stay Enforcement Failure",
                        String.format("Failed to stop collection activities for customer %s (bankruptcy %s). " +
                            "IMMEDIATE MANUAL INTERVENTION REQUIRED to avoid stay violation.",
                            customerId, bankruptcyId),
                        result
                    );
                }
            }

            List<String> actions = new ArrayList<>();
            actions.add(String.format("%d collection activities stopped", activitiesStopped));

            return new StayEnforcementResult(activitiesStopped, actions);

        } catch (Exception e) {
            log.error("CRITICAL: Exception stopping collections for customer {}: {}",
                customerId, e.getMessage(), e);

            // Critical failure - escalate immediately
            notificationService.sendCriticalNotification(
                "LEGAL_TEAM",
                "CRITICAL: Bankruptcy Stay Enforcement Exception",
                String.format("Exception stopping collection activities for customer %s (bankruptcy %s): %s. " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                    customerId, bankruptcyId, e.getMessage()),
                Map.of("customerId", customerId, "bankruptcyId", bankruptcyId, "error", e.getMessage())
            );

            // Return zero - indicates failure
            return new StayEnforcementResult(0, Collections.singletonList("FAILED: " + e.getMessage()));
        }
    }

    /**
     * Suspend all pending litigation
     */
    private StayEnforcementResult suspendAllLitigation(String customerId, String bankruptcyId) {
        log.debug("STAY_SUSPEND_LITIGATION: Suspending litigation for customer {}", customerId);

        try {
            // Call LitigationServiceClient to suspend all lawsuits
            Map<String, Object> result = litigationServiceClient.suspendAllLawsuits(
                customerId, "AUTOMATIC_STAY", bankruptcyId);

            boolean success = (boolean) result.getOrDefault("success", false);
            int lawsuitsSuspended = (int) result.getOrDefault("lawsuitsSuspended", 0);

            if (!success) {
                log.error("CRITICAL: Failed to suspend litigation for customer {} - {}",
                    customerId, result.get("error"));

                // Escalate immediately if fallback was triggered
                if (Boolean.TRUE.equals(result.get("fallback"))) {
                    notificationService.sendCriticalNotification(
                        "LEGAL_TEAM",
                        "CRITICAL: Bankruptcy Stay Litigation Suspension Failure",
                        String.format("Failed to suspend litigation for customer %s (bankruptcy %s). " +
                            "IMMEDIATE MANUAL INTERVENTION REQUIRED to avoid stay violation.",
                            customerId, bankruptcyId),
                        result
                    );
                }
            }

            List<String> actions = new ArrayList<>();
            actions.add(String.format("%d lawsuits suspended", lawsuitsSuspended));

            return new StayEnforcementResult(lawsuitsSuspended, actions);

        } catch (Exception e) {
            log.error("CRITICAL: Exception suspending litigation for customer {}: {}",
                customerId, e.getMessage(), e);

            // Critical failure - escalate immediately
            notificationService.sendCriticalNotification(
                "LEGAL_TEAM",
                "CRITICAL: Bankruptcy Stay Litigation Exception",
                String.format("Exception suspending litigation for customer %s (bankruptcy %s): %s. " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                    customerId, bankruptcyId, e.getMessage()),
                Map.of("customerId", customerId, "bankruptcyId", bankruptcyId, "error", e.getMessage())
            );

            return new StayEnforcementResult(0, Collections.singletonList("FAILED: " + e.getMessage()));
        }
    }

    /**
     * Halt foreclosure and repossession proceedings - COMPLETE IMPLEMENTATION
     */
    private StayEnforcementResult haltForeclosureProceedings(String customerId, String bankruptcyId) {
        log.debug("STAY_HALT_FORECLOSURE: Halting foreclosures for customer {}", customerId);

        try {
            // Get all active foreclosure proceedings
            List<com.waqiti.legal.client.ForeclosureServiceClient.ForeclosureDto> foreclosures =
                foreclosureServiceClient.getActiveForeclosures(customerId);

            int foreclosuresHalted = 0;
            List<String> actions = new ArrayList<>();

            // Halt each foreclosure proceeding
            for (com.waqiti.legal.client.ForeclosureServiceClient.ForeclosureDto foreclosure : foreclosures) {
                try {
                    Map<String, Object> result = foreclosureServiceClient.haltForeclosureProceeding(
                        foreclosure.getForeclosureId(), "AUTOMATIC_STAY", bankruptcyId);

                    boolean success = (boolean) result.getOrDefault("success", false);

                    if (success) {
                        foreclosuresHalted++;
                        actions.add(String.format("Foreclosure %s halted on property %s",
                            foreclosure.getForeclosureId(), foreclosure.getPropertyAddress()));
                        log.info("FORECLOSURE_HALTED: {} halted for customer {}",
                            foreclosure.getForeclosureId(), customerId);
                    } else {
                        log.error("CRITICAL: Failed to halt foreclosure {} - {}",
                            foreclosure.getForeclosureId(), result.get("error"));

                        // Escalate immediately if fallback was triggered
                        if (Boolean.TRUE.equals(result.get("fallback"))) {
                            notificationService.sendCriticalNotification(
                                "LEGAL_TEAM",
                                "CRITICAL: Bankruptcy Stay Foreclosure Halt Failure",
                                String.format("Failed to halt foreclosure %s for customer %s (bankruptcy %s). " +
                                    "IMMEDIATE MANUAL INTERVENTION REQUIRED to avoid stay violation.",
                                    foreclosure.getForeclosureId(), customerId, bankruptcyId),
                                result
                            );
                        }
                    }
                } catch (Exception e) {
                    log.error("Exception halting foreclosure {}: {}",
                        foreclosure.getForeclosureId(), e.getMessage());
                }
            }

            if (foreclosures.isEmpty()) {
                log.debug("No active foreclosures found for customer {}", customerId);
            }

            return new StayEnforcementResult(foreclosuresHalted, actions);

        } catch (Exception e) {
            log.error("CRITICAL: Exception halting foreclosures for customer {}: {}",
                customerId, e.getMessage(), e);

            // Critical failure - escalate immediately
            notificationService.sendCriticalNotification(
                "LEGAL_TEAM",
                "CRITICAL: Bankruptcy Stay Foreclosure Exception",
                String.format("Exception halting foreclosures for customer %s (bankruptcy %s): %s. " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                    customerId, bankruptcyId, e.getMessage()),
                Map.of("customerId", customerId, "bankruptcyId", bankruptcyId, "error", e.getMessage())
            );

            return new StayEnforcementResult(0, Collections.singletonList("FAILED: " + e.getMessage()));
        }
    }

    /**
     * Stop wage garnishments - COMPLETE IMPLEMENTATION
     */
    private StayEnforcementResult stopWageGarnishments(String customerId, String bankruptcyId) {
        log.debug("STAY_STOP_GARNISHMENTS: Stopping garnishments for customer {}", customerId);

        try {
            // Get all active wage garnishments
            List<com.waqiti.legal.client.GarnishmentServiceClient.GarnishmentDto> garnishments =
                garnishmentServiceClient.getActiveGarnishments(customerId);

            int garnishmentsStopped = 0;
            List<String> actions = new ArrayList<>();

            // Stop each garnishment
            for (com.waqiti.legal.client.GarnishmentServiceClient.GarnishmentDto garnishment : garnishments) {
                try {
                    Map<String, Object> result = garnishmentServiceClient.stopGarnishment(
                        garnishment.getGarnishmentId(), "AUTOMATIC_STAY", bankruptcyId);

                    boolean success = (boolean) result.getOrDefault("success", false);

                    if (success) {
                        garnishmentsStopped++;
                        actions.add(String.format("Garnishment %s stopped at %s",
                            garnishment.getGarnishmentId(), garnishment.getEmployerName()));
                        log.info("GARNISHMENT_STOPPED: {} stopped for customer {}",
                            garnishment.getGarnishmentId(), customerId);
                    } else {
                        log.error("CRITICAL: Failed to stop garnishment {} - {}",
                            garnishment.getGarnishmentId(), result.get("error"));

                        // Escalate immediately if fallback was triggered
                        if (Boolean.TRUE.equals(result.get("fallback"))) {
                            notificationService.sendCriticalNotification(
                                "LEGAL_TEAM",
                                "CRITICAL: Bankruptcy Stay Garnishment Stop Failure",
                                String.format("Failed to stop garnishment %s for customer %s (bankruptcy %s). " +
                                    "IMMEDIATE MANUAL INTERVENTION REQUIRED to avoid stay violation.",
                                    garnishment.getGarnishmentId(), customerId, bankruptcyId),
                                result
                            );
                        }
                    }
                } catch (Exception e) {
                    log.error("Exception stopping garnishment {}: {}",
                        garnishment.getGarnishmentId(), e.getMessage());
                }
            }

            if (garnishments.isEmpty()) {
                log.debug("No active garnishments found for customer {}", customerId);
            }

            return new StayEnforcementResult(garnishmentsStopped, actions);

        } catch (Exception e) {
            log.error("CRITICAL: Exception stopping garnishments for customer {}: {}",
                customerId, e.getMessage(), e);

            // Critical failure - escalate immediately
            notificationService.sendCriticalNotification(
                "LEGAL_TEAM",
                "CRITICAL: Bankruptcy Stay Garnishment Exception",
                String.format("Exception stopping garnishments for customer %s (bankruptcy %s): %s. " +
                    "IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                    customerId, bankruptcyId, e.getMessage()),
                Map.of("customerId", customerId, "bankruptcyId", bankruptcyId, "error", e.getMessage())
            );

            return new StayEnforcementResult(0, Collections.singletonList("FAILED: " + e.getMessage()));
        }
    }

    /**
     * Resume collection activities (after stay lift)
     */
    private void resumeCollectionActivities(String customerId, String bankruptcyId) {
        log.info("STAY_RESUME_COLLECTIONS: Resuming collections for customer {}", customerId);

        try {
            // Call CollectionServiceClient to resume activities
            Map<String, Object> result = collectionServiceClient.resumeActivities(
                customerId, "STAY_LIFTED", bankruptcyId);

            boolean success = (boolean) result.getOrDefault("success", false);

            if (!success) {
                log.warn("Failed to resume collections for customer {} - {}",
                    customerId, result.get("error"));

                // Not as critical as failure to stop, but should notify
                notificationService.sendNotification(
                    "COLLECTIONS_TEAM",
                    "EMAIL",
                    "Collection Resume Failed",
                    String.format("Failed to automatically resume collection activities for customer %s " +
                        "(bankruptcy %s stay lifted). Manual resume may be required.",
                        customerId, bankruptcyId)
                );
            } else {
                log.info("Collections resumed successfully for customer {}", customerId);
            }
        } catch (Exception e) {
            log.error("Exception resuming collections for customer {}: {}",
                customerId, e.getMessage(), e);

            // Notify but don't fail - collections team can handle manually
            notificationService.sendNotification(
                "COLLECTIONS_TEAM",
                "EMAIL",
                "Collection Resume Exception",
                String.format("Exception resuming collections for customer %s: %s",
                    customerId, e.getMessage())
            );
        }
    }

    /**
     * Send department notification
     */
    private void sendDepartmentNotification(
            String department,
            BankruptcyCase bankruptcyCase,
            String customerId,
            String caseNumber,
            String chapter) {

        log.debug("STAY_NOTIFY_DEPT: Notifying {} for case {}", department, bankruptcyCase.getBankruptcyId());

        // Build RFPA-compliant notification message
        String message = String.format(
                "�� AUTOMATIC STAY ENFORCED ��\n\n" +
                "Customer ID: %s\n" +
                "Case Number: %s\n" +
                "Chapter: %s\n" +
                "Filing Date: %s\n\n" +
                "�� ACTION REQUIRED: Immediately cease ALL collection activities.\n\n" +
                "Per 11 U.S.C. § 362, the automatic stay prohibits:\n" +
                "- Collection calls, letters, and emails\n" +
                "- Foreclosures and repossessions\n" +
                "- Wage garnishments\n" +
                "- Litigation\n" +
                "- Credit reporting updates\n\n" +
                "�� WARNING: Stay violations may result in sanctions up to $500,000 per violation.\n\n" +
                "Contact Legal Team immediately with any questions.\n\n" +
                "Bankruptcy ID: %s",
                customerId,
                caseNumber,
                chapter,
                bankruptcyCase.getFilingDate(),
                bankruptcyCase.getBankruptcyId()
        );

        try {
            // Send critical notification through NotificationService
            notificationService.sendCriticalNotification(
                department + "_TEAM",
                "�� AUTOMATIC STAY ENFORCED - " + caseNumber,
                message,
                Map.of(
                    "bankruptcyId", bankruptcyCase.getBankruptcyId(),
                    "customerId", customerId,
                    "caseNumber", caseNumber,
                    "chapter", chapter,
                    "filingDate", bankruptcyCase.getFilingDate().toString(),
                    "department", department,
                    "priority", "CRITICAL",
                    "actionRequired", "CEASE_ALL_COLLECTION_ACTIVITIES"
                )
            );

            log.info("STAY_NOTIFICATION_SENT: {} notified for case {}",
                department, bankruptcyCase.getBankruptcyId());

        } catch (Exception e) {
            log.error("STAY_NOTIFICATION_FAILED: Failed to notify {} for case {}: {}",
                department, bankruptcyCase.getBankruptcyId(), e.getMessage(), e);

            // Log the message anyway for audit trail
            log.warn("STAY_NOTIFICATION_MESSAGE: {} - {}", department, message);
        }
    }

    // ========== Helper Classes ==========

    /**
     * Stay enforcement result
     */
    private static class StayEnforcementResult {
        private final int actionCount;
        private final List<String> actions;

        public StayEnforcementResult(int actionCount, List<String> actions) {
            this.actionCount = actionCount;
            this.actions = actions;
        }

        public int getActionCount() {
            return actionCount;
        }

        public List<String> getActions() {
            return actions;
        }
    }
}
