package com.waqiti.business.service;

import com.waqiti.business.domain.*;
import com.waqiti.business.dto.*;
import com.waqiti.business.exception.BusinessExceptions.*;
import com.waqiti.business.repository.*;
import com.waqiti.common.security.AuthenticationFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Comprehensive Business Card Management Service
 * 
 * Handles business card issuance, employee cards, spending controls,
 * real-time monitoring, and expense automation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BusinessCardService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final BusinessAccountRepository businessAccountRepository;
    private final BusinessCardRepository cardRepository;
    private final BusinessEmployeeRepository employeeRepository;
    private final CardTransactionRepository transactionRepository;
    private final CardControlRepository controlRepository;
    private final SpendingLimitRepository limitRepository;
    private final CardRequestRepository requestRepository;
    private final AuthenticationFacade authenticationFacade;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Card limits and constants
    private static final BigDecimal DEFAULT_EMPLOYEE_LIMIT = new BigDecimal("1000.00");
    private static final BigDecimal MAX_SINGLE_TRANSACTION = new BigDecimal("5000.00");
    private static final int MAX_CARDS_PER_BUSINESS = 100;
    private static final int CARD_EXPIRY_YEARS = 3;

    /**
     * Issue master business card for account owner
     */
    public BusinessCardResponse issueMasterCard(UUID accountId, MasterCardRequest request) {
        log.info("Issuing master business card for account: {}", accountId);

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);

            // Check if master card already exists
            if (cardRepository.existsByAccountIdAndCardType(accountId, CardType.MASTER)) {
                throw new DuplicateCardException("Master card already exists for this account");
            }

            // Generate card details
            String cardNumber = generateCardNumber();
            String cvv = generateCVV();
            LocalDate expiryDate = LocalDate.now().plusYears(CARD_EXPIRY_YEARS);

            BusinessCard masterCard = BusinessCard.builder()
                    .accountId(accountId)
                    .cardType(CardType.MASTER)
                    .cardNumber(maskCardNumber(cardNumber))
                    .maskedNumber(maskCardNumber(cardNumber))
                    .expiryDate(expiryDate)
                    .cardholderName(request.getCardholderName())
                    .status(CardStatus.ACTIVE)
                    .spendingLimit(request.getSpendingLimit() != null ? 
                            request.getSpendingLimit() : account.getMonthlyTransactionLimit())
                    .dailyLimit(request.getDailyLimit())
                    .monthlyLimit(request.getMonthlyLimit())
                    .issuedAt(LocalDateTime.now())
                    .build();

            masterCard = cardRepository.save(masterCard);

            // Create default card controls
            createDefaultCardControls(masterCard, request.getCardControls());

            // Send card activation notification
            sendCardIssuedNotification(masterCard, account);

            // Publish card issued event
            publishCardEvent("MASTER_CARD_ISSUED", masterCard);

            log.info("Master business card issued: {} for account: {}", masterCard.getId(), accountId);

            return mapToCardResponse(masterCard);

        } catch (Exception e) {
            log.error("Failed to issue master card for account: {}", accountId, e);
            throw new CardIssuanceException("Failed to issue master card: " + e.getMessage(), e);
        }
    }

    /**
     * Request employee card
     */
    public CardRequestResponse requestEmployeeCard(UUID accountId, EmployeeCardRequest request) {
        log.info("Processing employee card request for account: {} employee: {}", 
                accountId, request.getEmployeeId());

        try {
            BusinessAccount account = getValidatedBusinessAccount(accountId);
            BusinessEmployee employee = getValidatedEmployee(accountId, request.getEmployeeId());

            // Check card limits
            int currentCardCount = cardRepository.countByAccountIdAndStatus(accountId, CardStatus.ACTIVE);
            if (currentCardCount >= MAX_CARDS_PER_BUSINESS) {
                throw new CardLimitExceededException("Maximum card limit reached for business");
            }

            // Check if employee already has a card
            if (cardRepository.existsByAccountIdAndEmployeeId(accountId, request.getEmployeeId())) {
                throw new DuplicateCardException("Employee already has a business card");
            }

            // Create card request
            BusinessCardRequest cardRequest = BusinessCardRequest.builder()
                    .accountId(accountId)
                    .employeeId(request.getEmployeeId())
                    .requestedBy(UUID.fromString(authenticationFacade.getCurrentUserId()))
                    .cardType(CardType.EMPLOYEE)
                    .requestedSpendingLimit(request.getRequestedSpendingLimit() != null ? 
                            request.getRequestedSpendingLimit() : DEFAULT_EMPLOYEE_LIMIT)
                    .justification(request.getJustification())
                    .urgency(request.getUrgency())
                    .status(determineRequestStatus(account, request))
                    .requestedAt(LocalDateTime.now())
                    .build();

            cardRequest = requestRepository.save(cardRequest);

            // Auto-approve if within limits and policies
            if (cardRequest.getStatus() == RequestStatus.AUTO_APPROVED) {
                return processCardApproval(cardRequest, true);
            }

            // Send approval notification to business owner
            sendCardRequestNotification(cardRequest, employee, account);

            return CardRequestResponse.builder()
                    .requestId(cardRequest.getId())
                    .status(cardRequest.getStatus())
                    .estimatedProcessingTime(calculateProcessingTime(cardRequest))
                    .build();

        } catch (Exception e) {
            log.error("Failed to process employee card request", e);
            throw new CardRequestException("Failed to process card request: " + e.getMessage(), e);
        }
    }

    /**
     * Approve or reject card request
     */
    public CardRequestResponse processCardRequest(UUID accountId, UUID requestId, CardApprovalRequest request) {
        log.info("Processing card request: {} decision: {}", requestId, request.getDecision());

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessCardRequest cardRequest = getValidatedCardRequest(accountId, requestId);

        if (cardRequest.getStatus() != RequestStatus.PENDING) {
            throw new InvalidRequestStatusException("Card request is not in pending status");
        }

        // Update request
        cardRequest.setApprovedBy(UUID.fromString(authenticationFacade.getCurrentUserId()));
        cardRequest.setApprovedAt(LocalDateTime.now());
        cardRequest.setApprovalNotes(request.getNotes());
        cardRequest.setApprovedSpendingLimit(request.getApprovedSpendingLimit());
        cardRequest.setStatus(request.getDecision() == CardDecision.APPROVE ? 
                RequestStatus.APPROVED : RequestStatus.REJECTED);

        cardRequest = requestRepository.save(cardRequest);

        // Process approval
        return processCardApproval(cardRequest, request.getDecision() == CardDecision.APPROVE);
    }

    /**
     * Issue physical employee card after approval
     */
    private CardRequestResponse processCardApproval(BusinessCardRequest cardRequest, boolean approved) {
        if (!approved) {
            sendCardRejectedNotification(cardRequest);
            return CardRequestResponse.builder()
                    .requestId(cardRequest.getId())
                    .status(cardRequest.getStatus())
                    .build();
        }

        try {
            // Issue employee card
            BusinessCard employeeCard = issueEmployeeCard(cardRequest);

            // Send card issued confirmation
            sendEmployeeCardIssuedNotification(employeeCard, cardRequest);

            return CardRequestResponse.builder()
                    .requestId(cardRequest.getId())
                    .status(RequestStatus.CARD_ISSUED)
                    .cardId(employeeCard.getId())
                    .estimatedDelivery(LocalDate.now().plusDays(7))
                    .build();

        } catch (Exception e) {
            log.error("Failed to issue employee card for request: {}", cardRequest.getId(), e);
            throw new CardIssuanceException("Failed to issue employee card", e);
        }
    }

    /**
     * Issue employee card
     */
    private BusinessCard issueEmployeeCard(BusinessCardRequest cardRequest) {
        BusinessEmployee employee = employeeRepository.findById(cardRequest.getEmployeeId())
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found"));

        String cardNumber = generateCardNumber();
        String cvv = generateCVV();
        LocalDate expiryDate = LocalDate.now().plusYears(CARD_EXPIRY_YEARS);

        BusinessCard employeeCard = BusinessCard.builder()
                .accountId(cardRequest.getAccountId())
                .employeeId(cardRequest.getEmployeeId())
                .cardType(CardType.EMPLOYEE)
                .cardNumber(maskCardNumber(cardNumber))
                .maskedNumber(maskCardNumber(cardNumber))
                .expiryDate(expiryDate)
                .cardholderName(employee.getFirstName() + " " + employee.getLastName())
                .status(CardStatus.PENDING_ACTIVATION)
                .spendingLimit(cardRequest.getApprovedSpendingLimit())
                .dailyLimit(calculateDailyLimit(cardRequest.getApprovedSpendingLimit()))
                .monthlyLimit(cardRequest.getApprovedSpendingLimit())
                .issuedAt(LocalDateTime.now())
                .build();

        employeeCard = cardRepository.save(employeeCard);

        // Create employee-specific card controls
        createEmployeeCardControls(employeeCard, employee);

        return employeeCard;
    }

    /**
     * Set up card spending controls and restrictions
     */
    public CardControlResponse configureCardControls(UUID accountId, UUID cardId, CardControlRequest request) {
        log.info("Configuring card controls for card: {}", cardId);

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessCard card = getValidatedCard(accountId, cardId);

        // Update existing controls or create new ones
        CardControl control = controlRepository.findByCardId(cardId)
                .orElse(CardControl.builder().cardId(cardId).build());

        control.setMerchantCategories(request.getAllowedMerchantCategories());
        control.setBlockedMerchantCategories(request.getBlockedMerchantCategories());
        control.setGeographicRestrictions(request.getGeographicRestrictions());
        control.setTimeRestrictions(request.getTimeRestrictions());
        control.setTransactionTypes(request.getAllowedTransactionTypes());
        control.setOnlineTransactionsEnabled(request.isOnlineTransactionsEnabled());
        control.setInternationalTransactionsEnabled(request.isInternationalTransactionsEnabled());
        control.setAtmWithdrawalsEnabled(request.isAtmWithdrawalsEnabled());
        control.setUpdatedAt(LocalDateTime.now());

        control = controlRepository.save(control);

        // Update real-time monitoring rules
        updateMonitoringRules(card, control);

        return mapToControlResponse(control);
    }

    /**
     * Monitor and process real-time transactions
     */
    @Async
    public CompletableFuture<TransactionAuthorizationResponse> authorizeTransaction(
            TransactionAuthorizationRequest request) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Authorizing transaction: {} amount: {}", request.getTransactionId(), request.getAmount());

                // Find card
                BusinessCard card = cardRepository.findByCardNumber(request.getCardNumber())
                        .orElseThrow(() -> new CardNotFoundException("Card not found"));

                // Validate card status
                if (card.getStatus() != CardStatus.ACTIVE) {
                    return TransactionAuthorizationResponse.declined("Card is not active");
                }

                // Check spending limits
                SpendingLimitCheckResult limitCheck = checkSpendingLimits(card, request);
                if (!limitCheck.isWithinLimits()) {
                    return TransactionAuthorizationResponse.declined("Spending limit exceeded: " + limitCheck.getReason());
                }

                // Check card controls
                CardControlCheckResult controlCheck = checkCardControls(card, request);
                if (!controlCheck.isAllowed()) {
                    return TransactionAuthorizationResponse.declined("Transaction blocked: " + controlCheck.getReason());
                }

                // Fraud detection
                FraudCheckResult fraudCheck = performFraudCheck(card, request);
                if (fraudCheck.isFraudulent()) {
                    return TransactionAuthorizationResponse.declined("Transaction flagged for fraud");
                }

                // Create transaction record
                CardTransaction transaction = createTransactionRecord(card, request);

                // Update spending totals
                updateSpendingTotals(card, request.getAmount());

                // Send real-time notification
                sendTransactionNotification(card, transaction);

                log.info("Transaction authorized: {} for card: {}", request.getTransactionId(), card.getId());

                return TransactionAuthorizationResponse.approved(transaction.getId());

            } catch (Exception e) {
                log.error("Error authorizing transaction: {}", request.getTransactionId(), e);
                return TransactionAuthorizationResponse.declined("System error");
            }
        });
    }

    /**
     * Temporarily lock/unlock card
     */
    public CardResponse lockCard(UUID accountId, UUID cardId, CardLockRequest request) {
        log.info("Locking card: {} reason: {}", cardId, request.getReason());

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessCard card = getValidatedCard(accountId, cardId);

        CardStatus previousStatus = card.getStatus();
        card.setStatus(CardStatus.LOCKED);
        card.setLockReason(request.getReason());
        card.setLockedAt(LocalDateTime.now());
        card.setLockedBy(UUID.fromString(authenticationFacade.getCurrentUserId()));

        card = cardRepository.save(card);

        // Create lock audit record
        createLockAuditRecord(card, previousStatus, request.getReason());

        // Send lock notification
        sendCardLockedNotification(card);

        return mapToCardResponse(card);
    }

    /**
     * Unlock card
     */
    public CardResponse unlockCard(UUID accountId, UUID cardId) {
        log.info("Unlocking card: {}", cardId);

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessCard card = getValidatedCard(accountId, cardId);

        if (card.getStatus() != CardStatus.LOCKED) {
            throw new InvalidCardStatusException("Card is not in locked status");
        }

        card.setStatus(CardStatus.ACTIVE);
        card.setLockReason(null);
        card.setLockedAt(null);
        card.setLockedBy(null);
        card.setUnlockedAt(LocalDateTime.now());

        card = cardRepository.save(card);

        // Send unlock notification
        sendCardUnlockedNotification(card);

        return mapToCardResponse(card);
    }

    /**
     * Get card transaction history with filtering
     */
    @Transactional(readOnly = true)
    public Page<CardTransactionResponse> getCardTransactions(UUID accountId, UUID cardId, 
                                                           CardTransactionFilter filter, Pageable pageable) {
        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessCard card = getValidatedCard(accountId, cardId);

        Page<CardTransaction> transactions = transactionRepository.findByFilters(
                cardId, filter.getStartDate(), filter.getEndDate(), 
                filter.getMerchantCategory(), filter.getMinAmount(), filter.getMaxAmount(), pageable);

        return transactions.map(this::mapToTransactionResponse);
    }

    /**
     * Generate card spending analytics
     */
    @Transactional(readOnly = true)
    public CardSpendingAnalytics generateSpendingAnalytics(UUID accountId, UUID cardId, 
                                                         SpendingAnalyticsRequest request) {
        log.info("Generating spending analytics for card: {} period: {}", cardId, request.getPeriod());

        BusinessAccount account = getValidatedBusinessAccount(accountId);
        BusinessCard card = getValidatedCard(accountId, cardId);

        // Get transactions for the period
        List<CardTransaction> transactions = transactionRepository.findByCardIdAndTransactionDateBetween(
                cardId, request.getStartDate(), request.getEndDate());

        // Calculate spending metrics
        SpendingMetrics metrics = calculateSpendingMetrics(transactions);

        // Category breakdown
        Map<String, CategorySpending> categoryBreakdown = analyzeCategorySpending(transactions);

        // Merchant analysis
        List<MerchantSpending> topMerchants = analyzeTopMerchants(transactions);

        // Spending trends
        List<SpendingTrend> trends = calculateSpendingTrends(transactions, request.getPeriod());

        // Budget comparison
        BudgetComparison budgetComparison = compareToBudget(card, metrics);

        return CardSpendingAnalytics.builder()
                .cardId(cardId)
                .period(request.getPeriod())
                .metrics(metrics)
                .categoryBreakdown(categoryBreakdown)
                .topMerchants(topMerchants)
                .trends(trends)
                .budgetComparison(budgetComparison)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    // Helper methods for card management

    private BusinessAccount getValidatedBusinessAccount(UUID accountId) {
        BusinessAccount account = businessAccountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessAccountNotFoundException("Business account not found"));

        String currentUserId = authenticationFacade.getCurrentUserId();
        if (!account.getOwnerId().toString().equals(currentUserId)) {
            throw new UnauthorizedBusinessAccessException("Unauthorized access to business account");
        }

        return account;
    }

    private BusinessEmployee getValidatedEmployee(UUID accountId, UUID employeeId) {
        return employeeRepository.findByIdAndAccountId(employeeId, accountId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found"));
    }

    private BusinessCard getValidatedCard(UUID accountId, UUID cardId) {
        return cardRepository.findByIdAndAccountId(cardId, accountId)
                .orElseThrow(() -> new CardNotFoundException("Card not found"));
    }

    private BusinessCardRequest getValidatedCardRequest(UUID accountId, UUID requestId) {
        return requestRepository.findByIdAndAccountId(requestId, accountId)
                .orElseThrow(() -> new CardRequestNotFoundException("Card request not found"));
    }

    private String generateCardNumber() {
        // Generate a test card number (in production, integrate with card processor)
        return "4532" + String.format("%012d", Math.abs(SECURE_RANDOM.nextLong() % 1000000000000L));
    }

    private String generateCVV() {
        return String.format("%03d", SECURE_RANDOM.nextInt(1000));
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber.length() < 4) return cardNumber;
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    private RequestStatus determineRequestStatus(BusinessAccount account, EmployeeCardRequest request) {
        // Auto-approve if within auto-approval limits
        if (request.getRequestedSpendingLimit().compareTo(account.getAutoApprovalLimit()) <= 0) {
            return RequestStatus.AUTO_APPROVED;
        }
        return RequestStatus.PENDING;
    }

    private BigDecimal calculateDailyLimit(BigDecimal monthlyLimit) {
        return monthlyLimit.divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
    }

    // Placeholder implementations for complex operations
    private void createDefaultCardControls(BusinessCard card, CardControlRequest controls) {}
    private void createEmployeeCardControls(BusinessCard card, BusinessEmployee employee) {}
    private void updateMonitoringRules(BusinessCard card, CardControl control) {}
    private SpendingLimitCheckResult checkSpendingLimits(BusinessCard card, TransactionAuthorizationRequest request) {
        return SpendingLimitCheckResult.withinLimits();
    }
    private CardControlCheckResult checkCardControls(BusinessCard card, TransactionAuthorizationRequest request) {
        return CardControlCheckResult.allowed();
    }
    private FraudCheckResult performFraudCheck(BusinessCard card, TransactionAuthorizationRequest request) {
        return FraudCheckResult.legitimate();
    }

    // Notification methods
    private void sendCardIssuedNotification(BusinessCard card, BusinessAccount account) {}
    private void sendCardRequestNotification(BusinessCardRequest request, BusinessEmployee employee, BusinessAccount account) {}
    private void sendCardRejectedNotification(BusinessCardRequest request) {}
    private void sendEmployeeCardIssuedNotification(BusinessCard card, BusinessCardRequest request) {}
    private void sendTransactionNotification(BusinessCard card, CardTransaction transaction) {}
    private void sendCardLockedNotification(BusinessCard card) {}
    private void sendCardUnlockedNotification(BusinessCard card) {}

    // Event publishing
    private void publishCardEvent(String eventType, BusinessCard card) {
        Map<String, Object> event = Map.of(
                "eventType", eventType,
                "cardId", card.getId(),
                "accountId", card.getAccountId(),
                "timestamp", LocalDateTime.now()
        );
        kafkaTemplate.send("business-card-events", card.getId().toString(), event);
    }

    // Response mapping methods
    private BusinessCardResponse mapToCardResponse(BusinessCard card) {
        return BusinessCardResponse.builder()
                .cardId(card.getId())
                .maskedNumber(card.getMaskedNumber())
                .cardholderName(card.getCardholderName())
                .status(card.getStatus())
                .spendingLimit(card.getSpendingLimit())
                .expiryDate(card.getExpiryDate())
                .build();
    }

    // Enum definitions and data classes
    public enum CardType { MASTER, EMPLOYEE, VIRTUAL }
    public enum CardStatus { PENDING_ACTIVATION, ACTIVE, LOCKED, SUSPENDED, EXPIRED, CANCELLED }
    public enum RequestStatus { PENDING, AUTO_APPROVED, APPROVED, REJECTED, CARD_ISSUED }
    public enum CardDecision { APPROVE, REJECT }
}