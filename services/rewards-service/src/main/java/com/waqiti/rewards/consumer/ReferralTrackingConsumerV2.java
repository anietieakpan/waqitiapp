package com.waqiti.rewards.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.rewards.domain.ReferralClick;
import com.waqiti.rewards.service.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Modern referral tracking consumer using service layer
 * Replaces the legacy monolithic ReferralTrackingConsumer
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferralTrackingConsumerV2 {

    private final ReferralLinkService linkService;
    private final ReferralClickAnalyticsService analyticsService;
    private final ReferralCampaignService campaignService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private Counter clicksTrackedCounter;
    private Counter campaignEventsCounter;
    private Counter failedCounter;
    private Timer processingTimer;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        clicksTrackedCounter = Counter.builder("referral.click.tracked")
                .description("Referral clicks tracked")
                .register(meterRegistry);

        campaignEventsCounter = Counter.builder("referral.campaign.events")
                .description("Referral campaign events processed")
                .register(meterRegistry);

        failedCounter = Counter.builder("referral.tracking.failed")
                .description("Referral tracking failures")
                .register(meterRegistry);

        processingTimer = Timer.builder("referral.tracking.processing.time")
                .description("Time to process tracking event")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "referral-tracking",
            groupId = "rewards-service-referral-tracking-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleTrackingEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation_id", required = false) byte[] correlationIdBytes,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = correlationIdBytes != null
                ? new String(correlationIdBytes, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();

        MDC.put("correlation_id", correlationId);
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));

        String eventType = null;

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            eventType = (String) event.get("eventType");

            MDC.put("event_type", eventType);

            log.debug("Processing tracking event: type={}", eventType);

            boolean processed = switch (eventType) {
                case "REFERRAL_LINK_CLICKED" -> handleLinkClicked(event);
                case "REFERRAL_CODE_CREATED" -> handleCodeCreated(event);
                case "REFERRAL_CODE_SHARED" -> handleCodeShared(event);
                case "CAMPAIGN_ENROLLED" -> handleCampaignEnrolled(event);
                case "CAMPAIGN_REFERRAL" -> handleCampaignReferral(event);
                default -> {
                    log.warn("Unknown tracking event type: {}", eventType);
                    yield false;
                }
            };

            if (processed) {
                acknowledgment.acknowledge();
            } else {
                log.warn("Event processing returned false for type: {}", eventType);
                acknowledgment.acknowledge(); // Still acknowledge to prevent blocking
            }

        } catch (Exception e) {
            log.error("Failed to process tracking event: eventType={}, error={}",
                    eventType, e.getMessage(), e);
            failedCounter.increment();

            acknowledgment.acknowledge();

        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean handleLinkClicked(Map<String, Object> event) {
        try {
            String referralCode = (String) event.get("referralCode");
            String ipAddress = (String) event.get("ipAddress");
            String userAgent = (String) event.get("userAgent");
            String deviceType = (String) event.getOrDefault("deviceType", "UNKNOWN");
            String browser = (String) event.getOrDefault("browser", "UNKNOWN");
            String countryCode = (String) event.get("countryCode");

            log.info("Recording click: code={}, ip={}, device={}", referralCode, ipAddress, deviceType);

            ReferralClick click = analyticsService.recordClick(
                    referralCode,
                    ipAddress,
                    userAgent,
                    deviceType,
                    browser,
                    countryCode
            );

            clicksTrackedCounter.increment();

            log.debug("Click recorded: clickId={}, unique={}", click.getClickId(), click.getIsUniqueClick());

            return true;

        } catch (Exception e) {
            log.error("Failed to handle link click", e);
            return false;
        }
    }

    private boolean handleCodeCreated(Map<String, Object> event) {
        try {
            String userId = (String) event.get("userId");
            String programId = (String) event.get("programId");
            String channel = (String) event.getOrDefault("channel", "APP");

            log.info("Creating referral link: userId={}, programId={}", userId, programId);

            var link = linkService.createLink(
                    UUID.fromString(userId),
                    programId,
                    channel
            );

            log.info("Referral link created: linkId={}, code={}", link.getLinkId(), link.getReferralCode());

            auditService.auditEvent(
                    "REFERRAL_CODE_CREATED",
                    userId,
                    String.format("Referral code created: %s", link.getReferralCode()),
                    Map.of(
                            "link_id", link.getLinkId(),
                            "referral_code", link.getReferralCode(),
                            "program_id", programId,
                            "channel", channel
                    )
            );

            return true;

        } catch (Exception e) {
            log.error("Failed to handle code creation", e);
            return false;
        }
    }

    private boolean handleCodeShared(Map<String, Object> event) {
        try {
            String linkId = (String) event.get("linkId");
            String platform = (String) event.get("platform");
            String sharedBy = (String) event.get("sharedBy");

            log.info("Referral code shared: linkId={}, platform={}, user={}",
                    linkId, platform, sharedBy);

            // Could track sharing analytics here if needed
            auditService.auditEvent(
                    "REFERRAL_CODE_SHARED",
                    sharedBy,
                    String.format("Referral code shared on %s", platform),
                    Map.of(
                            "link_id", linkId,
                            "platform", platform
                    )
            );

            return true;

        } catch (Exception e) {
            log.error("Failed to handle code shared", e);
            return false;
        }
    }

    private boolean handleCampaignEnrolled(Map<String, Object> event) {
        try {
            String userId = (String) event.get("userId");
            String campaignId = (String) event.get("campaignId");

            log.info("User enrolled in campaign: userId={}, campaignId={}", userId, campaignId);

            // Campaign enrollment is typically done via API, but we can audit it
            auditService.auditEvent(
                    "CAMPAIGN_ENROLLED",
                    userId,
                    String.format("Enrolled in campaign: %s", campaignId),
                    Map.of("campaign_id", campaignId)
            );

            campaignEventsCounter.increment();

            return true;

        } catch (Exception e) {
            log.error("Failed to handle campaign enrollment", e);
            return false;
        }
    }

    private boolean handleCampaignReferral(Map<String, Object> event) {
        try {
            String campaignId = (String) event.get("campaignId");
            String referrerId = (String) event.get("referrerId");
            String refereeId = (String) event.get("refereeId");

            log.info("Campaign referral: campaign={}, referrer={}, referee={}",
                    campaignId, referrerId, refereeId);

            campaignService.recordReferral(campaignId);

            campaignEventsCounter.increment();

            auditService.auditEvent(
                    "CAMPAIGN_REFERRAL",
                    referrerId,
                    String.format("Campaign referral completed: %s", campaignId),
                    Map.of(
                            "campaign_id", campaignId,
                            "referee_id", refereeId
                    )
            );

            return true;

        } catch (Exception e) {
            log.error("Failed to handle campaign referral", e);
            return false;
        }
    }
}
