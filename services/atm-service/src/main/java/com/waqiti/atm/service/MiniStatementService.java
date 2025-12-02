package com.waqiti.atm.service;

import com.waqiti.atm.client.AccountServiceClient;
import com.waqiti.atm.dto.TransactionSummary;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mini-Statement Service
 * Retrieves recent transactions for ATM mini-statement printing
 * Implements transaction formatting for receipt display
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MiniStatementService {

    private final AccountServiceClient accountServiceClient;

    /**
     * Get recent transactions for mini-statement
     *
     * @param accountId Account ID
     * @param limit Number of transactions to retrieve
     * @param timestamp Request timestamp
     * @return List of formatted transaction strings
     */
    @CircuitBreaker(name = "account-service")
    @Retry(name = "account-service")
    public List<String> getRecentTransactions(String accountId, int limit, LocalDateTime timestamp) {
        log.debug("Retrieving recent transactions: accountId={}, limit={}", accountId, limit);

        try {
            // Get recent transactions from account service
            List<TransactionSummary> transactions = accountServiceClient
                    .getRecentTransactions(UUID.fromString(accountId), limit);

            // Format for mini-statement display
            List<String> formattedTransactions = transactions.stream()
                    .map(this::formatTransaction)
                    .collect(Collectors.toList());

            log.info("Retrieved {} transactions for mini-statement", formattedTransactions.size());
            return formattedTransactions;

        } catch (Exception e) {
            log.error("Error retrieving recent transactions: {}", e.getMessage(), e);
            return List.of("Error retrieving transactions");
        }
    }

    /**
     * Format transaction for mini-statement display
     * Format: DATE TYPE AMOUNT BALANCE
     * Example: 01/15 ATM WD $100.00 $1,234.56
     */
    private String formatTransaction(TransactionSummary transaction) {
        String date = transaction.getDate().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"));
        String type = abbreviateType(transaction.getType());
        String amount = formatAmount(transaction.getAmount(), transaction.getDebitCredit());
        String balance = formatBalance(transaction.getBalance());

        return String.format("%s %-8s %10s %12s", date, type, amount, balance);
    }

    /**
     * Abbreviate transaction type for compact display
     */
    private String abbreviateType(String type) {
        switch (type) {
            case "ATM_WITHDRAWAL": return "ATM WD";
            case "ATM_DEPOSIT": return "ATM DEP";
            case "DEBIT_CARD": return "POS";
            case "TRANSFER": return "XFER";
            case "PAYMENT": return "PAYMENT";
            case "FEE": return "FEE";
            case "INTEREST": return "INT";
            default: return type.substring(0, Math.min(8, type.length()));
        }
    }

    /**
     * Format amount with debit/credit indicator
     */
    private String formatAmount(java.math.BigDecimal amount, String debitCredit) {
        String sign = "DR".equals(debitCredit) ? "-" : "+";
        return String.format("%s$%.2f", sign, amount);
    }

    /**
     * Format balance
     */
    private String formatBalance(java.math.BigDecimal balance) {
        return String.format("$%,.2f", balance);
    }
}
