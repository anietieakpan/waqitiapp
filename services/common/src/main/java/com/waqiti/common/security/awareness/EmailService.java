package com.waqiti.common.security.awareness;

import com.waqiti.common.security.awareness.domain.PhishingSimulationCampaign;

import java.util.UUID;

/**
 * Email Service Interface
 *
 * @author Waqiti Platform Team
 */
public interface EmailService {

    /**
     * Send phishing simulation email
     */
    void sendPhishingSimulationEmail(String recipientEmail, PhishingSimulationCampaign campaign);

    /**
     * Send email notification
     */
    void sendEmail(String to, String subject, String body);

    /**
     * Track email open
     */
    void trackEmailOpen(UUID campaignId, UUID employeeId);

    /**
     * Send campaign report to security team
     */
    void sendCampaignReportToSecurityTeam(PhishingSimulationCampaign campaign,
                                          double openRate, double clickRate,
                                          double submitRate, double reportRate);

    /**
     * Send phishing education email to employee
     */
    void sendPhishingEducationEmail(UUID employeeId, String educationContent);

    /**
     * Send security team alert
     */
    void sendSecurityTeamAlert(String subject, String message);
}