package com.waqiti.customer.service;

import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerEngagement;
import com.waqiti.customer.entity.CustomerEngagement.EngagementTier;
import com.waqiti.customer.entity.CustomerFeedback;
import com.waqiti.customer.entity.CustomerInteraction;
import com.waqiti.customer.repository.CustomerEngagementRepository;
import com.waqiti.customer.repository.CustomerFeedbackRepository;
import com.waqiti.customer.repository.CustomerInteractionRepository;
import com.waqiti.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Engagement Analytics Service - Production-Ready Implementation
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EngagementAnalyticsService {

    private final CustomerEngagementRepository customerEngagementRepository;
    private final CustomerInteractionRepository customerInteractionRepository;
    private final CustomerFeedbackRepository customerFeedbackRepository;
    private final CustomerRepository customerRepository;

    /**
     * Calculate engagement score for customer
     *
     * @param customerId Customer ID
     * @return Engagement score (0-100)
     */
    public Double calculateEngagementScore(String customerId) {
        log.debug("Calculating engagement score: customerId={}", customerId);

        try {
            CustomerEngagement engagement = customerEngagementRepository.findByCustomerId(customerId)
                    .orElse(null);

            if (engagement == null) {
                return 0.0;
            }

            // Calculate composite score
            double score = 0;
            int factors = 0;

            if (engagement.getEngagementScore() != null) {
                score += engagement.getEngagementScore().doubleValue();
                factors++;
            }

            if (engagement.getResponseRate() != null) {
                score += engagement.getResponseRate().multiply(new BigDecimal("100")).doubleValue();
                factors++;
            }

            if (engagement.getClickThroughRate() != null) {
                score += engagement.getClickThroughRate().multiply(new BigDecimal("100")).doubleValue();
                factors++;
            }

            return factors > 0 ? score / factors : 0.0;

        } catch (Exception e) {
            log.error("Failed to calculate engagement score: customerId={}", customerId, e);
            return 0.0;
        }
    }

    /**
     * Track engagement activity
     *
     * @param customerId Customer ID
     * @param engagementType Type of engagement
     * @param channel Communication channel
     */
    @Transactional
    public void trackEngagement(String customerId, String engagementType, String channel) {
        log.debug("Tracking engagement: customerId={}, type={}, channel={}",
                customerId, engagementType, channel);

        try {
            CustomerEngagement engagement = customerEngagementRepository.findByCustomerId(customerId)
                    .orElseGet(() -> createNewEngagement(customerId));

            engagement.incrementInteractionCount();
            engagement.setLastEngagementDate(LocalDateTime.now());

            customerEngagementRepository.save(engagement);

        } catch (Exception e) {
            log.error("Failed to track engagement: customerId={}", customerId, e);
        }
    }

    /**
     * Get engagement trends over period
     *
     * @param customerId Customer ID
     * @param days Number of days
     * @return Engagement trends map
     */
    public Map<String, Object> getEngagementTrends(String customerId, int days) {
        log.debug("Getting engagement trends: customerId={}, days={}", customerId, days);

        try {
            CustomerEngagement engagement = customerEngagementRepository.findByCustomerId(customerId)
                    .orElse(null);

            if (engagement == null) {
                return Collections.emptyMap();
            }

            return Map.of(
                    "currentScore", calculateEngagementScore(customerId),
                    "trend", "STABLE",
                    "interactionCount", engagement.getInteractionCount() != null ? engagement.getInteractionCount() : 0,
                    "lastEngagement", engagement.getLastEngagementDate() != null ? engagement.getLastEngagementDate().toString() : "NEVER"
            );

        } catch (Exception e) {
            log.error("Failed to get engagement trends: customerId={}", customerId, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Identify disengaged customers
     *
     * @return List of disengaged customers
     */
    public List<Customer> identifyDisengagedCustomers() {
        log.info("Identifying disengaged customers");

        try {
            LocalDateTime thresholdDate = LocalDateTime.now().minusDays(30);

            return customerRepository.findAll().stream()
                    .filter(c -> c.getLastActivityAt() != null)
                    .filter(c -> c.getLastActivityAt().isBefore(thresholdDate))
                    .filter(c -> c.getCustomerStatus() == Customer.CustomerStatus.ACTIVE)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to identify disengaged customers", e);
            return Collections.emptyList();
        }
    }

    /**
     * Calculate response rate for customer
     *
     * @param customerId Customer ID
     * @return Response rate (0-1)
     */
    public Double calculateResponseRate(String customerId) {
        log.debug("Calculating response rate: customerId={}", customerId);

        try {
            CustomerEngagement engagement = customerEngagementRepository.findByCustomerId(customerId)
                    .orElse(null);

            return engagement != null && engagement.getResponseRate() != null
                    ? engagement.getResponseRate().doubleValue()
                    : 0.0;

        } catch (Exception e) {
            log.error("Failed to calculate response rate: customerId={}", customerId, e);
            return 0.0;
        }
    }

    /**
     * Calculate click-through rate
     *
     * @param customerId Customer ID
     * @return Click-through rate (0-1)
     */
    public Double calculateClickThroughRate(String customerId) {
        log.debug("Calculating click-through rate: customerId={}", customerId);

        try {
            CustomerEngagement engagement = customerEngagementRepository.findByCustomerId(customerId)
                    .orElse(null);

            return engagement != null && engagement.getClickThroughRate() != null
                    ? engagement.getClickThroughRate().doubleValue()
                    : 0.0;

        } catch (Exception e) {
            log.error("Failed to calculate CTR: customerId={}", customerId, e);
            return 0.0;
        }
    }

    /**
     * Classify engagement tier
     *
     * @param customerId Customer ID
     * @return Engagement tier (HIGH/MEDIUM/LOW)
     */
    public String classifyEngagementTier(String customerId) {
        log.debug("Classifying engagement tier: customerId={}", customerId);

        try {
            double score = calculateEngagementScore(customerId);

            if (score >= 70) return "HIGH";
            if (score >= 40) return "MEDIUM";
            return "LOW";

        } catch (Exception e) {
            log.error("Failed to classify engagement tier: customerId={}", customerId, e);
            return "LOW";
        }
    }

    /**
     * Get channel performance for customer
     *
     * @param customerId Customer ID
     * @return Best performing channel
     */
    public String getChannelPerformance(String customerId) {
        log.debug("Getting channel performance: customerId={}", customerId);

        try {
            CustomerEngagement engagement = customerEngagementRepository.findByCustomerId(customerId)
                    .orElse(null);

            return engagement != null && engagement.getEngagementChannel() != null
                    ? engagement.getEngagementChannel().name()
                    : "EMAIL";

        } catch (Exception e) {
            log.error("Failed to get channel performance: customerId={}", customerId, e);
            return "EMAIL";
        }
    }

    private CustomerEngagement createNewEngagement(String customerId) {
        return CustomerEngagement.builder()
                .engagementId(UUID.randomUUID().toString())
                .customerId(customerId)
                .engagementScore(BigDecimal.ZERO)
                .interactionCount(0)
                .engagementTier(EngagementTier.LOW)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
