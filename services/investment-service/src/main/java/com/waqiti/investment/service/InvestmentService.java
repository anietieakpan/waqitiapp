package com.waqiti.investment.service;

import com.waqiti.investment.domain.*;
import com.waqiti.investment.dto.*;
import com.waqiti.investment.repository.*;
import com.waqiti.investment.provider.BrokerageProvider;
import com.waqiti.investment.exception.*;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.InvestmentEvent;
import com.waqiti.common.financial.BigDecimalMath;
import com.waqiti.common.exception.ErrorCode;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Investment Service - Handles investment accounts, trading, and portfolio management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentService {

    @Lazy
    private final InvestmentService self;

    private final InvestmentAccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final WatchlistRepository watchlistRepository;
    private final AutoInvestRepository autoInvestRepository;
    private final BrokerageProvider brokerageProvider;
    private final MarketDataService marketDataService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    private final SecurityContext securityContext;
    private final KYCClientService kycClientService;
    
    @Value("${investment.min-order-amount:1.00}")
    private BigDecimal minOrderAmount;
    
    @Value("${investment.max-order-amount:10000.00}")
    private BigDecimal maxOrderAmount;
    
    @Value("${investment.fractional-shares:true}")
    private boolean fractionalSharesEnabled;

    /**
     * Create investment account
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.INTERMEDIATE, action = "INVESTMENT_ACCOUNT")
    public InvestmentAccountDto createInvestmentAccount(CreateAccountRequest request) {
        String userId = securityContext.getCurrentUserId();
        
        // Validate request
        validateAccountCreation(request, userId);
        
        // Check if user already has an account
        if (accountRepository.existsByUserId(userId)) {
            throw new AccountAlreadyExistsException("User already has an investment account");
        }
        
        // Create account with brokerage provider
        BrokerageAccountResponse brokerageResponse = brokerageProvider.createAccount(
            BrokerageAccountRequest.builder()
                .userId(userId)
                .accountType(request.getAccountType())
                .personalInfo(request.getPersonalInfo())
                .financialInfo(request.getFinancialInfo())
                .investmentObjectives(request.getInvestmentObjectives())
                .build()
        );
        
        // Create investment account
        InvestmentAccount account = InvestmentAccount.builder()
            .userId(userId)
            .brokerageAccountId(brokerageResponse.getAccountId())
            .accountType(request.getAccountType())
            .status(AccountStatus.PENDING_APPROVAL)
            .totalValue(BigDecimal.ZERO)
            .cashBalance(BigDecimal.ZERO)
            .buyingPower(BigDecimal.ZERO)
            .dayTradingBuyingPower(BigDecimal.ZERO)
            .portfolioValue(BigDecimal.ZERO)
            .totalGainLoss(BigDecimal.ZERO)
            .totalGainLossPercent(BigDecimal.ZERO)
            .dayGainLoss(BigDecimal.ZERO)
            .dayGainLossPercent(BigDecimal.ZERO)
            .dividendYield(BigDecimal.ZERO)
            .riskProfile(request.getRiskProfile())
            .settings(AccountSettings.builder()
                .dividendReinvestment(true)
                .autoInvestEnabled(false)
                .fractionalShares(fractionalSharesEnabled)
                .advancedOrders(false)
                .marginTrading(false)
                .build())
            .createdAt(Instant.now())
            .build();
        
        account = accountRepository.save(account);
        
        // Create initial portfolio
        Portfolio portfolio = Portfolio.builder()
            .accountId(account.getId())
            .name("My Portfolio")
            .totalValue(BigDecimal.ZERO)
            .totalCost(BigDecimal.ZERO)
            .totalGainLoss(BigDecimal.ZERO)
            .totalGainLossPercent(BigDecimal.ZERO)
            .dayGainLoss(BigDecimal.ZERO)
            .dayGainLossPercent(BigDecimal.ZERO)
            .diversificationScore(BigDecimal.ZERO)
            .riskScore(BigDecimal.ZERO)
            .createdAt(Instant.now())
            .build();
        
        portfolioRepository.save(portfolio);
        
        // Send welcome notification
        notificationService.sendInvestmentAccountCreatedNotification(userId, account);
        
        // Publish event
        eventPublisher.publish(InvestmentEvent.accountCreated(account));
        
        log.info("Created investment account {} for user {}", account.getId(), userId);
        
        return toAccountDto(account);
    }

    /**
     * Fund investment account from wallet
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "FUND_INVESTMENT_ACCOUNT")
    public TransferDto fundAccount(String accountId, FundAccountRequest request) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
        
        // Validate amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        
        // Check wallet balance
        BigDecimal walletBalance = walletService.getBalance(userId, "USD");
        if (request.getAmount().compareTo(walletBalance) > 0) {
            throw new InsufficientFundsException("Insufficient wallet balance");
        }
        
        try {
            // Process transfer with brokerage
            BrokerageTransferResponse transferResponse = brokerageProvider.depositFunds(
                account.getBrokerageAccountId(),
                request.getAmount()
            );
            
            // Debit from wallet
            walletService.debit(userId, request.getAmount(), "USD", 
                "Investment account funding", 
                Map.of("accountId", accountId, "transferId", transferResponse.getTransferId()));
            
            // Update account balance
            account.setCashBalance(account.getCashBalance().add(request.getAmount()));
            account.setBuyingPower(account.getBuyingPower().add(request.getAmount()));
            account.setTotalValue(account.getTotalValue().add(request.getAmount()));
            accountRepository.save(account);
            
            // Create transfer record
            Transfer transfer = Transfer.builder()
                .accountId(accountId)
                .brokerageTransferId(transferResponse.getTransferId())
                .type(TransferType.DEPOSIT)
                .amount(request.getAmount())
                .status(TransferStatus.COMPLETED)
                .processedAt(Instant.now())
                .build();
            
            transfer = accountRepository.saveTransfer(transfer);
            
            // Send notification
            notificationService.sendAccountFundedNotification(userId, account, request.getAmount());
            
            log.info("Funded account {} with ${} for user {}", accountId, request.getAmount(), userId);
            
            return toTransferDto(transfer);
            
        } catch (Exception e) {
            log.error("Failed to fund account {} for user {}", accountId, userId, e);
            throw new InvestmentException("Failed to fund account", e);
        }
    }

    /**
     * Create investment order
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "INVESTMENT_ORDER")
    public InvestmentOrderDto createOrder(String accountId, CreateOrderRequest request) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
        
        // Validate order
        validateOrder(request, account);
        
        // Get current market price
        BigDecimal currentPrice = marketDataService.getCurrentPrice(request.getSymbol());
        
        // Calculate order value
        BigDecimal orderValue = calculateOrderValue(request, currentPrice);
        
        // Enhanced KYC check for high-value orders
        if (orderValue.compareTo(new BigDecimal("10000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(userId)) {
                throw new KycRequiredException("Enhanced KYC verification required for investment orders over $10,000");
            }
        }
        
        // Check buying power
        if (request.getSide() == OrderSide.BUY && 
            orderValue.compareTo(account.getBuyingPower()) > 0) {
            throw new InsufficientFundsException("Insufficient buying power");
        }
        
        // Create order
        InvestmentOrder order = InvestmentOrder.builder()
            .accountId(accountId)
            .symbol(request.getSymbol())
            .side(request.getSide())
            .type(request.getType())
            .timeInForce(request.getTimeInForce())
            .quantity(request.getQuantity())
            .notionalAmount(request.getNotionalAmount())
            .limitPrice(request.getLimitPrice())
            .stopPrice(request.getStopPrice())
            .estimatedValue(orderValue)
            .status(OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        order = orderRepository.save(order);
        
        try {
            // Submit order to brokerage
            BrokerageOrderResponse brokerageResponse = brokerageProvider.submitOrder(
                account.getBrokerageAccountId(),
                BrokerageOrderRequest.builder()
                    .symbol(request.getSymbol())
                    .side(request.getSide())
                    .type(request.getType())
                    .timeInForce(request.getTimeInForce())
                    .quantity(request.getQuantity())
                    .notionalAmount(request.getNotionalAmount())
                    .limitPrice(request.getLimitPrice())
                    .stopPrice(request.getStopPrice())
                    .build()
            );
            
            // Update order with brokerage ID
            order.setBrokerageOrderId(brokerageResponse.getOrderId());
            order.setStatus(OrderStatus.SUBMITTED);
            order.setSubmittedAt(Instant.now());
            orderRepository.save(order);
            
            // Update buying power if buy order
            if (request.getSide() == OrderSide.BUY) {
                account.setBuyingPower(account.getBuyingPower().subtract(orderValue));
                accountRepository.save(account);
            }
            
            // Send notification
            notificationService.sendOrderSubmittedNotification(userId, order);
            
            // Publish event
            eventPublisher.publish(InvestmentEvent.orderCreated(account, order));
            
            log.info("Created order {} for account {} - {} {} shares of {}", 
                order.getId(), accountId, request.getSide(), request.getQuantity(), request.getSymbol());
            
            return toOrderDto(order);
            
        } catch (Exception e) {
            log.error("Failed to submit order {} to brokerage", order.getId(), e);
            
            // Update order status to failed
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("Failed to submit to brokerage: " + e.getMessage());
            orderRepository.save(order);
            
            throw new OrderSubmissionException("Failed to submit order", e);
        }
    }

    /**
     * Cancel an order
     */
    @Transactional
    public InvestmentOrderDto cancelOrder(String accountId, String orderId) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        InvestmentOrder order = orderRepository.findByIdAndAccountId(orderId, accountId)
            .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        
        if (!order.getStatus().isCancellable()) {
            throw new IllegalStateException("Order cannot be cancelled in current status: " + order.getStatus());
        }
        
        try {
            // Cancel with brokerage
            brokerageProvider.cancelOrder(account.getBrokerageAccountId(), order.getBrokerageOrderId());
            
            // Update order status
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(Instant.now());
            orderRepository.save(order);
            
            // Restore buying power if buy order
            if (order.getSide() == OrderSide.BUY) {
                account.setBuyingPower(account.getBuyingPower().add(order.getEstimatedValue()));
                accountRepository.save(account);
            }
            
            // Send notification
            notificationService.sendOrderCancelledNotification(userId, order);
            
            log.info("Cancelled order {} for account {}", orderId, accountId);
            
            return toOrderDto(order);
            
        } catch (Exception e) {
            log.error("Failed to cancel order {} for account {}", orderId, accountId, e);
            throw new OrderCancellationException("Failed to cancel order", e);
        }
    }

    /**
     * Get portfolio summary
     */
    @Transactional(readOnly = true)
    public PortfolioDto getPortfolio(String accountId) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        Portfolio portfolio = portfolioRepository.findByAccountId(accountId)
            .orElseThrow(() -> new PortfolioNotFoundException("Portfolio not found"));
        
        // Get current holdings
        List<InvestmentHolding> holdings = holdingRepository.findByAccountId(accountId);
        
        // Update portfolio with current market values
        updatePortfolioValues(portfolio, holdings);
        
        return toPortfolioDto(portfolio, holdings);
    }

    /**
     * Get account positions
     */
    @Transactional(readOnly = true)
    public List<PositionDto> getPositions(String accountId) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        List<InvestmentHolding> holdings = holdingRepository.findByAccountId(accountId);
        
        return holdings.stream()
            .map(this::toPositionDto)
            .collect(Collectors.toList());
    }

    /**
     * Add symbol to watchlist
     */
    @Transactional
    public WatchlistItemDto addToWatchlist(String accountId, AddToWatchlistRequest request) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        // Check if already in watchlist
        if (watchlistRepository.existsByAccountIdAndSymbol(accountId, request.getSymbol())) {
            throw new InvestmentException(ErrorCode.BIZ_INVALID_OPERATION, 
                "Symbol already exists in watchlist")
                .withMetadata("symbol", request.getSymbol())
                .withMetadata("accountId", accountId);
        }
        
        // Validate symbol
        if (!marketDataService.isValidSymbol(request.getSymbol())) {
            throw new InvestmentException(ErrorCode.VAL_INVALID_FORMAT, 
                "Invalid trading symbol: " + request.getSymbol())
                .withMetadata("symbol", request.getSymbol());
        }
        
        // Get security info
        SecurityInfo securityInfo = marketDataService.getSecurityInfo(request.getSymbol());
        
        WatchlistItem item = WatchlistItem.builder()
            .accountId(accountId)
            .symbol(request.getSymbol())
            .companyName(securityInfo.getCompanyName())
            .sector(securityInfo.getSector())
            .priceTarget(request.getPriceTarget())
            .notes(request.getNotes())
            .addedAt(Instant.now())
            .build();
        
        item = watchlistRepository.save(item);
        
        log.info("Added {} to watchlist for account {}", request.getSymbol(), accountId);
        
        return toWatchlistItemDto(item);
    }

    /**
     * Setup auto-invest
     */
    @Transactional
    @RequireKYCVerification(level = VerificationLevel.INTERMEDIATE, action = "AUTO_INVEST_SETUP")
    public AutoInvestDto setupAutoInvest(String accountId, SetupAutoInvestRequest request) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        // Validate request
        validateAutoInvestRequest(request);
        
        // Create auto-invest configuration
        AutoInvest autoInvest = AutoInvest.builder()
            .accountId(accountId)
            .frequency(request.getFrequency())
            .amount(request.getAmount())
            .symbols(request.getSymbols())
            .allocationPercentages(request.getAllocationPercentages())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .enabled(true)
            .lastExecutionDate(null)
            .nextExecutionDate(calculateNextAutoInvestDate(request.getFrequency(), request.getStartDate()))
            .totalInvested(BigDecimal.ZERO)
            .executionCount(0)
            .createdAt(Instant.now())
            .build();
        
        autoInvest = autoInvestRepository.save(autoInvest);
        
        // Update account settings
        account.getSettings().setAutoInvestEnabled(true);
        accountRepository.save(account);
        
        // Send notification
        notificationService.sendAutoInvestSetupNotification(userId, autoInvest);
        
        log.info("Setup auto-invest for account {} - ${} every {}", 
            accountId, request.getAmount(), request.getFrequency());
        
        return toAutoInvestDto(autoInvest);
    }

    /**
     * Get investment performance
     */
    @Transactional(readOnly = true)
    public PerformanceDto getPerformance(String accountId, PerformanceTimeframe timeframe) {
        String userId = securityContext.getCurrentUserId();
        InvestmentAccount account = getAccountByIdAndUser(accountId, userId);
        
        // Calculate performance metrics
        Instant startDate = calculateStartDate(timeframe);
        
        List<PerformanceDataPoint> historicalValues = accountRepository
            .getHistoricalValues(accountId, startDate);
        
        BigDecimal startValue = historicalValues.isEmpty() ? 
            BigDecimal.ZERO : historicalValues.get(0).getValue();
        BigDecimal endValue = account.getTotalValue();
        
        BigDecimal totalReturn = endValue.subtract(startValue);
        BigDecimal totalReturnPercent = startValue.compareTo(BigDecimal.ZERO) > 0 ?
            totalReturn.divide(startValue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        
        // Calculate annualized return
        long days = Duration.between(startDate, Instant.now()).toDays();
        BigDecimal annualizedReturn = calculateAnnualizedReturn(totalReturnPercent, days);
        
        // Calculate benchmark comparison (S&P 500)
        BigDecimal benchmarkReturn = marketDataService.getBenchmarkReturn("SPY", startDate);
        BigDecimal alpha = totalReturnPercent.subtract(benchmarkReturn);
        
        return PerformanceDto.builder()
            .timeframe(timeframe)
            .startDate(startDate)
            .endDate(Instant.now())
            .startValue(startValue)
            .endValue(endValue)
            .totalReturn(totalReturn)
            .totalReturnPercent(totalReturnPercent)
            .annualizedReturn(annualizedReturn)
            .benchmarkReturn(benchmarkReturn)
            .alpha(alpha)
            .volatility(calculateVolatility(historicalValues))
            .sharpeRatio(calculateSharpeRatio(annualizedReturn, calculateVolatility(historicalValues)))
            .maxDrawdown(calculateMaxDrawdown(historicalValues))
            .historicalValues(historicalValues)
            .build();
    }

    /**
     * Process order execution (called by brokerage webhook)
     */
    @Transactional
    public void processOrderExecution(OrderExecutionWebhook webhook) {
        InvestmentOrder order = orderRepository.findByBrokerageOrderId(webhook.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException("Order not found"));
        
        InvestmentAccount account = accountRepository.findById(order.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        try {
            // Update order status
            order.setStatus(OrderStatus.FILLED);
            order.setFilledQuantity(webhook.getQuantity());
            order.setFillPrice(webhook.getPrice());
            order.setFilledValue(webhook.getQuantity().multiply(webhook.getPrice()));
            order.setFilledAt(Instant.now());
            orderRepository.save(order);
            
            // Update holdings
            updateHoldings(account, order, webhook);
            
            // Update account balances
            updateAccountBalances(account, order, webhook);
            
            // Send notification
            notificationService.sendOrderFilledNotification(account.getUserId(), order);
            
            // Publish event
            eventPublisher.publish(InvestmentEvent.orderFilled(account, order));
            
            log.info("Processed order execution for order {} - {} {} shares at ${}", 
                order.getId(), webhook.getQuantity(), order.getSymbol(), webhook.getPrice());
            
        } catch (Exception e) {
            log.error("Failed to process order execution for order {}", order.getId(), e);
            throw e;
        }
    }

    /**
     * Scheduled job to process auto-investments
     */
    @Scheduled(cron = "0 0 10 * * ?") // Daily at 10 AM
    @Transactional
    public void processAutoInvestments() {
        log.info("Processing auto-investments");
        
        LocalDate today = LocalDate.now();
        
        List<AutoInvest> dueAutoInvests = autoInvestRepository.findDueForExecution(today);
        
        for (AutoInvest autoInvest : dueAutoInvests) {
            try {
                processAutoInvestExecution(autoInvest);
            } catch (Exception e) {
                log.error("Failed to process auto-invest {}", autoInvest.getId(), e);
            }
        }
        
        log.info("Completed processing {} auto-investments", dueAutoInvests.size());
    }

    private void processAutoInvestExecution(AutoInvest autoInvest) {
        InvestmentAccount account = accountRepository.findById(autoInvest.getAccountId())
            .orElseThrow(() -> new AccountNotFoundException("Account not found"));
        
        // Check if account has sufficient cash
        if (autoInvest.getAmount().compareTo(account.getCashBalance()) > 0) {
            log.warn("Insufficient cash for auto-invest {} - skipping", autoInvest.getId());
            return;
        }
        
        // Create orders for each symbol
        for (Map.Entry<String, BigDecimal> entry : autoInvest.getAllocationPercentages().entrySet()) {
            String symbol = entry.getKey();
            BigDecimal percentage = entry.getValue();
            BigDecimal investmentAmount = autoInvest.getAmount()
                .multiply(percentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            
            try {
                CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .symbol(symbol)
                    .side(OrderSide.BUY)
                    .type(OrderType.MARKET)
                    .timeInForce(TimeInForce.DAY)
                    .notionalAmount(investmentAmount)
                    .build();
                
                self.createOrder(autoInvest.getAccountId(), orderRequest);
                
            } catch (Exception e) {
                log.error("Failed to create auto-invest order for {} in auto-invest {}", 
                    symbol, autoInvest.getId(), e);
            }
        }
        
        // Update auto-invest
        autoInvest.setLastExecutionDate(Instant.now());
        autoInvest.setNextExecutionDate(calculateNextAutoInvestDate(
            autoInvest.getFrequency(), autoInvest.getLastExecutionDate()));
        autoInvest.setTotalInvested(autoInvest.getTotalInvested().add(autoInvest.getAmount()));
        autoInvest.setExecutionCount(autoInvest.getExecutionCount() + 1);
        
        autoInvestRepository.save(autoInvest);
        
        log.info("Processed auto-invest {} - invested ${}", autoInvest.getId(), autoInvest.getAmount());
    }

    private void validateAccountCreation(CreateAccountRequest request, String userId) {
        // Validate age (must be 18+)
        if (request.getPersonalInfo().getAge() < 18) {
            throw new IllegalArgumentException("Must be 18 years or older to open investment account");
        }
        
        // Validate income
        if (request.getFinancialInfo().getAnnualIncome().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Annual income must be provided");
        }
        
        // KYC check
        if (!isUserVerified(userId)) {
            throw new KycRequiredException("User verification required for investment account");
        }
    }

    private void validateOrder(CreateOrderRequest request, InvestmentAccount account) {
        // Validate symbol
        if (!marketDataService.isValidSymbol(request.getSymbol())) {
            throw new IllegalArgumentException("Invalid symbol: " + request.getSymbol());
        }
        
        // Validate quantity vs notional amount
        if (request.getQuantity() == null && request.getNotionalAmount() == null) {
            throw new IllegalArgumentException("Either quantity or notional amount must be specified");
        }
        
        if (request.getQuantity() != null && request.getNotionalAmount() != null) {
            throw new IllegalArgumentException("Cannot specify both quantity and notional amount");
        }
        
        // Validate fractional shares
        if (!account.getSettings().isFractionalShares() && request.getNotionalAmount() != null) {
            throw new IllegalArgumentException("Fractional shares not enabled for account");
        }
        
        // Validate minimum order amount
        if (request.getNotionalAmount() != null && 
            request.getNotionalAmount().compareTo(minOrderAmount) < 0) {
            throw new IllegalArgumentException("Order amount below minimum: $" + minOrderAmount);
        }
        
        // Validate maximum order amount
        BigDecimal orderValue = request.getNotionalAmount() != null ? 
            request.getNotionalAmount() : 
            request.getQuantity().multiply(marketDataService.getCurrentPrice(request.getSymbol()));
        
        if (orderValue.compareTo(maxOrderAmount) > 0) {
            throw new IllegalArgumentException("Order amount exceeds maximum: $" + maxOrderAmount);
        }
    }

    private void validateAutoInvestRequest(SetupAutoInvestRequest request) {
        // Validate allocations sum to 100%
        BigDecimal totalPercentage = request.getAllocationPercentages().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (totalPercentage.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new IllegalArgumentException("Allocation percentages must sum to 100%");
        }
        
        // Validate minimum amount
        if (request.getAmount().compareTo(BigDecimal.valueOf(25)) < 0) {
            throw new IllegalArgumentException("Minimum auto-invest amount is $25");
        }
        
        // Validate symbols
        for (String symbol : request.getSymbols()) {
            if (!marketDataService.isValidSymbol(symbol)) {
                throw new IllegalArgumentException("Invalid symbol: " + symbol);
            }
        }
    }

    private BigDecimal calculateOrderValue(CreateOrderRequest request, BigDecimal currentPrice) {
        if (request.getNotionalAmount() != null) {
            return request.getNotionalAmount();
        }
        
        if (request.getQuantity() != null) {
            return request.getQuantity().multiply(currentPrice);
        }
        
        throw new IllegalArgumentException("Cannot calculate order value");
    }

    private void updateHoldings(InvestmentAccount account, InvestmentOrder order, 
                               OrderExecutionWebhook webhook) {
        Optional<InvestmentHolding> existingHolding = holdingRepository
            .findByAccountIdAndSymbol(account.getId(), order.getSymbol());
        
        if (existingHolding.isPresent()) {
            // Update existing holding
            InvestmentHolding holding = existingHolding.get();
            
            if (order.getSide() == OrderSide.BUY) {
                // Add to position
                BigDecimal newQuantity = holding.getQuantity().add(webhook.getQuantity());
                BigDecimal newCostBasis = holding.getTotalCost()
                    .add(webhook.getQuantity().multiply(webhook.getPrice()))
                    .divide(newQuantity, 4, RoundingMode.HALF_UP);
                
                holding.setQuantity(newQuantity);
                holding.setAverageCost(newCostBasis);
                holding.setTotalCost(holding.getTotalCost()
                    .add(webhook.getQuantity().multiply(webhook.getPrice())));
                
            } else { // SELL
                // Reduce position
                holding.setQuantity(holding.getQuantity().subtract(webhook.getQuantity()));
                holding.setTotalCost(holding.getTotalCost()
                    .subtract(webhook.getQuantity().multiply(holding.getAverageCost())));
                
                // If position is closed, remove holding
                if (holding.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    holdingRepository.delete(holding);
                    return;
                }
            }
            
            holding.setLastUpdated(Instant.now());
            holdingRepository.save(holding);
            
        } else if (order.getSide() == OrderSide.BUY) {
            // Create new holding
            InvestmentHolding holding = InvestmentHolding.builder()
                .accountId(account.getId())
                .symbol(order.getSymbol())
                .quantity(webhook.getQuantity())
                .averageCost(webhook.getPrice())
                .totalCost(webhook.getQuantity().multiply(webhook.getPrice()))
                .marketValue(webhook.getQuantity().multiply(webhook.getPrice()))
                .gainLoss(BigDecimal.ZERO)
                .gainLossPercent(BigDecimal.ZERO)
                .dayGainLoss(BigDecimal.ZERO)
                .dayGainLossPercent(BigDecimal.ZERO)
                .lastUpdated(Instant.now())
                .build();
            
            holdingRepository.save(holding);
        }
    }

    private void updateAccountBalances(InvestmentAccount account, InvestmentOrder order, 
                                     OrderExecutionWebhook webhook) {
        BigDecimal transactionValue = webhook.getQuantity().multiply(webhook.getPrice());
        
        if (order.getSide() == OrderSide.BUY) {
            // Deduct from cash balance
            account.setCashBalance(account.getCashBalance().subtract(transactionValue));
            // Buying power was already reduced when order was placed
            
        } else { // SELL
            // Add to cash balance
            account.setCashBalance(account.getCashBalance().add(transactionValue));
            account.setBuyingPower(account.getBuyingPower().add(transactionValue));
        }
        
        // Recalculate total value
        BigDecimal portfolioValue = holdingRepository.getTotalPortfolioValue(account.getId());
        account.setPortfolioValue(portfolioValue);
        account.setTotalValue(account.getCashBalance().add(portfolioValue));
        
        accountRepository.save(account);
    }

    private void updatePortfolioValues(Portfolio portfolio, List<InvestmentHolding> holdings) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal dayGainLoss = BigDecimal.ZERO;
        
        for (InvestmentHolding holding : holdings) {
            // Update holding with current market price
            BigDecimal currentPrice = marketDataService.getCurrentPrice(holding.getSymbol());
            BigDecimal marketValue = holding.getQuantity().multiply(currentPrice);
            BigDecimal gainLoss = marketValue.subtract(holding.getTotalCost());
            BigDecimal gainLossPercent = holding.getTotalCost().compareTo(BigDecimal.ZERO) > 0 ?
                gainLoss.divide(holding.getTotalCost(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            
            holding.setMarketValue(marketValue);
            holding.setGainLoss(gainLoss);
            holding.setGainLossPercent(gainLossPercent);
            holding.setLastUpdated(Instant.now());
            
            // Calculate day gain/loss
            BigDecimal previousClose = marketDataService.getPreviousClose(holding.getSymbol());
            BigDecimal dayChange = currentPrice.subtract(previousClose);
            BigDecimal dayChangeValue = holding.getQuantity().multiply(dayChange);
            
            holding.setDayGainLoss(dayChangeValue);
            holding.setDayGainLossPercent(previousClose.compareTo(BigDecimal.ZERO) > 0 ?
                dayChange.divide(previousClose, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO);
            
            holdingRepository.save(holding);
            
            totalValue = totalValue.add(marketValue);
            totalCost = totalCost.add(holding.getTotalCost());
            dayGainLoss = dayGainLoss.add(dayChangeValue);
        }
        
        // Update portfolio
        BigDecimal totalGainLoss = totalValue.subtract(totalCost);
        BigDecimal totalGainLossPercent = totalCost.compareTo(BigDecimal.ZERO) > 0 ?
            totalGainLoss.divide(totalCost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        
        BigDecimal dayGainLossPercent = totalValue.subtract(dayGainLoss).compareTo(BigDecimal.ZERO) > 0 ?
            dayGainLoss.divide(totalValue.subtract(dayGainLoss), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        
        portfolio.setTotalValue(totalValue);
        portfolio.setTotalCost(totalCost);
        portfolio.setTotalGainLoss(totalGainLoss);
        portfolio.setTotalGainLossPercent(totalGainLossPercent);
        portfolio.setDayGainLoss(dayGainLoss);
        portfolio.setDayGainLossPercent(dayGainLossPercent);
        portfolio.setLastUpdated(Instant.now());
        
        portfolioRepository.save(portfolio);
    }

    private boolean isUserVerified(String userId) {
        return kycClientService.isUserBasicVerified(userId);
    }

    private Instant calculateNextAutoInvestDate(AutoInvestFrequency frequency, Instant lastExecution) {
        LocalDate nextDate = (lastExecution != null ? lastExecution : Instant.now())
            .atZone(ZoneId.systemDefault()).toLocalDate();
        
        switch (frequency) {
            case WEEKLY:
                nextDate = nextDate.plusWeeks(1);
                break;
            case BIWEEKLY:
                nextDate = nextDate.plusWeeks(2);
                break;
            case MONTHLY:
                nextDate = nextDate.plusMonths(1);
                break;
            default:
                throw new IllegalArgumentException("Unsupported frequency: " + frequency);
        }
        
        return nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private Instant calculateStartDate(PerformanceTimeframe timeframe) {
        Instant now = Instant.now();
        switch (timeframe) {
            case ONE_DAY:
                return now.minus(Duration.ofDays(1));
            case ONE_WEEK:
                return now.minus(Duration.ofDays(7));
            case ONE_MONTH:
                return now.minus(Duration.ofDays(30));
            case THREE_MONTHS:
                return now.minus(Duration.ofDays(90));
            case ONE_YEAR:
                return now.minus(Duration.ofDays(365));
            case ALL_TIME:
                return Instant.EPOCH;
            default:
                return now.minus(Duration.ofDays(30));
        }
    }

    private BigDecimal calculateAnnualizedReturn(BigDecimal totalReturn, long days) {
        if (days <= 0) return BigDecimal.ZERO;
        
        // Calculate annualized return with high precision
        // Formula: (1 + return)^(365/days) - 1
        BigDecimal returnFactor = BigDecimal.ONE.add(
            totalReturn.divide(BigDecimal.valueOf(100), BigDecimalMath.FINANCIAL_PRECISION));
        
        double exponent = 365.0 / days;
        BigDecimal annualizedReturnFactor = BigDecimalMath.pow(returnFactor, exponent, BigDecimalMath.FINANCIAL_PRECISION);
        BigDecimal annualizedReturn = annualizedReturnFactor.subtract(BigDecimal.ONE);
        
        return annualizedReturn.multiply(BigDecimal.valueOf(100), BigDecimalMath.FINANCIAL_PRECISION)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVolatility(List<PerformanceDataPoint> values) {
        if (values.size() < 2) return BigDecimal.ZERO;
        
        // Calculate daily returns
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 1; i < values.size(); i++) {
            BigDecimal prev = values.get(i - 1).getValue();
            BigDecimal curr = values.get(i).getValue();
            
            if (prev.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = curr.subtract(prev).divide(prev, 6, RoundingMode.HALF_UP);
                returns.add(dailyReturn);
            }
        }
        
        if (returns.isEmpty()) return BigDecimal.ZERO;
        
        // Calculate standard deviation
        BigDecimal mean = returns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        BigDecimal variance = returns.stream()
            .map(r -> r.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
        
        // Annualize volatility with high precision
        BigDecimal dailyVolatility = BigDecimalMath.sqrt(variance);
        BigDecimal annualizationFactor = BigDecimalMath.sqrt(new BigDecimal("252")); // 252 trading days
        BigDecimal annualizedVolatility = dailyVolatility.multiply(annualizationFactor, BigDecimalMath.FINANCIAL_PRECISION);
        
        return annualizedVolatility.multiply(BigDecimal.valueOf(100), BigDecimalMath.FINANCIAL_PRECISION)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSharpeRatio(BigDecimal annualizedReturn, BigDecimal volatility) {
        if (volatility.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        
        BigDecimal riskFreeRate = BigDecimal.valueOf(2.0); // Assume 2% risk-free rate
        BigDecimal excessReturn = annualizedReturn.subtract(riskFreeRate);
        
        return excessReturn.divide(volatility, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMaxDrawdown(List<PerformanceDataPoint> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = values.get(0).getValue();
        
        for (PerformanceDataPoint point : values) {
            if (point.getValue().compareTo(peak) > 0) {
                peak = point.getValue();
            }
            
            BigDecimal drawdown = peak.subtract(point.getValue())
                .divide(peak, 4, RoundingMode.HALF_UP);
            
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        
        return maxDrawdown.multiply(BigDecimal.valueOf(100));
    }

    private InvestmentAccount getAccountByIdAndUser(String accountId, String userId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
            .orElseThrow(() -> new AccountNotFoundException("Investment account not found"));
    }

    // DTO conversion methods
    private InvestmentAccountDto toAccountDto(InvestmentAccount account) {
        return InvestmentAccountDto.builder()
            .id(account.getId())
            .accountType(account.getAccountType())
            .status(account.getStatus())
            .totalValue(account.getTotalValue())
            .cashBalance(account.getCashBalance())
            .buyingPower(account.getBuyingPower())
            .portfolioValue(account.getPortfolioValue())
            .totalGainLoss(account.getTotalGainLoss())
            .totalGainLossPercent(account.getTotalGainLossPercent())
            .dayGainLoss(account.getDayGainLoss())
            .dayGainLossPercent(account.getDayGainLossPercent())
            .riskProfile(account.getRiskProfile())
            .settings(account.getSettings())
            .createdAt(account.getCreatedAt())
            .build();
    }

    private InvestmentOrderDto toOrderDto(InvestmentOrder order) {
        return InvestmentOrderDto.builder()
            .id(order.getId())
            .symbol(order.getSymbol())
            .side(order.getSide())
            .type(order.getType())
            .status(order.getStatus())
            .quantity(order.getQuantity())
            .notionalAmount(order.getNotionalAmount())
            .limitPrice(order.getLimitPrice())
            .stopPrice(order.getStopPrice())
            .filledQuantity(order.getFilledQuantity())
            .fillPrice(order.getFillPrice())
            .filledValue(order.getFilledValue())
            .estimatedValue(order.getEstimatedValue())
            .createdAt(order.getCreatedAt())
            .filledAt(order.getFilledAt())
            .build();
    }

    private PortfolioDto toPortfolioDto(Portfolio portfolio, List<InvestmentHolding> holdings) {
        return PortfolioDto.builder()
            .id(portfolio.getId())
            .name(portfolio.getName())
            .totalValue(portfolio.getTotalValue())
            .totalCost(portfolio.getTotalCost())
            .totalGainLoss(portfolio.getTotalGainLoss())
            .totalGainLossPercent(portfolio.getTotalGainLossPercent())
            .dayGainLoss(portfolio.getDayGainLoss())
            .dayGainLossPercent(portfolio.getDayGainLossPercent())
            .diversificationScore(portfolio.getDiversificationScore())
            .riskScore(portfolio.getRiskScore())
            .positions(holdings.stream().map(this::toPositionDto).collect(Collectors.toList()))
            .lastUpdated(portfolio.getLastUpdated())
            .build();
    }

    private PositionDto toPositionDto(InvestmentHolding holding) {
        return PositionDto.builder()
            .symbol(holding.getSymbol())
            .quantity(holding.getQuantity())
            .averageCost(holding.getAverageCost())
            .totalCost(holding.getTotalCost())
            .marketValue(holding.getMarketValue())
            .gainLoss(holding.getGainLoss())
            .gainLossPercent(holding.getGainLossPercent())
            .dayGainLoss(holding.getDayGainLoss())
            .dayGainLossPercent(holding.getDayGainLossPercent())
            .lastUpdated(holding.getLastUpdated())
            .build();
    }

    private TransferDto toTransferDto(Transfer transfer) {
        return TransferDto.builder()
            .id(transfer.getId())
            .type(transfer.getType())
            .amount(transfer.getAmount())
            .status(transfer.getStatus())
            .processedAt(transfer.getProcessedAt())
            .build();
    }

    private WatchlistItemDto toWatchlistItemDto(WatchlistItem item) {
        return WatchlistItemDto.builder()
            .id(item.getId())
            .symbol(item.getSymbol())
            .companyName(item.getCompanyName())
            .sector(item.getSector())
            .priceTarget(item.getPriceTarget())
            .notes(item.getNotes())
            .addedAt(item.getAddedAt())
            .build();
    }

    private AutoInvestDto toAutoInvestDto(AutoInvest autoInvest) {
        return AutoInvestDto.builder()
            .id(autoInvest.getId())
            .frequency(autoInvest.getFrequency())
            .amount(autoInvest.getAmount())
            .symbols(autoInvest.getSymbols())
            .allocationPercentages(autoInvest.getAllocationPercentages())
            .enabled(autoInvest.isEnabled())
            .nextExecutionDate(autoInvest.getNextExecutionDate())
            .totalInvested(autoInvest.getTotalInvested())
            .executionCount(autoInvest.getExecutionCount())
            .createdAt(autoInvest.getCreatedAt())
            .build();
    }
}