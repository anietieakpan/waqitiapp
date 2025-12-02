package com.waqiti.analytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reporting Service - Handles financial reporting and statement generation
 * 
 * Provides comprehensive reporting capabilities for:
 * - Financial statement preparation and updates
 * - Revenue recognition and reporting
 * - Expense tracking and categorization
 * - Balance sheet maintenance
 * - Income statement generation
 * - Cash flow reporting
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportingService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${reporting.enabled:true}")
    private boolean reportingEnabled;

    @Value("${reporting.realtime.enabled:true}")
    private boolean realtimeReportingEnabled;

    // Cache for tracking financial statement components
    private final Map<String, FinancialStatement> financialStatements = new ConcurrentHashMap<>();

    /**
     * Processes ledger event for reporting
     */
    public void processLedgerEventForReporting(
            String eventId,
            String eventType,
            String journalEntryId,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            String description,
            LocalDateTime timestamp) {

        if (!reportingEnabled) {
            log.debug("Reporting disabled, skipping ledger event processing");
            return;
        }

        try {
            log.debug("Processing ledger event for reporting: {} - Type: {}", eventId, eventType);

            // Determine account category for reporting
            AccountCategory category = determineAccountCategory(eventType, accountNumber);

            // Update reporting based on category
            switch (category) {
                case REVENUE:
                    processRevenueEvent(eventId, debitAmount, creditAmount, currency, description, timestamp);
                    break;
                case EXPENSE:
                    processExpenseEvent(eventId, debitAmount, creditAmount, currency, description, timestamp);
                    break;
                case ASSET:
                    processAssetEvent(eventId, accountNumber, debitAmount, creditAmount, currency, timestamp);
                    break;
                case LIABILITY:
                    processLiabilityEvent(eventId, accountNumber, debitAmount, creditAmount, currency, timestamp);
                    break;
                case EQUITY:
                    processEquityEvent(eventId, accountNumber, debitAmount, creditAmount, currency, timestamp);
                    break;
            }

            // Update period reporting totals
            updatePeriodReportingTotals(category, debitAmount, creditAmount, currency, timestamp);

            log.debug("Successfully processed ledger event for reporting: {}", eventId);

        } catch (Exception e) {
            log.error("Failed to process ledger event for reporting: {}", eventId, e);
        }
    }

    /**
     * Updates financial statements based on ledger events
     */
    public void updateFinancialStatements(
            String eventType,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            LocalDateTime timestamp) {

        if (!reportingEnabled) {
            return;
        }

        try {
            log.debug("Updating financial statements for event type: {}", eventType);

            // Get or create financial statement for period
            String period = getPeriod(timestamp);
            FinancialStatement statement = financialStatements.computeIfAbsent(
                period + ":" + currency, k -> new FinancialStatement(period, currency));

            // Update statement based on event type
            updateStatementComponents(statement, eventType, debitAmount, creditAmount);

            // Calculate derived metrics
            calculateFinancialMetrics(statement);

            // Store updated statement
            storeFinancialStatement(statement);

            log.debug("Financial statements updated for period: {} {}", period, currency);

        } catch (Exception e) {
            log.error("Failed to update financial statements", e);
        }
    }

    /**
     * Processes revenue event for reporting
     */
    private void processRevenueEvent(
            String eventId,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            String description,
            LocalDateTime timestamp) {

        try {
            // Revenue is typically credited
            BigDecimal revenueAmount = creditAmount != null ? creditAmount : BigDecimal.ZERO;
            if (debitAmount != null) {
                revenueAmount = revenueAmount.subtract(debitAmount); // Revenue reversals
            }

            if (revenueAmount.compareTo(BigDecimal.ZERO) != 0) {
                // Store revenue record
                String revenueKey = "reporting:revenue:" + getPeriod(timestamp) + ":" + currency;
                redisTemplate.opsForValue().increment(revenueKey, revenueAmount.doubleValue());
                redisTemplate.expire(revenueKey, Duration.ofDays(400));

                // Update revenue by category if description contains category info
                String category = extractRevenueCategory(description);
                if (category != null) {
                    String categoryKey = "reporting:revenue:category:" + category + ":" + 
                        getPeriod(timestamp) + ":" + currency;
                    redisTemplate.opsForValue().increment(categoryKey, revenueAmount.doubleValue());
                    redisTemplate.expire(categoryKey, Duration.ofDays(400));
                }

                log.debug("Revenue event processed: {} - Amount: {} {}", eventId, revenueAmount, currency);
            }

        } catch (Exception e) {
            log.error("Failed to process revenue event: {}", eventId, e);
        }
    }

    /**
     * Processes expense event for reporting
     */
    private void processExpenseEvent(
            String eventId,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            String description,
            LocalDateTime timestamp) {

        try {
            // Expenses are typically debited
            BigDecimal expenseAmount = debitAmount != null ? debitAmount : BigDecimal.ZERO;
            if (creditAmount != null) {
                expenseAmount = expenseAmount.subtract(creditAmount); // Expense reversals
            }

            if (expenseAmount.compareTo(BigDecimal.ZERO) != 0) {
                // Store expense record
                String expenseKey = "reporting:expense:" + getPeriod(timestamp) + ":" + currency;
                redisTemplate.opsForValue().increment(expenseKey, expenseAmount.doubleValue());
                redisTemplate.expire(expenseKey, Duration.ofDays(400));

                // Update expense by category
                String category = extractExpenseCategory(description);
                if (category != null) {
                    String categoryKey = "reporting:expense:category:" + category + ":" + 
                        getPeriod(timestamp) + ":" + currency;
                    redisTemplate.opsForValue().increment(categoryKey, expenseAmount.doubleValue());
                    redisTemplate.expire(categoryKey, Duration.ofDays(400));
                }

                log.debug("Expense event processed: {} - Amount: {} {}", eventId, expenseAmount, currency);
            }

        } catch (Exception e) {
            log.error("Failed to process expense event: {}", eventId, e);
        }
    }

    /**
     * Processes asset event for reporting
     */
    private void processAssetEvent(
            String eventId,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            LocalDateTime timestamp) {

        try {
            // Assets increase with debits, decrease with credits
            BigDecimal assetChange = (debitAmount != null ? debitAmount : BigDecimal.ZERO)
                .subtract(creditAmount != null ? creditAmount : BigDecimal.ZERO);

            if (assetChange.compareTo(BigDecimal.ZERO) != 0) {
                // Update asset balance
                String assetKey = "reporting:assets:" + accountNumber + ":" + currency;
                redisTemplate.opsForValue().increment(assetKey, assetChange.doubleValue());
                redisTemplate.expire(assetKey, Duration.ofDays(400));

                // Update total assets
                String totalAssetsKey = "reporting:assets:total:" + getPeriod(timestamp) + ":" + currency;
                redisTemplate.opsForValue().increment(totalAssetsKey, assetChange.doubleValue());
                redisTemplate.expire(totalAssetsKey, Duration.ofDays(400));

                log.debug("Asset event processed: {} - Change: {} {}", eventId, assetChange, currency);
            }

        } catch (Exception e) {
            log.error("Failed to process asset event: {}", eventId, e);
        }
    }

    /**
     * Processes liability event for reporting
     */
    private void processLiabilityEvent(
            String eventId,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            LocalDateTime timestamp) {

        try {
            // Liabilities increase with credits, decrease with debits
            BigDecimal liabilityChange = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);

            if (liabilityChange.compareTo(BigDecimal.ZERO) != 0) {
                // Update liability balance
                String liabilityKey = "reporting:liabilities:" + accountNumber + ":" + currency;
                redisTemplate.opsForValue().increment(liabilityKey, liabilityChange.doubleValue());
                redisTemplate.expire(liabilityKey, Duration.ofDays(400));

                // Update total liabilities
                String totalLiabilitiesKey = "reporting:liabilities:total:" + getPeriod(timestamp) + ":" + currency;
                redisTemplate.opsForValue().increment(totalLiabilitiesKey, liabilityChange.doubleValue());
                redisTemplate.expire(totalLiabilitiesKey, Duration.ofDays(400));

                log.debug("Liability event processed: {} - Change: {} {}", eventId, liabilityChange, currency);
            }

        } catch (Exception e) {
            log.error("Failed to process liability event: {}", eventId, e);
        }
    }

    /**
     * Processes equity event for reporting
     */
    private void processEquityEvent(
            String eventId,
            String accountNumber,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            LocalDateTime timestamp) {

        try {
            // Equity increases with credits, decreases with debits
            BigDecimal equityChange = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);

            if (equityChange.compareTo(BigDecimal.ZERO) != 0) {
                // Update equity balance
                String equityKey = "reporting:equity:" + accountNumber + ":" + currency;
                redisTemplate.opsForValue().increment(equityKey, equityChange.doubleValue());
                redisTemplate.expire(equityKey, Duration.ofDays(400));

                // Update total equity
                String totalEquityKey = "reporting:equity:total:" + getPeriod(timestamp) + ":" + currency;
                redisTemplate.opsForValue().increment(totalEquityKey, equityChange.doubleValue());
                redisTemplate.expire(totalEquityKey, Duration.ofDays(400));

                log.debug("Equity event processed: {} - Change: {} {}", eventId, equityChange, currency);
            }

        } catch (Exception e) {
            log.error("Failed to process equity event: {}", eventId, e);
        }
    }

    /**
     * Updates period reporting totals
     */
    private void updatePeriodReportingTotals(
            AccountCategory category,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String currency,
            LocalDateTime timestamp) {

        try {
            String period = getPeriod(timestamp);
            
            // Update category totals
            String categoryKey = "reporting:totals:" + category.toString().toLowerCase() + 
                ":" + period + ":" + currency;
            
            BigDecimal amount = calculateCategoryAmount(category, debitAmount, creditAmount);
            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                redisTemplate.opsForValue().increment(categoryKey, amount.doubleValue());
                redisTemplate.expire(categoryKey, Duration.ofDays(400));
            }

            // Update grand totals
            String totalKey = "reporting:totals:all:" + period + ":" + currency;
            BigDecimal totalAmount = (debitAmount != null ? debitAmount : BigDecimal.ZERO)
                .add(creditAmount != null ? creditAmount : BigDecimal.ZERO);
            
            if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                redisTemplate.opsForValue().increment(totalKey, totalAmount.doubleValue());
                redisTemplate.expire(totalKey, Duration.ofDays(400));
            }

        } catch (Exception e) {
            log.error("Failed to update period reporting totals", e);
        }
    }

    /**
     * Calculates amount based on account category
     */
    private BigDecimal calculateCategoryAmount(
            AccountCategory category,
            BigDecimal debitAmount,
            BigDecimal creditAmount) {

        BigDecimal debit = debitAmount != null ? debitAmount : BigDecimal.ZERO;
        BigDecimal credit = creditAmount != null ? creditAmount : BigDecimal.ZERO;

        switch (category) {
            case ASSET:
            case EXPENSE:
                return debit.subtract(credit); // Normal debit balance
            case LIABILITY:
            case EQUITY:
            case REVENUE:
                return credit.subtract(debit); // Normal credit balance
            default:
                return BigDecimal.ZERO;
        }
    }

    /**
     * Updates financial statement components
     */
    private void updateStatementComponents(
            FinancialStatement statement,
            String eventType,
            BigDecimal debitAmount,
            BigDecimal creditAmount) {

        if (eventType.contains("REVENUE")) {
            BigDecimal revenue = creditAmount != null ? creditAmount : BigDecimal.ZERO;
            statement.addRevenue(revenue);
        }

        if (eventType.contains("EXPENSE")) {
            BigDecimal expense = debitAmount != null ? debitAmount : BigDecimal.ZERO;
            statement.addExpense(expense);
        }

        if (eventType.contains("ASSET")) {
            BigDecimal assetChange = (debitAmount != null ? debitAmount : BigDecimal.ZERO)
                .subtract(creditAmount != null ? creditAmount : BigDecimal.ZERO);
            statement.adjustAssets(assetChange);
        }

        if (eventType.contains("LIABILITY")) {
            BigDecimal liabilityChange = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);
            statement.adjustLiabilities(liabilityChange);
        }

        if (eventType.contains("EQUITY")) {
            BigDecimal equityChange = (creditAmount != null ? creditAmount : BigDecimal.ZERO)
                .subtract(debitAmount != null ? debitAmount : BigDecimal.ZERO);
            statement.adjustEquity(equityChange);
        }
    }

    /**
     * Calculates financial metrics for statement
     */
    private void calculateFinancialMetrics(FinancialStatement statement) {
        // Calculate net income
        statement.calculateNetIncome();

        // Calculate key ratios
        statement.calculateCurrentRatio();
        statement.calculateDebtToEquityRatio();
        statement.calculateReturnOnAssets();
        statement.calculateReturnOnEquity();
    }

    /**
     * Stores financial statement in Redis
     */
    private void storeFinancialStatement(FinancialStatement statement) {
        try {
            String statementKey = "reporting:statement:" + statement.getPeriod() + ":" + statement.getCurrency();
            
            Map<String, String> statementData = Map.of(
                "period", statement.getPeriod(),
                "currency", statement.getCurrency(),
                "total_revenue", statement.getTotalRevenue().toString(),
                "total_expenses", statement.getTotalExpenses().toString(),
                "net_income", statement.getNetIncome().toString(),
                "total_assets", statement.getTotalAssets().toString(),
                "total_liabilities", statement.getTotalLiabilities().toString(),
                "total_equity", statement.getTotalEquity().toString(),
                "last_updated", LocalDateTime.now().toString()
            );

            redisTemplate.opsForHash().putAll(statementKey, statementData);
            redisTemplate.expire(statementKey, Duration.ofDays(400));

        } catch (Exception e) {
            log.error("Failed to store financial statement", e);
        }
    }

    /**
     * Determines account category from event type and account number
     */
    private AccountCategory determineAccountCategory(String eventType, String accountNumber) {
        String upperEventType = eventType.toUpperCase();
        
        if (upperEventType.contains("REVENUE") || upperEventType.contains("INCOME")) {
            return AccountCategory.REVENUE;
        }
        if (upperEventType.contains("EXPENSE") || upperEventType.contains("COST")) {
            return AccountCategory.EXPENSE;
        }
        if (upperEventType.contains("ASSET") || upperEventType.contains("CASH")) {
            return AccountCategory.ASSET;
        }
        if (upperEventType.contains("LIABILITY") || upperEventType.contains("PAYABLE")) {
            return AccountCategory.LIABILITY;
        }
        if (upperEventType.contains("EQUITY") || upperEventType.contains("CAPITAL")) {
            return AccountCategory.EQUITY;
        }
        
        // Default based on account number pattern
        if (accountNumber != null) {
            if (accountNumber.startsWith("1")) return AccountCategory.ASSET;
            if (accountNumber.startsWith("2")) return AccountCategory.LIABILITY;
            if (accountNumber.startsWith("3")) return AccountCategory.EQUITY;
            if (accountNumber.startsWith("4")) return AccountCategory.REVENUE;
            if (accountNumber.startsWith("5")) return AccountCategory.EXPENSE;
        }
        
        return AccountCategory.ASSET; // Default
    }

    /**
     * Extracts revenue category from description
     */
    private String extractRevenueCategory(String description) {
        if (description == null) return "OTHER";
        
        String upperDesc = description.toUpperCase();
        if (upperDesc.contains("TRANSACTION") || upperDesc.contains("PAYMENT")) return "TRANSACTION_FEES";
        if (upperDesc.contains("SUBSCRIPTION")) return "SUBSCRIPTION";
        if (upperDesc.contains("INTERCHANGE")) return "INTERCHANGE";
        if (upperDesc.contains("INTEREST")) return "INTEREST";
        
        return "OTHER";
    }

    /**
     * Extracts expense category from description
     */
    private String extractExpenseCategory(String description) {
        if (description == null) return "OTHER";
        
        String upperDesc = description.toUpperCase();
        if (upperDesc.contains("CHARGEBACK")) return "CHARGEBACK";
        if (upperDesc.contains("FRAUD")) return "FRAUD_LOSS";
        if (upperDesc.contains("OPERATIONAL")) return "OPERATIONAL";
        if (upperDesc.contains("MARKETING")) return "MARKETING";
        if (upperDesc.contains("PERSONNEL") || upperDesc.contains("SALARY")) return "PERSONNEL";
        
        return "OTHER";
    }

    /**
     * Gets reporting period from timestamp
     */
    private String getPeriod(LocalDateTime timestamp) {
        return timestamp.getYear() + "-" + String.format("%02d", timestamp.getMonthValue());
    }

    // Data structures

    private enum AccountCategory {
        ASSET,
        LIABILITY,
        EQUITY,
        REVENUE,
        EXPENSE
    }

    /**
     * Financial statement representation
     */
    private static class FinancialStatement {
        private final String period;
        private final String currency;
        private BigDecimal totalRevenue = BigDecimal.ZERO;
        private BigDecimal totalExpenses = BigDecimal.ZERO;
        private BigDecimal netIncome = BigDecimal.ZERO;
        private BigDecimal totalAssets = BigDecimal.ZERO;
        private BigDecimal totalLiabilities = BigDecimal.ZERO;
        private BigDecimal totalEquity = BigDecimal.ZERO;
        private BigDecimal currentRatio = BigDecimal.ZERO;
        private BigDecimal debtToEquityRatio = BigDecimal.ZERO;
        private BigDecimal returnOnAssets = BigDecimal.ZERO;
        private BigDecimal returnOnEquity = BigDecimal.ZERO;

        public FinancialStatement(String period, String currency) {
            this.period = period;
            this.currency = currency;
        }

        public void addRevenue(BigDecimal amount) {
            this.totalRevenue = this.totalRevenue.add(amount);
        }

        public void addExpense(BigDecimal amount) {
            this.totalExpenses = this.totalExpenses.add(amount);
        }

        public void adjustAssets(BigDecimal amount) {
            this.totalAssets = this.totalAssets.add(amount);
        }

        public void adjustLiabilities(BigDecimal amount) {
            this.totalLiabilities = this.totalLiabilities.add(amount);
        }

        public void adjustEquity(BigDecimal amount) {
            this.totalEquity = this.totalEquity.add(amount);
        }

        public void calculateNetIncome() {
            this.netIncome = this.totalRevenue.subtract(this.totalExpenses);
        }

        public void calculateCurrentRatio() {
            if (totalLiabilities.compareTo(BigDecimal.ZERO) > 0) {
                this.currentRatio = totalAssets.divide(totalLiabilities, 2, RoundingMode.HALF_UP);
            }
        }

        public void calculateDebtToEquityRatio() {
            if (totalEquity.compareTo(BigDecimal.ZERO) > 0) {
                this.debtToEquityRatio = totalLiabilities.divide(totalEquity, 2, RoundingMode.HALF_UP);
            }
        }

        public void calculateReturnOnAssets() {
            if (totalAssets.compareTo(BigDecimal.ZERO) > 0) {
                this.returnOnAssets = netIncome.divide(totalAssets, 4, RoundingMode.HALF_UP);
            }
        }

        public void calculateReturnOnEquity() {
            if (totalEquity.compareTo(BigDecimal.ZERO) > 0) {
                this.returnOnEquity = netIncome.divide(totalEquity, 4, RoundingMode.HALF_UP);
            }
        }

        // Getters
        public String getPeriod() { return period; }
        public String getCurrency() { return currency; }
        public BigDecimal getTotalRevenue() { return totalRevenue; }
        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public BigDecimal getNetIncome() { return netIncome; }
        public BigDecimal getTotalAssets() { return totalAssets; }
        public BigDecimal getTotalLiabilities() { return totalLiabilities; }
        public BigDecimal getTotalEquity() { return totalEquity; }
    }
}