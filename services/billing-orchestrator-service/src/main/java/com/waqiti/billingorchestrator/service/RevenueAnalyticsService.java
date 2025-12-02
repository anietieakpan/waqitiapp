package com.waqiti.billingorchestrator.service;

import com.waqiti.billingorchestrator.dto.response.RevenueAnalyticsResponse;
import com.waqiti.billingorchestrator.dto.response.OutstandingPaymentsResponse;
import com.waqiti.billingorchestrator.dto.response.ChurnAnalyticsResponse;
import com.waqiti.billingorchestrator.repository.BillingCycleRepository;
import com.waqiti.billingorchestrator.repository.SubscriptionRepository;
import com.waqiti.billingorchestrator.repository.UsageRecordRepository;
import com.waqiti.billingorchestrator.entity.BillingCycle;
import com.waqiti.billingorchestrator.entity.Subscription;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Revenue Analytics Service
 *
 * Provides comprehensive revenue reporting and analytics for business intelligence.
 *
 * KEY METRICS TRACKED:
 * - Total revenue by period (MRR, ARR)
 * - Revenue by segment (customer type, product, region)
 * - Revenue growth trends
 * - Outstanding payments (aging analysis)
 * - Churn analytics (customer/revenue churn)
 *
 * BUSINESS USE CASES:
 * - Executive dashboards
 * - Investor reporting
 * - Sales forecasting
 * - Collections prioritization
 * - Customer health scoring
 *
 * @author Waqiti Billing Team
 * @since 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RevenueAnalyticsService {

    private final BillingCycleRepository billingCycleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UsageRecordRepository usageRecordRepository;

    /**
     * Get comprehensive revenue analytics
     *
     * @param startDate Period start date
     * @param endDate Period end date
     * @param groupBy Grouping: "day", "week", "month", "customer_type", "product"
     * @return Revenue analytics with segmentation
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "revenue-analytics-service")
    public RevenueAnalyticsResponse getRevenueAnalytics(LocalDate startDate, LocalDate endDate, String groupBy) {
        log.info("Generating revenue analytics from {} to {}, groupBy: {}", startDate, endDate, groupBy);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        // Get all billing cycles in period
        List<BillingCycle> billingCycles = billingCycleRepository.findByCycleStartDateBetween(start, end);

        // Calculate total revenue
        BigDecimal totalRevenue = billingCycles.stream()
                .map(BillingCycle::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidRevenue = billingCycles.stream()
                .map(BillingCycle::getPaidAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingRevenue = totalRevenue.subtract(paidRevenue);

        // Get usage-based revenue (aggregate across all accounts)
        BigDecimal usageRevenue = usageRecordRepository.getTotalUsageAmountByPeriod(
                start,
                end
        );

        // Group revenue by specified dimension
        Map<String, BigDecimal> revenueBySegment = groupRevenueBy(billingCycles, groupBy);

        // Calculate growth metrics
        BigDecimal previousPeriodRevenue = calculatePreviousPeriodRevenue(startDate, endDate);
        BigDecimal growthRate = calculateGrowthRate(totalRevenue, previousPeriodRevenue);

        // Calculate MRR and ARR
        BigDecimal mrr = calculateMRR(billingCycles);
        BigDecimal arr = mrr.multiply(BigDecimal.valueOf(12));

        log.info("Revenue analytics generated: Total={}, MRR={}, ARR={}, Growth={}%",
                totalRevenue, mrr, arr, growthRate);

        return RevenueAnalyticsResponse.builder()
                .periodStart(startDate)
                .periodEnd(endDate)
                .totalRevenue(totalRevenue)
                .paidRevenue(paidRevenue)
                .outstandingRevenue(outstandingRevenue)
                .usageRevenue(usageRevenue)
                .subscriptionRevenue(totalRevenue.subtract(usageRevenue))
                .mrr(mrr)
                .arr(arr)
                .growthRate(growthRate)
                .groupBy(groupBy)
                .revenueBySegment(revenueBySegment)
                .totalInvoices(billingCycles.size())
                .paidInvoices((int) billingCycles.stream()
                        .filter(bc -> "PAID".equals(bc.getStatus().name()))
                        .count())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get outstanding payments report with aging analysis
     *
     * @param daysOverdue Filter by days overdue (null = all outstanding)
     * @return Outstanding payments report with aging buckets
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "revenue-analytics-service")
    public OutstandingPaymentsResponse getOutstandingPayments(Integer daysOverdue) {
        log.info("Generating outstanding payments report, daysOverdue filter: {}", daysOverdue);

        LocalDateTime now = LocalDateTime.now();

        // Get all unpaid/partially paid billing cycles
        List<BillingCycle> outstandingCycles = billingCycleRepository.findAll().stream()
                .filter(bc -> {
                    BigDecimal balanceDue = bc.getBalanceDue();
                    return balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) > 0;
                })
                .filter(bc -> {
                    if (daysOverdue == null) return true;
                    LocalDateTime dueDate = bc.getDueDate().atStartOfDay();
                    long daysPastDue = java.time.Duration.between(dueDate, now).toDays();
                    return daysPastDue >= daysOverdue;
                })
                .collect(Collectors.toList());

        // Calculate totals
        BigDecimal totalOutstanding = outstandingCycles.stream()
                .map(BillingCycle::getBalanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Aging buckets
        Map<String, AgingBucket> agingBuckets = calculateAgingBuckets(outstandingCycles, now);

        // Top delinquent accounts
        List<OutstandingInvoice> topDelinquent = outstandingCycles.stream()
                .sorted(Comparator.comparing(BillingCycle::getBalanceDue).reversed())
                .limit(10)
                .map(bc -> {
                    long daysPastDue = java.time.Duration.between(
                            bc.getDueDate().atStartOfDay(), now).toDays();
                    return OutstandingInvoice.builder()
                            .invoiceId(bc.getInvoiceId())
                            .invoiceNumber(bc.getInvoiceNumber())
                            .customerId(bc.getCustomerId())
                            .accountId(bc.getAccountId())
                            .totalAmount(bc.getTotalAmount())
                            .balanceDue(bc.getBalanceDue())
                            .dueDate(bc.getDueDate())
                            .daysPastDue(daysPastDue)
                            .build();
                })
                .collect(Collectors.toList());

        log.info("Outstanding payments report generated: Total={}, Invoices={}",
                totalOutstanding, outstandingCycles.size());

        return OutstandingPaymentsResponse.builder()
                .totalOutstanding(totalOutstanding)
                .invoiceCount(outstandingCycles.size())
                .daysOverdueFilter(daysOverdue)
                .agingBuckets(agingBuckets)
                .topDelinquentAccounts(topDelinquent)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get churn analytics
     *
     * @param startDate Period start
     * @param endDate Period end
     * @return Churn metrics (customer churn, revenue churn, retention)
     */
    @Transactional(readOnly = true)
    @CircuitBreaker(name = "revenue-analytics-service")
    public ChurnAnalyticsResponse getChurnAnalytics(LocalDate startDate, LocalDate endDate) {
        log.info("Generating churn analytics from {} to {}", startDate, endDate);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        // Get all subscriptions
        List<Subscription> allSubscriptions = subscriptionRepository.findAll();

        // Starting active subscriptions
        long startingActiveSubscriptions = allSubscriptions.stream()
                .filter(s -> s.getStartDate().isBefore(start))
                .filter(s -> s.getEndDate() == null || s.getEndDate().isAfter(start))
                .count();

        // Churned subscriptions in period
        List<Subscription> churnedSubscriptions = allSubscriptions.stream()
                .filter(s -> s.getEndDate() != null)
                .filter(s -> s.getEndDate().isAfter(start) && s.getEndDate().isBefore(end))
                .filter(s -> "CANCELLED".equals(s.getStatus().name()) ||
                             "EXPIRED".equals(s.getStatus().name()))
                .collect(Collectors.toList());

        long churnedCustomers = churnedSubscriptions.stream()
                .map(Subscription::getCustomerId)
                .distinct()
                .count();

        // Calculate churn rate
        BigDecimal churnRate = startingActiveSubscriptions > 0 ?
                BigDecimal.valueOf(churnedCustomers)
                        .divide(BigDecimal.valueOf(startingActiveSubscriptions), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        // Calculate revenue churn
        BigDecimal churnedMRR = churnedSubscriptions.stream()
                .map(Subscription::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMRR = allSubscriptions.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus().name()))
                .map(Subscription::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal revenueChurnRate = totalMRR.compareTo(BigDecimal.ZERO) > 0 ?
                churnedMRR.divide(totalMRR, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        // Retention rate
        BigDecimal retentionRate = BigDecimal.valueOf(100).subtract(churnRate);

        // Churn reasons breakdown
        Map<String, Long> churnReasons = churnedSubscriptions.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCancellationReason() != null ? s.getCancellationReason() : "Unknown",
                        Collectors.counting()
                ));

        log.info("Churn analytics generated: ChurnRate={}%, RevenueChurn={}%, ChurnedCustomers={}",
                churnRate, revenueChurnRate, churnedCustomers);

        return ChurnAnalyticsResponse.builder()
                .periodStart(startDate)
                .periodEnd(endDate)
                .startingCustomers(startingActiveSubscriptions)
                .churnedCustomers(churnedCustomers)
                .endingCustomers(startingActiveSubscriptions - churnedCustomers)
                .churnRate(churnRate)
                .retentionRate(retentionRate)
                .churnedMRR(churnedMRR)
                .revenueChurnRate(revenueChurnRate)
                .churnReasons(churnReasons)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== Helper Methods ====================

    private Map<String, BigDecimal> groupRevenueBy(List<BillingCycle> billingCycles, String groupBy) {
        if (groupBy == null) {
            return Map.of("total", billingCycles.stream()
                    .map(BillingCycle::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        return switch (groupBy.toLowerCase()) {
            case "day" -> groupByDay(billingCycles);
            case "week" -> groupByWeek(billingCycles);
            case "month" -> groupByMonth(billingCycles);
            case "customer_type" -> groupByCustomerType(billingCycles);
            default -> Map.of("total", billingCycles.stream()
                    .map(BillingCycle::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        };
    }

    private Map<String, BigDecimal> groupByDay(List<BillingCycle> cycles) {
        return cycles.stream()
                .collect(Collectors.groupingBy(
                        bc -> bc.getCycleStartDate().toLocalDate().toString(),
                        Collectors.reducing(BigDecimal.ZERO,
                                bc -> bc.getTotalAmount() != null ? bc.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));
    }

    private Map<String, BigDecimal> groupByWeek(List<BillingCycle> cycles) {
        return cycles.stream()
                .collect(Collectors.groupingBy(
                        bc -> "Week " + bc.getCycleStartDate().toLocalDate().toString().substring(0, 7),
                        Collectors.reducing(BigDecimal.ZERO,
                                bc -> bc.getTotalAmount() != null ? bc.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));
    }

    private Map<String, BigDecimal> groupByMonth(List<BillingCycle> cycles) {
        return cycles.stream()
                .collect(Collectors.groupingBy(
                        bc -> bc.getCycleStartDate().toLocalDate().toString().substring(0, 7),
                        Collectors.reducing(BigDecimal.ZERO,
                                bc -> bc.getTotalAmount() != null ? bc.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));
    }

    private Map<String, BigDecimal> groupByCustomerType(List<BillingCycle> cycles) {
        return cycles.stream()
                .collect(Collectors.groupingBy(
                        bc -> bc.getCustomerType() != null ? bc.getCustomerType().name() : "Unknown",
                        Collectors.reducing(BigDecimal.ZERO,
                                bc -> bc.getTotalAmount() != null ? bc.getTotalAmount() : BigDecimal.ZERO,
                                BigDecimal::add)
                ));
    }

    private BigDecimal calculatePreviousPeriodRevenue(LocalDate startDate, LocalDate endDate) {
        long daysDiff = java.time.Period.between(startDate, endDate).getDays();
        LocalDate prevStart = startDate.minusDays(daysDiff);
        LocalDate prevEnd = startDate.minusDays(1);

        List<BillingCycle> prevCycles = billingCycleRepository.findByCycleStartDateBetween(
                prevStart.atStartOfDay(),
                prevEnd.atTime(23, 59, 59)
        );

        return prevCycles.stream()
                .map(BillingCycle::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateGrowthRate(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calculateMRR(List<BillingCycle> cycles) {
        // Average monthly revenue from cycles
        if (cycles.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalRevenue = cycles.stream()
                .map(BillingCycle::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Normalize to monthly
        return totalRevenue.divide(BigDecimal.valueOf(cycles.size()), 2, RoundingMode.HALF_UP);
    }

    private Map<String, AgingBucket> calculateAgingBuckets(List<BillingCycle> cycles, LocalDateTime now) {
        Map<String, AgingBucket> buckets = new LinkedHashMap<>();

        // 0-30 days
        buckets.put("0-30", createAgingBucket(cycles, now, 0, 30));

        // 31-60 days
        buckets.put("31-60", createAgingBucket(cycles, now, 31, 60));

        // 61-90 days
        buckets.put("61-90", createAgingBucket(cycles, now, 61, 90));

        // 90+ days
        buckets.put("90+", createAgingBucket(cycles, now, 91, Integer.MAX_VALUE));

        return buckets;
    }

    private AgingBucket createAgingBucket(List<BillingCycle> cycles, LocalDateTime now, int minDays, int maxDays) {
        List<BillingCycle> bucketCycles = cycles.stream()
                .filter(bc -> {
                    long daysPastDue = java.time.Duration.between(
                            bc.getDueDate().atStartOfDay(), now).toDays();
                    return daysPastDue >= minDays && daysPastDue <= maxDays;
                })
                .collect(Collectors.toList());

        BigDecimal amount = bucketCycles.stream()
                .map(BillingCycle::getBalanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AgingBucket.builder()
                .invoiceCount(bucketCycles.size())
                .totalAmount(amount)
                .build();
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class AgingBucket {
        private Integer invoiceCount;
        private BigDecimal totalAmount;
    }

    @lombok.Data
    @lombok.Builder
    public static class OutstandingInvoice {
        private UUID invoiceId;
        private String invoiceNumber;
        private UUID customerId;
        private UUID accountId;
        private BigDecimal totalAmount;
        private BigDecimal balanceDue;
        private LocalDate dueDate;
        private Long daysPastDue;
    }
}
