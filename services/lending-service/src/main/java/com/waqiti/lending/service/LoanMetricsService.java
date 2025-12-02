package com.waqiti.lending.service;

import com.waqiti.lending.repository.LoanApplicationRepository;
import com.waqiti.lending.repository.LoanPaymentRepository;
import com.waqiti.lending.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loan Metrics Service
 * Provides portfolio analytics and metrics
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanMetricsService {

    private final LoanRepository loanRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanPaymentRepository loanPaymentRepository;

    /**
     * Get comprehensive portfolio metrics
     */
    @Transactional(readOnly = true)
    public PortfolioMetrics getPortfolioMetrics() {
        PortfolioMetrics metrics = new PortfolioMetrics();

        // Loan statistics
        List<Object[]> loanStats = loanRepository.getLoanPortfolioStatistics();
        Map<String, LoanStatusMetric> loanMetrics = new HashMap<>();

        for (Object[] stat : loanStats) {
            String status = stat[0].toString();
            Long count = ((Number) stat[1]).longValue();
            BigDecimal totalBalance = stat[2] != null ? (BigDecimal) stat[2] : BigDecimal.ZERO;

            LoanStatusMetric metric = new LoanStatusMetric();
            metric.setCount(count);
            metric.setTotalOutstanding(totalBalance);

            loanMetrics.put(status, metric);
        }

        metrics.setLoansByStatus(loanMetrics);

        // Application statistics
        List<Object[]> appStats = loanApplicationRepository.getApplicationStatistics();
        Map<String, Long> appMetrics = new HashMap<>();

        for (Object[] stat : appStats) {
            String status = stat[0].toString();
            Long count = ((Number) stat[1]).longValue();
            appMetrics.put(status, count);
        }

        metrics.setApplicationsByStatus(appMetrics);

        // Total outstanding balance
        BigDecimal totalOutstanding = loanRepository.calculateTotalOutstandingBalance();
        metrics.setTotalOutstandingBalance(totalOutstanding != null ? totalOutstanding : BigDecimal.ZERO);

        log.debug("Portfolio metrics calculated: {} loans, {} total outstanding",
                loanMetrics.values().stream().mapToLong(LoanStatusMetric::getCount).sum(),
                metrics.getTotalOutstandingBalance());

        return metrics;
    }

    /**
     * Portfolio Metrics DTO
     */
    @lombok.Data
    public static class PortfolioMetrics {
        private Map<String, LoanStatusMetric> loansByStatus;
        private Map<String, Long> applicationsByStatus;
        private BigDecimal totalOutstandingBalance;
    }

    /**
     * Loan Status Metric DTO
     */
    @lombok.Data
    public static class LoanStatusMetric {
        private Long count;
        private BigDecimal totalOutstanding;
    }
}
