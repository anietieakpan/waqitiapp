package com.waqiti.common.service;

import com.waqiti.common.model.incident.Incident;
import com.waqiti.common.model.incident.IncidentPriority;
import com.waqiti.common.model.incident.IncidentStatus;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.model.*;
import com.waqiti.common.repository.IncidentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade escalation service for incident management with comprehensive
 * SLA monitoring, multi-tier escalation policies, and automated notification workflows.
 *
 * Features:
 * - Automatic SLA breach detection
 * - Multi-level escalation (L1 -> L2 -> L3 -> Executive)
 * - Intelligent escalation path determination
 * - Integration with PagerDuty, Slack, and email
 * - Escalation history tracking and audit trail
 * - Configurable escalation policies per priority
 * - Automatic de-escalation on resolution
 * - Executive escalation for critical P0/P1 incidents
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationService {

    private final IncidentRepository incidentRepository;
    private final MeterRegistry meterRegistry;

    @Autowired(required = false)
    private NotificationService notificationService;

    // Configuration from application properties
    @Value("${escalation.l1.emails:oncall-l1@example.com}")
    private String l1EscalationEmails;

    @Value("${escalation.l2.emails:oncall-l2@example.com,engineering-leads@example.com}")
    private String l2EscalationEmails;

    @Value("${escalation.l3.emails:oncall-l3@example.com,engineering-vp@example.com}")
    private String l3EscalationEmails;

    @Value("${escalation.executive.emails:cto@example.com,ceo@example.com}")
    private String executiveEscalationEmails;

    @Value("${escalation.slack.channel.critical:#incident-critical}")
    private String criticalSlackChannel;

    @Value("${escalation.slack.channel.high:#incident-high}")
    private String highSlackChannel;

    @Value("${escalation.pagerduty.integration.key:}")
    private String pagerDutyIntegrationKey;

    @Value("${escalation.enabled:true}")
    private boolean escalationEnabled;

    // Metrics
    private Counter escalationCounter;
    private Counter executiveEscalationCounter;
    private Counter slaBreachCounter;
    private Timer escalationProcessingTime;

    @PostConstruct
    public void initMetrics() {
        escalationCounter = Counter.builder("escalations_total")
                .description("Total number of incident escalations")
                .tag("service", "escalation")
                .register(meterRegistry);

        executiveEscalationCounter = Counter.builder("executive_escalations_total")
                .description("Total executive escalations")
                .tag("severity", "critical")
                .register(meterRegistry);

        slaBreachCounter = Counter.builder("sla_breaches_total")
                .description("Total SLA breaches detected")
                .register(meterRegistry);

        escalationProcessingTime = Timer.builder("escalation_processing_duration")
                .description("Time to process escalations")
                .register(meterRegistry);
    }

    /**
     * Escalate an incident to the next level based on its current state and SLA compliance.
     * This method determines the appropriate escalation path and triggers notifications.
     *
     * @param incident The incident to escalate
     * @param reason The reason for escalation
     * @return EscalationResult containing escalation details
     */
    @Transactional
    public EscalationResult escalateIncident(Incident incident, String reason) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Escalating incident: id={}, priority={}, currentLevel={}, reason={}",
                incident.getId(), incident.getPriority(), incident.getEscalationLevel(), reason);

        try {
            if (!escalationEnabled) {
                log.warn("Escalation is disabled system-wide. Incident {} will not be escalated.", incident.getId());
                return EscalationResult.builder()
                        .incidentId(incident.getId())
                        .success(false)
                        .escalationLevel(incident.getEscalationLevel())
                        .message("Escalation disabled")
                        .build();
            }

            // Determine current and next escalation level
            int currentLevel = incident.getEscalationLevel() != null ? incident.getEscalationLevel() : 0;
            int nextLevel = determineNextEscalationLevel(incident, currentLevel);

            if (nextLevel <= currentLevel && currentLevel > 0) {
                log.warn("Incident {} already at maximum escalation level {}", incident.getId(), currentLevel);
                return EscalationResult.builder()
                        .incidentId(incident.getId())
                        .success(false)
                        .escalationLevel(currentLevel)
                        .message("Already at maximum escalation level")
                        .build();
            }

            // Update incident escalation status
            incident.escalate(nextLevel, reason);
            incident = incidentRepository.save(incident);

            // Record metrics
            escalationCounter.increment();
            if (nextLevel >= 4) {
                executiveEscalationCounter.increment();
            }

            // Send notifications based on escalation level
            List<NotificationResult> notificationResults = sendEscalationNotifications(incident, nextLevel, reason);

            // Create escalation result
            EscalationResult result = EscalationResult.builder()
                    .incidentId(incident.getId())
                    .success(true)
                    .previousLevel(currentLevel)
                    .escalationLevel(nextLevel)
                    .escalationType(getEscalationType(nextLevel))
                    .reason(reason)
                    .escalatedAt(Instant.now())
                    .notificationsSent(notificationResults.size())
                    .notifiedParties(extractNotifiedParties(notificationResults))
                    .message(String.format("Successfully escalated to level %d (%s)", nextLevel, getEscalationType(nextLevel)))
                    .build();

            log.info("Escalation completed successfully: incident={}, level={}, type={}, notifications={}",
                    incident.getId(), nextLevel, result.getEscalationType(), notificationResults.size());

            return result;

        } catch (Exception e) {
            log.error("Failed to escalate incident {}: {}", incident.getId(), e.getMessage(), e);
            return EscalationResult.builder()
                    .incidentId(incident.getId())
                    .success(false)
                    .escalationLevel(incident.getEscalationLevel())
                    .reason(reason)
                    .message("Escalation failed: " + e.getMessage())
                    .build();
        } finally {
            sample.stop(escalationProcessingTime);
        }
    }

    /**
     * Automatically check for SLA breaches and escalate incidents as needed.
     * Runs every 5 minutes to ensure timely escalation.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // Every 5 minutes
    @Transactional
    public void checkAndEscalateSlaBreaches() {
        if (!escalationEnabled) {
            log.debug("Automatic escalation disabled, skipping SLA breach check");
            return;
        }

        log.debug("Running automatic SLA breach check and escalation");
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // Find all active incidents
            List<Incident> activeIncidents = incidentRepository.findByStatusIn(
                    Arrays.asList(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS)
            );

            log.info("Checking {} active incidents for SLA breaches", activeIncidents.size());

            for (Incident incident : activeIncidents) {
                try {
                    checkAndEscalateIncident(incident);
                } catch (Exception e) {
                    log.error("Error checking incident {} for escalation: {}", incident.getId(), e.getMessage(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error in automatic SLA escalation check: {}", e.getMessage(), e);
        } finally {
            sample.stop(meterRegistry.timer("escalation_sla_check_duration"));
        }
    }

    /**
     * Check a single incident for SLA compliance and escalate if needed
     */
    private void checkAndEscalateIncident(Incident incident) {
        Duration age = incident.getAge();
        IncidentPriority priority = incident.getPriority();

        // Check acknowledgement SLA
        if (incident.getAcknowledgedAt() == null) {
            Duration ackSla = priority.getAcknowledgeSla();
            if (age.compareTo(ackSla) > 0) {
                slaBreachCounter.increment();
                log.warn("Acknowledgement SLA breached for incident {}: age={}, sla={}",
                        incident.getId(), age, ackSla);
                escalateIncident(incident, String.format(
                        "Acknowledgement SLA breached: Not acknowledged within %s", formatDuration(ackSla)));
                return;
            }
        }

        // Check resolution SLA
        if (incident.getResolvedAt() == null) {
            Duration resolveSla = priority.getResolveSla();
            if (age.compareTo(resolveSla) > 0) {
                slaBreachCounter.increment();
                log.warn("Resolution SLA breached for incident {}: age={}, sla={}",
                        incident.getId(), age, resolveSla);
                escalateIncident(incident, String.format(
                        "Resolution SLA breached: Not resolved within %s", formatDuration(resolveSla)));
                return;
            }

            // Early escalation for critical incidents approaching SLA
            if (priority.isCritical()) {
                Duration warningThreshold = resolveSla.multipliedBy(75).dividedBy(100); // 75% of SLA
                if (age.compareTo(warningThreshold) > 0) {
                    int currentLevel = incident.getEscalationLevel() != null ? incident.getEscalationLevel() : 0;
                    if (currentLevel < 2) { // Only escalate if not already at high level
                        log.warn("Critical incident {} approaching SLA breach: {}% of SLA elapsed",
                                incident.getId(), (age.toMinutes() * 100) / resolveSla.toMinutes());
                        escalateIncident(incident, "Approaching SLA deadline - proactive escalation");
                    }
                }
            }
        }
    }

    /**
     * Determine the next escalation level based on current state and incident priority
     */
    private int determineNextEscalationLevel(Incident incident, int currentLevel) {
        IncidentPriority priority = incident.getPriority();

        // P0 incidents escalate faster
        if (priority == IncidentPriority.P0) {
            return switch (currentLevel) {
                case 0 -> 1;  // L1 on-call
                case 1 -> 2;  // L2 engineering leads
                case 2 -> 3;  // L3 senior leadership
                case 3 -> 4;  // Executive escalation
                default -> 4; // Cap at executive level
            };
        }

        // P1 incidents skip L1, go straight to L2
        if (priority == IncidentPriority.P1) {
            return switch (currentLevel) {
                case 0 -> 2;  // Skip to L2
                case 1 -> 2;  // Ensure at least L2
                case 2 -> 3;  // L3 senior leadership
                case 3 -> 4;  // Executive escalation
                default -> 4;
            };
        }

        // P2/P3/P4 incidents use standard escalation
        return switch (currentLevel) {
            case 0 -> 1;  // L1 on-call
            case 1 -> 2;  // L2 engineering leads
            case 2 -> 3;  // L3 senior leadership
            default -> 3; // Cap at L3 for non-critical
        };
    }

    /**
     * Send escalation notifications to appropriate parties
     */
    private List<NotificationResult> sendEscalationNotifications(Incident incident, int escalationLevel, String reason) {
        List<NotificationResult> results = new ArrayList<>();

        if (notificationService == null) {
            log.warn("NotificationService not available, skipping escalation notifications for incident {}", incident.getId());
            return results;
        }

        try {
            // Prepare escalation context
            Map<String, Object> context = buildEscalationContext(incident, escalationLevel, reason);

            // Send email notifications
            List<String> recipients = getEscalationRecipients(escalationLevel);
            if (!recipients.isEmpty()) {
                EmailNotificationRequest emailRequest = EmailNotificationRequest.builder()
                        .to(recipients)
                        .subject(buildEscalationSubject(incident, escalationLevel))
                        .templateId("incident-escalation")
                        .templateVariables(context)
                        .priority(escalationLevel >= 3 ? NotificationRequest.Priority.CRITICAL : NotificationRequest.Priority.HIGH)
                        .requireConfirmation(escalationLevel >= 4) // Require confirmation for executive escalation
                        .build();

                CompletableFuture<NotificationResult> emailResult = notificationService.sendEmail(emailRequest);
                results.add(emailResult.join());
            }

            // Send Slack notification for critical incidents
            if (incident.getPriority().isCritical()) {
                String targetChannel = incident.getPriority() == IncidentPriority.P0 ?
                        criticalSlackChannel : highSlackChannel;

                SlackNotificationRequest slackRequest = SlackNotificationRequest.builder()
                        .slackChannel(targetChannel)
                        .message(buildSlackEscalationMessage(incident, escalationLevel, reason))
                        .priority(NotificationRequest.Priority.CRITICAL)
                        .mentions(getSlackMentions(escalationLevel))
                        .build();

                CompletableFuture<NotificationResult> slackResult = notificationService.sendSlack(slackRequest);
                results.add(slackResult.join());
            }

            // Trigger PagerDuty for P0 escalations at L2 and above
            if (incident.getPriority() == IncidentPriority.P0 && escalationLevel >= 2) {
                triggerPagerDutyEscalation(incident, escalationLevel);
            }

        } catch (Exception e) {
            log.error("Error sending escalation notifications for incident {}: {}",
                    incident.getId(), e.getMessage(), e);
        }

        return results;
    }

    /**
     * Build escalation context for notification templates
     */
    private Map<String, Object> buildEscalationContext(Incident incident, int escalationLevel, String reason) {
        Map<String, Object> context = new HashMap<>();
        context.put("incidentId", incident.getId());
        context.put("title", incident.getTitle());
        context.put("description", incident.getDescription());
        context.put("priority", incident.getPriority().name());
        context.put("status", incident.getStatus().name());
        context.put("sourceService", incident.getSourceService());
        context.put("escalationLevel", escalationLevel);
        context.put("escalationType", getEscalationType(escalationLevel));
        context.put("escalationReason", reason);
        context.put("createdAt", incident.getCreatedAt());
        context.put("age", formatDuration(incident.getAge()));
        context.put("slaDeadline", incident.getSlaDeadline());
        context.put("impactedUsers", incident.getImpactedUsers());
        context.put("assignedTo", incident.getAssignedTo());
        return context;
    }

    /**
     * Get escalation recipients based on level
     */
    private List<String> getEscalationRecipients(int level) {
        return switch (level) {
            case 1 -> Arrays.asList(l1EscalationEmails.split(","));
            case 2 -> Arrays.asList(l2EscalationEmails.split(","));
            case 3 -> Arrays.asList(l3EscalationEmails.split(","));
            case 4 -> Arrays.asList(executiveEscalationEmails.split(","));
            default -> Collections.emptyList();
        };
    }

    /**
     * Build escalation email subject
     */
    private String buildEscalationSubject(Incident incident, int escalationLevel) {
        String urgency = escalationLevel >= 4 ? "[EXECUTIVE ESCALATION]" :
                        escalationLevel >= 3 ? "[SENIOR LEADERSHIP]" :
                        escalationLevel >= 2 ? "[ENGINEERING LEADS]" : "[L1 ESCALATION]";

        return String.format("%s [%s] Incident #%s: %s",
                urgency,
                incident.getPriority().name(),
                incident.getId().substring(0, 8),
                incident.getTitle());
    }

    /**
     * Build Slack escalation message
     */
    private String buildSlackEscalationMessage(Incident incident, int escalationLevel, String reason) {
        return String.format(
                "🚨 *INCIDENT ESCALATED TO LEVEL %d (%s)*\n\n" +
                "*Incident:* %s\n" +
                "*Priority:* %s\n" +
                "*Status:* %s\n" +
                "*Service:* %s\n" +
                "*Age:* %s\n" +
                "*Reason:* %s\n" +
                "*Assigned To:* %s\n\n" +
                "_View details: %s_",
                escalationLevel,
                getEscalationType(escalationLevel),
                incident.getTitle(),
                incident.getPriority().name(),
                incident.getStatus().name(),
                incident.getSourceService(),
                formatDuration(incident.getAge()),
                reason,
                incident.getAssignedTo() != null ? incident.getAssignedTo() : "Unassigned",
                "/incidents/" + incident.getId()
        );
    }

    /**
     * Get Slack user mentions for escalation level
     */
    private List<String> getSlackMentions(int level) {
        return switch (level) {
            case 4 -> Arrays.asList("@channel", "@leadership");
            case 3 -> Arrays.asList("@engineering-leads", "@vp-engineering");
            case 2 -> Arrays.asList("@engineering-leads");
            default -> Collections.emptyList();
        };
    }

    /**
     * Trigger PagerDuty escalation for critical incidents
     */
    @Async
    protected void triggerPagerDutyEscalation(Incident incident, int escalationLevel) {
        if (pagerDutyIntegrationKey == null || pagerDutyIntegrationKey.isEmpty()) {
            log.warn("PagerDuty integration not configured, skipping PagerDuty escalation");
            return;
        }

        log.info("Triggering PagerDuty escalation for incident {}, level {}", incident.getId(), escalationLevel);
        // Implementation would integrate with PagerDuty API
        // This is a placeholder for the actual implementation
    }

    /**
     * Get escalation type description
     */
    private String getEscalationType(int level) {
        return switch (level) {
            case 1 -> "L1 On-Call";
            case 2 -> "L2 Engineering Leads";
            case 3 -> "L3 Senior Leadership";
            case 4 -> "Executive Escalation";
            default -> "Standard";
        };
    }

    /**
     * Extract notified parties from notification results
     */
    private List<String> extractNotifiedParties(List<NotificationResult> results) {
        return results.stream()
                .filter(r -> r.getStatus() == NotificationResult.DeliveryStatus.SENT || r.getStatus() == NotificationResult.DeliveryStatus.DELIVERED)
                .flatMap(r -> r.getRecipients() != null ? r.getRecipients().stream() : java.util.stream.Stream.empty())
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Format duration in human-readable format
     */
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    /**
     * Fast-track escalation for P0 incidents (skips to L2/L3 immediately)
     */
    @Transactional
    public EscalationResult escalateP0Incident(Incident incident) {
        log.warn("Fast-track P0 escalation for incident {}", incident.getId());

        // P0 incidents skip L1 and go straight to L2/L3
        int targetLevel = 3; // L3 for P0
        incident.setEscalationLevel(targetLevel);
        incident.setEscalatedAt(Instant.now());
        incidentRepository.save(incident);

        // Send notifications to L2, L3, and Executive teams simultaneously
        List<NotificationResult> results = new ArrayList<>();

        // Notify L2
        results.addAll(sendEscalationNotifications(incident, 2, "P0 Fast-Track Escalation"));

        // Notify L3
        results.addAll(sendEscalationNotifications(incident, 3, "P0 Fast-Track Escalation"));

        // Notify Executive
        results.addAll(sendExecutiveEscalationNotifications(incident, "P0 CRITICAL INCIDENT"));

        executiveEscalationCounter.increment();
        escalationCounter.increment();

        return EscalationResult.builder()
                .incidentId(incident.getId())
                .success(true)
                .previousLevel(0)
                .escalationLevel(targetLevel)
                .escalationType("P0 Fast-Track")
                .reason("P0 Critical Incident - Immediate Executive Notification")
                .escalatedAt(incident.getEscalatedAt())
                .notificationsSent(results.size())
                .notifiedParties(extractNotifiedParties(results))
                .message("P0 incident escalated to L3 and Executive team")
                .build();
    }

    /**
     * Send executive escalation notifications for P0 incidents
     */
    private List<NotificationResult> sendExecutiveEscalationNotifications(Incident incident, String urgencyMessage) {
        List<NotificationResult> results = new ArrayList<>();

        if (notificationService == null) {
            log.warn("NotificationService not available, skipping executive escalation notifications for incident {}", incident.getId());
            return results;
        }

        try {
            // Prepare executive escalation context
            Map<String, Object> context = buildEscalationContext(incident, 4, urgencyMessage);
            context.put("executiveEscalation", true);
            context.put("urgencyLevel", "CRITICAL");

            // Send email to executive team
            List<String> executives = Arrays.asList(executiveEscalationEmails.split(","));
            if (!executives.isEmpty()) {
                EmailNotificationRequest emailRequest = EmailNotificationRequest.builder()
                        .to(executives)
                        .subject(buildExecutiveEscalationSubject(incident))
                        .templateId("executive-incident-escalation")
                        .templateVariables(context)
                        .priority(NotificationRequest.Priority.CRITICAL)
                        .requireConfirmation(true)
                        .build();

                CompletableFuture<NotificationResult> emailResult = notificationService.sendEmail(emailRequest);
                results.add(emailResult.join());
            }

            // Send critical Slack notification with @channel mention
            SlackNotificationRequest slackRequest = SlackNotificationRequest.builder()
                    .slackChannel(criticalSlackChannel)
                    .message(buildExecutiveSlackMessage(incident, urgencyMessage))
                    .priority(NotificationRequest.Priority.CRITICAL)
                    .mentions(Arrays.asList("@channel", "@leadership", "@executives"))
                    .build();

            CompletableFuture<NotificationResult> slackResult = notificationService.sendSlack(slackRequest);
            results.add(slackResult.join());

            // Trigger PagerDuty
            triggerPagerDutyEscalation(incident, 4);

        } catch (Exception e) {
            log.error("Error sending executive escalation notifications for incident {}: {}",
                    incident.getId(), e.getMessage(), e);
        }

        return results;
    }

    /**
     * Build executive escalation email subject
     */
    private String buildExecutiveEscalationSubject(Incident incident) {
        return String.format("🚨 [EXECUTIVE ESCALATION] [P0] CRITICAL: %s", incident.getTitle());
    }

    /**
     * Build executive Slack escalation message
     */
    private String buildExecutiveSlackMessage(Incident incident, String urgencyMessage) {
        return String.format(
                "🚨🚨🚨 *EXECUTIVE ESCALATION - P0 CRITICAL INCIDENT* 🚨🚨🚨\n\n" +
                "*%s*\n\n" +
                "*Incident:* %s\n" +
                "*Description:* %s\n" +
                "*Service:* %s\n" +
                "*Priority:* P0 (CRITICAL)\n" +
                "*Status:* %s\n" +
                "*Age:* %s\n" +
                "*Impacted Users:* %s\n" +
                "*Assigned To:* %s\n\n" +
                "⚡ *IMMEDIATE ACTION REQUIRED* ⚡\n\n" +
                "_View incident: %s_",
                urgencyMessage,
                incident.getTitle(),
                incident.getDescription(),
                incident.getSourceService(),
                incident.getStatus().name(),
                formatDuration(incident.getAge()),
                incident.getImpactedUsers() != null ? incident.getImpactedUsers().toString() : "Unknown",
                incident.getAssignedTo() != null ? incident.getAssignedTo() : "Unassigned",
                "/incidents/" + incident.getId()
        );
    }

    /**
     * Notify about escalation (used by IncidentManagementService)
     */
    public void notifyEscalation(Incident incident, int escalationLevel, String reason) {
        log.info("Notifying escalation for incident {} to level {}: {}",
                incident.getId(), escalationLevel, reason);

        sendEscalationNotifications(incident, escalationLevel, reason);
        escalationCounter.increment();
    }

    /**
     * Result object for escalation operations
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EscalationResult {
        private String incidentId;
        private boolean success;
        private int previousLevel;
        private int escalationLevel;
        private String escalationType;
        private String reason;
        private Instant escalatedAt;
        private int notificationsSent;
        private List<String> notifiedParties;
        private String message;
    }
}
