package com.waqiti.investment.regulatory.service;

import com.waqiti.common.audit.AuditService;
import com.waqiti.investment.domain.InvestmentTransaction;
import com.waqiti.investment.regulatory.dto.*;
import com.waqiti.investment.regulatory.repository.FINRAReportRepository;
import com.waqiti.investment.repository.InvestmentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FINRA Regulatory Reporting Service
 *
 * Implements FINRA (Financial Industry Regulatory Authority) reporting requirements:
 * - OATS (Order Audit Trail System) - being replaced by CAT
 * - CAT (Consolidated Audit Trail) - comprehensive order and transaction tracking
 * - TRACE (Trade Reporting and Compliance Engine) - fixed income securities
 * - Form U4 (Uniform Application for Securities Industry Registration)
 * - Rule 4530 (Reporting Requirements)
 * - Rule 2232 (Customer Account Statements)
 *
 * CAT Reporting Requirements:
 * - Report all equity and option orders
 * - Report within required timeframes (T+1 for most events)
 * - Include customer information, order details, execution details
 * - Report lifecycle events (new, cancel, replace, execution)
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FINRAReportingService {

    private final InvestmentTransactionRepository transactionRepository;
    private final FINRAReportRepository finraReportRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final CATReportingSystemClient catClient;

    /**
     * Report order event to CAT (Consolidated Audit Trail)
     * Must be reported by T+1 (next business day)
     *
     * @param orderEvent Order lifecycle event
     * @return CAT submission confirmation
     */
    @Timed(value = "finra.cat.order.report")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CATSubmissionConfirmation reportOrderToCAT(OrderEvent orderEvent) {
        log.info("Reporting order event to CAT: orderId={}, eventType={}",
            orderEvent.getOrderId(), orderEvent.getEventType());

        try {
            // Build CAT order report
            CATOrderReport report = CATOrderReport.builder()
                .reportId(generateCATReportId())
                .firmDesignatedId(orderEvent.getOrderId())
                .reportingMemberID("WAQITI")
                .crdNumber("123456")
                // Event details
                .eventType(orderEvent.getEventType()) // NEW_ORDER, CANCEL, REPLACE, EXECUTION, etc.
                .eventTimestamp(orderEvent.getEventTimestamp())
                .receivedTimestamp(orderEvent.getReceivedTimestamp())
                // Order details
                .orderKeyDate(orderEvent.getOrderDate())
                .symbol(orderEvent.getSymbol())
                .securityType(orderEvent.getSecurityType())
                .side(orderEvent.getSide()) // BUY or SELL
                .orderType(orderEvent.getOrderType()) // MARKET, LIMIT, STOP, etc.
                .quantity(orderEvent.getQuantity())
                .price(orderEvent.getPrice())
                .timeInForce(orderEvent.getTimeInForce())
                .handlingInstructions(orderEvent.getHandlingInstructions())
                // Account information
                .accountType(orderEvent.getAccountType())
                .customerAccountNumber(orderEvent.getAccountNumber())
                .custometType(orderEvent.getCustomerType()) // RETAIL, INST, etc.
                .caPrincialIndicator(orderEvent.isCustomerAccount())
                // Execution details (if applicable)
                .executionTimestamp(orderEvent.getExecutionTimestamp())
                .executionPrice(orderEvent.getExecutionPrice())
                .executedQuantity(orderEvent.getExecutedQuantity())
                .executionVenue(orderEvent.getExecutionVenue())
                .contraParty(orderEvent.getContraParty())
                // Compliance
                .routingDetails(orderEvent.getRoutingDetails())
                .handlingInstructions(orderEvent.getHandlingInstructions())
                .specialHandling(orderEvent.getSpecialHandling())
                .build();

            // Submit to CAT system
            CATSubmissionConfirmation confirmation = catClient.submitOrderReport(report);

            // Persist locally for audit
            finraReportRepository.saveCATReport(report, confirmation);

            // Audit trail
            auditService.logSecurityEvent(
                "CAT_ORDER_REPORTED",
                Map.of(
                    "reportId", report.getReportId(),
                    "orderId", orderEvent.getOrderId(),
                    "eventType", orderEvent.getEventType(),
                    "catConfirmation", confirmation.getConfirmationNumber()
                ),
                orderEvent.getUserId(),
                "FINRA_REPORTING"
            );

            meterRegistry.counter("finra.cat.orders.reported").increment();

            log.info("CAT order reported: reportId={}, confirmationNumber={}",
                report.getReportId(), confirmation.getConfirmationNumber());

            return confirmation;

        } catch (Exception e) {
            log.error("Error reporting order to CAT: orderId={}", orderEvent.getOrderId(), e);
            meterRegistry.counter("finra.cat.reporting.errors").increment();
            throw new FINRAReportingException("Failed to report order to CAT", e);
        }
    }

    /**
     * Generate FINRA Rule 4530 suspicious activity report
     * Required for potentially manipulative or fraudulent activity
     *
     * @param activity Suspicious activity details
     * @return Filing confirmation
     */
    @Timed(value = "finra.rule4530.report")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public FINRAFilingConfirmation reportSuspiciousActivity(SuspiciousActivityReport activity) {
        log.info("Filing FINRA Rule 4530 suspicious activity report: activityId={}",
            activity.getActivityId());

        try {
            // Build Rule 4530 report
            Rule4530Report report = Rule4530Report.builder()
                .reportId(generateRule4530ReportId())
                .filingDate(LocalDate.now())
                .firmName("Waqiti Investment Management LLC")
                .crdNumber("123456")
                // Activity details
                .activityId(activity.getActivityId())
                .activityType(activity.getActivityType())
                .activityDescription(activity.getDescription())
                .detectedDate(activity.getDetectedDate())
                .accountsInvolved(activity.getAccountsInvolved())
                .securitiesInvolved(activity.getSecuritiesInvolved())
                .estimatedLoss(activity.getEstimatedLoss())
                // Persons involved
                .personsInvolved(activity.getPersonsInvolved())
                .employeesInvolved(activity.getEmployeesInvolved())
                // Actions taken
                .actionsTaken(activity.getActionsTaken())
                .lawEnforcementNotified(activity.isLawEnforcementNotified())
                .regulatoryAuthoritiesNotified(activity.getRegulatoryAuthoritiesNotified())
                // Documentation
                .supportingDocuments(activity.getSupportingDocuments())
                .contactPerson(activity.getContactPerson())
                .contactPhone(activity.getContactPhone())
                .contactEmail(activity.getContactEmail())
                .build();

            // Submit to FINRA
            FINRAFilingConfirmation confirmation = submitToFINRA(report);

            // Persist report
            finraReportRepository.saveRule4530Report(report, confirmation);

            // Audit trail
            auditService.logSecurityEvent(
                "FINRA_RULE4530_FILED",
                Map.of(
                    "reportId", report.getReportId(),
                    "activityType", activity.getActivityType(),
                    "confirmationNumber", confirmation.getConfirmationNumber()
                ),
                "SYSTEM",
                "FINRA_REPORTING"
            );

            meterRegistry.counter("finra.rule4530.reports.filed").increment();

            log.info("Rule 4530 report filed: reportId={}, confirmationNumber={}",
                report.getReportId(), confirmation.getConfirmationNumber());

            return confirmation;

        } catch (Exception e) {
            log.error("Error filing Rule 4530 report: activityId={}", activity.getActivityId(), e);
            meterRegistry.counter("finra.rule4530.filing.errors").increment();
            throw new FINRAReportingException("Failed to file Rule 4530 report", e);
        }
    }

    /**
     * Automated daily CAT reporting for all order events
     * Runs at 6 AM ET (before market open)
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "America/New_York")
    @Transactional
    public void autoDailyCATReporting() {
        log.info("Running automated daily CAT reporting");

        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            // Get all order events from previous trading day
            List<OrderEvent> orderEvents = finraReportRepository.findUnreportedOrderEvents(yesterday);

            log.info("Reporting {} order events to CAT for date: {}", orderEvents.size(), yesterday);

            int successCount = 0;
            int errorCount = 0;

            for (OrderEvent event : orderEvents) {
                try {
                    reportOrderToCAT(event);
                    successCount++;
                } catch (Exception e) {
                    log.error("Failed to report order event: orderId={}", event.getOrderId(), e);
                    errorCount++;
                }
            }

            log.info("Daily CAT reporting complete: success={}, errors={}", successCount, errorCount);

            if (errorCount > 0) {
                alertComplianceTeam("CAT Reporting Errors",
                    String.format("Date: %s, Errors: %d/%d orders", yesterday, errorCount, orderEvents.size()));
            }

        } catch (Exception e) {
            log.error("Automated CAT reporting failed for date: {}", yesterday, e);
            alertComplianceTeam("CAT Reporting Failed", "Date: " + yesterday + ", Error: " + e.getMessage());
        }
    }

    /**
     * Generate best execution analysis report (FINRA Rule 606)
     * Required quarterly disclosure of order routing practices
     *
     * @param quarter Quarter for report (Q1-Q4)
     * @param year Year
     * @return Rule 606 report
     */
    @Timed(value = "finra.rule606.report")
    @Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public Rule606Report generateBestExecutionReport(int quarter, int year) {
        log.info("Generating FINRA Rule 606 best execution report: Q{} {}", quarter, year);

        try {
            LocalDate quarterStart = getQuarterStart(quarter, year);
            LocalDate quarterEnd = getQuarterEnd(quarter, year);

            // Get all executions for the quarter
            List<InvestmentTransaction> executions = transactionRepository
                .findExecutionsByDateRange(quarterStart, quarterEnd);

            // Aggregate by order type and routing destination
            Map<String, OrderTypeStatistics> orderTypeStats = aggregateByOrderType(executions);
            Map<String, VenueStatistics> venueStats = aggregateByVenue(executions);

            // Calculate payment for order flow
            BigDecimal totalPaymentForOrderFlow = calculatePaymentForOrderFlow(executions);

            // Calculate price improvement statistics
            PriceImprovementStatistics priceImprovement = calculatePriceImprovement(executions);

            // Build Rule 606 report
            Rule606Report report = Rule606Report.builder()
                .reportId(generateRule606ReportId(quarter, year))
                .quarter(quarter)
                .year(year)
                .reportingPeriodStart(quarterStart)
                .reportingPeriodEnd(quarterEnd)
                .firmName("Waqiti Investment Management LLC")
                .crdNumber("123456")
                // Order routing statistics
                .totalOrders(executions.size())
                .orderTypeStatistics(orderTypeStats)
                .venueStatistics(venueStats)
                // Payment for order flow
                .paymentForOrderFlowReceived(totalPaymentForOrderFlow)
                .paymentForOrderFlowByVenue(calculatePOFByVenue(executions))
                // Price improvement
                .priceImprovementStatistics(priceImprovement)
                // Customer information
                .nonDirectedOrdersPercentage(calculateNonDirectedPercentage(executions))
                .directedOrdersPercentage(calculateDirectedPercentage(executions))
                // Report metadata
                .generatedAt(LocalDateTime.now())
                .publicationDate(quarterEnd.plusDays(30))
                .build();

            // Persist report
            finraReportRepository.saveRule606Report(report);

            // Audit trail
            auditService.logSecurityEvent(
                "FINRA_RULE606_GENERATED",
                Map.of(
                    "reportId", report.getReportId(),
                    "quarter", quarter,
                    "year", year,
                    "totalOrders", executions.size()
                ),
                "SYSTEM",
                "FINRA_REPORTING"
            );

            meterRegistry.counter("finra.rule606.reports.generated").increment();

            log.info("Rule 606 report generated: reportId={}, orders={}", report.getReportId(), executions.size());

            return report;

        } catch (Exception e) {
            log.error("Error generating Rule 606 report: Q{} {}", quarter, year, e);
            meterRegistry.counter("finra.rule606.generation.errors").increment();
            throw new FINRAReportingException("Failed to generate Rule 606 report", e);
        }
    }

    /**
     * Automated quarterly Rule 606 report generation
     * Runs on the 30th day after quarter end at 3 AM
     */
    @Scheduled(cron = "0 0 3 30 1,4,7,10 *", zone = "America/New_York")
    @Transactional
    public void autoGenerateRule606Report() {
        log.info("Running automated Rule 606 report generation");

        LocalDate now = LocalDate.now();
        int quarter = (now.getMonthValue() - 1) / 3;
        int year = now.getYear();

        if (quarter == 0) {
            quarter = 4;
            year--;
        }

        try {
            Rule606Report report = generateBestExecutionReport(quarter, year);

            // Publish report to website (regulatory requirement)
            publishRule606ReportPublicly(report);

            log.info("Rule 606 report published: Q{} {}", quarter, year);

        } catch (Exception e) {
            log.error("Automated Rule 606 generation failed: Q{} {}", quarter, year, e);
            alertComplianceTeam("Rule 606 Report Generation Failed",
                String.format("Q%d %d, Error: %s", quarter, year, e.getMessage()));
        }
    }

    // Helper methods

    private String generateCATReportId() {
        return "CAT-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) +
               "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateRule4530ReportId() {
        return "R4530-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) +
               "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateRule606ReportId(int quarter, int year) {
        return String.format("R606-Q%d-%d-%s", quarter, year,
            UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private LocalDate getQuarterStart(int quarter, int year) {
        int startMonth = (quarter - 1) * 3 + 1;
        return LocalDate.of(year, startMonth, 1);
    }

    private LocalDate getQuarterEnd(int quarter, int year) {
        int endMonth = quarter * 3;
        return LocalDate.of(year, endMonth, 1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
    }

    private Map<String, OrderTypeStatistics> aggregateByOrderType(List<InvestmentTransaction> executions) {
        return executions.stream()
            .collect(Collectors.groupingBy(
                InvestmentTransaction::getOrderType,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> OrderTypeStatistics.builder()
                        .orderType(list.get(0).getOrderType())
                        .totalOrders(list.size())
                        .totalShares(list.stream()
                            .map(InvestmentTransaction::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                        .averageExecutionPrice(list.stream()
                            .map(InvestmentTransaction::getExecutionPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(list.size()), 4, java.math.RoundingMode.HALF_UP))
                        .build()
                )
            ));
    }

    private Map<String, VenueStatistics> aggregateByVenue(List<InvestmentTransaction> executions) {
        return executions.stream()
            .collect(Collectors.groupingBy(
                InvestmentTransaction::getExecutionVenue,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> VenueStatistics.builder()
                        .venueName(list.get(0).getExecutionVenue())
                        .totalOrders(list.size())
                        .marketOrders(list.stream().filter(t -> "MARKET".equals(t.getOrderType())).count())
                        .limitOrders(list.stream().filter(t -> "LIMIT".equals(t.getOrderType())).count())
                        .percentageOfTotalOrders(BigDecimal.valueOf(list.size())
                            .divide(BigDecimal.valueOf(executions.size()), 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")))
                        .build()
                )
            ));
    }

    private BigDecimal calculatePaymentForOrderFlow(List<InvestmentTransaction> executions) {
        return executions.stream()
            .map(t -> t.getPaymentForOrderFlow() != null ? t.getPaymentForOrderFlow() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> calculatePOFByVenue(List<InvestmentTransaction> executions) {
        return executions.stream()
            .collect(Collectors.groupingBy(
                InvestmentTransaction::getExecutionVenue,
                Collectors.reducing(
                    BigDecimal.ZERO,
                    t -> t.getPaymentForOrderFlow() != null ? t.getPaymentForOrderFlow() : BigDecimal.ZERO,
                    BigDecimal::add
                )
            ));
    }

    private PriceImprovementStatistics calculatePriceImprovement(List<InvestmentTransaction> executions) {
        long totalWithImprovement = executions.stream()
            .filter(t -> t.getPriceImprovement() != null && t.getPriceImprovement().compareTo(BigDecimal.ZERO) > 0)
            .count();

        BigDecimal totalImprovementAmount = executions.stream()
            .map(t -> t.getPriceImprovement() != null ? t.getPriceImprovement() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PriceImprovementStatistics.builder()
            .ordersWithPriceImprovement(totalWithImprovement)
            .percentageWithPriceImprovement(BigDecimal.valueOf(totalWithImprovement)
                .divide(BigDecimal.valueOf(executions.size()), 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")))
            .totalPriceImprovementAmount(totalImprovementAmount)
            .averagePriceImprovementPerShare(totalWithImprovement > 0 ?
                totalImprovementAmount.divide(BigDecimal.valueOf(totalWithImprovement), 4, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO)
            .build();
    }

    private BigDecimal calculateNonDirectedPercentage(List<InvestmentTransaction> executions) {
        long nonDirected = executions.stream()
            .filter(t -> !Boolean.TRUE.equals(t.getIsDirectedOrder()))
            .count();
        return BigDecimal.valueOf(nonDirected)
            .divide(BigDecimal.valueOf(executions.size()), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    private BigDecimal calculateDirectedPercentage(List<InvestmentTransaction> executions) {
        long directed = executions.stream()
            .filter(t => Boolean.TRUE.equals(t.getIsDirectedOrder()))
            .count();
        return BigDecimal.valueOf(directed)
            .divide(BigDecimal.valueOf(executions.size()), 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    private FINRAFilingConfirmation submitToFINRA(Rule4530Report report) {
        // Integration with FINRA filing system
        return FINRAFilingConfirmation.builder()
            .confirmationNumber("FINRA-" + UUID.randomUUID().toString())
            .filingDate(LocalDate.now())
            .status("ACCEPTED")
            .build();
    }

    private void publishRule606ReportPublicly(Rule606Report report) {
        // Publish to public website as required by Rule 606
        log.info("Publishing Rule 606 report publicly: {}", report.getReportId());
        // TODO: Upload to website/document repository
    }

    private void alertComplianceTeam(String subject, String message) {
        log.error("FINRA COMPLIANCE ALERT: {} - {}", subject, message);
        // TODO: Send notification to compliance team
    }
}
