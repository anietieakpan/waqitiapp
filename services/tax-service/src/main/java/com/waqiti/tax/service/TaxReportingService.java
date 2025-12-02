package com.waqiti.tax.service;

import com.waqiti.tax.dto.TaxReportRequest;
import com.waqiti.tax.dto.TaxReportResponse;
import com.waqiti.tax.entity.TaxJurisdiction;
import com.waqiti.tax.entity.TaxTransaction;
import com.waqiti.tax.repository.TaxJurisdictionRepository;
import com.waqiti.tax.repository.TaxTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating comprehensive tax reports and analytics.
 * Provides detailed reporting functionality for compliance and business intelligence.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TaxReportingService {

    private final TaxTransactionRepository taxTransactionRepository;
    private final TaxJurisdictionRepository taxJurisdictionRepository;

    /**
     * Generate comprehensive tax report
     */
    public TaxReportResponse generateTaxReport(TaxReportRequest request) {
        log.info("Generating tax report for user: {}, period: {} to {}", 
                request.getUserId(), request.getStartDate(), request.getEndDate());

        try {
            // Validate request
            validateReportRequest(request);

            // Fetch transaction data
            List<TaxTransaction> transactions = fetchTransactionsForReport(request);
            
            if (transactions.isEmpty()) {
                log.warn("No transactions found for report criteria");
                return buildEmptyReport(request);
            }

            // Build comprehensive report
            TaxReportResponse report = buildTaxReport(request, transactions);
            
            log.info("Tax report generated successfully with {} transactions", transactions.size());
            return report;

        } catch (Exception e) {
            log.error("Failed to generate tax report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate tax report", e);
        }
    }

    /**
     * Generate user tax summary
     */
    public TaxReportResponse generateUserTaxSummary(String userId, Integer taxYear) {
        log.info("Generating tax summary for user: {}, tax year: {}", userId, taxYear);

        List<TaxTransaction> transactions = taxTransactionRepository.findByUserIdAndTaxYear(userId, taxYear);
        
        if (transactions.isEmpty()) {
            return buildEmptyUserSummary(userId, taxYear);
        }

        return buildUserTaxSummary(userId, taxYear, transactions);
    }

    /**
     * Generate jurisdiction tax summary
     */
    public TaxReportResponse generateJurisdictionSummary(String jurisdiction, 
                                                        LocalDateTime startDate, 
                                                        LocalDateTime endDate) {
        log.info("Generating jurisdiction summary for: {}, period: {} to {}", 
                jurisdiction, startDate, endDate);

        List<TaxTransaction> transactions = taxTransactionRepository
                .findByJurisdictionAndCalculationDateBetween(jurisdiction, startDate, endDate);

        return buildJurisdictionSummary(jurisdiction, startDate, endDate, transactions);
    }

    /**
     * Generate cross-border tax report
     */
    public TaxReportResponse generateCrossBorderReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating cross-border tax report for period: {} to {}", startDate, endDate);

        List<TaxTransaction> transactions = taxTransactionRepository
                .findByCrossBorderTrueAndCalculationDateBetween(startDate, endDate);

        return buildCrossBorderReport(startDate, endDate, transactions);
    }

    /**
     * Generate high-value transaction report
     */
    public TaxReportResponse generateHighValueReport(BigDecimal threshold, 
                                                   LocalDateTime startDate, 
                                                   LocalDateTime endDate) {
        log.info("Generating high-value transaction report with threshold: {}", threshold);

        List<TaxTransaction> transactions = taxTransactionRepository
                .findHighValueTransactions(threshold, startDate, endDate);

        return buildHighValueReport(threshold, startDate, endDate, transactions);
    }

    /**
     * Generate compliance filing report
     */
    public TaxReportResponse generateComplianceFilingReport(BigDecimal threshold,
                                                          LocalDateTime startDate,
                                                          LocalDateTime endDate) {
        log.info("Generating compliance filing report");

        List<TaxTransaction> transactions = taxTransactionRepository
                .findTransactionsRequiringCompliance(threshold, startDate, endDate);

        return buildComplianceFilingReport(threshold, startDate, endDate, transactions);
    }

    /**
     * Validate report request
     */
    private void validateReportRequest(TaxReportRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }

        if (request.getStartDate().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start date cannot be in the future");
        }
    }

    /**
     * Fetch transactions for report based on request criteria
     */
    private List<TaxTransaction> fetchTransactionsForReport(TaxReportRequest request) {
        if (request.getUserId() != null) {
            return taxTransactionRepository.findByUserIdAndCalculationDateBetween(
                    request.getUserId(), request.getStartDate(), request.getEndDate());
        }

        if (request.getJurisdiction() != null) {
            return taxTransactionRepository.findByJurisdictionAndCalculationDateBetween(
                    request.getJurisdiction(), request.getStartDate(), request.getEndDate());
        }

        if (request.getTransactionType() != null) {
            return taxTransactionRepository.findByTransactionTypeAndCalculationDateBetween(
                    request.getTransactionType(), request.getStartDate(), request.getEndDate());
        }

        // Default: fetch transactions by date range with repository method
        return taxTransactionRepository.findByCalculationDateBetween(
                request.getStartDate(), request.getEndDate());
    }

    /**
     * Build comprehensive tax report
     */
    private TaxReportResponse buildTaxReport(TaxReportRequest request, List<TaxTransaction> transactions) {
        Map<String, Object> reportData = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();

        // Basic statistics
        reportData.put("totalTransactions", transactions.size());
        reportData.put("totalTransactionAmount", calculateTotalTransactionAmount(transactions));
        reportData.put("totalTaxAmount", calculateTotalTaxAmount(transactions));
        reportData.put("averageTaxRate", calculateAverageTaxRate(transactions));
        reportData.put("effectiveTaxBurden", calculateEffectiveTaxBurden(transactions));

        // Breakdown by jurisdiction
        reportData.put("jurisdictionBreakdown", buildJurisdictionBreakdown(transactions));
        
        // Breakdown by transaction type
        reportData.put("transactionTypeBreakdown", buildTransactionTypeBreakdown(transactions));
        
        // Breakdown by tax year
        reportData.put("taxYearBreakdown", buildTaxYearBreakdown(transactions));

        // Monthly trends
        reportData.put("monthlyTrends", buildMonthlyTrends(transactions));

        // High-value transactions
        reportData.put("highValueTransactions", buildHighValueTransactionSummary(transactions));

        // Cross-border transactions
        reportData.put("crossBorderSummary", buildCrossBorderSummary(transactions));

        // Compliance summary
        reportData.put("complianceSummary", buildComplianceSummary(transactions));

        // Tax savings summary
        reportData.put("taxSavingsSummary", buildTaxSavingsSummary(transactions));

        // Metadata
        metadata.put("reportType", "COMPREHENSIVE_TAX_REPORT");
        metadata.put("generationTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("reportPeriod", request.getStartDate() + " to " + request.getEndDate());
        metadata.put("dataQualityScore", calculateDataQualityScore(transactions).toString());

        return TaxReportResponse.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType("COMPREHENSIVE_TAX_REPORT")
                .userId(request.getUserId())
                .jurisdiction(request.getJurisdiction())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalTransactions(transactions.size())
                .totalTaxAmount(calculateTotalTaxAmount(transactions))
                .averageTaxRate(calculateAverageTaxRate(transactions))
                .reportData(reportData)
                .metadata(metadata)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Build empty report when no data found
     */
    private TaxReportResponse buildEmptyReport(TaxReportRequest request) {
        Map<String, Object> reportData = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();

        reportData.put("message", "No transactions found for the specified criteria");
        metadata.put("reportType", "EMPTY_REPORT");
        metadata.put("generationTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return TaxReportResponse.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType("EMPTY_REPORT")
                .userId(request.getUserId())
                .jurisdiction(request.getJurisdiction())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalTransactions(0)
                .totalTaxAmount(BigDecimal.ZERO)
                .averageTaxRate(BigDecimal.ZERO)
                .reportData(reportData)
                .metadata(metadata)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Build user tax summary
     */
    private TaxReportResponse buildUserTaxSummary(String userId, Integer taxYear, 
                                                 List<TaxTransaction> transactions) {
        Map<String, Object> reportData = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();

        // User-specific calculations
        reportData.put("totalTransactions", transactions.size());
        reportData.put("totalTransactionAmount", calculateTotalTransactionAmount(transactions));
        reportData.put("totalTaxAmount", calculateTotalTaxAmount(transactions));
        reportData.put("totalTaxSavings", calculateTotalTaxSavings(transactions));
        reportData.put("effectiveTaxRate", calculateAverageTaxRate(transactions));

        // Monthly breakdown
        reportData.put("monthlyBreakdown", buildMonthlyBreakdownForUser(transactions));

        // Jurisdiction breakdown
        reportData.put("jurisdictionBreakdown", buildJurisdictionBreakdown(transactions));

        // Transaction type breakdown
        reportData.put("transactionTypeBreakdown", buildTransactionTypeBreakdown(transactions));

        // Exemptions and deductions
        reportData.put("exemptionsApplied", buildExemptionsSummary(transactions));
        reportData.put("deductionsApplied", buildDeductionsSummary(transactions));

        metadata.put("reportType", "USER_TAX_SUMMARY");
        metadata.put("userId", userId);
        metadata.put("taxYear", taxYear.toString());
        metadata.put("generationTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return TaxReportResponse.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType("USER_TAX_SUMMARY")
                .userId(userId)
                .taxYear(taxYear)
                .totalTransactions(transactions.size())
                .totalTaxAmount(calculateTotalTaxAmount(transactions))
                .averageTaxRate(calculateAverageTaxRate(transactions))
                .reportData(reportData)
                .metadata(metadata)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Build empty user summary
     */
    private TaxReportResponse buildEmptyUserSummary(String userId, Integer taxYear) {
        Map<String, Object> reportData = new HashMap<>();
        Map<String, String> metadata = new HashMap<>();

        reportData.put("message", "No tax transactions found for user in the specified tax year");
        metadata.put("reportType", "EMPTY_USER_SUMMARY");
        metadata.put("userId", userId);
        metadata.put("taxYear", taxYear.toString());

        return TaxReportResponse.builder()
                .reportId(UUID.randomUUID().toString())
                .reportType("EMPTY_USER_SUMMARY")
                .userId(userId)
                .taxYear(taxYear)
                .totalTransactions(0)
                .totalTaxAmount(BigDecimal.ZERO)
                .averageTaxRate(BigDecimal.ZERO)
                .reportData(reportData)
                .metadata(metadata)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Calculate total transaction amount
     */
    private BigDecimal calculateTotalTransactionAmount(List<TaxTransaction> transactions) {
        return transactions.stream()
                .map(TaxTransaction::getTransactionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate total tax amount
     */
    private BigDecimal calculateTotalTaxAmount(List<TaxTransaction> transactions) {
        return transactions.stream()
                .map(TaxTransaction::getTaxAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate average tax rate
     */
    private BigDecimal calculateAverageTaxRate(List<TaxTransaction> transactions) {
        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalRate = transactions.stream()
                .map(TaxTransaction::getEffectiveTaxRate)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long count = transactions.stream()
                .map(TaxTransaction::getEffectiveTaxRate)
                .filter(Objects::nonNull)
                .count();

        return count > 0 ? totalRate.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    /**
     * Calculate effective tax burden
     */
    private BigDecimal calculateEffectiveTaxBurden(List<TaxTransaction> transactions) {
        BigDecimal totalTransactionAmount = calculateTotalTransactionAmount(transactions);
        BigDecimal totalTaxAmount = calculateTotalTaxAmount(transactions);

        if (totalTransactionAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalTaxAmount.divide(totalTransactionAmount, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * Calculate total tax savings
     */
    private BigDecimal calculateTotalTaxSavings(List<TaxTransaction> transactions) {
        return transactions.stream()
                .map(TaxTransaction::getTaxSavings)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Build jurisdiction breakdown
     */
    private Map<String, Object> buildJurisdictionBreakdown(List<TaxTransaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(TaxTransaction::getJurisdiction))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Map.of(
                                "transactionCount", entry.getValue().size(),
                                "totalTaxAmount", calculateTotalTaxAmount(entry.getValue()),
                                "averageTaxRate", calculateAverageTaxRate(entry.getValue())
                        )
                ));
    }

    /**
     * Build transaction type breakdown
     */
    private Map<String, Object> buildTransactionTypeBreakdown(List<TaxTransaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(TaxTransaction::getTransactionType))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Map.of(
                                "transactionCount", entry.getValue().size(),
                                "totalTaxAmount", calculateTotalTaxAmount(entry.getValue()),
                                "averageTaxRate", calculateAverageTaxRate(entry.getValue())
                        )
                ));
    }

    /**
     * Build tax year breakdown
     */
    private Map<String, Object> buildTaxYearBreakdown(List<TaxTransaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(TaxTransaction::getTaxYear))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        entry -> Map.of(
                                "transactionCount", entry.getValue().size(),
                                "totalTaxAmount", calculateTotalTaxAmount(entry.getValue()),
                                "averageTaxRate", calculateAverageTaxRate(entry.getValue())
                        )
                ));
    }

    /**
     * Build monthly trends
     */
    private List<Map<String, Object>> buildMonthlyTrends(List<TaxTransaction> transactions) {
        return transactions.stream()
                .collect(Collectors.groupingBy(t -> t.getCalculationDate().toLocalDate().withDayOfMonth(1)))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of(
                        "month", entry.getKey().toString(),
                        "transactionCount", entry.getValue().size(),
                        "totalTaxAmount", calculateTotalTaxAmount(entry.getValue()),
                        "averageTaxRate", calculateAverageTaxRate(entry.getValue())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Additional helper methods for building other report sections...
     */
    private Map<String, Object> buildHighValueTransactionSummary(List<TaxTransaction> transactions) {
        List<TaxTransaction> highValue = transactions.stream()
                .filter(t -> t.getTransactionAmount().compareTo(new BigDecimal("10000")) > 0)
                .collect(Collectors.toList());

        return Map.of(
                "count", highValue.size(),
                "totalAmount", calculateTotalTransactionAmount(highValue),
                "totalTax", calculateTotalTaxAmount(highValue)
        );
    }

    private Map<String, Object> buildCrossBorderSummary(List<TaxTransaction> transactions) {
        List<TaxTransaction> crossBorder = transactions.stream()
                .filter(t -> Boolean.TRUE.equals(t.getCrossBorder()))
                .collect(Collectors.toList());

        return Map.of(
                "count", crossBorder.size(),
                "totalAmount", calculateTotalTransactionAmount(crossBorder),
                "totalTax", calculateTotalTaxAmount(crossBorder)
        );
    }

    private Map<String, Object> buildComplianceSummary(List<TaxTransaction> transactions) {
        List<TaxTransaction> requiresCompliance = transactions.stream()
                .filter(TaxTransaction::requiresComplianceFiling)
                .collect(Collectors.toList());

        return Map.of(
                "transactionsRequiringFiling", requiresCompliance.size(),
                "totalComplianceAmount", calculateTotalTransactionAmount(requiresCompliance),
                "totalComplianceTax", calculateTotalTaxAmount(requiresCompliance)
        );
    }

    private Map<String, Object> buildTaxSavingsSummary(List<TaxTransaction> transactions) {
        return Map.of(
                "totalSavings", calculateTotalTaxSavings(transactions),
                "totalExemptions", calculateTotalExemptions(transactions),
                "totalDeductions", calculateTotalDeductions(transactions)
        );
    }

    private BigDecimal calculateTotalExemptions(List<TaxTransaction> transactions) {
        return transactions.stream()
                .map(TaxTransaction::getExemptionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateTotalDeductions(List<TaxTransaction> transactions) {
        return transactions.stream()
                .map(TaxTransaction::getDeductionAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateDataQualityScore(List<TaxTransaction> transactions) {
        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        long completeRecords = transactions.stream()
                .mapToLong(t -> {
                    int score = 0;
                    if (t.getTransactionAmount() != null) score++;
                    if (t.getTaxAmount() != null) score++;
                    if (t.getEffectiveTaxRate() != null) score++;
                    if (t.getJurisdiction() != null) score++;
                    if (t.getTransactionType() != null) score++;
                    return score;
                })
                .sum();

        long maxPossibleScore = transactions.size() * 5L;
        return BigDecimal.valueOf(completeRecords)
                .divide(BigDecimal.valueOf(maxPossibleScore), 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    // Additional placeholder methods for complete implementation
    private List<Map<String, Object>> buildMonthlyBreakdownForUser(List<TaxTransaction> transactions) {
        return buildMonthlyTrends(transactions);
    }

    private Map<String, Object> buildExemptionsSummary(List<TaxTransaction> transactions) {
        return Map.of("totalExemptions", calculateTotalExemptions(transactions));
    }

    private Map<String, Object> buildDeductionsSummary(List<TaxTransaction> transactions) {
        return Map.of("totalDeductions", calculateTotalDeductions(transactions));
    }

    private TaxReportResponse buildJurisdictionSummary(String jurisdiction, LocalDateTime startDate, 
                                                     LocalDateTime endDate, List<TaxTransaction> transactions) {
        // Implementation for jurisdiction-specific summary
        return buildTaxReport(TaxReportRequest.builder()
                .jurisdiction(jurisdiction)
                .startDate(startDate)
                .endDate(endDate)
                .build(), transactions);
    }

    private TaxReportResponse buildCrossBorderReport(LocalDateTime startDate, LocalDateTime endDate, 
                                                   List<TaxTransaction> transactions) {
        // Implementation for cross-border specific report
        return buildTaxReport(TaxReportRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build(), transactions);
    }

    private TaxReportResponse buildHighValueReport(BigDecimal threshold, LocalDateTime startDate, 
                                                 LocalDateTime endDate, List<TaxTransaction> transactions) {
        // Implementation for high-value specific report
        return buildTaxReport(TaxReportRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build(), transactions);
    }

    private TaxReportResponse buildComplianceFilingReport(BigDecimal threshold, LocalDateTime startDate, 
                                                        LocalDateTime endDate, List<TaxTransaction> transactions) {
        // Implementation for compliance filing specific report
        return buildTaxReport(TaxReportRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build(), transactions);
    }
}