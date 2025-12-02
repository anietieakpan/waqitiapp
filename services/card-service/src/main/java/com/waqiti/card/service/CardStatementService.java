package com.waqiti.card.service;

import com.waqiti.card.dto.CardStatementResponse;
import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardStatement;
import com.waqiti.card.entity.CardTransaction;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardStatementRepository;
import com.waqiti.card.repository.CardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CardStatementService - Billing statement management
 *
 * Handles:
 * - Statement generation
 * - Balance calculations
 * - Payment tracking
 * - Statement delivery
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardStatementService {

    private final CardStatementRepository statementRepository;
    private final CardRepository cardRepository;
    private final CardTransactionRepository transactionRepository;

    /**
     * Generate monthly statement for card
     */
    @Transactional
    public CardStatementResponse generateStatement(UUID cardId, int year, int month) {
        log.info("Generating statement for card: {} - {}/{}", cardId, year, month);

        Card card = cardRepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        // Check if statement already exists
        if (statementRepository.findByCardIdAndYearMonth(cardId, year, month).isPresent()) {
            throw new RuntimeException("Statement already exists for this period");
        }

        // Calculate period dates
        LocalDate periodStartDate = LocalDate.of(year, month, 1);
        LocalDate periodEndDate = periodStartDate.plusMonths(1).minusDays(1);
        LocalDate statementDate = LocalDate.now();
        LocalDate paymentDueDate = statementDate.plusDays(21); // 21 days to pay

        // Get previous statement for previous balance
        BigDecimal previousBalance = getPreviousStatementBalance(cardId, year, month);

        // Get transactions for period
        List<CardTransaction> transactions = transactionRepository.findByCardIdAndDateRange(
            cardId,
            periodStartDate.atStartOfDay(),
            periodEndDate.atTime(23, 59, 59)
        );

        // Calculate statement totals
        StatementTotals totals = calculateStatementTotals(transactions);

        // Generate statement ID
        String statementId = generateStatementId(card.getCardId(), year, month);
        String statementNumber = String.format("%s-%04d%02d", card.getCardId(), year, month);

        // Create statement
        CardStatement statement = CardStatement.builder()
            .statementId(statementId)
            .statementNumber(statementNumber)
            .statementYear(year)
            .statementMonth(month)
            .cardId(cardId)
            .userId(card.getUserId())
            .accountId(card.getAccountId())
            .periodStartDate(periodStartDate)
            .periodEndDate(periodEndDate)
            .statementDate(statementDate)
            .daysInPeriod((int) java.time.temporal.ChronoUnit.DAYS.between(periodStartDate, periodEndDate) + 1)
            .statementStatus("GENERATED")
            .isCurrentStatement(true)
            .isFinalized(false)
            .previousBalance(previousBalance)
            .newCharges(totals.newCharges)
            .paymentsReceived(totals.paymentsReceived)
            .creditsApplied(totals.creditsApplied)
            .feesCharged(totals.feesCharged)
            .interestCharged(totals.interestCharged)
            .currencyCode(card.getCreditLimit() != null ? "USD" : "USD") // Default to USD
            .paymentDueDate(paymentDueDate)
            .transactionCount(transactions.size())
            .purchaseCount(totals.purchaseCount)
            .paymentCount(totals.paymentCount)
            .totalPurchases(totals.totalPurchases)
            .totalCashAdvances(totals.totalCashAdvances)
            .totalRefunds(totals.totalRefunds)
            .creditLimit(card.getCreditLimit())
            .availableCredit(card.getAvailableCredit())
            .isPaid(false)
            .isOverdue(false)
            .build();

        // Calculate closing balance
        statement.calculateClosingBalance();

        // Calculate minimum payment (2% of balance or $25, whichever is greater)
        BigDecimal minimumPayment = statement.getClosingBalance()
            .multiply(new BigDecimal("0.02"))
            .max(new BigDecimal("25.00"));
        statement.setMinimumPaymentDue(minimumPayment);
        statement.setTotalAmountDue(statement.getClosingBalance());

        // Calculate credit utilization
        statement.calculateCreditUtilization();

        // Mark previous statement as not current
        statementRepository.findCurrentStatementByCardId(cardId)
            .ifPresent(previousStatement -> {
                previousStatement.setIsCurrentStatement(false);
                statementRepository.save(previousStatement);
            });

        statement = statementRepository.save(statement);

        log.info("Statement generated: {} for card: {}", statementId, card.getCardId());

        return mapToStatementResponse(statement);
    }

    /**
     * Finalize statement
     */
    @Transactional
    public CardStatementResponse finalizeStatement(String statementId) {
        log.info("Finalizing statement: {}", statementId);

        CardStatement statement = statementRepository.findByStatementId(statementId)
            .orElseThrow(() -> new RuntimeException("Statement not found: " + statementId));

        if (statement.getIsFinalized()) {
            throw new RuntimeException("Statement is already finalized");
        }

        statement.finalizeStatement();
        statement = statementRepository.save(statement);

        log.info("Statement finalized: {}", statementId);

        return mapToStatementResponse(statement);
    }

    /**
     * Record payment
     */
    @Transactional
    public CardStatementResponse recordPayment(String statementId, BigDecimal paymentAmount, LocalDate paymentDate) {
        log.info("Recording payment for statement: {} - Amount: {}", statementId, paymentAmount);

        CardStatement statement = statementRepository.findByStatementId(statementId)
            .orElseThrow(() -> new RuntimeException("Statement not found: " + statementId));

        if (statement.getIsPaid()) {
            throw new RuntimeException("Statement is already paid");
        }

        statement.markAsPaid(paymentAmount, paymentDate);
        statement = statementRepository.save(statement);

        // Update card balance
        Card card = cardRepository.findById(statement.getCardId())
            .orElseThrow(() -> new RuntimeException("Card not found"));

        card.setOutstandingBalance(card.getOutstandingBalance().subtract(paymentAmount));
        card.setAvailableCredit(card.getAvailableCredit().add(paymentAmount));
        card.setLastPaymentDate(paymentDate.atStartOfDay());
        cardRepository.save(card);

        log.info("Payment recorded for statement: {}", statementId);

        return mapToStatementResponse(statement);
    }

    /**
     * Get statement by ID
     */
    @Transactional(readOnly = true)
    public CardStatementResponse getStatementById(String statementId) {
        CardStatement statement = statementRepository.findByStatementId(statementId)
            .orElseThrow(() -> new RuntimeException("Statement not found: " + statementId));

        return mapToStatementResponse(statement);
    }

    /**
     * Get statements for card with pagination
     */
    @Transactional(readOnly = true)
    public Page<CardStatementResponse> getStatementsByCardId(UUID cardId, Pageable pageable) {
        Page<CardStatement> statementPage = statementRepository.findByCardId(cardId, pageable);

        return statementPage.map(this::mapToStatementResponse);
    }

    /**
     * Get current statement for card
     */
    @Transactional(readOnly = true)
    public CardStatementResponse getCurrentStatement(UUID cardId) {
        CardStatement statement = statementRepository.findCurrentStatementByCardId(cardId)
            .orElseThrow(() -> new RuntimeException("No current statement found for card: " + cardId));

        return mapToStatementResponse(statement);
    }

    /**
     * Get overdue statements
     */
    @Transactional(readOnly = true)
    public List<CardStatementResponse> getOverdueStatements() {
        List<CardStatement> statements = statementRepository.findOverdueStatements(LocalDate.now());
        return statements.stream()
            .map(this::mapToStatementResponse)
            .collect(Collectors.toList());
    }

    /**
     * Send statement email
     */
    @Transactional
    public void sendStatementEmail(String statementId) {
        log.info("Sending statement email: {}", statementId);

        CardStatement statement = statementRepository.findByStatementId(statementId)
            .orElseThrow(() -> new RuntimeException("Statement not found: " + statementId));

        if (!statement.getIsFinalized()) {
            throw new RuntimeException("Statement must be finalized before sending");
        }

        // Get card and user details for email
        Card card = cardRepository.findByCardIdAndNotDeleted(statement.getCardId().toString())
                .orElseThrow(() -> new RuntimeException("Card not found: " + statement.getCardId()));

        // Format statement period
        String statementPeriod = String.format("%04d-%02d",
                statement.getStatementYear(), statement.getStatementMonth());

        // Send email via notification service
        cardNotificationService.sendStatementNotification(
                card.getUserId(),
                card,
                statementPeriod,
                statement.getClosingBalance(),
                statement.getUserEmail()
        );

        statement.sendEmail();
        statementRepository.save(statement);

        log.info("Statement email sent: {}", statementId);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private BigDecimal getPreviousStatementBalance(UUID cardId, int year, int month) {
        // Calculate previous month
        LocalDate currentPeriod = LocalDate.of(year, month, 1);
        LocalDate previousPeriod = currentPeriod.minusMonths(1);

        return statementRepository.findByCardIdAndYearMonth(
            cardId, previousPeriod.getYear(), previousPeriod.getMonthValue())
            .map(CardStatement::getClosingBalance)
            .orElse(BigDecimal.ZERO);
    }

    private StatementTotals calculateStatementTotals(List<CardTransaction> transactions) {
        StatementTotals totals = new StatementTotals();

        for (CardTransaction tx : transactions) {
            switch (tx.getTransactionType()) {
                case PURCHASE:
                case ONLINE_PURCHASE:
                case CONTACTLESS_PURCHASE:
                    totals.newCharges = totals.newCharges.add(tx.getAmount());
                    totals.totalPurchases = totals.totalPurchases.add(tx.getAmount());
                    totals.purchaseCount++;
                    break;

                case CASH_ADVANCE:
                    totals.newCharges = totals.newCharges.add(tx.getAmount());
                    totals.totalCashAdvances = totals.totalCashAdvances.add(tx.getAmount());
                    break;

                case PAYMENT:
                    totals.paymentsReceived = totals.paymentsReceived.add(tx.getAmount());
                    totals.paymentCount++;
                    break;

                case REFUND:
                    totals.creditsApplied = totals.creditsApplied.add(tx.getAmount());
                    totals.totalRefunds = totals.totalRefunds.add(tx.getAmount());
                    break;

                case FEE:
                    totals.feesCharged = totals.feesCharged.add(tx.getAmount());
                    break;

                case INTEREST:
                    totals.interestCharged = totals.interestCharged.add(tx.getAmount());
                    break;

                default:
                    // Handle other transaction types
                    break;
            }
        }

        return totals;
    }

    private CardStatementResponse mapToStatementResponse(CardStatement statement) {
        return CardStatementResponse.builder()
            .id(statement.getId())
            .statementId(statement.getStatementId())
            .statementNumber(statement.getStatementNumber())
            .cardId(statement.getCardId())
            .userId(statement.getUserId())
            .periodStartDate(statement.getPeriodStartDate())
            .periodEndDate(statement.getPeriodEndDate())
            .statementDate(statement.getStatementDate())
            .statementStatus(statement.getStatementStatus())
            .isCurrentStatement(statement.getIsCurrentStatement())
            .previousBalance(statement.getPreviousBalance())
            .newCharges(statement.getNewCharges())
            .paymentsReceived(statement.getPaymentsReceived())
            .feesCharged(statement.getFeesCharged())
            .interestCharged(statement.getInterestCharged())
            .closingBalance(statement.getClosingBalance())
            .currencyCode(statement.getCurrencyCode())
            .paymentDueDate(statement.getPaymentDueDate())
            .minimumPaymentDue(statement.getMinimumPaymentDue())
            .totalAmountDue(statement.getTotalAmountDue())
            .isPaid(statement.getIsPaid())
            .isOverdue(statement.getIsOverdue())
            .daysOverdue(statement.getDaysOverdue())
            .transactionCount(statement.getTransactionCount())
            .creditLimit(statement.getCreditLimit())
            .availableCredit(statement.getAvailableCredit())
            .statementFileUrl(statement.getStatementFileUrl())
            .build();
    }

    private String generateStatementId() {
        return "STMT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String generateStatementId(String cardId, int year, int month) {
        return String.format("STMT-%s-%04d%02d", cardId, year, month);
    }

    /**
     * Helper class for statement totals
     */
    private static class StatementTotals {
        BigDecimal newCharges = BigDecimal.ZERO;
        BigDecimal paymentsReceived = BigDecimal.ZERO;
        BigDecimal creditsApplied = BigDecimal.ZERO;
        BigDecimal feesCharged = BigDecimal.ZERO;
        BigDecimal interestCharged = BigDecimal.ZERO;
        BigDecimal totalPurchases = BigDecimal.ZERO;
        BigDecimal totalCashAdvances = BigDecimal.ZERO;
        BigDecimal totalRefunds = BigDecimal.ZERO;
        int purchaseCount = 0;
        int paymentCount = 0;
    }
}
