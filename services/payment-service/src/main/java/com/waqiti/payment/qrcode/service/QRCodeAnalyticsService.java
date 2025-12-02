package com.waqiti.payment.qrcode.service;

import com.waqiti.payment.qrcode.dto.QRCodeAnalyticsRequest;
import com.waqiti.payment.qrcode.dto.QRCodeAnalyticsResponse;
import com.waqiti.payment.qrcode.domain.QRCodePayment;
import com.waqiti.payment.qrcode.domain.QRCodeType;
import com.waqiti.payment.qrcode.repository.QRCodePaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for QR Code analytics and reporting
 * Provides comprehensive metrics, trends, and business intelligence
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class QRCodeAnalyticsService {

    private final QRCodePaymentRepository qrCodePaymentRepository;
    
    /**
     * Get comprehensive analytics for QR codes
     */
    @PreAuthorize("hasRole('ADMIN') or hasRole('ANALYTICS') or hasRole('MERCHANT')")
    @Cacheable(value = "qr-analytics", key = "#request.hashCode()", unless = "#result == null")
    public QRCodeAnalyticsResponse getAnalytics(QRCodeAnalyticsRequest request) {
        log.info("Generating QR code analytics for request: {}", request);
        
        validateAnalyticsRequest(request);
        
        return QRCodeAnalyticsResponse.builder()
            .overview(generateOverview(request))
            .trends(generateTrends(request))
            .performance(generatePerformance(request))
            .demographics(generateDemographics(request))
            .conversionRates(generateConversionRates(request))
            .topPerformers(generateTopPerformers(request))
            .riskAnalysis(generateRiskAnalysis(request))
            .geographicAnalysis(generateGeographicAnalysis(request))
            .timeSeriesData(generateTimeSeriesData(request))
            .recommendations(generateRecommendations(request))
            .generatedAt(LocalDateTime.now())
            .reportPeriod(formatReportPeriod(request.getStartDate(), request.getEndDate()))
            .build();
    }
    
    /**
     * Generate overview statistics
     */
    private QRCodeAnalyticsResponse.Overview generateOverview(QRCodeAnalyticsRequest request) {
        List<QRCodePayment> payments = getPaymentsForRequest(request);
        
        long totalQRCodes = payments.size();
        long completedPayments = payments.stream()
            .mapToLong(p -> p.getStatus() == QRCodePayment.Status.COMPLETED ? 1 : 0)
            .sum();
            
        BigDecimal totalValue = payments.stream()
            .filter(p -> p.getStatus() == QRCodePayment.Status.COMPLETED)
            .map(p -> p.getFinalAmount() != null ? p.getFinalAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal averageValue = completedPayments > 0 ? 
            totalValue.divide(BigDecimal.valueOf(completedPayments), 2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
            
        double conversionRate = totalQRCodes > 0 ? 
            (double) completedPayments / totalQRCodes * 100 : 0.0;
        
        Map<String, Long> statusBreakdown = payments.stream()
            .collect(Collectors.groupingBy(
                p -> p.getStatus().name(),
                Collectors.counting()
            ));
            
        Map<String, Long> typeBreakdown = payments.stream()
            .collect(Collectors.groupingBy(
                p -> p.getType().name(),
                Collectors.counting()
            ));
        
        return QRCodeAnalyticsResponse.Overview.builder()
            .totalQRCodes(totalQRCodes)
            .completedPayments(completedPayments)
            .totalValue(totalValue)
            .averageValue(averageValue)
            .conversionRate(conversionRate)
            .statusBreakdown(statusBreakdown)
            .typeBreakdown(typeBreakdown)
            .build();
    }
    
    /**
     * Generate trend analysis
     */
    private QRCodeAnalyticsResponse.Trends generateTrends(QRCodeAnalyticsRequest request) {
        List<QRCodePayment> payments = getPaymentsForRequest(request);
        
        // Calculate growth rates
        Map<String, Object> growthRates = calculateGrowthRates(payments, request);
        
        // Seasonal patterns
        Map<String, Object> seasonalPatterns = analyzeSeasonalPatterns(payments);
        
        // Peak usage times
        Map<String, Object> peakTimes = analyzePeakTimes(payments);
        
        // Trend indicators
        Map<String, Object> indicators = calculateTrendIndicators(payments);
        
        return QRCodeAnalyticsResponse.Trends.builder()
            .growthRates(growthRates)
            .seasonalPatterns(seasonalPatterns)
            .peakUsageTimes(peakTimes)
            .trendIndicators(indicators)
            .build();
    }
    
    /**
     * Generate performance metrics
     */
    private QRCodeAnalyticsResponse.Performance generatePerformance(QRCodeAnalyticsRequest request) {
        List<QRCodePayment> payments = getPaymentsForRequest(request);
        
        // Calculate average processing time
        double avgProcessingTime = payments.stream()
            .filter(p -> p.getProcessedAt() != null && p.getCreatedAt() != null)
            .mapToDouble(p -> java.time.Duration.between(p.getCreatedAt(), p.getProcessedAt()).toSeconds())
            .average()
            .orElse(0.0);
            
        // Success rates by type
        Map<String, Double> successRatesByType = payments.stream()
            .collect(Collectors.groupingBy(
                p -> p.getType().name(),
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    list -> {
                        long total = list.size();
                        long success = list.stream()
                            .mapToLong(p -> p.getStatus() == QRCodePayment.Status.COMPLETED ? 1 : 0)
                            .sum();
                        return total > 0 ? (double) success / total * 100 : 0.0;
                    }
                )
            ));
            
        // Error analysis
        Map<String, Long> errorBreakdown = payments.stream()
            .filter(p -> p.getStatus() == QRCodePayment.Status.FAILED)
            .collect(Collectors.groupingBy(
                p -> p.getErrorMessage() != null ? p.getErrorMessage() : "Unknown Error",
                Collectors.counting()
            ));
            
        // Performance scores
        Map<String, Object> scores = calculatePerformanceScores(payments);
        
        return QRCodeAnalyticsResponse.Performance.builder()
            .averageProcessingTime(avgProcessingTime)
            .successRatesByType(successRatesByType)
            .errorBreakdown(errorBreakdown)
            .performanceScores(scores)
            .build();
    }
    
    /**
     * Generate demographic analysis
     */
    private QRCodeAnalyticsResponse.Demographics generateDemographics(QRCodeAnalyticsRequest request) {
        List<QRCodePayment> payments = getPaymentsForRequest(request);
        
        // User segments
        Map<String, Long> userSegments = categorizeUserSegments(payments);
        
        // Merchant categories
        Map<String, Long> merchantCategories = payments.stream()
            .filter(p -> p.getCategory() != null)
            .collect(Collectors.groupingBy(
                QRCodePayment::getCategory,
                Collectors.counting()
            ));
            
        // Device types
        Map<String, Long> deviceTypes = payments.stream()
            .filter(p -> p.getScanMethod() != null)
            .collect(Collectors.groupingBy(
                QRCodePayment::getScanMethod,
                Collectors.counting()
            ));
            
        // Geographic distribution
        Map<String, Object> geographic = analyzeGeographicDistribution(payments);
        
        return QRCodeAnalyticsResponse.Demographics.builder()
            .userSegments(userSegments)
            .merchantCategories(merchantCategories)
            .deviceTypes(deviceTypes)
            .geographicDistribution(geographic)
            .build();
    }
    
    /**
     * Generate conversion rate analysis
     */
    private QRCodeAnalyticsResponse.ConversionRates generateConversionRates(QRCodeAnalyticsRequest request) {
        List<QRCodePayment> payments = getPaymentsForRequest(request);
        
        // Overall conversion rate
        long totalScans = payments.stream().mapToLong(QRCodePayment::getScanCount).sum();
        long completedPayments = payments.stream()
            .mapToLong(p -> p.getStatus() == QRCodePayment.Status.COMPLETED ? 1 : 0)
            .sum();
            
        double overallRate = totalScans > 0 ? (double) completedPayments / totalScans * 100 : 0.0;
        
        // Conversion by QR type
        Map<String, Double> byType = calculateConversionByType(payments);
        
        // Conversion by time periods
        Map<String, Double> byTimePeriod = calculateConversionByTimePeriod(payments);
        
        // Funnel analysis
        Map<String, Object> funnelAnalysis = analyzeFunnel(payments);
        
        return QRCodeAnalyticsResponse.ConversionRates.builder()
            .overallRate(overallRate)
            .byType(byType)
            .byTimePeriod(byTimePeriod)
            .funnelAnalysis(funnelAnalysis)
            .build();
    }
    
    /**
     * Generate top performers analysis
     */
    private QRCodeAnalyticsResponse.TopPerformers generateTopPerformers(QRCodeAnalyticsRequest request) {
        // Top merchants by volume
        List<Object[]> topMerchants = qrCodePaymentRepository.getTopMerchantsByVolume(
            QRCodePayment.Status.COMPLETED,
            request.getStartDate(),
            PageRequest.of(0, 10)
        );
        
        // Top users by frequency
        List<Object[]> topUsers = qrCodePaymentRepository.getTopUsersByFrequency(
            QRCodePayment.Status.COMPLETED,
            request.getStartDate(),
            PageRequest.of(0, 10)
        );
        
        // Best performing QR types
        Map<String, Object> topQRTypes = findTopQRTypes(request);
        
        return QRCodeAnalyticsResponse.TopPerformers.builder()
            .topMerchants(formatTopMerchants(topMerchants))
            .topUsers(formatTopUsers(topUsers))
            .topQRTypes(topQRTypes)
            .build();
    }
    
    /**
     * Generate risk analysis
     */
    private QRCodeAnalyticsResponse.RiskAnalysis generateRiskAnalysis(QRCodeAnalyticsRequest request) {
        List<QRCodePayment> payments = getPaymentsForRequest(request);
        
        // High-risk transactions
        List<QRCodePayment> highRisk = qrCodePaymentRepository.findHighRiskTransactions(
            70, QRCodePayment.Status.COMPLETED
        );
        
        // Fraud indicators
        Map<String, Object> fraudIndicators = analyzeFraudIndicators(payments);
        
        // Risk scores distribution
        Map<String, Long> riskDistribution = payments.stream()
            .filter(p -> p.getFraudScore() != null)
            .collect(Collectors.groupingBy(
                p -> categorizeRisk(p.getFraudScore()),
                Collectors.counting()
            ));
            
        // Suspicious patterns
        List<Object> suspiciousPatterns = identifySuspiciousPatterns(payments);
        
        return QRCodeAnalyticsResponse.RiskAnalysis.builder()
            .highRiskTransactions(highRisk.size())
            .fraudIndicators(fraudIndicators)
            .riskDistribution(riskDistribution)
            .suspiciousPatterns(suspiciousPatterns)
            .build();
    }
    
    // Helper methods
    
    private List<QRCodePayment> getPaymentsForRequest(QRCodeAnalyticsRequest request) {
        if (request.getUserId() != null) {
            return qrCodePaymentRepository.findByUserIdAndCreatedAtBetween(
                request.getUserId(), request.getStartDate(), request.getEndDate()
            );
        } else if (request.getMerchantId() != null) {
            return qrCodePaymentRepository.findByMerchantIdAndCreatedAtBetween(
                request.getMerchantId(), request.getStartDate(), request.getEndDate()
            );
        } else {
            return qrCodePaymentRepository.findByCreatedAtBetween(
                request.getStartDate(), request.getEndDate()
            );
        }
    }
    
    private void validateAnalyticsRequest(QRCodeAnalyticsRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("Start date cannot be after end date");
        }
    }
    
    // Additional helper methods would be implemented here...
    // Including: calculateGrowthRates, analyzeSeasonalPatterns, etc.
    
    private Map<String, Object> calculateGrowthRates(List<QRCodePayment> payments, QRCodeAnalyticsRequest request) {
        // Implementation for growth rate calculations
        return new HashMap<>();
    }
    
    private Map<String, Object> analyzeSeasonalPatterns(List<QRCodePayment> payments) {
        // Implementation for seasonal analysis
        return new HashMap<>();
    }
    
    private String formatReportPeriod(LocalDateTime start, LocalDateTime end) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return start.format(formatter) + " to " + end.format(formatter);
    }
    
    // More helper methods would continue...
}