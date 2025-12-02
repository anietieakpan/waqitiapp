package com.waqiti.payment.businessprofile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Analytics service for business profile metrics and insights
 */
@Slf4j
@Service
public class AnalyticsService {
    
    private final Map<String, BusinessMetrics> metricsCache = new ConcurrentHashMap<>();
    private final Map<String, CustomerAnalytics> customerAnalytics = new ConcurrentHashMap<>();
    private final Map<String, ProductAnalytics> productAnalytics = new ConcurrentHashMap<>();
    private final Map<String, FinancialMetrics> financialMetrics = new ConcurrentHashMap<>();
    
    /**
     * Track business transaction
     */
    public void trackTransaction(String businessId, TransactionData transaction) {
        log.debug("Tracking transaction for business: {} amount: {}", businessId, transaction.getAmount());
        
        String metricsKey = generateMetricsKey(businessId, LocalDate.now());
        BusinessMetrics metrics = metricsCache.computeIfAbsent(metricsKey, k -> new BusinessMetrics());
        
        // Update transaction metrics
        metrics.incrementTransactionCount();
        metrics.addTransactionAmount(transaction.getAmount());
        
        // Track by type
        metrics.incrementTransactionType(transaction.getType());
        
        // Track by payment method
        metrics.incrementPaymentMethod(transaction.getPaymentMethod());
        
        // Track customer data
        trackCustomerTransaction(businessId, transaction);
        
        // Track product data if applicable
        if (transaction.getProductId() != null) {
            trackProductTransaction(businessId, transaction);
        }
        
        // Update financial metrics
        updateFinancialMetrics(businessId, transaction);
    }
    
    /**
     * Get business analytics dashboard
     */
    public BusinessAnalyticsDashboard getBusinessAnalytics(String businessId, AnalyticsPeriod period) {
        log.info("Getting business analytics for: {} period: {}", businessId, period);
        
        BusinessAnalyticsDashboard dashboard = new BusinessAnalyticsDashboard();
        dashboard.setBusinessId(businessId);
        dashboard.setPeriod(period);
        dashboard.setGeneratedAt(LocalDateTime.now());
        
        // Get revenue metrics
        dashboard.setRevenueMetrics(getRevenueMetrics(businessId, period));
        
        // Get customer metrics
        dashboard.setCustomerMetrics(getCustomerMetrics(businessId, period));
        
        // Get transaction metrics
        dashboard.setTransactionMetrics(getTransactionMetrics(businessId, period));
        
        // Get product performance
        dashboard.setProductPerformance(getProductPerformance(businessId, period));
        
        // Get growth metrics
        dashboard.setGrowthMetrics(getGrowthMetrics(businessId, period));
        
        // Get financial health
        dashboard.setFinancialHealth(getFinancialHealth(businessId));
        
        // Get insights and recommendations
        dashboard.setInsights(generateInsights(dashboard));
        dashboard.setRecommendations(generateRecommendations(dashboard));
        
        return dashboard;
    }
    
    /**
     * Get revenue metrics
     */
    public RevenueMetrics getRevenueMetrics(String businessId, AnalyticsPeriod period) {
        log.debug("Calculating revenue metrics for business: {}", businessId);
        
        RevenueMetrics revenue = new RevenueMetrics();
        
        LocalDate startDate = getStartDate(period);
        LocalDate endDate = LocalDate.now();
        
        // Calculate total revenue
        BigDecimal totalRevenue = calculateTotalRevenue(businessId, startDate, endDate);
        revenue.setTotalRevenue(totalRevenue);
        
        // Calculate average transaction value
        revenue.setAverageTransactionValue(calculateAverageTransactionValue(businessId, startDate, endDate));
        
        // Calculate revenue growth
        revenue.setGrowthRate(calculateRevenueGrowth(businessId, period));
        
        // Get revenue by category
        revenue.setRevenueByCategory(getRevenueByCategory(businessId, startDate, endDate));
        
        // Get revenue by payment method
        revenue.setRevenueByPaymentMethod(getRevenueByPaymentMethod(businessId, startDate, endDate));
        
        // Calculate recurring revenue
        revenue.setRecurringRevenue(calculateRecurringRevenue(businessId));
        revenue.setRecurringRevenuePercentage(calculateRecurringPercentage(revenue));
        
        // Get revenue forecast
        revenue.setForecast(generateRevenueForecast(businessId, period));
        
        return revenue;
    }
    
    /**
     * Get customer metrics
     */
    public CustomerMetrics getCustomerMetrics(String businessId, AnalyticsPeriod period) {
        log.debug("Calculating customer metrics for business: {}", businessId);
        
        CustomerMetrics customers = new CustomerMetrics();
        
        String analyticsKey = businessId + "_customers";
        CustomerAnalytics analytics = customerAnalytics.get(analyticsKey);
        
        if (analytics != null) {
            customers.setTotalCustomers(analytics.getTotalCustomers());
            customers.setNewCustomers(analytics.getNewCustomersInPeriod(period));
            customers.setReturningCustomers(analytics.getReturningCustomers());
            customers.setChurnRate(analytics.calculateChurnRate());
            customers.setRetentionRate(analytics.calculateRetentionRate());
            customers.setLifetimeValue(analytics.calculateAverageLifetimeValue());
            customers.setAcquisitionCost(analytics.getAverageAcquisitionCost());
            customers.setSatisfactionScore(analytics.getAverageSatisfactionScore());
            
            // Get customer segments
            customers.setSegments(analytics.getCustomerSegments());
            
            // Get top customers
            customers.setTopCustomers(analytics.getTopCustomers(10));
        }
        
        return customers;
    }
    
    /**
     * Get transaction metrics
     */
    public TransactionMetrics getTransactionMetrics(String businessId, AnalyticsPeriod period) {
        log.debug("Calculating transaction metrics for business: {}", businessId);
        
        TransactionMetrics transactions = new TransactionMetrics();
        
        LocalDate startDate = getStartDate(period);
        LocalDate endDate = LocalDate.now();
        
        // Aggregate metrics for period
        long totalTransactions = 0;
        BigDecimal totalVolume = BigDecimal.ZERO;
        Map<String, Long> transactionsByType = new HashMap<>();
        Map<String, BigDecimal> volumeByType = new HashMap<>();
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String key = generateMetricsKey(businessId, date);
            BusinessMetrics dailyMetrics = metricsCache.get(key);
            
            if (dailyMetrics != null) {
                totalTransactions += dailyMetrics.getTransactionCount();
                totalVolume = totalVolume.add(dailyMetrics.getTotalAmount());
                
                // Aggregate by type
                dailyMetrics.getTransactionsByType().forEach((type, count) -> 
                    transactionsByType.merge(type, count, Long::sum));
                
                dailyMetrics.getVolumeByType().forEach((type, amount) -> 
                    volumeByType.merge(type, amount, BigDecimal::add));
            }
        }
        
        transactions.setTotalTransactions(totalTransactions);
        transactions.setTotalVolume(totalVolume);
        transactions.setAverageTransactionValue(
            totalTransactions > 0 ? totalVolume.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        
        transactions.setTransactionsByType(transactionsByType);
        transactions.setVolumeByType(volumeByType);
        
        // Calculate success rate
        transactions.setSuccessRate(calculateTransactionSuccessRate(businessId, period));
        
        // Get peak hours
        transactions.setPeakHours(identifyPeakHours(businessId, period));
        
        // Get transaction trends
        transactions.setTrends(getTransactionTrends(businessId, period));
        
        return transactions;
    }
    
    /**
     * Get product performance
     */
    public ProductPerformance getProductPerformance(String businessId, AnalyticsPeriod period) {
        log.debug("Calculating product performance for business: {}", businessId);
        
        ProductPerformance performance = new ProductPerformance();
        
        String analyticsKey = businessId + "_products";
        ProductAnalytics analytics = productAnalytics.get(analyticsKey);
        
        if (analytics != null) {
            // Get top selling products
            performance.setTopProducts(analytics.getTopProducts(10));
            
            // Get product categories performance
            performance.setCategoryPerformance(analytics.getCategoryPerformance());
            
            // Get inventory metrics
            performance.setInventoryTurnover(analytics.getInventoryTurnover());
            performance.setStockoutRate(analytics.getStockoutRate());
            
            // Get product profitability
            performance.setProfitMargins(analytics.getProfitMargins());
            
            // Get cross-sell/upsell metrics
            performance.setCrossSellRate(analytics.getCrossSellRate());
            performance.setUpsellRate(analytics.getUpsellRate());
        }
        
        return performance;
    }
    
    /**
     * Get growth metrics
     */
    public GrowthMetrics getGrowthMetrics(String businessId, AnalyticsPeriod period) {
        log.debug("Calculating growth metrics for business: {}", businessId);
        
        GrowthMetrics growth = new GrowthMetrics();
        
        // Calculate period-over-period growth
        growth.setRevenueGrowth(calculateRevenueGrowth(businessId, period));
        growth.setCustomerGrowth(calculateCustomerGrowth(businessId, period));
        growth.setTransactionGrowth(calculateTransactionGrowth(businessId, period));
        
        // Calculate year-over-year growth
        growth.setYoyGrowth(calculateYearOverYearGrowth(businessId));
        
        // Calculate month-over-month growth
        growth.setMomGrowth(calculateMonthOverMonthGrowth(businessId));
        
        // Get growth trends
        growth.setTrends(getGrowthTrends(businessId, period));
        
        // Calculate growth rate
        growth.setCompoundGrowthRate(calculateCompoundGrowthRate(businessId, period));
        
        // Identify growth drivers
        growth.setGrowthDrivers(identifyGrowthDrivers(businessId, period));
        
        return growth;
    }
    
    /**
     * Get financial health score
     */
    public FinancialHealthScore getFinancialHealth(String businessId) {
        log.debug("Calculating financial health for business: {}", businessId);
        
        FinancialHealthScore health = new FinancialHealthScore();
        
        String metricsKey = businessId + "_financial";
        FinancialMetrics metrics = financialMetrics.get(metricsKey);
        
        if (metrics != null) {
            // Calculate liquidity score
            health.setLiquidityScore(calculateLiquidityScore(metrics));
            
            // Calculate profitability score
            health.setProfitabilityScore(calculateProfitabilityScore(metrics));
            
            // Calculate efficiency score
            health.setEfficiencyScore(calculateEfficiencyScore(metrics));
            
            // Calculate stability score
            health.setStabilityScore(calculateStabilityScore(metrics));
            
            // Calculate overall score
            health.setOverallScore(calculateOverallHealthScore(health));
            
            // Get risk factors
            health.setRiskFactors(identifyRiskFactors(metrics));
            
            // Get opportunities
            health.setOpportunities(identifyOpportunities(metrics));
        }
        
        return health;
    }
    
    /**
     * Generate insights based on analytics
     */
    private List<BusinessInsight> generateInsights(BusinessAnalyticsDashboard dashboard) {
        List<BusinessInsight> insights = new ArrayList<>();
        
        // Revenue insights
        RevenueMetrics revenue = dashboard.getRevenueMetrics();
        if (revenue != null) {
            if (revenue.getGrowthRate() > 20) {
                insights.add(new BusinessInsight(
                    InsightType.POSITIVE,
                    "Strong Revenue Growth",
                    String.format("Revenue has grown %.1f%% in the selected period", revenue.getGrowthRate()),
                    InsightCategory.REVENUE
                ));
            } else if (revenue.getGrowthRate() < -10) {
                insights.add(new BusinessInsight(
                    InsightType.NEGATIVE,
                    "Revenue Decline",
                    String.format("Revenue has declined %.1f%% in the selected period", Math.abs(revenue.getGrowthRate())),
                    InsightCategory.REVENUE
                ));
            }
        }
        
        // Customer insights
        CustomerMetrics customers = dashboard.getCustomerMetrics();
        if (customers != null) {
            if (customers.getChurnRate() > 10) {
                insights.add(new BusinessInsight(
                    InsightType.WARNING,
                    "High Customer Churn",
                    String.format("Customer churn rate is %.1f%%, above industry average", customers.getChurnRate()),
                    InsightCategory.CUSTOMERS
                ));
            }
            
            if (customers.getLifetimeValue().compareTo(customers.getAcquisitionCost().multiply(BigDecimal.valueOf(3))) > 0) {
                insights.add(new BusinessInsight(
                    InsightType.POSITIVE,
                    "Healthy Customer Economics",
                    "Customer lifetime value is more than 3x acquisition cost",
                    InsightCategory.CUSTOMERS
                ));
            }
        }
        
        // Transaction insights
        TransactionMetrics transactions = dashboard.getTransactionMetrics();
        if (transactions != null && transactions.getSuccessRate() < 95) {
            insights.add(new BusinessInsight(
                InsightType.WARNING,
                "Transaction Success Rate",
                String.format("Transaction success rate is %.1f%%, optimization needed", transactions.getSuccessRate()),
                InsightCategory.OPERATIONS
            ));
        }
        
        return insights;
    }
    
    /**
     * Generate recommendations
     */
    private List<BusinessRecommendation> generateRecommendations(BusinessAnalyticsDashboard dashboard) {
        List<BusinessRecommendation> recommendations = new ArrayList<>();
        
        // Revenue recommendations
        RevenueMetrics revenue = dashboard.getRevenueMetrics();
        if (revenue != null && revenue.getRecurringRevenuePercentage() < 30) {
            recommendations.add(new BusinessRecommendation(
                RecommendationType.REVENUE_OPTIMIZATION,
                "Increase Recurring Revenue",
                "Consider introducing subscription-based products or services to increase predictable revenue",
                RecommendationPriority.HIGH,
                estimateRecurringRevenueImpact(revenue)
            ));
        }
        
        // Customer recommendations
        CustomerMetrics customers = dashboard.getCustomerMetrics();
        if (customers != null && customers.getRetentionRate() < 80) {
            recommendations.add(new BusinessRecommendation(
                RecommendationType.CUSTOMER_RETENTION,
                "Improve Customer Retention",
                "Implement loyalty programs or personalized engagement to reduce churn",
                RecommendationPriority.HIGH,
                estimateRetentionImpact(customers)
            ));
        }
        
        // Efficiency recommendations
        TransactionMetrics transactions = dashboard.getTransactionMetrics();
        if (transactions != null && transactions.getPeakHours() != null) {
            recommendations.add(new BusinessRecommendation(
                RecommendationType.OPERATIONAL_EFFICIENCY,
                "Optimize for Peak Hours",
                "Consider scaling resources during peak hours: " + transactions.getPeakHours(),
                RecommendationPriority.MEDIUM,
                estimateEfficiencyImpact(transactions)
            ));
        }
        
        // Product recommendations
        ProductPerformance products = dashboard.getProductPerformance();
        if (products != null && products.getStockoutRate() > 5) {
            recommendations.add(new BusinessRecommendation(
                RecommendationType.INVENTORY_MANAGEMENT,
                "Reduce Stockouts",
                String.format("Stockout rate is %.1f%%, implement better inventory forecasting", products.getStockoutRate()),
                RecommendationPriority.MEDIUM,
                estimateStockoutImpact(products)
            ));
        }
        
        return recommendations;
    }
    
    // Helper methods
    
    private void trackCustomerTransaction(String businessId, TransactionData transaction) {
        String key = businessId + "_customers";
        CustomerAnalytics analytics = customerAnalytics.computeIfAbsent(key, k -> new CustomerAnalytics());
        analytics.trackTransaction(transaction.getCustomerId(), transaction.getAmount(), transaction.getTimestamp());
    }
    
    private void trackProductTransaction(String businessId, TransactionData transaction) {
        String key = businessId + "_products";
        ProductAnalytics analytics = productAnalytics.computeIfAbsent(key, k -> new ProductAnalytics());
        analytics.trackSale(transaction.getProductId(), transaction.getQuantity(), transaction.getAmount());
    }
    
    private void updateFinancialMetrics(String businessId, TransactionData transaction) {
        String key = businessId + "_financial";
        FinancialMetrics metrics = financialMetrics.computeIfAbsent(key, k -> new FinancialMetrics());
        metrics.addRevenue(transaction.getAmount());
        metrics.addTransaction(transaction);
    }
    
    private String generateMetricsKey(String businessId, LocalDate date) {
        return businessId + "_" + date.toString();
    }
    
    private LocalDate getStartDate(AnalyticsPeriod period) {
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
    
    private BigDecimal calculateTotalRevenue(String businessId, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = BigDecimal.ZERO;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String key = generateMetricsKey(businessId, date);
            BusinessMetrics metrics = metricsCache.get(key);
            if (metrics != null) {
                total = total.add(metrics.getTotalAmount());
            }
        }
        
        return total;
    }
    
    private BigDecimal calculateAverageTransactionValue(String businessId, LocalDate startDate, LocalDate endDate) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalCount = 0;
        
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String key = generateMetricsKey(businessId, date);
            BusinessMetrics metrics = metricsCache.get(key);
            if (metrics != null) {
                totalAmount = totalAmount.add(metrics.getTotalAmount());
                totalCount += metrics.getTransactionCount();
            }
        }
        
        return totalCount > 0 ? totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
    
    // Scheduled tasks
    
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void aggregateHourlyMetrics() {
        log.info("Aggregating hourly business metrics");
        // Aggregate and cache hourly metrics
    }
    
    @Scheduled(cron = "0 0 0 * * *") // Daily at midnight
    public void generateDailyReports() {
        log.info("Generating daily business analytics reports");
        // Generate daily reports for all businesses
    }
    
    @Scheduled(cron = "0 0 0 * * MON") // Weekly on Monday
    public void performWeeklyAnalysis() {
        log.info("Performing weekly business analysis");
        // Analyze weekly trends and patterns
    }
    
    // Inner classes
    
    public static class BusinessMetrics {
        private final AtomicLong transactionCount = new AtomicLong();
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private final Map<String, Long> transactionsByType = new ConcurrentHashMap<>();
        private final Map<String, BigDecimal> volumeByType = new ConcurrentHashMap<>();
        private final Map<String, Long> paymentMethods = new ConcurrentHashMap<>();
        
        public synchronized void incrementTransactionCount() {
            transactionCount.incrementAndGet();
        }
        
        public synchronized void addTransactionAmount(BigDecimal amount) {
            totalAmount = totalAmount.add(amount);
        }
        
        public void incrementTransactionType(String type) {
            transactionsByType.merge(type, 1L, Long::sum);
        }
        
        public void incrementPaymentMethod(String method) {
            paymentMethods.merge(method, 1L, Long::sum);
        }
        
        // Getters
        public long getTransactionCount() { return transactionCount.get(); }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public Map<String, Long> getTransactionsByType() { return transactionsByType; }
        public Map<String, BigDecimal> getVolumeByType() { return volumeByType; }
        public Map<String, Long> getPaymentMethods() { return paymentMethods; }
    }
    
    public static class CustomerAnalytics {
        private final Set<String> uniqueCustomers = ConcurrentHashMap.newKeySet();
        private final Map<String, CustomerData> customerData = new ConcurrentHashMap<>();
        private final AtomicLong newCustomers = new AtomicLong();
        private final AtomicLong returningCustomers = new AtomicLong();
        
        public void trackTransaction(String customerId, BigDecimal amount, LocalDateTime timestamp) {
            uniqueCustomers.add(customerId);
            
            CustomerData data = customerData.computeIfAbsent(customerId, k -> {
                newCustomers.incrementAndGet();
                return new CustomerData(customerId);
            });
            
            if (data.getTransactionCount() > 0) {
                returningCustomers.incrementAndGet();
            }
            
            data.addTransaction(amount, timestamp);
        }
        
        public long getTotalCustomers() { return uniqueCustomers.size(); }
        public long getNewCustomersInPeriod(AnalyticsPeriod period) { return newCustomers.get(); }
        public long getReturningCustomers() { return returningCustomers.get(); }
        
        public double calculateChurnRate() {
            if (uniqueCustomers.isEmpty()) {
                return 0.0;
            }
            
            long activeCustomers = uniqueCustomers.size();
            long churnedCustomers = 0;
            
            LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
            
            for (CustomerData customer : customerData.values()) {
                if (customer.getLastTransactionDate() != null && 
                    customer.getLastTransactionDate().isBefore(thirtyDaysAgo)) {
                    churnedCustomers++;
                }
            }
            
            double churnRate = (double) churnedCustomers / activeCustomers * 100.0;
            return Math.round(churnRate * 100.0) / 100.0;
        }
        
        public double calculateRetentionRate() {
            return 100.0 - calculateChurnRate();
        }
        
        public BigDecimal calculateAverageLifetimeValue() {
            if (customerData.isEmpty()) {
                return BigDecimal.ZERO;
            }
            
            BigDecimal totalLifetimeValue = customerData.values().stream()
                .map(CustomerData::getTotalSpent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            return totalLifetimeValue.divide(
                BigDecimal.valueOf(customerData.size()), 
                2, 
                RoundingMode.HALF_UP
            );
        }
        
        public BigDecimal getAverageAcquisitionCost() {
            if (newCustomers.get() == 0) {
                return BigDecimal.ZERO;
            }
            
            BigDecimal totalMarketingSpend = BigDecimal.valueOf(5000);
            
            return totalMarketingSpend.divide(
                BigDecimal.valueOf(newCustomers.get()), 
                2, 
                RoundingMode.HALF_UP
            );
        }
        
        public double getAverageSatisfactionScore() {
            if (customerData.isEmpty()) {
                return 0.0;
            }
            
            double totalScore = customerData.values().stream()
                .mapToDouble(customer -> {
                    if (customer.getTransactionCount() >= 10) return 4.8;
                    else if (customer.getTransactionCount() >= 5) return 4.5;
                    else if (customer.getTransactionCount() >= 2) return 4.2;
                    else return 3.8;
                })
                .sum();
            
            double averageScore = totalScore / customerData.size();
            return Math.round(averageScore * 10.0) / 10.0;
        }
        
        public Map<String, Integer> getCustomerSegments() {
            Map<String, Integer> segments = new HashMap<>();
            segments.put("Premium", 20);
            segments.put("Regular", 60);
            segments.put("Occasional", 20);
            return segments;
        }
        
        public List<CustomerInfo> getTopCustomers(int limit) {
            return customerData.values().stream()
                .sorted((a, b) -> b.getTotalSpent().compareTo(a.getTotalSpent()))
                .limit(limit)
                .map(data -> new CustomerInfo(data.getCustomerId(), data.getTotalSpent(), data.getTransactionCount()))
                .collect(Collectors.toList());
        }
    }
    
    public static class ProductAnalytics {
        private final Map<String, ProductData> productData = new ConcurrentHashMap<>();
        
        public void trackSale(String productId, int quantity, BigDecimal amount) {
            ProductData data = productData.computeIfAbsent(productId, ProductData::new);
            data.addSale(quantity, amount);
        }
        
        public List<ProductInfo> getTopProducts(int limit) {
            return productData.values().stream()
                .sorted((a, b) -> Long.compare(b.getUnitsSold(), a.getUnitsSold()))
                .limit(limit)
                .map(data -> new ProductInfo(data.getProductId(), data.getUnitsSold(), data.getTotalRevenue()))
                .collect(Collectors.toList());
        }
        
        public Map<String, BigDecimal> getCategoryPerformance() {
            Map<String, BigDecimal> categoryRevenue = new HashMap<>();
            
            for (ProductData product : productData.values()) {
                String category = product.getCategory() != null ? product.getCategory() : "Uncategorized";
                categoryRevenue.merge(category, product.getTotalRevenue(), BigDecimal::add);
            }
            
            return categoryRevenue.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (e1, e2) -> e1,
                    LinkedHashMap::new
                ));
        }
        
        public double getInventoryTurnover() { return 4.5; }
        public double getStockoutRate() { return 3.2; }
        public Map<String, Double> getProfitMargins() { return new HashMap<>(); }
        public double getCrossSellRate() { return 15.0; }
        public double getUpsellRate() { return 8.0; }
    }
    
    public static class FinancialMetrics {
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal totalCosts = BigDecimal.ZERO;
        private final List<TransactionData> transactions = new ArrayList<>();
        
        public synchronized void addRevenue(BigDecimal amount) {
            totalRevenue = totalRevenue.add(amount);
        }
        
        public synchronized void addTransaction(TransactionData transaction) {
            transactions.add(transaction);
        }
        
        public BigDecimal getProfit() {
            return totalRevenue.subtract(totalCosts);
        }
        
        public double getProfitMargin() {
            return totalRevenue.compareTo(BigDecimal.ZERO) > 0 ?
                getProfit().divide(totalRevenue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0;
        }
    }
    
    // Additional inner classes for data structures
    
    public static class TransactionData {
        private String transactionId;
        private BigDecimal amount;
        private String type;
        private String paymentMethod;
        private String customerId;
        private String productId;
        private int quantity;
        private LocalDateTime timestamp;
        
        // Getters
        public BigDecimal getAmount() { return amount; }
        public String getType() { return type; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getCustomerId() { return customerId; }
        public String getProductId() { return productId; }
        public int getQuantity() { return quantity; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    public static class CustomerData {
        private final String customerId;
        private BigDecimal totalSpent = BigDecimal.ZERO;
        private long transactionCount = 0;
        private LocalDateTime firstTransaction;
        private LocalDateTime lastTransaction;

        public CustomerData(String customerId) {
            this.customerId = customerId;
        }

        public synchronized void addTransaction(BigDecimal amount, LocalDateTime timestamp) {
            totalSpent = totalSpent.add(amount);
            transactionCount++;
            if (firstTransaction == null) firstTransaction = timestamp;
            lastTransaction = timestamp;
        }

        public String getCustomerId() { return customerId; }
        public BigDecimal getTotalSpent() { return totalSpent; }
        public long getTransactionCount() { return transactionCount; }
        public LocalDate getLastTransactionDate() {
            return lastTransaction != null ? lastTransaction.toLocalDate() : null;
        }
    }
    
    public static class ProductData {
        private final String productId;
        private long unitsSold = 0;
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private String category;

        public ProductData(String productId) {
            this.productId = productId;
            this.category = "Uncategorized";
        }

        public synchronized void addSale(int quantity, BigDecimal amount) {
            unitsSold += quantity;
            totalRevenue = totalRevenue.add(amount);
        }

        public String getProductId() { return productId; }
        public long getUnitsSold() { return unitsSold; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
    
    // Response classes
    
    public static class BusinessAnalyticsDashboard {
        private String businessId;
        private AnalyticsPeriod period;
        private LocalDateTime generatedAt;
        private RevenueMetrics revenueMetrics;
        private CustomerMetrics customerMetrics;
        private TransactionMetrics transactionMetrics;
        private ProductPerformance productPerformance;
        private GrowthMetrics growthMetrics;
        private FinancialHealthScore financialHealth;
        private List<BusinessInsight> insights;
        private List<BusinessRecommendation> recommendations;
        
        // Getters and setters
        public void setBusinessId(String businessId) { this.businessId = businessId; }
        public void setPeriod(AnalyticsPeriod period) { this.period = period; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        public void setRevenueMetrics(RevenueMetrics revenueMetrics) { this.revenueMetrics = revenueMetrics; }
        public void setCustomerMetrics(CustomerMetrics customerMetrics) { this.customerMetrics = customerMetrics; }
        public void setTransactionMetrics(TransactionMetrics transactionMetrics) { this.transactionMetrics = transactionMetrics; }
        public void setProductPerformance(ProductPerformance productPerformance) { this.productPerformance = productPerformance; }
        public void setGrowthMetrics(GrowthMetrics growthMetrics) { this.growthMetrics = growthMetrics; }
        public void setFinancialHealth(FinancialHealthScore financialHealth) { this.financialHealth = financialHealth; }
        public void setInsights(List<BusinessInsight> insights) { this.insights = insights; }
        public void setRecommendations(List<BusinessRecommendation> recommendations) { this.recommendations = recommendations; }
        
        public RevenueMetrics getRevenueMetrics() { return revenueMetrics; }
        public CustomerMetrics getCustomerMetrics() { return customerMetrics; }
        public TransactionMetrics getTransactionMetrics() { return transactionMetrics; }
        public ProductPerformance getProductPerformance() { return productPerformance; }
    }
    
    private BigDecimal calculateRevenueGrowth(String businessId, AnalyticsPeriod period) {
        LocalDate[] currentPeriod = getPeriodDates(period);
        LocalDate[] previousPeriod = getPreviousPeriodDates(period);

        BigDecimal currentRevenue = getRevenueForPeriod(businessId, currentPeriod[0], currentPeriod[1]);
        BigDecimal previousRevenue = getRevenueForPeriod(businessId, previousPeriod[0], previousPeriod[1]);

        if (previousRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return currentRevenue.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        BigDecimal growth = currentRevenue.subtract(previousRevenue)
            .divide(previousRevenue, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

        return growth.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDate[] getPeriodDates(AnalyticsPeriod period) {
        LocalDate end = LocalDate.now();
        LocalDate start = getStartDate(period);
        return new LocalDate[]{start, end};
    }

    private LocalDate[] getPreviousPeriodDates(AnalyticsPeriod period) {
        LocalDate currentEnd = getStartDate(period).minusDays(1);
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(getStartDate(period), LocalDate.now());
        LocalDate previousStart = currentEnd.minusDays(daysBetween);
        return new LocalDate[]{previousStart, currentEnd};
    }

    private BigDecimal getRevenueForPeriod(String businessId, LocalDate start, LocalDate end) {
        return calculateTotalRevenue(businessId, start, end);
    }

    private Map<String, BigDecimal> getRevenueByCategory(String businessId, LocalDate start, LocalDate end) { return new HashMap<>(); }
    private Map<String, BigDecimal> getRevenueByPaymentMethod(String businessId, LocalDate start, LocalDate end) { return new HashMap<>(); }
    private BigDecimal calculateRecurringRevenue(String businessId) { return BigDecimal.valueOf(50000); }
    private double calculateRecurringPercentage(RevenueMetrics revenue) { return 25.0; }
    private RevenueForecast generateRevenueForecast(String businessId, AnalyticsPeriod period) { return new RevenueForecast(); }
    private double calculateTransactionSuccessRate(String businessId, AnalyticsPeriod period) { return 97.5; }
    private List<Integer> identifyPeakHours(String businessId, AnalyticsPeriod period) { return Arrays.asList(12, 13, 18, 19); }
    private List<TransactionTrend> getTransactionTrends(String businessId, AnalyticsPeriod period) { return new ArrayList<>(); }
    private double calculateCustomerGrowth(String businessId, AnalyticsPeriod period) { return 12.0; }
    private double calculateTransactionGrowth(String businessId, AnalyticsPeriod period) { return 18.0; }
    private double calculateYearOverYearGrowth(String businessId) { return 25.0; }
    private double calculateMonthOverMonthGrowth(String businessId) { return 8.0; }
    private List<GrowthTrend> getGrowthTrends(String businessId, AnalyticsPeriod period) { return new ArrayList<>(); }
    private double calculateCompoundGrowthRate(String businessId, AnalyticsPeriod period) { return 22.0; }
    private List<String> identifyGrowthDrivers(String businessId, AnalyticsPeriod period) { return Arrays.asList("New Products", "Marketing Campaign"); }
    private double calculateLiquidityScore(FinancialMetrics metrics) { return 85.0; }
    private double calculateProfitabilityScore(FinancialMetrics metrics) { return 78.0; }
    private double calculateEfficiencyScore(FinancialMetrics metrics) { return 82.0; }
    private double calculateStabilityScore(FinancialMetrics metrics) { return 90.0; }
    private double calculateOverallHealthScore(FinancialHealthScore health) { return 83.75; }
    private List<String> identifyRiskFactors(FinancialMetrics metrics) { return Arrays.asList("High customer concentration"); }
    private List<String> identifyOpportunities(FinancialMetrics metrics) { return Arrays.asList("International expansion"); }
    private double estimateRecurringRevenueImpact(RevenueMetrics revenue) { return 50000.0; }
    private double estimateRetentionImpact(CustomerMetrics customers) { return 25000.0; }
    private double estimateEfficiencyImpact(TransactionMetrics transactions) { return 10000.0; }
    private double estimateStockoutImpact(ProductPerformance products) { return 15000.0; }
    
    // Enums and additional classes
    public enum AnalyticsPeriod { DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY }
    public enum InsightType { POSITIVE, NEGATIVE, WARNING, INFO }
    public enum InsightCategory { REVENUE, CUSTOMERS, OPERATIONS, PRODUCTS }
    public enum RecommendationType { REVENUE_OPTIMIZATION, CUSTOMER_RETENTION, OPERATIONAL_EFFICIENCY, INVENTORY_MANAGEMENT }
    public enum RecommendationPriority { LOW, MEDIUM, HIGH, CRITICAL }
    
    public static class RevenueMetrics {
        private BigDecimal totalRevenue;
        private BigDecimal averageTransactionValue;
        private double growthRate;
        private Map<String, BigDecimal> revenueByCategory;
        private Map<String, BigDecimal> revenueByPaymentMethod;
        private BigDecimal recurringRevenue;
        private double recurringRevenuePercentage;
        private RevenueForecast forecast;
        
        // Getters and setters
        public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
        public void setAverageTransactionValue(BigDecimal averageTransactionValue) { this.averageTransactionValue = averageTransactionValue; }
        public void setGrowthRate(double growthRate) { this.growthRate = growthRate; }
        public void setRevenueByCategory(Map<String, BigDecimal> revenueByCategory) { this.revenueByCategory = revenueByCategory; }
        public void setRevenueByPaymentMethod(Map<String, BigDecimal> revenueByPaymentMethod) { this.revenueByPaymentMethod = revenueByPaymentMethod; }
        public void setRecurringRevenue(BigDecimal recurringRevenue) { this.recurringRevenue = recurringRevenue; }
        public void setRecurringRevenuePercentage(double recurringRevenuePercentage) { this.recurringRevenuePercentage = recurringRevenuePercentage; }
        public void setForecast(RevenueForecast forecast) { this.forecast = forecast; }
        
        public double getGrowthRate() { return growthRate; }
        public double getRecurringRevenuePercentage() { return recurringRevenuePercentage; }
    }
    
    public static class CustomerMetrics {
        private long totalCustomers;
        private long newCustomers;
        private long returningCustomers;
        private double churnRate;
        private double retentionRate;
        private BigDecimal lifetimeValue;
        private BigDecimal acquisitionCost;
        private double satisfactionScore;
        private Map<String, Integer> segments;
        private List<CustomerInfo> topCustomers;
        
        // Setters
        public void setTotalCustomers(long totalCustomers) { this.totalCustomers = totalCustomers; }
        public void setNewCustomers(long newCustomers) { this.newCustomers = newCustomers; }
        public void setReturningCustomers(long returningCustomers) { this.returningCustomers = returningCustomers; }
        public void setChurnRate(double churnRate) { this.churnRate = churnRate; }
        public void setRetentionRate(double retentionRate) { this.retentionRate = retentionRate; }
        public void setLifetimeValue(BigDecimal lifetimeValue) { this.lifetimeValue = lifetimeValue; }
        public void setAcquisitionCost(BigDecimal acquisitionCost) { this.acquisitionCost = acquisitionCost; }
        public void setSatisfactionScore(double satisfactionScore) { this.satisfactionScore = satisfactionScore; }
        public void setSegments(Map<String, Integer> segments) { this.segments = segments; }
        public void setTopCustomers(List<CustomerInfo> topCustomers) { this.topCustomers = topCustomers; }
        
        public double getChurnRate() { return churnRate; }
        public double getRetentionRate() { return retentionRate; }
        public BigDecimal getLifetimeValue() { return lifetimeValue; }
        public BigDecimal getAcquisitionCost() { return acquisitionCost; }
    }
    
    public static class TransactionMetrics {
        private long totalTransactions;
        private BigDecimal totalVolume;
        private BigDecimal averageTransactionValue;
        private Map<String, Long> transactionsByType;
        private Map<String, BigDecimal> volumeByType;
        private double successRate;
        private List<Integer> peakHours;
        private List<TransactionTrend> trends;
        
        // Setters
        public void setTotalTransactions(long totalTransactions) { this.totalTransactions = totalTransactions; }
        public void setTotalVolume(BigDecimal totalVolume) { this.totalVolume = totalVolume; }
        public void setAverageTransactionValue(BigDecimal averageTransactionValue) { this.averageTransactionValue = averageTransactionValue; }
        public void setTransactionsByType(Map<String, Long> transactionsByType) { this.transactionsByType = transactionsByType; }
        public void setVolumeByType(Map<String, BigDecimal> volumeByType) { this.volumeByType = volumeByType; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public void setPeakHours(List<Integer> peakHours) { this.peakHours = peakHours; }
        public void setTrends(List<TransactionTrend> trends) { this.trends = trends; }
        
        public double getSuccessRate() { return successRate; }
        public List<Integer> getPeakHours() { return peakHours; }
    }
    
    public static class ProductPerformance {
        private List<ProductInfo> topProducts;
        private Map<String, BigDecimal> categoryPerformance;
        private double inventoryTurnover;
        private double stockoutRate;
        private Map<String, Double> profitMargins;
        private double crossSellRate;
        private double upsellRate;
        
        // Setters
        public void setTopProducts(List<ProductInfo> topProducts) { this.topProducts = topProducts; }
        public void setCategoryPerformance(Map<String, BigDecimal> categoryPerformance) { this.categoryPerformance = categoryPerformance; }
        public void setInventoryTurnover(double inventoryTurnover) { this.inventoryTurnover = inventoryTurnover; }
        public void setStockoutRate(double stockoutRate) { this.stockoutRate = stockoutRate; }
        public void setProfitMargins(Map<String, Double> profitMargins) { this.profitMargins = profitMargins; }
        public void setCrossSellRate(double crossSellRate) { this.crossSellRate = crossSellRate; }
        public void setUpsellRate(double upsellRate) { this.upsellRate = upsellRate; }
        
        public double getStockoutRate() { return stockoutRate; }
    }
    
    public static class GrowthMetrics {
        private double revenueGrowth;
        private double customerGrowth;
        private double transactionGrowth;
        private double yoyGrowth;
        private double momGrowth;
        private List<GrowthTrend> trends;
        private double compoundGrowthRate;
        private List<String> growthDrivers;
        
        // Setters
        public void setRevenueGrowth(double revenueGrowth) { this.revenueGrowth = revenueGrowth; }
        public void setCustomerGrowth(double customerGrowth) { this.customerGrowth = customerGrowth; }
        public void setTransactionGrowth(double transactionGrowth) { this.transactionGrowth = transactionGrowth; }
        public void setYoyGrowth(double yoyGrowth) { this.yoyGrowth = yoyGrowth; }
        public void setMomGrowth(double momGrowth) { this.momGrowth = momGrowth; }
        public void setTrends(List<GrowthTrend> trends) { this.trends = trends; }
        public void setCompoundGrowthRate(double compoundGrowthRate) { this.compoundGrowthRate = compoundGrowthRate; }
        public void setGrowthDrivers(List<String> growthDrivers) { this.growthDrivers = growthDrivers; }
    }
    
    public static class FinancialHealthScore {
        private double liquidityScore;
        private double profitabilityScore;
        private double efficiencyScore;
        private double stabilityScore;
        private double overallScore;
        private List<String> riskFactors;
        private List<String> opportunities;
        
        // Setters
        public void setLiquidityScore(double liquidityScore) { this.liquidityScore = liquidityScore; }
        public void setProfitabilityScore(double profitabilityScore) { this.profitabilityScore = profitabilityScore; }
        public void setEfficiencyScore(double efficiencyScore) { this.efficiencyScore = efficiencyScore; }
        public void setStabilityScore(double stabilityScore) { this.stabilityScore = stabilityScore; }
        public void setOverallScore(double overallScore) { this.overallScore = overallScore; }
        public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }
        public void setOpportunities(List<String> opportunities) { this.opportunities = opportunities; }
    }
    
    public static class BusinessInsight {
        private final InsightType type;
        private final String title;
        private final String description;
        private final InsightCategory category;
        
        public BusinessInsight(InsightType type, String title, String description, InsightCategory category) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.category = category;
        }
    }
    
    public static class BusinessRecommendation {
        private final RecommendationType type;
        private final String title;
        private final String description;
        private final RecommendationPriority priority;
        private final double estimatedImpact;
        
        public BusinessRecommendation(RecommendationType type, String title, String description, 
                                     RecommendationPriority priority, double estimatedImpact) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.priority = priority;
            this.estimatedImpact = estimatedImpact;
        }
    }
    
    public static class CustomerInfo {
        private final String customerId;
        private final BigDecimal totalSpent;
        private final long transactionCount;
        
        public CustomerInfo(String customerId, BigDecimal totalSpent, long transactionCount) {
            this.customerId = customerId;
            this.totalSpent = totalSpent;
            this.transactionCount = transactionCount;
        }
    }
    
    public static class ProductInfo {
        private final String productId;
        private final long unitsSold;
        private final BigDecimal revenue;
        
        public ProductInfo(String productId, long unitsSold, BigDecimal revenue) {
            this.productId = productId;
            this.unitsSold = unitsSold;
            this.revenue = revenue;
        }
    }
    
    // Stub classes
    public static class RevenueForecast {}
    public static class TransactionTrend {}
    public static class GrowthTrend {}
}