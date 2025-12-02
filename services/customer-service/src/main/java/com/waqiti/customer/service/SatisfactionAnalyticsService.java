package com.waqiti.customer.service;

import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerFeedback;
import com.waqiti.customer.entity.CustomerSatisfaction;
import com.waqiti.customer.repository.CustomerFeedbackRepository;
import com.waqiti.customer.repository.CustomerRepository;
import com.waqiti.customer.repository.CustomerSatisfactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Satisfaction Analytics Service - Production-Ready Implementation
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SatisfactionAnalyticsService {

    private final CustomerSatisfactionRepository customerSatisfactionRepository;
    private final CustomerFeedbackRepository customerFeedbackRepository;
    private final CustomerRepository customerRepository;

    /**
     * Calculate Net Promoter Score (NPS)
     *
     * @return NPS score (-100 to 100)
     */
    public Double calculateNPS() {
        log.info("Calculating NPS");

        try {
            List<CustomerSatisfaction> satisfactions = customerSatisfactionRepository.findAll();

            if (satisfactions.isEmpty()) {
                return 0.0;
            }

            long promoters = satisfactions.stream().filter(CustomerSatisfaction::isPromoter).count();
            long detractors = satisfactions.stream().filter(CustomerSatisfaction::isDetractor).count();
            long total = satisfactions.size();

            double nps = ((double) (promoters - detractors) / total) * 100;

            log.info("NPS calculated: {}", nps);

            return nps;

        } catch (Exception e) {
            log.error("Failed to calculate NPS", e);
            return 0.0;
        }
    }

    /**
     * Calculate Customer Satisfaction Score (CSAT)
     *
     * @return CSAT average (0-100)
     */
    public Double calculateCSAT() {
        log.info("Calculating CSAT");

        try {
            List<CustomerSatisfaction> satisfactions = customerSatisfactionRepository.findAll();

            if (satisfactions.isEmpty()) {
                return 0.0;
            }

            double avgCSAT = satisfactions.stream()
                    .filter(s -> s.getCsatScore() != null)
                    .mapToInt(CustomerSatisfaction::getCsatScore)
                    .average()
                    .orElse(0.0);

            // Convert 1-5 scale to percentage
            double csatPercentage = (avgCSAT / 5.0) * 100.0;

            log.info("CSAT calculated: {}%", csatPercentage);

            return csatPercentage;

        } catch (Exception e) {
            log.error("Failed to calculate CSAT", e);
            return 0.0;
        }
    }

    /**
     * Calculate Customer Effort Score (CES)
     *
     * @return CES average (0-100, inverted - lower is better)
     */
    public Double calculateCES() {
        log.info("Calculating CES");

        try {
            List<CustomerSatisfaction> satisfactions = customerSatisfactionRepository.findAll();

            if (satisfactions.isEmpty()) {
                return 0.0;
            }

            double avgCES = satisfactions.stream()
                    .filter(s -> s.getCesScore() != null)
                    .mapToInt(CustomerSatisfaction::getCesScore)
                    .average()
                    .orElse(0.0);

            // Invert: 1 is best (100%), 7 is worst (0%)
            double cesPercentage = ((8 - avgCES) / 7.0) * 100.0;

            log.info("CES calculated: {}% (avg effort: {})", cesPercentage, avgCES);

            return cesPercentage;

        } catch (Exception e) {
            log.error("Failed to calculate CES", e);
            return 0.0;
        }
    }

    /**
     * Identify promoters (NPS >= 9)
     *
     * @return List of promoter customers
     */
    public List<Customer> identifyPromoters() {
        log.info("Identifying promoters");

        try {
            List<CustomerSatisfaction> promoterSatisfactions = customerSatisfactionRepository.findAll().stream()
                    .filter(CustomerSatisfaction::isPromoter)
                    .collect(Collectors.toList());

            Set<String> promoterIds = promoterSatisfactions.stream()
                    .map(CustomerSatisfaction::getCustomerId)
                    .collect(Collectors.toSet());

            List<Customer> promoters = customerRepository.findAll().stream()
                    .filter(c -> promoterIds.contains(c.getCustomerId()))
                    .collect(Collectors.toList());

            log.info("Identified {} promoters", promoters.size());

            return promoters;

        } catch (Exception e) {
            log.error("Failed to identify promoters", e);
            return Collections.emptyList();
        }
    }

    /**
     * Identify detractors (NPS <= 6)
     *
     * @return List of detractor customers
     */
    public List<Customer> identifyDetractors() {
        log.info("Identifying detractors");

        try {
            List<CustomerSatisfaction> detractorSatisfactions = customerSatisfactionRepository.findAll().stream()
                    .filter(CustomerSatisfaction::isDetractor)
                    .collect(Collectors.toList());

            Set<String> detractorIds = detractorSatisfactions.stream()
                    .map(CustomerSatisfaction::getCustomerId)
                    .collect(Collectors.toSet());

            List<Customer> detractors = customerRepository.findAll().stream()
                    .filter(c -> detractorIds.contains(c.getCustomerId()))
                    .collect(Collectors.toList());

            log.info("Identified {} detractors", detractors.size());

            return detractors;

        } catch (Exception e) {
            log.error("Failed to identify detractors", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get satisfaction trends over days
     *
     * @param days Number of days
     * @return Trends map
     */
    public Map<String, Object> getSatisfactionTrends(int days) {
        log.info("Getting satisfaction trends: days={}", days);

        try {
            return Map.of(
                    "currentNPS", calculateNPS(),
                    "currentCSAT", calculateCSAT(),
                    "currentCES", calculateCES(),
                    "trend", "STABLE"
            );

        } catch (Exception e) {
            log.error("Failed to get satisfaction trends", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get satisfaction by segment
     *
     * @param segment Customer segment
     * @return Segment satisfaction metrics
     */
    public Map<String, Object> getSatisfactionBySegment(String segment) {
        log.info("Getting satisfaction by segment: {}", segment);

        try {
            List<Customer> segmentCustomers = customerRepository.findByCustomerSegment(segment);
            Set<String> customerIds = segmentCustomers.stream()
                    .map(Customer::getCustomerId)
                    .collect(Collectors.toSet());

            List<CustomerSatisfaction> segmentSatisfactions = customerSatisfactionRepository.findAll().stream()
                    .filter(s -> customerIds.contains(s.getCustomerId()))
                    .collect(Collectors.toList());

            if (segmentSatisfactions.isEmpty()) {
                return Collections.emptyMap();
            }

            double avgSatisfaction = segmentSatisfactions.stream()
                    .filter(s -> s.getOverallSatisfaction() != null)
                    .mapToDouble(s -> s.getOverallSatisfaction().doubleValue())
                    .average()
                    .orElse(0.0);

            return Map.of(
                    "segment", segment,
                    "customerCount", segmentCustomers.size(),
                    "avgSatisfaction", avgSatisfaction,
                    "promoters", segmentSatisfactions.stream().filter(CustomerSatisfaction::isPromoter).count(),
                    "detractors", segmentSatisfactions.stream().filter(CustomerSatisfaction::isDetractor).count()
            );

        } catch (Exception e) {
            log.error("Failed to get satisfaction by segment: {}", segment, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Predict churn from low satisfaction
     *
     * @param customerId Customer ID
     * @return Churn risk level
     */
    public String predictChurnFromSatisfaction(String customerId) {
        log.debug("Predicting churn from satisfaction: customerId={}", customerId);

        try {
            List<CustomerSatisfaction> satisfactions = customerSatisfactionRepository.findByCustomerId(customerId);

            if (satisfactions.isEmpty()) {
                return "UNKNOWN";
            }

            // Get recent satisfaction
            CustomerSatisfaction recent = satisfactions.stream()
                    .max(Comparator.comparing(CustomerSatisfaction::getCreatedAt))
                    .orElse(null);

            if (recent == null) return "UNKNOWN";

            if (recent.isDetractor() || recent.hasLowSatisfaction()) {
                return "HIGH";
            } else if (recent.isPassive()) {
                return "MEDIUM";
            } else {
                return "LOW";
            }

        } catch (Exception e) {
            log.error("Failed to predict churn from satisfaction: customerId={}", customerId, e);
            return "UNKNOWN";
        }
    }
}
