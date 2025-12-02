package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Statement Generation Service
 * 
 * Handles generation of account statements in various formats including PDF.
 * Supports monthly, quarterly, and custom date range statements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatementGenerationService {
    
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PdfGenerationService pdfGenerationService;
    
    @Value("${core-banking.statements.enabled:true}")
    private boolean statementsEnabled;
    
    @Value("${core-banking.statements.auto-generate:true}")
    private boolean autoGenerateStatements;
    
    @Value("${core-banking.statements.batch-size:50}")
    private int batchSize;
    
    /**
     * Generate statement for specific account and date range
     */
    public StatementResult generateStatement(UUID accountId, LocalDate startDate, LocalDate endDate, 
                                           StatementFormat format) {
        
        log.info("Generating {} statement for account: {} from {} to {}", 
            format, accountId, startDate, endDate);
        
        try {
            Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
            
            // Get transactions for the period
            List<Transaction> transactions = transactionRepository.findByAccountIdAndDateRange(
                accountId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
            
            // Calculate statement summary
            StatementSummary summary = calculateStatementSummary(account, transactions, startDate, endDate);
            
            // Generate statement based on format
            byte[] statementData = generateStatementData(account, transactions, summary, format);
            
            // Update account's last statement date
            account.setLastStatementDate(LocalDateTime.now());
            accountRepository.save(account);
            
            log.info("Generated {} statement for account: {}, {} transactions", 
                format, account.getAccountNumber(), transactions.size());
            
            return StatementResult.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .startDate(startDate)
                .endDate(endDate)
                .format(format)
                .statementData(statementData)
                .transactionCount(transactions.size())
                .summary(summary)
                .generationTimestamp(LocalDateTime.now())
                .status("SUCCESS")
                .build();
                
        } catch (Exception e) {
            log.error("Error generating statement for account: {}", accountId, e);
            
            return StatementResult.builder()
                .accountId(accountId)
                .startDate(startDate)
                .endDate(endDate)
                .format(format)
                .status("FAILED")
                .error(e.getMessage())
                .generationTimestamp(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Generate monthly statement for account
     */
    public StatementResult generateMonthlyStatement(UUID accountId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        return generateStatement(accountId, startDate, endDate, StatementFormat.PDF);
    }
    
    /**
     * Generate statement asynchronously
     */
    @Async
    public CompletableFuture<StatementResult> generateStatementAsync(UUID accountId, 
                                                                    LocalDate startDate, 
                                                                    LocalDate endDate, 
                                                                    StatementFormat format) {
        
        StatementResult result = generateStatement(accountId, startDate, endDate, format);
        return CompletableFuture.completedFuture(result);
    }
    
    /**
     * Scheduled task to auto-generate monthly statements
     */
    @Scheduled(cron = "${core-banking.statements.cron:0 0 1 1 * ?}") // 1st of every month
    @Transactional
    public void autoGenerateMonthlyStatements() {
        if (!statementsEnabled || !autoGenerateStatements) {
            log.info("Auto statement generation is disabled");
            return;
        }
        
        log.info("Starting auto-generation of monthly statements");
        long startTime = System.currentTimeMillis();
        int processedAccounts = 0;
        int successfulStatements = 0;
        
        try {
            // Get previous month dates
            LocalDate now = LocalDate.now();
            LocalDate lastMonth = now.minusMonths(1);
            LocalDate startDate = lastMonth.withDayOfMonth(1);
            LocalDate endDate = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
            
            // Process active user accounts in batches
            List<Account> activeAccounts;
            int offset = 0;
            
            do {
                activeAccounts = accountRepository.findActiveUserAccounts(
                    null); // This would need pagination implementation
                
                for (Account account : activeAccounts) {
                    if (shouldGenerateStatement(account, startDate, endDate)) {
                        try {
                            StatementResult result = generateStatement(
                                account.getId(), startDate, endDate, StatementFormat.PDF);
                            
                            if ("SUCCESS".equals(result.getStatus())) {
                                successfulStatements++;
                                // Optionally store or email the statement
                                storeOrEmailStatement(account, result);
                            }
                        } catch (Exception e) {
                            log.error("Failed to generate statement for account: {}", 
                                account.getAccountNumber(), e);
                        }
                    }
                    processedAccounts++;
                }
                
                offset += batchSize;
            } while (!activeAccounts.isEmpty() && activeAccounts.size() == batchSize);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Completed auto statement generation. Processed: {} accounts, " +
                    "Generated: {} statements, Duration: {} ms", 
                processedAccounts, successfulStatements, duration);
            
        } catch (Exception e) {
            log.error("Error during auto statement generation", e);
        }
    }
    
    /**
     * Get available statement periods for an account
     */
    public List<StatementPeriod> getAvailableStatementPeriods(UUID accountId) {
        Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new RuntimeException("Account not found"));
        
        // Calculate available periods from account creation to current month
        LocalDate accountCreation = account.getCreatedAt().toLocalDate();
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        
        List<StatementPeriod> periods = new java.util.ArrayList<>();
        LocalDate periodStart = accountCreation.withDayOfMonth(1);
        
        while (!periodStart.isAfter(currentMonth)) {
            LocalDate periodEnd = periodStart.withDayOfMonth(periodStart.lengthOfMonth());
            
            // Check if there were transactions in this period
            int transactionCount = transactionRepository.countByAccountIdAndCreatedAtBetween(
                accountId, periodStart.atStartOfDay(), periodEnd.atTime(23, 59, 59));
            
            if (transactionCount > 0) {
                periods.add(StatementPeriod.builder()
                    .startDate(periodStart)
                    .endDate(periodEnd)
                    .transactionCount(transactionCount)
                    .periodName(periodStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
                    .build());
            }
            
            periodStart = periodStart.plusMonths(1);
        }
        
        return periods;
    }
    
    // Private helper methods
    
    private StatementSummary calculateStatementSummary(Account account, List<Transaction> transactions,
                                                      LocalDate startDate, LocalDate endDate) {
        
        BigDecimal openingBalance = calculateOpeningBalance(account, startDate);
        BigDecimal closingBalance = account.getCurrentBalance();
        
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        
        int debitCount = 0;
        int creditCount = 0;
        
        for (Transaction transaction : transactions) {
            if (transaction.getStatus() != Transaction.TransactionStatus.COMPLETED) {
                continue;
            }
            
            boolean isDebit = isDebitTransaction(transaction, account.getId());
            
            if (isDebit) {
                totalDebits = totalDebits.add(transaction.getAmount());
                debitCount++;
            } else {
                totalCredits = totalCredits.add(transaction.getAmount());
                creditCount++;
            }
            
            // Categorize special transaction types
            if (isFeeTransaction(transaction)) {
                totalFees = totalFees.add(transaction.getAmount());
            } else if (isInterestTransaction(transaction)) {
                totalInterest = totalInterest.add(transaction.getAmount());
            }
        }
        
        return StatementSummary.builder()
            .openingBalance(openingBalance)
            .closingBalance(closingBalance)
            .totalDebits(totalDebits)
            .totalCredits(totalCredits)
            .totalFees(totalFees)
            .totalInterest(totalInterest)
            .debitTransactionCount(debitCount)
            .creditTransactionCount(creditCount)
            .totalTransactionCount(transactions.size())
            .build();
    }
    
    private BigDecimal calculateOpeningBalance(Account account, LocalDate startDate) {
        // Get all transactions from account creation to start date
        List<Transaction> historicalTransactions = transactionRepository.findByAccountIdAndDateRange(
            account.getId(), 
            account.getCreatedAt(),
            startDate.atStartOfDay()
        );
        
        BigDecimal openingBalance = BigDecimal.ZERO;
        
        for (Transaction transaction : historicalTransactions) {
            if (transaction.getStatus() == Transaction.TransactionStatus.COMPLETED) {
                if (isDebitTransaction(transaction, account.getId())) {
                    openingBalance = openingBalance.subtract(transaction.getAmount());
                } else {
                    openingBalance = openingBalance.add(transaction.getAmount());
                }
            }
        }
        
        return openingBalance;
    }
    
    private byte[] generateStatementData(Account account, List<Transaction> transactions, 
                                       StatementSummary summary, StatementFormat format) {
        
        switch (format) {
            case PDF:
                return pdfGenerationService.generateStatementPdf(account, transactions, summary);
                
            case CSV:
                return generateCsvStatement(account, transactions, summary);
                
            case JSON:
                return generateJsonStatement(account, transactions, summary);
                
            default:
                throw new IllegalArgumentException("Unsupported statement format: " + format);
        }
    }
    
    private byte[] generateCsvStatement(Account account, List<Transaction> transactions, 
                                      StatementSummary summary) {
        
        StringBuilder csv = new StringBuilder();
        
        // Header
        csv.append("Account Statement - ").append(account.getAccountNumber()).append("\n");
        csv.append("Generated: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n\n");
        
        // Summary
        csv.append("Opening Balance,").append(summary.getOpeningBalance()).append("\n");
        csv.append("Closing Balance,").append(summary.getClosingBalance()).append("\n");
        csv.append("Total Credits,").append(summary.getTotalCredits()).append("\n");
        csv.append("Total Debits,").append(summary.getTotalDebits()).append("\n\n");
        
        // Transaction header
        csv.append("Date,Description,Type,Amount,Balance,Reference\n");
        
        // Transactions
        BigDecimal runningBalance = summary.getOpeningBalance();
        for (Transaction transaction : transactions) {
            if (transaction.getStatus() == Transaction.TransactionStatus.COMPLETED) {
                boolean isDebit = isDebitTransaction(transaction, account.getId());
                BigDecimal amount = isDebit ? transaction.getAmount().negate() : transaction.getAmount();
                runningBalance = runningBalance.add(amount);
                
                csv.append(transaction.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE)).append(",");
                csv.append("\"").append(transaction.getDescription() != null ? transaction.getDescription() : "").append("\",");
                csv.append(transaction.getType().toString()).append(",");
                csv.append(amount).append(",");
                csv.append(runningBalance).append(",");
                csv.append(transaction.getReferenceNumber() != null ? transaction.getReferenceNumber() : "").append("\n");
            }
        }
        
        return csv.toString().getBytes();
    }
    
    private byte[] generateJsonStatement(Account account, List<Transaction> transactions, 
                                       StatementSummary summary) {
        // This would use Jackson or similar JSON library
        // Simplified implementation for now
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"accountNumber\":\"").append(account.getAccountNumber()).append("\",");
        json.append("\"generatedAt\":\"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",");
        json.append("\"summary\":").append(convertSummaryToJson(summary)).append(",");
        json.append("\"transactions\":[");
        
        for (int i = 0; i < transactions.size(); i++) {
            if (i > 0) json.append(",");
            json.append(convertTransactionToJson(transactions.get(i), account.getId()));
        }
        
        json.append("]}");
        return json.toString().getBytes();
    }
    
    private boolean shouldGenerateStatement(Account account, LocalDate startDate, LocalDate endDate) {
        // Check if statement already generated for this period
        if (account.getLastStatementDate() != null) {
            LocalDate lastStatementMonth = account.getLastStatementDate().toLocalDate().withDayOfMonth(1);
            LocalDate requestedMonth = startDate.withDayOfMonth(1);
            
            if (!lastStatementMonth.isBefore(requestedMonth)) {
                return false; // Already generated for this period
            }
        }
        
        // Check if there were any transactions in the period
        int transactionCount = transactionRepository.countByAccountIdAndCreatedAtBetween(
            account.getId(), startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        
        return transactionCount > 0;
    }
    
    private void storeOrEmailStatement(Account account, StatementResult result) {
        try {
            // STEP 1: Store statement in secure document storage
            String documentId = UUID.randomUUID().toString();
            String storageLocation = documentStorageService.storeStatement(
                documentId,
                account.getAccountNumber(),
                result.getStatementData(),
                result.getStatementPeriod()
            );
            
            // STEP 2: Create statement record in database
            StatementRecord statementRecord = StatementRecord.builder()
                .documentId(documentId)
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .statementPeriod(result.getStatementPeriod())
                .storageLocation(storageLocation)
                .fileSize(result.getStatementData().length)
                .generatedAt(LocalDateTime.now())
                .status("GENERATED")
                .build();
                
            statementRecordRepository.save(statementRecord);
            
            // STEP 3: Send statement via preferred delivery method
            CustomerProfile customerProfile = customerService.getProfile(account.getCustomerId());
            
            if (customerProfile.getStatementDeliveryMethod() == DeliveryMethod.EMAIL) {
                emailStatementService.sendStatement(
                    customerProfile.getEmail(),
                    customerProfile.getFullName(),
                    account.getAccountNumber(),
                    result.getStatementData(),
                    result.getStatementPeriod()
                );
                statementRecord.setEmailSentAt(LocalDateTime.now());
            }
            
            if (customerProfile.getStatementDeliveryMethod() == DeliveryMethod.POSTAL) {
                postalStatementService.schedulePostalDelivery(
                    customerProfile.getMailingAddress(),
                    documentId,
                    account.getAccountNumber()
                );
                statementRecord.setPostalScheduledAt(LocalDateTime.now());
            }
            
            // STEP 4: Update statement record with delivery status
            statementRecord.setStatus("DELIVERED");
            statementRecordRepository.save(statementRecord);
            
            // STEP 5: Create comprehensive audit trail
            auditService.recordStatementGeneration(
                account.getAccountNumber(),
                documentId,
                storageLocation,
                result.getStatementData().length,
                customerProfile.getStatementDeliveryMethod().toString()
            );
            
            log.info("Statement successfully generated and delivered for account: {}, document: {}", 
                account.getAccountNumber(), documentId);
                
        } catch (Exception e) {
            log.error("Failed to store/deliver statement for account: {}", 
                account.getAccountNumber(), e);
                
            // Create exception record for operations team
            statementExceptionService.createException(
                account.getAccountNumber(),
                "STATEMENT_DELIVERY_FAILED",
                e.getMessage(),
                result.getStatementPeriod()
            );
            
            throw new StatementProcessingException(
                "Failed to process statement for account: " + account.getAccountNumber(), e);
        }
    }
    
    private boolean isDebitTransaction(Transaction transaction, UUID accountId) {
        // If this account is the source, it's a debit
        return accountId.equals(transaction.getSourceAccountId());
    }
    
    private boolean isFeeTransaction(Transaction transaction) {
        return transaction.getType().toString().contains("FEE");
    }
    
    private boolean isInterestTransaction(Transaction transaction) {
        return transaction.getType().toString().contains("INTEREST");
    }
    
    private String convertSummaryToJson(StatementSummary summary) {
        return String.format(
            "{\"openingBalance\":%s,\"closingBalance\":%s,\"totalCredits\":%s,\"totalDebits\":%s}",
            summary.getOpeningBalance(),
            summary.getClosingBalance(), 
            summary.getTotalCredits(),
            summary.getTotalDebits()
        );
    }
    
    private String convertTransactionToJson(Transaction transaction, UUID accountId) {
        boolean isDebit = isDebitTransaction(transaction, accountId);
        BigDecimal amount = isDebit ? transaction.getAmount().negate() : transaction.getAmount();
        
        return String.format(
            "{\"date\":"  + transaction.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",\"type\":\"%s\",\"amount\":%s,\"description\":\"%s\"}",
            transaction.getType(),
            amount,
            transaction.getDescription() != null ? transaction.getDescription() : ""
        );
    }
    
    // Enums and DTOs
    
    public enum StatementFormat {
        PDF,
        CSV, 
        JSON
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StatementResult {
        private UUID accountId;
        private String accountNumber;
        private LocalDate startDate;
        private LocalDate endDate;
        private StatementFormat format;
        private byte[] statementData;
        private int transactionCount;
        private StatementSummary summary;
        private LocalDateTime generationTimestamp;
        private String status;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StatementSummary {
        private BigDecimal openingBalance;
        private BigDecimal closingBalance;
        private BigDecimal totalCredits;
        private BigDecimal totalDebits;
        private BigDecimal totalFees;
        private BigDecimal totalInterest;
        private int creditTransactionCount;
        private int debitTransactionCount;
        private int totalTransactionCount;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StatementPeriod {
        private LocalDate startDate;
        private LocalDate endDate;
        private String periodName;
        private int transactionCount;
    }
}