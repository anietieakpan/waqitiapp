package com.waqiti.common.security.awareness;

import com.waqiti.common.security.awareness.model.SecurityTrainingModule;
import com.waqiti.common.security.awareness.dto.QuestionResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Email Notification Service Interface
 *
 * @author Waqiti Platform Team
 */
public interface EmailNotificationService {

    /**
     * Send training reminder
     */
    void sendTrainingReminder(String email, List<SecurityTrainingModule> overdueModules);

    /**
     * Send assessment completion notification
     */
    void sendAssessmentCompletionNotification(String email, UUID assessmentId, BigDecimal score, boolean passed);

    /**
     * Send phishing test results
     */
    void sendPhishingTestResults(String email, boolean passed, String campaignName);

    /**
     * Send assessment results with detailed feedback
     */
    void sendAssessmentResultsWithFeedback(UUID employeeId, UUID assessmentId,
                                           BigDecimal score, List<QuestionResult> results);

    /**
     * Send compliance alert
     */
    void sendComplianceAlert(UUID employeeId, String alertMessage);

    /**
     * Send new employee training email with assigned modules
     */
    void sendNewEmployeeTrainingEmail(String email, List<SecurityTrainingModule> mandatoryModules);

    /**
     * Send annual training reminder email
     */
    void sendAnnualTrainingReminderEmail(UUID employeeId, long overdueCount, BigDecimal completionRate);

    /**
     * Send generic email notification
     */
    void sendEmail(String email, String subject, Object... params);

    /**
     * Send assessment notification to employee
     */
    void sendAssessmentNotification(String email, String assessmentTitle, int quarter, int year, java.time.LocalDateTime deadline);
}