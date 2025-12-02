package com.waqiti.customer.service;

import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerLifecycle;
import com.waqiti.customer.entity.CustomerLifecycle.LifecycleStage;
import com.waqiti.customer.repository.CustomerLifecycleRepository;
import com.waqiti.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Customer Lifecycle Service - Production-Ready Implementation
 *
 * Handles customer lifecycle management including:
 * - Lifecycle stage transitions
 * - Churn probability tracking
 * - At-risk customer identification
 * - Dormant customer detection
 * - Loyalty program promotions
 * - Engagement tracking
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerLifecycleService {

    private final CustomerLifecycleRepository customerLifecycleRepository;
    private final CustomerRepository customerRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RetentionService retentionService;

    private static final String LIFECYCLE_EVENTS_TOPIC = "customer-lifecycle-events";
    private static final String LIFECYCLE_TRANSITION_TOPIC = "customer-lifecycle-transitions";
    private static final int AT_RISK_DAYS_INACTIVE = 21;
    private static final int DORMANT_DAYS_THRESHOLD = 60;
    private static final BigDecimal HIGH_CHURN_THRESHOLD = new BigDecimal("0.70");

    /**
     * Process lifecycle stage transition for a customer
     *
     * @param customerId Customer ID
     * @param newStage New lifecycle stage
     */
    @Transactional
    public void processLifecycleTransition(String customerId, LifecycleStage newStage) {
        log.info("Processing lifecycle transition: customerId={}, newStage={}", customerId, newStage);

        try {
            // Get or create customer lifecycle record
            CustomerLifecycle lifecycle = customerLifecycleRepository.findByCustomerId(customerId)
                    .orElseGet(() -> createNewLifecycle(customerId, newStage));

            // Store previous stage
            LifecycleStage previousStage = lifecycle.getLifecycleStage();

            // Check if this is actually a transition
            if (previousStage == newStage) {
                log.debug("Customer already in stage {}, skipping transition", newStage);
                return;
            }

            // Update lifecycle stage
            lifecycle.setPreviousStage(previousStage);
            lifecycle.setLifecycleStage(newStage);
            lifecycle.setStageEnteredAt(LocalDateTime.now());
            lifecycle.setStageDurationDays(0);

            // Calculate days in previous stage
            if (previousStage != null && lifecycle.getStageEnteredAt() != null) {
                long daysInPrevious = ChronoUnit.DAYS.between(
                        lifecycle.getStageEnteredAt(), LocalDateTime.now());
                lifecycle.setStageDurationDays((int) daysInPrevious);
            }

            // Update engagement level based on new stage
            updateEngagementLevel(lifecycle, newStage);

            // Save transition
            customerLifecycleRepository.save(lifecycle);

            // Publish lifecycle transition event
            publishLifecycleTransitionEvent(customerId, previousStage, newStage);

            // Handle stage-specific actions
            handleStageSpecificActions(customerId, newStage);

            log.info("Lifecycle transition completed: customerId={}, {} -> {}",
                    customerId, previousStage, newStage);

        } catch (Exception e) {
            log.error("Failed to process lifecycle transition: customerId={}, newStage={}",
                    customerId, newStage, e);
            throw new RuntimeException("Failed to process lifecycle transition", e);
        }
    }

    /**
     * Identify at-risk customers who haven't been active recently
     *
     * @return List of at-risk customers
     */
    public List<Customer> identifyAtRiskCustomers() {
        log.info("Identifying at-risk customers");

        try {
            LocalDateTime thresholdDate = LocalDateTime.now().minusDays(AT_RISK_DAYS_INACTIVE);

            // Find customers with recent activity below threshold
            List<Customer> atRiskCustomers = customerRepository.findAll().stream()
                    .filter(customer -> customer.getLastActivityAt() != null)
                    .filter(customer -> customer.getLastActivityAt().isBefore(thresholdDate))
                    .filter(customer -> customer.getCustomerStatus() == Customer.CustomerStatus.ACTIVE)
                    .collect(Collectors.toList());

            log.info("Identified {} at-risk customers", atRiskCustomers.size());

            // Mark these customers as at-risk in lifecycle
            for (Customer customer : atRiskCustomers) {
                markAsAtRisk(customer.getCustomerId());
            }

            return atRiskCustomers;

        } catch (Exception e) {
            log.error("Failed to identify at-risk customers", e);
            return Collections.emptyList();
        }
    }

    /**
     * Identify dormant customers inactive for specified days
     *
     * @param days Number of days of inactivity
     * @return List of dormant customers
     */
    public List<Customer> identifyDormantCustomers(int days) {
        log.info("Identifying dormant customers inactive for {} days", days);

        try {
            LocalDateTime thresholdDate = LocalDateTime.now().minusDays(days);

            List<Customer> dormantCustomers = customerRepository.findAll().stream()
                    .filter(customer -> customer.getLastActivityAt() != null)
                    .filter(customer -> customer.getLastActivityAt().isBefore(thresholdDate))
                    .filter(customer -> customer.getCustomerStatus() == Customer.CustomerStatus.ACTIVE)
                    .collect(Collectors.toList());

            log.info("Identified {} dormant customers", dormantCustomers.size());

            // Mark these customers as dormant in lifecycle
            for (Customer customer : dormantCustomers) {
                processLifecycleTransition(customer.getCustomerId(), LifecycleStage.DORMANT);
            }

            return dormantCustomers;

        } catch (Exception e) {
            log.error("Failed to identify dormant customers", e);
            return Collections.emptyList();
        }
    }

    /**
     * Update churn probability for a customer
     *
     * @param customerId Customer ID
     */
    @Transactional
    public void updateChurnProbability(String customerId) {
        log.debug("Updating churn probability: customerId={}", customerId);

        try {
            CustomerLifecycle lifecycle = customerLifecycleRepository.findByCustomerId(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Lifecycle not found: " + customerId));

            // Calculate churn probability using retention service
            BigDecimal churnProbability = retentionService.calculateChurnProbability(customerId);

            // Update lifecycle record
            lifecycle.updateChurnProbability(churnProbability);
            customerLifecycleRepository.save(lifecycle);

            // Alert if high churn risk
            if (churnProbability.compareTo(HIGH_CHURN_THRESHOLD) > 0) {
                log.warn("High churn risk detected: customerId={}, probability={}",
                        customerId, churnProbability);
                processLifecycleTransition(customerId, LifecycleStage.AT_RISK);
            }

            log.info("Churn probability updated: customerId={}, probability={}",
                    customerId, churnProbability);

        } catch (Exception e) {
            log.error("Failed to update churn probability: customerId={}", customerId, e);
        }
    }

    /**
     * Calculate lifecycle score for a customer
     *
     * @param customerId Customer ID
     * @return Lifecycle score (0-100)
     */
    public Double calculateLifecycleScore(String customerId) {
        log.debug("Calculating lifecycle score: customerId={}", customerId);

        try {
            CustomerLifecycle lifecycle = customerLifecycleRepository.findByCustomerId(customerId)
                    .orElse(null);

            if (lifecycle == null) {
                return 50.0; // Default neutral score
            }

            // Score components
            double stageScore = getStageScore(lifecycle.getLifecycleStage());
            double engagementScore = getEngagementScore(lifecycle.getEngagementLevel());
            double churnScore = lifecycle.getChurnProbability() != null
                    ? (1 - lifecycle.getChurnProbability().doubleValue()) * 100
                    : 50.0;

            // Weighted average
            double lifecycleScore = (stageScore * 0.4) + (engagementScore * 0.3) + (churnScore * 0.3);

            log.debug("Lifecycle score calculated: customerId={}, score={}",
                    customerId, lifecycleScore);

            return lifecycleScore;

        } catch (Exception e) {
            log.error("Failed to calculate lifecycle score: customerId={}", customerId, e);
            return 50.0;
        }
    }

    /**
     * Get customer lifecycle record
     *
     * @param customerId Customer ID
     * @return CustomerLifecycle
     */
    public CustomerLifecycle getCustomerLifecycle(String customerId) {
        log.debug("Getting customer lifecycle: customerId={}", customerId);

        return customerLifecycleRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Lifecycle not found: " + customerId));
    }

    /**
     * Track when customer enters a new lifecycle stage
     *
     * @param customerId Customer ID
     * @param stage Lifecycle stage
     */
    @Transactional
    public void trackStageEntry(String customerId, LifecycleStage stage) {
        log.info("Tracking stage entry: customerId={}, stage={}", customerId, stage);

        try {
            CustomerLifecycle lifecycle = customerLifecycleRepository.findByCustomerId(customerId)
                    .orElseGet(() -> createNewLifecycle(customerId, stage));

            lifecycle.setLifecycleStage(stage);
            lifecycle.setStageEnteredAt(LocalDateTime.now());
            lifecycle.setStageDurationDays(0);

            customerLifecycleRepository.save(lifecycle);

            // Publish stage entry event
            Map<String, Object> event = Map.of(
                    "eventType", "STAGE_ENTRY",
                    "customerId", customerId,
                    "stage", stage.name(),
                    "enteredAt", LocalDateTime.now().toString()
            );

            kafkaTemplate.send(LIFECYCLE_EVENTS_TOPIC, customerId, event);

            log.info("Stage entry tracked: customerId={}, stage={}", customerId, stage);

        } catch (Exception e) {
            log.error("Failed to track stage entry: customerId={}, stage={}", customerId, stage, e);
            throw new RuntimeException("Failed to track stage entry", e);
        }
    }

    /**
     * Promote loyal customers to higher tiers or lifecycle stages
     */
    @Transactional
    public void promoteLoyalCustomers() {
        log.info("Promoting loyal customers");

        try {
            // Find customers eligible for promotion
            List<CustomerLifecycle> eligibleCustomers = customerLifecycleRepository.findAll().stream()
                    .filter(this::isEligibleForPromotion)
                    .collect(Collectors.toList());

            int promotedCount = 0;

            for (CustomerLifecycle lifecycle : eligibleCustomers) {
                try {
                    // Promote to higher tier
                    processLifecycleTransition(lifecycle.getCustomerId(), LifecycleStage.ACTIVE);

                    // Update customer segment if applicable
                    Customer customer = customerRepository.findByCustomerId(lifecycle.getCustomerId())
                            .orElse(null);

                    if (customer != null) {
                        updateCustomerSegmentForPromotion(customer);
                    }

                    promotedCount++;

                } catch (Exception e) {
                    log.error("Failed to promote customer: customerId={}",
                            lifecycle.getCustomerId(), e);
                }
            }

            log.info("Promoted {} loyal customers", promotedCount);

        } catch (Exception e) {
            log.error("Failed to promote loyal customers", e);
        }
    }

    // ==================== Private Helper Methods ====================

    private CustomerLifecycle createNewLifecycle(String customerId, LifecycleStage initialStage) {
        return CustomerLifecycle.builder()
                .lifecycleId(UUID.randomUUID().toString())
                .customerId(customerId)
                .lifecycleStage(initialStage)
                .stageEnteredAt(LocalDateTime.now())
                .stageDurationDays(0)
                .lifecycleScore(BigDecimal.valueOf(50.0))
                .engagementLevel(CustomerLifecycle.EngagementLevel.MEDIUM)
                .churnProbability(BigDecimal.ZERO)
                .retentionPriority(CustomerLifecycle.RetentionPriority.NONE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void updateEngagementLevel(CustomerLifecycle lifecycle, LifecycleStage stage) {
        switch (stage) {
            case ACTIVE, REACTIVATED -> lifecycle.setEngagementLevel(CustomerLifecycle.EngagementLevel.HIGH);
            case ONBOARDING -> lifecycle.setEngagementLevel(CustomerLifecycle.EngagementLevel.MEDIUM);
            case AT_RISK -> lifecycle.setEngagementLevel(CustomerLifecycle.EngagementLevel.LOW);
            case DORMANT, CHURNED -> lifecycle.setEngagementLevel(CustomerLifecycle.EngagementLevel.VERY_LOW);
            default -> lifecycle.setEngagementLevel(CustomerLifecycle.EngagementLevel.MEDIUM);
        }
    }

    private void publishLifecycleTransitionEvent(String customerId, LifecycleStage previousStage,
                                                 LifecycleStage newStage) {
        try {
            Map<String, Object> event = Map.of(
                    "eventType", "LIFECYCLE_TRANSITION",
                    "customerId", customerId,
                    "previousStage", previousStage != null ? previousStage.name() : "NONE",
                    "newStage", newStage.name(),
                    "transitionedAt", LocalDateTime.now().toString()
            );

            kafkaTemplate.send(LIFECYCLE_TRANSITION_TOPIC, customerId, event);

            log.debug("Published lifecycle transition event: customerId={}", customerId);

        } catch (Exception e) {
            log.error("Failed to publish lifecycle transition event: customerId={}", customerId, e);
        }
    }

    private void handleStageSpecificActions(String customerId, LifecycleStage stage) {
        switch (stage) {
            case AT_RISK -> retentionService.createRetentionCampaign(customerId);
            case DORMANT -> retentionService.scheduleWinbackCampaign(customerId);
            case CHURNED -> log.info("Customer churned: {}", customerId);
            case REACTIVATED -> log.info("Customer reactivated: {}", customerId);
            default -> log.debug("No specific action for stage: {}", stage);
        }
    }

    private void markAsAtRisk(String customerId) {
        try {
            CustomerLifecycle lifecycle = customerLifecycleRepository.findByCustomerId(customerId)
                    .orElseGet(() -> createNewLifecycle(customerId, LifecycleStage.AT_RISK));

            if (lifecycle.getLifecycleStage() != LifecycleStage.AT_RISK) {
                processLifecycleTransition(customerId, LifecycleStage.AT_RISK);
            }

        } catch (Exception e) {
            log.error("Failed to mark customer as at-risk: customerId={}", customerId, e);
        }
    }

    private double getStageScore(LifecycleStage stage) {
        return switch (stage) {
            case ACTIVE, REACTIVATED -> 90.0;
            case ONBOARDING -> 70.0;
            case PROSPECT -> 50.0;
            case AT_RISK -> 30.0;
            case DORMANT -> 20.0;
            case CHURNED -> 10.0;
        };
    }

    private double getEngagementScore(CustomerLifecycle.EngagementLevel level) {
        if (level == null) return 50.0;

        return switch (level) {
            case VERY_HIGH -> 100.0;
            case HIGH -> 80.0;
            case MEDIUM -> 60.0;
            case LOW -> 40.0;
            case VERY_LOW -> 20.0;
            case NONE -> 0.0;
        };
    }

    private boolean isEligibleForPromotion(CustomerLifecycle lifecycle) {
        // Criteria for promotion
        return lifecycle.getLifecycleStage() == LifecycleStage.ACTIVE &&
                lifecycle.getEngagementLevel() == CustomerLifecycle.EngagementLevel.VERY_HIGH &&
                lifecycle.getChurnProbability() != null &&
                lifecycle.getChurnProbability().compareTo(new BigDecimal("0.20")) < 0;
    }

    private void updateCustomerSegmentForPromotion(Customer customer) {
        // Update customer segment to premium/VIP if not already
        if (customer.getCustomerSegment() == null ||
                !customer.getCustomerSegment().contains("PREMIUM")) {
            customer.setCustomerSegment("PREMIUM");
            customerRepository.save(customer);
            log.info("Customer promoted to PREMIUM segment: {}", customer.getCustomerId());
        }
    }
}
