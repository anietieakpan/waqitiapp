package com.waqiti.payment.invoice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Analytics service for invoice and payment metrics
 */
@Slf4j
@Service
public class AnalyticsService {
    
    private final Map<String, MetricData> metricsCache = new ConcurrentHashMap<>();
    
    /**
     * Track invoice creation
     */
    public void trackInvoiceCreation(Invoice invoice) {
        log.debug("Tracking invoice creation: {}", invoice.getInvoiceNumber());
        
        String metricKey = "invoice.created." + invoice.getBusinessProfileId();
        incrementMetric(metricKey);
        
        // Track by status
        incrementMetric("invoice.status." + invoice.getStatus());
        
        // Track by amount range
        trackAmountRange("invoice.amount", invoice.getTotalAmount());
        
        // Track by currency
        incrementMetric("invoice.currency." + invoice.getCurrency());
        
        // Store detailed analytics
        storeInvoiceAnalytics(invoice);
    }
    
    /**
     * Track invoice payment
     */
    public void trackInvoicePayment(Invoice invoice, BigDecimal paymentAmount) {
        log.debug("Tracking invoice payment: {} amount: {}", invoice.getInvoiceNumber(), paymentAmount);
        
        String metricKey = "invoice.paid." + invoice.getBusinessProfileId();
        incrementMetric(metricKey);
        
        // Track payment amount
        addValueMetric("invoice.payment.amount", paymentAmount);
        
        // Track payment timing
        long daysToPayment = calculateDaysToPayment(invoice);
        addValueMetric("invoice.payment.days", BigDecimal.valueOf(daysToPayment));
        
        // Track payment method
        incrementMetric("invoice.payment.method." + invoice.getPaymentMethod());
    }
    
    /**
     * Track invoice overdue
     */
    public void trackInvoiceOverdue(Invoice invoice) {
        log.debug("Tracking overdue invoice: {}", invoice.getInvoiceNumber());
        
        incrementMetric("invoice.overdue." + invoice.getBusinessProfileId());
        
        long daysOverdue = calculateDaysOverdue(invoice);
        addValueMetric("invoice.overdue.days", BigDecimal.valueOf(daysOverdue));
        
        // Track overdue amount
        addValueMetric("invoice.overdue.amount", invoice.getTotalAmount());
    }
    
    /**
     * Get invoice analytics for a business
     */
    public InvoiceAnalytics getInvoiceAnalytics(String businessProfileId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting invoice analytics for business: {} from {} to {}", businessProfileId, startDate, endDate);
        
        InvoiceAnalytics analytics = new InvoiceAnalytics();
        analytics.setBusinessProfileId(businessProfileId);
        analytics.setStartDate(startDate);
        analytics.setEndDate(endDate);
        
        // Calculate metrics
        analytics.setTotalInvoices(getMetricCount("invoice.created." + businessProfileId));
        analytics.setPaidInvoices(getMetricCount("invoice.paid." + businessProfileId));
        analytics.setOverdueInvoices(getMetricCount("invoice.overdue." + businessProfileId));
        
        // Calculate amounts
        analytics.setTotalAmount(getMetricSum("invoice.amount." + businessProfileId));
        analytics.setPaidAmount(getMetricSum("invoice.payment.amount." + businessProfileId));
        analytics.setOverdueAmount(getMetricSum("invoice.overdue.amount." + businessProfileId));
        
        // Calculate averages
        analytics.setAverageInvoiceAmount(calculateAverage(analytics.getTotalAmount(), analytics.getTotalInvoices()));
        analytics.setAverageDaysToPayment(getMetricAverage("invoice.payment.days"));
        analytics.setPaymentRate(calculatePaymentRate(analytics.getPaidInvoices(), analytics.getTotalInvoices()));
        
        // Get top customers
        analytics.setTopCustomers(getTopCustomers(businessProfileId, 10));
        
        // Get payment trends
        analytics.setPaymentTrends(getPaymentTrends(businessProfileId, startDate, endDate));
        
        return analytics;
    }
    
    /**
     * Get payment trends
     */
    public List<PaymentTrend> getPaymentTrends(String businessProfileId, LocalDate startDate, LocalDate endDate) {
        List<PaymentTrend> trends = new ArrayList<>();
        
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            PaymentTrend trend = new PaymentTrend();
            trend.setDate(current);
            trend.setInvoicesCreated(getDailyMetric("invoice.created", businessProfileId, current));
            trend.setInvoicesPaid(getDailyMetric("invoice.paid", businessProfileId, current));
            trend.setTotalAmount(getDailySum("invoice.amount", businessProfileId, current));
            trend.setPaidAmount(getDailySum("invoice.payment.amount", businessProfileId, current));
            
            trends.add(trend);
            current = current.plusDays(1);
        }
        
        return trends;
    }
    
    /**
     * Get revenue analytics
     */
    public RevenueAnalytics getRevenueAnalytics(String businessProfileId, Period period) {
        log.info("Getting revenue analytics for business: {} period: {}", businessProfileId, period);
        
        RevenueAnalytics analytics = new RevenueAnalytics();
        analytics.setBusinessProfileId(businessProfileId);
        analytics.setPeriod(period);
        
        // Calculate current period revenue
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculatePeriodStart(period);
        
        BigDecimal currentRevenue = calculatePeriodRevenue(businessProfileId, startDate, endDate);
        analytics.setCurrentPeriodRevenue(currentRevenue);
        
        // Calculate previous period revenue
        LocalDate prevEndDate = startDate.minusDays(1);
        LocalDate prevStartDate = calculatePreviousPeriodStart(period, prevEndDate);
        
        BigDecimal previousRevenue = calculatePeriodRevenue(businessProfileId, prevStartDate, prevEndDate);
        analytics.setPreviousPeriodRevenue(previousRevenue);
        
        // Calculate growth
        BigDecimal growth = calculateGrowth(currentRevenue, previousRevenue);
        analytics.setGrowthRate(growth);
        analytics.setGrowthAmount(currentRevenue.subtract(previousRevenue));
        
        // Get revenue by category
        analytics.setRevenueByCategory(getRevenueByCategory(businessProfileId, startDate, endDate));
        
        // Get revenue by customer
        analytics.setRevenueByCustomer(getRevenueByCustomer(businessProfileId, startDate, endDate));
        
        // Calculate projections
        analytics.setProjectedRevenue(calculateProjectedRevenue(businessProfileId, currentRevenue, period));
        
        return analytics;
    }
    
    /**
     * Track customer behavior
     */
    public void trackCustomerBehavior(String customerId, String action, Map<String, Object> properties) {
        log.debug("Tracking customer behavior: {} action: {}", customerId, action);
        
        String metricKey = "customer.behavior." + customerId + "." + action;
        incrementMetric(metricKey);
        
        // Store detailed behavior data
        storeBehaviorData(customerId, action, properties);
    }
    
    // Helper methods
    
    private void incrementMetric(String key) {
        metricsCache.computeIfAbsent(key, k -> new MetricData())
                   .increment();
    }
    
    private void addValueMetric(String key, BigDecimal value) {
        metricsCache.computeIfAbsent(key, k -> new MetricData())
                   .addValue(value);
    }
    
    private long getMetricCount(String key) {
        MetricData metric = metricsCache.get(key);
        return metric != null ? metric.getCount() : 0;
    }
    
    private BigDecimal getMetricSum(String key) {
        MetricData metric = metricsCache.get(key);
        return metric != null ? metric.getSum() : BigDecimal.ZERO;
    }
    
    private BigDecimal getMetricAverage(String key) {
        MetricData metric = metricsCache.get(key);
        return metric != null ? metric.getAverage() : BigDecimal.ZERO;
    }
    
    private void trackAmountRange(String prefix, BigDecimal amount) {
        String range = determineAmountRange(amount);
        incrementMetric(prefix + ".range." + range);
    }
    
    private String determineAmountRange(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(100)) < 0) return "0-100";
        if (amount.compareTo(BigDecimal.valueOf(500)) < 0) return "100-500";
        if (amount.compareTo(BigDecimal.valueOf(1000)) < 0) return "500-1000";
        if (amount.compareTo(BigDecimal.valueOf(5000)) < 0) return "1000-5000";
        if (amount.compareTo(BigDecimal.valueOf(10000)) < 0) return "5000-10000";
        return "10000+";
    }
    
    private long calculateDaysToPayment(Invoice invoice) {
        if (invoice.getPaidDate() == null) return 0;
        return ChronoUnit.DAYS.between(invoice.getIssueDate(), invoice.getPaidDate());
    }
    
    private long calculateDaysOverdue(Invoice invoice) {
        if (invoice.getDueDate() == null) return 0;
        LocalDate today = LocalDate.now();
        if (today.isBefore(invoice.getDueDate())) return 0;
        return ChronoUnit.DAYS.between(invoice.getDueDate(), today);
    }
    
    private BigDecimal calculateAverage(BigDecimal total, long count) {
        if (count == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculatePaymentRate(long paid, long total) {
        if (total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(paid * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateGrowth(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                     .multiply(BigDecimal.valueOf(100))
                     .divide(previous, 2, RoundingMode.HALF_UP);
    }
    
    private LocalDate calculatePeriodStart(Period period) {
        LocalDate today = LocalDate.now();
        switch (period) {
            case DAILY: return today;
            case WEEKLY: return today.minusWeeks(1);
            case MONTHLY: return today.minusMonths(1);
            case QUARTERLY: return today.minusMonths(3);
            case YEARLY: return today.minusYears(1);
            default: return today.minusMonths(1);
        }
    }
    
    private LocalDate calculatePreviousPeriodStart(Period period, LocalDate endDate) {
        switch (period) {
            case DAILY: return endDate;
            case WEEKLY: return endDate.minusWeeks(1);
            case MONTHLY: return endDate.minusMonths(1);
            case QUARTERLY: return endDate.minusMonths(3);
            case YEARLY: return endDate.minusYears(1);
            default: return endDate.minusMonths(1);
        }
    }
    
    private BigDecimal calculatePeriodRevenue(String businessProfileId, LocalDate startDate, LocalDate endDate) {
        // Placeholder implementation - would query database
        return getMetricSum("invoice.payment.amount." + businessProfileId);
    }
    
    private BigDecimal calculateProjectedRevenue(String businessProfileId, BigDecimal currentRevenue, Period period) {
        // Simple projection based on growth trend
        BigDecimal growthRate = BigDecimal.valueOf(0.1); // 10% growth assumption
        return currentRevenue.multiply(BigDecimal.ONE.add(growthRate));
    }
    
    private List<CustomerMetric> getTopCustomers(String businessProfileId, int limit) {
        // Placeholder implementation
        return new ArrayList<>();
    }
    
    private Map<String, BigDecimal> getRevenueByCategory(String businessProfileId, LocalDate startDate, LocalDate endDate) {
        // Placeholder implementation
        return new HashMap<>();
    }
    
    private Map<String, BigDecimal> getRevenueByCustomer(String businessProfileId, LocalDate startDate, LocalDate endDate) {
        // Placeholder implementation
        return new HashMap<>();
    }
    
    private long getDailyMetric(String metric, String businessProfileId, LocalDate date) {
        // Placeholder implementation
        return 0;
    }
    
    private BigDecimal getDailySum(String metric, String businessProfileId, LocalDate date) {
        // Placeholder implementation
        return BigDecimal.ZERO;
    }
    
    private void storeInvoiceAnalytics(Invoice invoice) {
        // Store detailed analytics data
        log.debug("Storing invoice analytics: {}", invoice.getInvoiceNumber());
    }
    
    private void storeBehaviorData(String customerId, String action, Map<String, Object> properties) {
        // Store customer behavior data
        log.debug("Storing behavior data for customer: {}", customerId);
    }
    
    // Inner classes
    
    private static class MetricData {
        private long count = 0;
        private BigDecimal sum = BigDecimal.ZERO;
        private final List<BigDecimal> values = new ArrayList<>();
        
        public synchronized void increment() {
            count++;
        }
        
        public synchronized void addValue(BigDecimal value) {
            count++;
            sum = sum.add(value);
            values.add(value);
        }
        
        public long getCount() {
            return count;
        }
        
        public BigDecimal getSum() {
            return sum;
        }
        
        public BigDecimal getAverage() {
            if (count == 0) return BigDecimal.ZERO;
            return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        }
    }
    
    public static class InvoiceAnalytics {
        private String businessProfileId;
        private LocalDate startDate;
        private LocalDate endDate;
        private long totalInvoices;
        private long paidInvoices;
        private long overdueInvoices;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private BigDecimal overdueAmount;
        private BigDecimal averageInvoiceAmount;
        private BigDecimal averageDaysToPayment;
        private BigDecimal paymentRate;
        private List<CustomerMetric> topCustomers;
        private List<PaymentTrend> paymentTrends;
        
        // Getters and setters
        public String getBusinessProfileId() { return businessProfileId; }
        public void setBusinessProfileId(String businessProfileId) { this.businessProfileId = businessProfileId; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public long getTotalInvoices() { return totalInvoices; }
        public void setTotalInvoices(long totalInvoices) { this.totalInvoices = totalInvoices; }
        public long getPaidInvoices() { return paidInvoices; }
        public void setPaidInvoices(long paidInvoices) { this.paidInvoices = paidInvoices; }
        public long getOverdueInvoices() { return overdueInvoices; }
        public void setOverdueInvoices(long overdueInvoices) { this.overdueInvoices = overdueInvoices; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
        public BigDecimal getOverdueAmount() { return overdueAmount; }
        public void setOverdueAmount(BigDecimal overdueAmount) { this.overdueAmount = overdueAmount; }
        public BigDecimal getAverageInvoiceAmount() { return averageInvoiceAmount; }
        public void setAverageInvoiceAmount(BigDecimal averageInvoiceAmount) { this.averageInvoiceAmount = averageInvoiceAmount; }
        public BigDecimal getAverageDaysToPayment() { return averageDaysToPayment; }
        public void setAverageDaysToPayment(BigDecimal averageDaysToPayment) { this.averageDaysToPayment = averageDaysToPayment; }
        public BigDecimal getPaymentRate() { return paymentRate; }
        public void setPaymentRate(BigDecimal paymentRate) { this.paymentRate = paymentRate; }
        public List<CustomerMetric> getTopCustomers() { return topCustomers; }
        public void setTopCustomers(List<CustomerMetric> topCustomers) { this.topCustomers = topCustomers; }
        public List<PaymentTrend> getPaymentTrends() { return paymentTrends; }
        public void setPaymentTrends(List<PaymentTrend> paymentTrends) { this.paymentTrends = paymentTrends; }
    }
    
    public static class RevenueAnalytics {
        private String businessProfileId;
        private Period period;
        private BigDecimal currentPeriodRevenue;
        private BigDecimal previousPeriodRevenue;
        private BigDecimal growthRate;
        private BigDecimal growthAmount;
        private Map<String, BigDecimal> revenueByCategory;
        private Map<String, BigDecimal> revenueByCustomer;
        private BigDecimal projectedRevenue;
        
        // Getters and setters
        public String getBusinessProfileId() { return businessProfileId; }
        public void setBusinessProfileId(String businessProfileId) { this.businessProfileId = businessProfileId; }
        public Period getPeriod() { return period; }
        public void setPeriod(Period period) { this.period = period; }
        public BigDecimal getCurrentPeriodRevenue() { return currentPeriodRevenue; }
        public void setCurrentPeriodRevenue(BigDecimal currentPeriodRevenue) { this.currentPeriodRevenue = currentPeriodRevenue; }
        public BigDecimal getPreviousPeriodRevenue() { return previousPeriodRevenue; }
        public void setPreviousPeriodRevenue(BigDecimal previousPeriodRevenue) { this.previousPeriodRevenue = previousPeriodRevenue; }
        public BigDecimal getGrowthRate() { return growthRate; }
        public void setGrowthRate(BigDecimal growthRate) { this.growthRate = growthRate; }
        public BigDecimal getGrowthAmount() { return growthAmount; }
        public void setGrowthAmount(BigDecimal growthAmount) { this.growthAmount = growthAmount; }
        public Map<String, BigDecimal> getRevenueByCategory() { return revenueByCategory; }
        public void setRevenueByCategory(Map<String, BigDecimal> revenueByCategory) { this.revenueByCategory = revenueByCategory; }
        public Map<String, BigDecimal> getRevenueByCustomer() { return revenueByCustomer; }
        public void setRevenueByCustomer(Map<String, BigDecimal> revenueByCustomer) { this.revenueByCustomer = revenueByCustomer; }
        public BigDecimal getProjectedRevenue() { return projectedRevenue; }
        public void setProjectedRevenue(BigDecimal projectedRevenue) { this.projectedRevenue = projectedRevenue; }
    }
    
    public static class PaymentTrend {
        private LocalDate date;
        private long invoicesCreated;
        private long invoicesPaid;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        
        // Getters and setters
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public long getInvoicesCreated() { return invoicesCreated; }
        public void setInvoicesCreated(long invoicesCreated) { this.invoicesCreated = invoicesCreated; }
        public long getInvoicesPaid() { return invoicesPaid; }
        public void setInvoicesPaid(long invoicesPaid) { this.invoicesPaid = invoicesPaid; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    }
    
    public static class CustomerMetric {
        private String customerId;
        private String customerName;
        private long invoiceCount;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private BigDecimal averagePaymentTime;
        
        // Getters and setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public long getInvoiceCount() { return invoiceCount; }
        public void setInvoiceCount(long invoiceCount) { this.invoiceCount = invoiceCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getPaidAmount() { return paidAmount; }
        public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
        public BigDecimal getAveragePaymentTime() { return averagePaymentTime; }
        public void setAveragePaymentTime(BigDecimal averagePaymentTime) { this.averagePaymentTime = averagePaymentTime; }
    }
    
    public enum Period {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    }
}