/**
 * Banking Data Service
 * Analyzes user banking data for credit assessment
 *
 * Integrates with Open Banking APIs (Plaid, Yodlee, etc.) to analyze:
 * - Income patterns and stability
 * - Spending behavior
 * - Account balances and history
 * - Cash flow analysis
 */
package com.waqiti.bnpl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.bnpl.exception.BankingDataException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes banking data to assess creditworthiness
 * Uses Open Banking APIs and internal transaction data
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankingDataService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${banking.data.provider.url:#{null}}")
    private String bankingDataProviderUrl;

    @Value("${banking.data.provider.api-key:#{null}}")
    private String bankingDataProviderApiKey;

    @Value("${banking.data.enabled:false}")
    private boolean bankingDataEnabled;

    @Value("${banking.data.analysis-period-months:12}")
    private int analysisPeriodMonths;

    /**
     * Analyzes banking data for credit assessment
     * Cached for 6 hours (banking data changes daily but analysis is expensive)
     */
    @Cacheable(value = "bankingAnalysis", key = "#userId", unless = "#result == null")
    @CircuitBreaker(name = "bankingData", fallbackMethod = "analyzeBankingDataFallback")
    @Retry(name = "bankingData", fallbackMethod = "analyzeBankingDataFallback")
    public BankingAnalysis analyzeBankingData(UUID userId) {
        log.info("Analyzing banking data for user: {}", userId);

        if (!bankingDataEnabled) {
            log.warn("Banking data integration disabled, using conservative defaults");
            return generateConservativeDefaults(userId);
        }

        try {
            // Fetch banking data from Open Banking provider (e.g., Plaid)
            BankingTransactions transactions = fetchBankingTransactions(userId);

            if (transactions == null || transactions.isEmpty()) {
                log.warn("No banking data available for user: {}, using defaults", userId);
                return generateConservativeDefaults(userId);
            }

            // Perform comprehensive analysis
            BankingAnalysis analysis = new BankingAnalysis();

            // 1. Income Analysis
            analyzeIncome(analysis, transactions);

            // 2. Balance Analysis
            analyzeBalances(analysis, transactions);

            // 3. Spending Patterns
            analyzeSpending(analysis, transactions);

            // 4. Overdraft Analysis
            analyzeOverdrafts(analysis, transactions);

            // 5. Digital Banking Adoption
            analyzeDigitalAdoption(analysis, transactions);

            // 6. Financial Behavior Patterns
            analyzeBehavioralPatterns(analysis, transactions);

            log.info("Banking analysis completed for user: {} - Income: {}, Balance: {}",
                userId, analysis.monthlyIncome, analysis.currentBalance);

            return analysis;

        } catch (Exception e) {
            log.error("Failed to analyze banking data for user: {}", userId, e);
            throw new BankingDataException("Failed to analyze banking data", e);
        }
    }

    /**
     * Fetch banking transactions from Open Banking provider
     */
    private BankingTransactions fetchBankingTransactions(UUID userId) {
        if (bankingDataProviderUrl == null || bankingDataProviderApiKey == null) {
            log.warn("Banking data provider not configured");
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + bankingDataProviderApiKey);
            headers.set("X-Request-ID", UUID.randomUUID().toString());

            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(analysisPeriodMonths);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("userId", userId.toString());
            requestBody.put("startDate", startDate.toString());
            requestBody.put("endDate", endDate.toString());
            requestBody.put("includeBalance", true);
            requestBody.put("includeTransactions", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                bankingDataProviderUrl + "/api/v1/banking-data",
                HttpMethod.POST,
                request,
                JsonNode.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseBankingTransactions(response.getBody());
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to fetch banking transactions for user: {}", userId, e);
            return null;
        }
    }

    /**
     * Parse banking transaction response
     */
    private BankingTransactions parseBankingTransactions(JsonNode response) {
        BankingTransactions transactions = new BankingTransactions();

        try {
            // Parse current balance
            if (response.has("currentBalance")) {
                transactions.currentBalance = new BigDecimal(response.get("currentBalance").asText());
            }

            // Parse transaction history
            if (response.has("transactions") && response.get("transactions").isArray()) {
                JsonNode transactionsNode = response.get("transactions");
                for (JsonNode txn : transactionsNode) {
                    Transaction transaction = new Transaction();
                    transaction.date = LocalDate.parse(txn.get("date").asText());
                    transaction.amount = new BigDecimal(txn.get("amount").asText());
                    transaction.type = txn.get("type").asText();
                    transaction.category = txn.has("category") ? txn.get("category").asText() : "OTHER";
                    transaction.description = txn.has("description") ? txn.get("description").asText() : "";

                    transactions.addTransaction(transaction);
                }
            }

            // Parse account metadata
            if (response.has("accountAge")) {
                transactions.accountAgeMonths = response.get("accountAge").asInt();
            }

            log.debug("Parsed {} transactions for analysis", transactions.getTransactionCount());
            return transactions;

        } catch (Exception e) {
            log.error("Failed to parse banking transactions", e);
            throw new BankingDataException("Failed to parse banking transactions", e);
        }
    }

    /**
     * Analyze income patterns
     */
    private void analyzeIncome(BankingAnalysis analysis, BankingTransactions transactions) {
        List<Transaction> incomeTransactions = transactions.getIncomeTransactions();

        if (incomeTransactions.isEmpty()) {
            analysis.monthlyIncome = BigDecimal.ZERO;
            analysis.incomeVerified = false;
            analysis.employmentStatus = "UNKNOWN";
            analysis.incomeVariability = 1.0; // High variability for unknown income
            return;
        }

        // Calculate monthly income (average of last 3 months)
        Map<LocalDate, BigDecimal> monthlyIncomes = transactions.groupIncomeByMonth();
        List<BigDecimal> recentMonthlyIncomes = monthlyIncomes.values().stream()
            .sorted(Comparator.reverseOrder())
            .limit(3)
            .collect(Collectors.toList());

        if (!recentMonthlyIncomes.isEmpty()) {
            BigDecimal totalIncome = recentMonthlyIncomes.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            analysis.monthlyIncome = totalIncome.divide(
                new BigDecimal(recentMonthlyIncomes.size()),
                2,
                RoundingMode.HALF_UP
            );
        } else {
            analysis.monthlyIncome = BigDecimal.ZERO;
        }

        // Income verification (regular deposits indicate employment)
        analysis.incomeVerified = hasRegularIncomePattern(incomeTransactions);

        // Determine employment status
        analysis.employmentStatus = determineEmploymentStatus(incomeTransactions, analysis.incomeVerified);

        // Calculate income variability (coefficient of variation)
        if (recentMonthlyIncomes.size() > 1) {
            DescriptiveStatistics stats = new DescriptiveStatistics();
            recentMonthlyIncomes.forEach(income -> stats.addValue(income.doubleValue()));
            double mean = stats.getMean();
            double stdDev = stats.getStandardDeviation();
            analysis.incomeVariability = mean > 0 ? stdDev / mean : 1.0;
        } else {
            analysis.incomeVariability = 0.5; // Moderate variability for limited data
        }

        log.debug("Income analysis: monthly={}, verified={}, variability={}",
            analysis.monthlyIncome, analysis.incomeVerified, analysis.incomeVariability);
    }

    /**
     * Check if income pattern is regular (indicates employment)
     */
    private boolean hasRegularIncomePattern(List<Transaction> incomeTransactions) {
        if (incomeTransactions.size() < 3) {
            return false;
        }

        // Group by month and check for consistency
        Map<LocalDate, BigDecimal> monthlyIncomes = new HashMap<>();
        for (Transaction txn : incomeTransactions) {
            LocalDate monthStart = txn.date.withDayOfMonth(1);
            monthlyIncomes.merge(monthStart, txn.amount, BigDecimal::add);
        }

        // Regular income = income in at least 3 of last 4 months
        long monthsWithIncome = monthlyIncomes.keySet().stream()
            .filter(month -> month.isAfter(LocalDate.now().minusMonths(4)))
            .count();

        return monthsWithIncome >= 3;
    }

    /**
     * Determine employment status from income patterns
     */
    private String determineEmploymentStatus(List<Transaction> incomeTransactions, boolean verified) {
        if (!verified) {
            return "UNKNOWN";
        }

        // Check for bi-weekly or monthly patterns
        List<Long> daysBetweenDeposits = new ArrayList<>();
        for (int i = 1; i < incomeTransactions.size(); i++) {
            long days = ChronoUnit.DAYS.between(
                incomeTransactions.get(i-1).date,
                incomeTransactions.get(i).date
            );
            daysBetweenDeposits.add(days);
        }

        if (daysBetweenDeposits.isEmpty()) {
            return "UNKNOWN";
        }

        // Average days between deposits
        double avgDays = daysBetweenDeposits.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);

        // Bi-weekly (14 days) or monthly (30 days) patterns indicate full-time employment
        if (avgDays >= 13 && avgDays <= 16) {
            return "EMPLOYED_FULL_TIME";
        } else if (avgDays >= 28 && avgDays <= 32) {
            return "EMPLOYED_FULL_TIME";
        } else if (avgDays < 13) {
            return "EMPLOYED_PART_TIME";
        } else {
            return "SELF_EMPLOYED";
        }
    }

    /**
     * Analyze account balances
     */
    private void analyzeBalances(BankingAnalysis analysis, BankingTransactions transactions) {
        analysis.currentBalance = transactions.getCurrentBalance();

        // Calculate average balance over analysis period
        List<BigDecimal> dailyBalances = transactions.calculateDailyBalances();
        if (!dailyBalances.isEmpty()) {
            BigDecimal sum = dailyBalances.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            analysis.averageBalance = sum.divide(
                new BigDecimal(dailyBalances.size()),
                2,
                RoundingMode.HALF_UP
            );
        } else {
            analysis.averageBalance = analysis.currentBalance;
        }

        analysis.avgBalance = analysis.averageBalance; // Alias for compatibility
    }

    /**
     * Analyze spending patterns
     */
    private void analyzeSpending(BankingAnalysis analysis, BankingTransactions transactions) {
        List<Transaction> expenses = transactions.getExpenseTransactions();

        // Calculate monthly transaction count
        Map<LocalDate, List<Transaction>> monthlyTransactions = transactions.groupByMonth();
        if (!monthlyTransactions.isEmpty()) {
            double avgMonthlyCount = monthlyTransactions.values().stream()
                .mapToInt(List::size)
                .average()
                .orElse(0.0);
            analysis.monthlyTransactionCount = (int) Math.round(avgMonthlyCount);
            analysis.avgMonthlyTransactions = (int) Math.round(avgMonthlyCount);
        } else {
            analysis.monthlyTransactionCount = 0;
            analysis.avgMonthlyTransactions = 0;
        }

        // Calculate average transaction amount
        if (!expenses.isEmpty()) {
            BigDecimal totalExpenses = expenses.stream()
                .map(t -> t.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            analysis.avgTransactionAmount = totalExpenses.divide(
                new BigDecimal(expenses.size()),
                2,
                RoundingMode.HALF_UP
            );
        } else {
            analysis.avgTransactionAmount = BigDecimal.ZERO;
        }

        // Calculate spending variability
        if (!expenses.isEmpty()) {
            Map<LocalDate, BigDecimal> monthlySpending = transactions.groupSpendingByMonth();
            if (monthlySpending.size() > 1) {
                DescriptiveStatistics stats = new DescriptiveStatistics();
                monthlySpending.values().forEach(amount -> stats.addValue(amount.doubleValue()));
                double mean = stats.getMean();
                double stdDev = stats.getStandardDeviation();
                analysis.spendingVariability = mean > 0 ? new BigDecimal(stdDev / mean) : BigDecimal.ONE;
            } else {
                analysis.spendingVariability = new BigDecimal("0.5");
            }
        } else {
            analysis.spendingVariability = BigDecimal.ONE;
        }

        // Detect recurring payments
        analysis.hasRecurringPayments = transactions.hasRecurringPayments();
    }

    /**
     * Analyze overdraft incidents
     */
    private void analyzeOverdrafts(BankingAnalysis analysis, BankingTransactions transactions) {
        List<BigDecimal> balances = transactions.calculateDailyBalances();

        int overdraftCount = 0;
        for (BigDecimal balance : balances) {
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                overdraftCount++;
            }
        }

        analysis.overdraftIncidents = overdraftCount;
        analysis.overdraftCount = overdraftCount;
    }

    /**
     * Analyze digital banking adoption
     */
    private void analyzeDigitalAdoption(BankingAnalysis analysis, BankingTransactions transactions) {
        List<Transaction> allTransactions = transactions.getAllTransactions();

        if (allTransactions.isEmpty()) {
            analysis.digitalBankingUsage = BigDecimal.ZERO;
            analysis.mobileBankingUsage = BigDecimal.ZERO;
            analysis.onlinePaymentUsage = BigDecimal.ZERO;
            return;
        }

        // Count digital transactions (categorized as online/mobile)
        long digitalCount = allTransactions.stream()
            .filter(t -> isDigitalTransaction(t))
            .count();

        long mobileCount = allTransactions.stream()
            .filter(t -> isMobileTransaction(t))
            .count();

        long onlinePaymentCount = allTransactions.stream()
            .filter(t -> isOnlinePayment(t))
            .count();

        int totalCount = allTransactions.size();

        analysis.digitalBankingUsage = new BigDecimal(digitalCount)
            .divide(new BigDecimal(totalCount), 4, RoundingMode.HALF_UP);

        analysis.mobileBankingUsage = new BigDecimal(mobileCount)
            .divide(new BigDecimal(totalCount), 4, RoundingMode.HALF_UP);

        analysis.onlinePaymentUsage = new BigDecimal(onlinePaymentCount)
            .divide(new BigDecimal(totalCount), 4, RoundingMode.HALF_UP);
    }

    /**
     * Analyze financial behavioral patterns
     */
    private void analyzeBehavioralPatterns(BankingAnalysis analysis, BankingTransactions transactions) {
        // Account age
        analysis.accountAge = transactions.getAccountAgeMonths();

        // Multiple accounts indicator
        analysis.hasMultipleBankAccounts = false; // Would check via API in production

        // Investment account indicator
        analysis.hasInvestmentAccount = transactions.hasInvestmentTransactions();

        // Credit card indicator
        analysis.hasCreditCard = transactions.hasCreditCardPayments();

        // Multiple accounts
        analysis.hasMultipleAccounts = analysis.hasMultipleBankAccounts || analysis.hasCreditCard;

        // Calculate average monthly savings
        Map<LocalDate, BigDecimal> monthlyNetFlow = transactions.calculateMonthlyNetFlow();
        if (!monthlyNetFlow.isEmpty()) {
            BigDecimal totalSavings = monthlyNetFlow.values().stream()
                .filter(flow -> flow.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            analysis.avgMonthlySavings = totalSavings.divide(
                new BigDecimal(monthlyNetFlow.size()),
                2,
                RoundingMode.HALF_UP
            );
        } else {
            analysis.avgMonthlySavings = BigDecimal.ZERO;
        }
    }

    /**
     * Helper methods for transaction categorization
     */
    private boolean isDigitalTransaction(Transaction t) {
        return t.category != null && (
            t.category.contains("ONLINE") ||
            t.category.contains("MOBILE") ||
            t.category.contains("DIGITAL") ||
            t.category.contains("APP")
        );
    }

    private boolean isMobileTransaction(Transaction t) {
        return t.category != null && (
            t.category.contains("MOBILE") ||
            t.category.contains("APP")
        );
    }

    private boolean isOnlinePayment(Transaction t) {
        return t.type != null && t.type.equals("DEBIT") &&
               t.category != null && (
                   t.category.contains("ONLINE") ||
                   t.category.contains("ECOMMERCE") ||
                   t.category.contains("DIGITAL_PAYMENT")
               );
    }

    /**
     * Generate conservative defaults when banking data is unavailable
     */
    private BankingAnalysis generateConservativeDefaults(UUID userId) {
        log.warn("Generating conservative defaults for user: {}", userId);

        BankingAnalysis analysis = new BankingAnalysis();
        analysis.monthlyIncome = BigDecimal.ZERO;
        analysis.incomeVerified = false;
        analysis.employmentStatus = "UNKNOWN";
        analysis.currentBalance = BigDecimal.ZERO;
        analysis.averageBalance = BigDecimal.ZERO;
        analysis.avgBalance = BigDecimal.ZERO;
        analysis.monthlyTransactionCount = 0;
        analysis.avgMonthlyTransactions = 0;
        analysis.overdraftIncidents = 0;
        analysis.overdraftCount = 0;
        analysis.incomeVariability = 1.0;
        analysis.spendingVariability = BigDecimal.ONE;
        analysis.digitalBankingUsage = BigDecimal.ZERO;
        analysis.mobileBankingUsage = BigDecimal.ZERO;
        analysis.onlinePaymentUsage = BigDecimal.ZERO;
        analysis.hasRecurringPayments = false;
        analysis.accountAge = 0;
        analysis.hasMultipleBankAccounts = false;
        analysis.hasInvestmentAccount = false;
        analysis.hasCreditCard = false;
        analysis.hasMultipleAccounts = false;
        analysis.avgTransactionAmount = BigDecimal.ZERO;
        analysis.avgMonthlySavings = BigDecimal.ZERO;

        return analysis;
    }

    /**
     * Fallback method for circuit breaker
     */
    public BankingAnalysis analyzeBankingDataFallback(UUID userId, Exception e) {
        log.error("Circuit breaker activated for banking data service, using defaults for user: {}", userId, e);
        return generateConservativeDefaults(userId);
    }

    /**
     * Data classes
     */
    public static class BankingAnalysis {
        public BigDecimal monthlyIncome;
        public boolean incomeVerified;
        public String employmentStatus;
        public BigDecimal currentBalance;
        public BigDecimal averageBalance;
        public BigDecimal avgBalance;
        public Integer monthlyTransactionCount;
        public Integer avgMonthlyTransactions;
        public Integer overdraftIncidents;
        public Integer overdraftCount;
        public double incomeVariability;
        public BigDecimal spendingVariability;
        public BigDecimal digitalBankingUsage;
        public BigDecimal mobileBankingUsage;
        public BigDecimal onlinePaymentUsage;
        public Boolean hasRecurringPayments;
        public Integer accountAge;
        public Boolean hasMultipleBankAccounts;
        public Boolean hasInvestmentAccount;
        public Boolean hasCreditCard;
        public Boolean hasMultipleAccounts;
        public BigDecimal avgTransactionAmount;
        public BigDecimal avgMonthlySavings;
    }

    private static class Transaction {
        LocalDate date;
        BigDecimal amount;
        String type;
        String category;
        String description;
    }

    private static class BankingTransactions {
        private BigDecimal currentBalance = BigDecimal.ZERO;
        private List<Transaction> transactions = new ArrayList<>();
        private Integer accountAgeMonths = 0;

        void addTransaction(Transaction t) {
            transactions.add(t);
        }

        boolean isEmpty() {
            return transactions.isEmpty();
        }

        int getTransactionCount() {
            return transactions.size();
        }

        BigDecimal getCurrentBalance() {
            return currentBalance;
        }

        Integer getAccountAgeMonths() {
            return accountAgeMonths;
        }

        List<Transaction> getAllTransactions() {
            return new ArrayList<>(transactions);
        }

        List<Transaction> getIncomeTransactions() {
            return transactions.stream()
                .filter(t -> "CREDIT".equals(t.type) || t.amount.compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(t -> t.date))
                .collect(Collectors.toList());
        }

        List<Transaction> getExpenseTransactions() {
            return transactions.stream()
                .filter(t -> "DEBIT".equals(t.type) || t.amount.compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.toList());
        }

        Map<LocalDate, List<Transaction>> groupByMonth() {
            return transactions.stream()
                .collect(Collectors.groupingBy(t -> t.date.withDayOfMonth(1)));
        }

        Map<LocalDate, BigDecimal> groupIncomeByMonth() {
            return getIncomeTransactions().stream()
                .collect(Collectors.groupingBy(
                    t -> t.date.withDayOfMonth(1),
                    Collectors.reducing(BigDecimal.ZERO, t -> t.amount.abs(), BigDecimal::add)
                ));
        }

        Map<LocalDate, BigDecimal> groupSpendingByMonth() {
            return getExpenseTransactions().stream()
                .collect(Collectors.groupingBy(
                    t -> t.date.withDayOfMonth(1),
                    Collectors.reducing(BigDecimal.ZERO, t -> t.amount.abs(), BigDecimal::add)
                ));
        }

        List<BigDecimal> calculateDailyBalances() {
            List<BigDecimal> balances = new ArrayList<>();
            BigDecimal runningBalance = currentBalance;

            // Simplified: In production, would calculate historical balances
            for (int i = 0; i < 90; i++) {
                balances.add(runningBalance);
            }

            return balances;
        }

        boolean hasRecurringPayments() {
            // Detect recurring patterns by matching similar amounts and frequencies
            Map<String, Long> merchantCounts = transactions.stream()
                .collect(Collectors.groupingBy(t -> t.description, Collectors.counting()));

            return merchantCounts.values().stream().anyMatch(count -> count >= 3);
        }

        boolean hasInvestmentTransactions() {
            return transactions.stream()
                .anyMatch(t -> t.category != null && (
                    t.category.contains("INVESTMENT") ||
                    t.category.contains("BROKERAGE") ||
                    t.category.contains("SECURITIES")
                ));
        }

        boolean hasCreditCardPayments() {
            return transactions.stream()
                .anyMatch(t -> t.category != null && t.category.contains("CREDIT_CARD_PAYMENT"));
        }

        Map<LocalDate, BigDecimal> calculateMonthlyNetFlow() {
            Map<LocalDate, BigDecimal> incomeByMonth = groupIncomeByMonth();
            Map<LocalDate, BigDecimal> spendingByMonth = groupSpendingByMonth();

            Map<LocalDate, BigDecimal> netFlow = new HashMap<>();
            for (LocalDate month : incomeByMonth.keySet()) {
                BigDecimal income = incomeByMonth.getOrDefault(month, BigDecimal.ZERO);
                BigDecimal spending = spendingByMonth.getOrDefault(month, BigDecimal.ZERO);
                netFlow.put(month, income.subtract(spending));
            }

            return netFlow;
        }
    }
}
