package com.waqiti.compliance.fincen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * FinCEN JIRA Integration Service
 *
 * Creates and manages JIRA tickets for manual SAR filings.
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0
 * @since 2025-11-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FincenJiraIntegrationService {

    private final WebClient.Builder webClientBuilder;

    @Value("${jira.api.base-url:https://waqiti.atlassian.net}")
    private String jiraBaseUrl;

    @Value("${jira.api.token:#{null}}")
    private String jiraApiToken;

    @Value("${jira.api.email:#{null}}")
    private String jiraEmail;

    @Value("${jira.project.key:COMPLIANCE}")
    private String jiraProjectKey;

    @Value("${jira.enabled:false}")
    private boolean jiraEnabled;

    /**
     * Create JIRA ticket for manual SAR filing
     *
     * @param sarId SAR ID
     * @param expedited Whether expedited
     * @param failureReason API failure reason
     * @param slaDeadline SLA deadline
     * @return JIRA ticket ID
     */
    public String createManualFilingTicket(
            String sarId,
            boolean expedited,
            String failureReason,
            LocalDateTime slaDeadline) {

        if (!jiraEnabled) {
            log.warn("JIRA integration disabled - generating mock ticket ID for SAR: {}", sarId);
            return generateMockTicketId();
        }

        try {
            log.info("Creating JIRA ticket for manual SAR filing: sarId={}", sarId);

            // JIRA API payload
            Map<String, Object> jiraPayload = Map.of(
                    "fields", Map.of(
                            "project", Map.of("key", jiraProjectKey),
                            "summary", String.format("[URGENT] Manual SAR Filing Required - %s", sarId),
                            "description", buildDescription(sarId, expedited, failureReason, slaDeadline),
                            "issuetype", Map.of("name", "Task"),
                            "priority", Map.of("name", expedited ? "Highest" : "High"),
                            "labels", java.util.List.of("SAR", "ManualFiling", "FinCEN", expedited ? "EXPEDITED" : "STANDARD"),
                            "duedate", slaDeadline.toLocalDate().toString()
                    )
            );

            WebClient webClient = webClientBuilder
                    .baseUrl(jiraBaseUrl)
                    .defaultHeader("Authorization", "Basic " + encodeCredentials())
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            Map<String, Object> response = webClient
                    .post()
                    .uri("/rest/api/3/issue")
                    .bodyValue(jiraPayload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String ticketKey = (String) response.get("key");
            log.info("JIRA ticket created successfully: sarId={}, ticketKey={}", sarId, ticketKey);

            return ticketKey;

        } catch (Exception e) {
            log.error("Failed to create JIRA ticket for SAR: {}", sarId, e);
            // Return mock ID to allow processing to continue
            return generateMockTicketId();
        }
    }

    /**
     * Escalate JIRA ticket priority
     */
    public void escalateTicket(String ticketId, String reason) {
        if (!jiraEnabled) {
            log.warn("JIRA integration disabled - skipping escalation for ticket: {}", ticketId);
            return;
        }

        try {
            log.info("Escalating JIRA ticket: ticketId={}, reason={}", ticketId, reason);

            Map<String, Object> updatePayload = Map.of(
                    "fields", Map.of(
                            "priority", Map.of("name", "Highest")
                    ),
                    "update", Map.of(
                            "comment", java.util.List.of(
                                    Map.of("add", Map.of(
                                            "body", String.format("ESCALATED: %s", reason)
                                    ))
                            )
                    )
            );

            WebClient webClient = webClientBuilder
                    .baseUrl(jiraBaseUrl)
                    .defaultHeader("Authorization", "Basic " + encodeCredentials())
                    .build();

            webClient
                    .put()
                    .uri("/rest/api/3/issue/" + ticketId)
                    .bodyValue(updatePayload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("JIRA ticket escalated successfully: ticketId={}", ticketId);

        } catch (Exception e) {
            log.error("Failed to escalate JIRA ticket: {}", ticketId, e);
        }
    }

    /**
     * Close JIRA ticket after manual filing
     */
    public void closeTicket(String ticketId, String resolution) {
        if (!jiraEnabled) {
            log.warn("JIRA integration disabled - skipping close for ticket: {}", ticketId);
            return;
        }

        try {
            log.info("Closing JIRA ticket: ticketId={}, resolution={}", ticketId, resolution);

            Map<String, Object> transitionPayload = Map.of(
                    "transition", Map.of("id", "31"), // "Done" transition ID (may vary)
                    "fields", Map.of(
                            "resolution", Map.of("name", "Done")
                    ),
                    "update", Map.of(
                            "comment", java.util.List.of(
                                    Map.of("add", Map.of("body", resolution))
                            )
                    )
            );

            WebClient webClient = webClientBuilder
                    .baseUrl(jiraBaseUrl)
                    .defaultHeader("Authorization", "Basic " + encodeCredentials())
                    .build();

            webClient
                    .post()
                    .uri("/rest/api/3/issue/" + ticketId + "/transitions")
                    .bodyValue(transitionPayload)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            log.info("JIRA ticket closed successfully: ticketId={}", ticketId);

        } catch (Exception e) {
            log.error("Failed to close JIRA ticket: {}", ticketId, e);
        }
    }

    // ========== PRIVATE HELPER METHODS ==========

    private String buildDescription(String sarId, boolean expedited, String failureReason, LocalDateTime slaDeadline) {
        return String.format("""
                *URGENT: Manual SAR Filing Required*

                *SAR ID:* %s
                *Priority:* %s
                *SLA Deadline:* %s
                *Failure Reason:* %s

                *Action Required:*
                1. Retrieve SAR XML from manual filing queue
                2. File SAR manually via FinCEN BSA E-Filing portal
                3. Record filing number in system
                4. Update queue entry status to MANUALLY_FILED
                5. Close this ticket

                *Important:*
                - This SAR must be filed before the SLA deadline
                - Failure to file on time may result in regulatory penalties ($25K-$1M)
                - Document all actions taken

                *FinCEN Portal:* https://bsaefiling.fincen.treas.gov/
                """,
                sarId,
                expedited ? "EXPEDITED (24h SLA)" : "STANDARD (72h SLA)",
                slaDeadline,
                failureReason
        );
    }

    private String encodeCredentials() {
        String credentials = jiraEmail + ":" + jiraApiToken;
        return java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private String generateMockTicketId() {
        return "COMPLIANCE-MOCK-" + System.currentTimeMillis();
    }
}
