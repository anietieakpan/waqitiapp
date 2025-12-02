package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralClick;
import com.waqiti.rewards.repository.ReferralClickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for referral click analytics
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralClickAnalyticsService {

    private final ReferralClickRepository clickRepository;
    private final ReferralLinkService linkService;
    private final com.waqiti.rewards.publisher.ReferralEventPublisher eventPublisher;

    /**
     * Records a click with full analytics
     */
    @Transactional
    public ReferralClick recordClick(String referralCode, String ipAddress, String userAgent,
                                     String deviceType, String browser, String countryCode) {
        log.debug("Recording click: code={}, ip={}, country={}", referralCode, ipAddress, countryCode);

        String clickId = "CLK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Check if unique click
        boolean isUnique = !clickRepository.existsByIpAddressAndLinkIdAndClickedAtAfter(
                ipAddress,
                referralCode,
                LocalDateTime.now().minusHours(24)
        );

        ReferralClick click = ReferralClick.builder()
                .clickId(clickId)
                .linkId(referralCode)
                .referralCode(referralCode)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceType(deviceType)
                .browser(browser)
                .countryCode(countryCode)
                .isUniqueClick(isUnique)
                .isBot(isPotentialBot(userAgent))
                .build();

        ReferralClick saved = clickRepository.save(click);

        // Update link metrics
        linkService.recordClick(referralCode, isUnique);

        // Publish click event
        publishClickEvent(saved);

        log.debug("Recorded click: clickId={}, unique={}", clickId, isUnique);
        return saved;
    }

    /**
     * Publish click event
     */
    private void publishClickEvent(ReferralClick click) {
        try {
            // Get link to find referrer ID
            var link = linkService.getLinkByCode(click.getReferralCode());

            var event = com.waqiti.rewards.event.ReferralClickedEvent.create(
                    click.getClickId(),
                    click.getLinkId(),
                    click.getReferralCode(),
                    link.getUserId(),
                    click.getIpAddress(),
                    click.getUserAgent(),
                    click.getDeviceType(),
                    click.getBrowser(),
                    click.getCountryCode(),
                    click.getIsUniqueClick(),
                    org.slf4j.MDC.get("correlation_id")
            );

            eventPublisher.publishLinkClicked(event);
        } catch (Exception e) {
            log.error("Failed to publish click event: clickId={}", click.getClickId(), e);
        }
    }

    /**
     * Gets analytics for a link
     */
    public Map<String, Object> getLinkAnalytics(String linkId) {
        Map<String, Object> analytics = new HashMap<>();

        Long totalClicks = clickRepository.getTotalClicksForCode(linkId);
        Long conversions = clickRepository.getConversionCountForCode(linkId);
        Double conversionRate = clickRepository.getConversionRateForLink(linkId);
        Double avgTime = clickRepository.getAverageTimeToConversionHours(linkId);

        analytics.put("total_clicks", totalClicks);
        analytics.put("conversions", conversions);
        analytics.put("conversion_rate", conversionRate != null ? conversionRate : 0.0);
        analytics.put("avg_time_to_conversion_hours", avgTime != null ? avgTime : 0.0);

        // Get geographic distribution
        List<Object[]> byCountry = clickRepository.getClicksByCountry(linkId);
        Map<String, Long> countryMap = new HashMap<>();
        for (Object[] row : byCountry) {
            countryMap.put((String) row[0], ((Number) row[1]).longValue());
        }
        analytics.put("by_country", countryMap);

        // Get device distribution
        List<Object[]> byDevice = clickRepository.getClicksByDeviceType(linkId);
        Map<String, Long> deviceMap = new HashMap<>();
        for (Object[] row : byDevice) {
            deviceMap.put((String) row[0], ((Number) row[1]).longValue());
        }
        analytics.put("by_device", deviceMap);

        return analytics;
    }

    /**
     * Detects suspicious click patterns
     */
    public List<Object[]> detectSuspiciousPatterns(int hours, long threshold) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return clickRepository.findSuspiciousClickPatterns(since, threshold);
    }

    /**
     * Simple bot detection
     */
    private boolean isPotentialBot(String userAgent) {
        if (userAgent == null) return false;
        String lower = userAgent.toLowerCase();
        return lower.contains("bot") || lower.contains("crawler") ||
               lower.contains("spider") || lower.contains("scraper");
    }
}
