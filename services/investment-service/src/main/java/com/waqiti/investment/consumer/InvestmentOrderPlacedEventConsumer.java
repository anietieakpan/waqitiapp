package com.waqiti.investment.consumer;

import com.waqiti.common.events.InvestmentOrderPlacedEvent;
import com.waqiti.investment.service.OrderExecutionService;
import com.waqiti.investment.service.MarketDataService;
import com.waqiti.investment.service.RiskManagementService;
import com.waqiti.investment.service.ComplianceService;
import com.waqiti.investment.service.NotificationService;
import com.waqiti.investment.repository.ProcessedEventRepository;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.model.ProcessedEvent;
import com.waqiti.investment.model.InvestmentOrder;
import com.waqiti.investment.model.OrderStatus;
import com.waqiti.investment.model.OrderType;
import com.waqiti.investment.model.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for InvestmentOrderPlacedEvent - Critical for portfolio management
 * Handles order validation, market data analysis, risk assessment, and execution
 * ZERO TOLERANCE: All investment orders must comply with SEC regulations and fiduciary duty
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvestmentOrderPlacedEventConsumer {
    
    private final OrderExecutionService orderExecutionService;
    private final MarketDataService marketDataService;
    private final RiskManagementService riskManagementService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final InvestmentOrderRepository investmentOrderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("100000");
    private static final BigDecimal CONCENTRATION_LIMIT = new BigDecimal("0.20"); // 20% max in single security
    private static final BigDecimal PENNY_STOCK_THRESHOLD = new BigDecimal("5.00");
    
    @KafkaListener(
        topics = "investment.order.placed",
        groupId = "investment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for financial transactions
    public void handleInvestmentOrderPlaced(InvestmentOrderPlacedEvent event) {
        log.info("Processing investment order: {} - Symbol: {} - Quantity: {} - Type: {} - Amount: ${}", 
            event.getOrderId(), event.getSymbol(), event.getQuantity(), 
            event.getOrderType(), event.getOrderAmount());
        
        // IDEMPOTENCY CHECK - Prevent duplicate order processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Investment order already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Get or create order record
            InvestmentOrder order = getOrCreateOrder(event);
            
            // STEP 1: Perform regulatory compliance checks (SEC, FINRA)
            performRegulatoryComplianceChecks(order, event);
            
            // STEP 2: Validate account eligibility and authorization
            validateAccountEligibilityAndAuth(order, event);
            
            // STEP 3: Assess portfolio risk and concentration limits
            assessPortfolioRiskAndConcentration(order, event);
            
            // STEP 4: Execute market data analysis and pricing
            executeMarketDataAnalysis(order, event);
            
            // STEP 5: Perform suitability analysis (fiduciary duty)
            performSuitabilityAnalysis(order, event);
            
            // STEP 6: Apply pattern day trader and margin rules
            applyTradingRulesAndMarginRequirements(order, event);
            
            // STEP 7: Execute order routing and best execution
            executeOrderRoutingAndBestExecution(order, event);
            
            // STEP 8: Process fractional shares and odd lots
            processFractionalSharesAndOddLots(order, event);
            
            // STEP 9: Generate trade confirmations and tax implications
            generateTradeConfirmationsAndTaxData(order, event);
            
            // STEP 10: Update portfolio analytics and rebalancing
            updatePortfolioAnalyticsAndRebalancing(order, event);
            
            // STEP 11: Send investor notifications and disclosures
            sendInvestorNotificationsAndDisclosures(order, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("InvestmentOrderPlacedEvent")
                .processedAt(Instant.now())
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .symbol(event.getSymbol())
                .quantity(event.getQuantity())
                .orderType(event.getOrderType())
                .orderAmount(event.getOrderAmount())
                .orderStatus(order.getStatus())
                .executionPrice(order.getExecutionPrice())
                .riskLevel(order.getRiskLevel())
                .complianceChecksPassed(order.isComplianceChecksPassed())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed investment order: {} - Status: {}, Execution Price: ${}, Risk Level: {}", 
                event.getOrderId(), order.getStatus(), order.getExecutionPrice(), order.getRiskLevel());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process investment order: {}", 
                event.getOrderId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Investment order processing failed", e);
        }
    }
    
    private InvestmentOrder getOrCreateOrder(InvestmentOrderPlacedEvent event) {
        return investmentOrderRepository.findById(event.getOrderId())
            .orElseGet(() -> createNewOrder(event));
    }
    
    private InvestmentOrder createNewOrder(InvestmentOrderPlacedEvent event) {
        InvestmentOrder order = InvestmentOrder.builder()
            .id(event.getOrderId())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .symbol(event.getSymbol())
            .quantity(event.getQuantity())
            .orderType(mapOrderType(event.getOrderType()))
            .orderAmount(event.getOrderAmount())
            .limitPrice(event.getLimitPrice())
            .stopPrice(event.getStopPrice())
            .timeInForce(event.getTimeInForce())
            .placedAt(LocalDateTime.now())
            .status(OrderStatus.PENDING_VALIDATION)
            .complianceFlags(new ArrayList<>())
            .riskFlags(new ArrayList<>())
            .build();
        
        return investmentOrderRepository.save(order);
    }
    
    private void performRegulatoryComplianceChecks(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        List<String> complianceFlags = new ArrayList<>();
        
        // SEC Rule 144 - Restricted securities check
        boolean restrictedSecurity = complianceService.checkRestrictedSecurity(
            event.getSymbol(),
            event.getUserId()
        );
        
        if (restrictedSecurity) {
            complianceFlags.add("RESTRICTED_SECURITY");
            order.setRequiresManualReview(true);
        }
        
        // Check wash sale rules (IRC Section 1091)
        boolean washSaleViolation = complianceService.checkWashSaleRule(
            event.getUserId(),
            event.getSymbol(),
            event.getOrderType(),
            LocalDateTime.now().minusDays(30),
            LocalDateTime.now().plusDays(30)
        );
        
        if (washSaleViolation) {
            complianceFlags.add("WASH_SALE_VIOLATION");
            order.setTaxImplicationsPresent(true);
        }
        
        // Reg SHO - Short sale regulations
        if ("SELL_SHORT".equals(event.getOrderType())) {
            boolean shortSaleEligible = complianceService.checkRegSHOEligibility(
                event.getSymbol(),
                event.getQuantity()
            );
            
            if (!shortSaleEligible) {
                complianceFlags.add("REG_SHO_VIOLATION");
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Short sale not permitted under Reg SHO");
                
                investmentOrderRepository.save(order);
                
                log.warn("Order {} rejected - Reg SHO violation for symbol: {}", 
                    event.getOrderId(), event.getSymbol());
                    
                throw new RuntimeException("Short sale violates Regulation SHO");
            }
        }
        
        // Check penny stock regulations (SEC Rule 15g)
        BigDecimal currentPrice = marketDataService.getCurrentPrice(event.getSymbol());
        
        if (currentPrice.compareTo(PENNY_STOCK_THRESHOLD) <= 0) {
            complianceFlags.add("PENNY_STOCK");
            
            boolean pennyStockAgreement = complianceService.hasPennyStockAgreement(event.getUserId());
            
            if (!pennyStockAgreement) {
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Penny stock agreement required");
                
                investmentOrderRepository.save(order);
                
                log.warn("Order {} rejected - penny stock agreement missing for: {}", 
                    event.getOrderId(), event.getSymbol());
                    
                throw new RuntimeException("Penny stock purchase requires signed agreement");
            }
        }
        
        // Check insider trading regulations
        boolean insiderTradingRisk = complianceService.checkInsiderTradingRisk(
            event.getUserId(),
            event.getSymbol(),
            event.getOrderAmount()
        );
        
        if (insiderTradingRisk) {
            complianceFlags.add("INSIDER_TRADING_RISK");
            order.setRequiresManualReview(true);
        }
        
        order.addComplianceFlags(complianceFlags);
        order.setComplianceChecksPassed(complianceFlags.isEmpty() || 
            complianceFlags.stream().noneMatch(flag -> flag.contains("VIOLATION")));
        
        investmentOrderRepository.save(order);
        
        log.info("Regulatory compliance checks completed for order {}: Flags: {}, Passed: {}", 
            event.getOrderId(), complianceFlags.size(), order.isComplianceChecksPassed());
    }
    
    private void validateAccountEligibilityAndAuth(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Verify account is approved for investment activity
        boolean accountApproved = complianceService.isAccountApprovedForInvestments(
            event.getAccountId(),
            event.getUserId()
        );
        
        if (!accountApproved) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("Account not approved for investment activity");
            
            investmentOrderRepository.save(order);
            
            log.warn("Order {} rejected - account not approved: {}", 
                event.getOrderId(), event.getAccountId());
                
            throw new RuntimeException("Account not approved for investment activity");
        }
        
        // Check trading authorization level
        String requiredAuthLevel = determineRequiredAuthLevel(
            event.getSymbol(),
            event.getOrderType(),
            event.getOrderAmount()
        );
        
        boolean hasRequiredAuth = complianceService.hasRequiredTradingAuth(
            event.getUserId(),
            requiredAuthLevel
        );
        
        if (!hasRequiredAuth) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("Insufficient trading authorization: " + requiredAuthLevel);
            
            investmentOrderRepository.save(order);
            
            log.warn("Order {} rejected - insufficient auth level: {} required", 
                event.getOrderId(), requiredAuthLevel);
                
            throw new RuntimeException("Insufficient trading authorization level");
        }
        
        // Verify sufficient account balance
        BigDecimal availableBalance = orderExecutionService.getAvailableBalance(event.getAccountId());
        BigDecimal requiredAmount = calculateRequiredAmount(event, order);
        
        if (availableBalance.compareTo(requiredAmount) < 0) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("Insufficient funds");
            
            investmentOrderRepository.save(order);
            
            log.warn("Order {} rejected - insufficient funds: ${} available, ${} required", 
                event.getOrderId(), availableBalance, requiredAmount);
                
            throw new RuntimeException("Insufficient account balance");
        }
        
        order.setRequiredAmount(requiredAmount);
        order.setAvailableBalance(availableBalance);
        
        investmentOrderRepository.save(order);
        
        log.info("Account eligibility validated for order {}: Auth level: {}, Balance: ${}", 
            event.getOrderId(), requiredAuthLevel, availableBalance);
    }
    
    private void assessPortfolioRiskAndConcentration(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Get current portfolio composition
        Map<String, Object> portfolioData = riskManagementService.getPortfolioComposition(
            event.getUserId(),
            event.getAccountId()
        );
        
        order.setPortfolioData(portfolioData);
        
        // Calculate concentration risk
        BigDecimal totalPortfolioValue = (BigDecimal) portfolioData.get("totalValue");
        BigDecimal currentPositionValue = (BigDecimal) portfolioData.get("positionValue_" + event.getSymbol());
        
        if (currentPositionValue == null) {
            currentPositionValue = BigDecimal.ZERO;
        }
        
        BigDecimal newPositionValue = currentPositionValue.add(event.getOrderAmount());
        BigDecimal concentrationRatio = newPositionValue.divide(
            totalPortfolioValue.add(event.getOrderAmount()), 4, RoundingMode.HALF_UP
        );
        
        order.setConcentrationRatio(concentrationRatio);
        
        if (concentrationRatio.compareTo(CONCENTRATION_LIMIT) > 0) {
            order.addRiskFlag("CONCENTRATION_LIMIT_EXCEEDED");
            order.setRequiresManualReview(true);
            
            log.warn("Concentration limit exceeded for order {}: {}% in {}", 
                event.getOrderId(), concentrationRatio.multiply(new BigDecimal("100")), event.getSymbol());
        }
        
        // Assess overall portfolio risk
        RiskLevel portfolioRiskLevel = riskManagementService.assessPortfolioRisk(
            portfolioData,
            event.getSymbol(),
            event.getOrderAmount()
        );
        
        order.setRiskLevel(portfolioRiskLevel);
        
        // Check sector concentration
        String sector = marketDataService.getSectorForSymbol(event.getSymbol());
        BigDecimal sectorConcentration = riskManagementService.calculateSectorConcentration(
            portfolioData,
            sector,
            event.getOrderAmount()
        );
        
        if (sectorConcentration.compareTo(new BigDecimal("0.30")) > 0) { // 30% sector limit
            order.addRiskFlag("SECTOR_CONCENTRATION_HIGH");
        }
        
        // Check volatility risk
        double symbolVolatility = marketDataService.getHistoricalVolatility(
            event.getSymbol(),
            252 // 1 year of trading days
        );
        
        if (symbolVolatility > 0.50) { // 50% annualized volatility
            order.addRiskFlag("HIGH_VOLATILITY_SECURITY");
            order.setVolatility(symbolVolatility);
        }
        
        investmentOrderRepository.save(order);
        
        log.info("Portfolio risk assessment completed for order {}: Risk Level: {}, Concentration: {}%", 
            event.getOrderId(), portfolioRiskLevel, concentrationRatio.multiply(new BigDecimal("100")));
    }
    
    private void executeMarketDataAnalysis(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Get comprehensive market data
        Map<String, Object> marketData = marketDataService.getComprehensiveMarketData(
            event.getSymbol()
        );
        
        order.setMarketData(marketData);
        
        BigDecimal currentPrice = (BigDecimal) marketData.get("currentPrice");
        BigDecimal bidPrice = (BigDecimal) marketData.get("bidPrice");
        BigDecimal askPrice = (BigDecimal) marketData.get("askPrice");
        BigDecimal volume = (BigDecimal) marketData.get("volume");
        BigDecimal avgVolume = (BigDecimal) marketData.get("avgVolume30Day");
        
        order.setCurrentPrice(currentPrice);
        order.setBidAskSpread(askPrice.subtract(bidPrice));
        
        // Check liquidity
        if (volume.compareTo(avgVolume.multiply(new BigDecimal("0.1"))) < 0) {
            order.addRiskFlag("LOW_LIQUIDITY");
            order.setRequiresManualReview(true);
        }
        
        // Market impact analysis for large orders
        if (event.getOrderAmount().compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            double marketImpact = marketDataService.calculateMarketImpact(
                event.getSymbol(),
                event.getQuantity(),
                volume
            );
            
            order.setMarketImpact(marketImpact);
            
            if (marketImpact > 0.02) { // 2% impact threshold
                order.addRiskFlag("HIGH_MARKET_IMPACT");
                order.setRequiresManualReview(true);
            }
        }
        
        // Check for trading halts
        boolean tradingHalted = marketDataService.isTradingHalted(event.getSymbol());
        
        if (tradingHalted) {
            order.setStatus(OrderStatus.PENDING_MARKET_OPEN);
            order.addRiskFlag("TRADING_HALTED");
        }
        
        // Analyze technical indicators
        Map<String, Double> technicalIndicators = marketDataService.calculateTechnicalIndicators(
            event.getSymbol()
        );
        
        order.setTechnicalIndicators(technicalIndicators);
        
        // Check for unusual market activity
        boolean unusualActivity = marketDataService.detectUnusualActivity(
            event.getSymbol(),
            volume,
            currentPrice
        );
        
        if (unusualActivity) {
            order.addRiskFlag("UNUSUAL_MARKET_ACTIVITY");
            order.setRequiresManualReview(true);
        }
        
        investmentOrderRepository.save(order);
        
        log.info("Market data analysis completed for order {}: Price: ${}, Volume: {}, Impact: {}%", 
            event.getOrderId(), currentPrice, volume, 
            order.getMarketImpact() != null ? order.getMarketImpact() * 100 : 0);
    }
    
    private void performSuitabilityAnalysis(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Get investor profile
        Map<String, Object> investorProfile = complianceService.getInvestorProfile(event.getUserId());
        order.setInvestorProfile(investorProfile);
        
        String riskTolerance = (String) investorProfile.get("riskTolerance");
        String investmentObjective = (String) investorProfile.get("investmentObjective");
        String timeHorizon = (String) investorProfile.get("timeHorizon");
        
        // Analyze investment suitability
        boolean suitable = complianceService.analyzeSuitability(
            event.getSymbol(),
            event.getOrderAmount(),
            riskTolerance,
            investmentObjective,
            timeHorizon,
            order.getRiskLevel()
        );
        
        order.setSuitabilityAnalysis(Map.of(
            "suitable", suitable,
            "riskTolerance", riskTolerance,
            "investmentObjective", investmentObjective,
            "timeHorizon", timeHorizon,
            "securityRiskLevel", order.getRiskLevel().toString()
        ));
        
        if (!suitable) {
            order.addComplianceFlag("SUITABILITY_CONCERN");
            order.setRequiresManualReview(true);
            
            log.warn("Suitability concern for order {}: Risk tolerance: {}, Security risk: {}", 
                event.getOrderId(), riskTolerance, order.getRiskLevel());
        }
        
        // Check investment experience requirements
        String requiredExperience = complianceService.getRequiredExperienceLevel(
            event.getSymbol(),
            order.getRiskLevel()
        );
        
        String actualExperience = (String) investorProfile.get("experienceLevel");
        
        if (!complianceService.hasRequiredExperience(actualExperience, requiredExperience)) {
            order.addComplianceFlag("INSUFFICIENT_EXPERIENCE");
            order.setRequiresManualReview(true);
        }
        
        // Check age-based restrictions
        Integer investorAge = (Integer) investorProfile.get("age");
        
        if (investorAge < 18) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason("Minor account - adult authorization required");
            
            investmentOrderRepository.save(order);
            throw new RuntimeException("Minor account requires adult authorization");
        }
        
        investmentOrderRepository.save(order);
        
        log.info("Suitability analysis completed for order {}: Suitable: {}, Experience: {}", 
            event.getOrderId(), suitable, actualExperience);
    }
    
    private void applyTradingRulesAndMarginRequirements(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Check Pattern Day Trader rules
        boolean isPatternDayTrader = complianceService.isPatternDayTrader(
            event.getUserId(),
            LocalDateTime.now().minusDays(5)
        );
        
        if (isPatternDayTrader) {
            BigDecimal requiredEquity = new BigDecimal("25000"); // PDT minimum
            BigDecimal accountEquity = orderExecutionService.getAccountEquity(event.getAccountId());
            
            if (accountEquity.compareTo(requiredEquity) < 0) {
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Pattern Day Trader minimum equity not met");
                
                investmentOrderRepository.save(order);
                
                log.warn("Order {} rejected - PDT equity requirement: ${} required, ${} available", 
                    event.getOrderId(), requiredEquity, accountEquity);
                    
                throw new RuntimeException("Pattern Day Trader minimum equity requirement not met");
            }
        }
        
        // Calculate margin requirements
        if ("MARGIN".equals(event.getAccountType())) {
            BigDecimal marginRequirement = riskManagementService.calculateMarginRequirement(
                event.getSymbol(),
                event.getQuantity(),
                order.getCurrentPrice()
            );
            
            order.setMarginRequirement(marginRequirement);
            
            BigDecimal availableMargin = orderExecutionService.getAvailableMargin(event.getAccountId());
            
            if (availableMargin.compareTo(marginRequirement) < 0) {
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Insufficient margin");
                
                investmentOrderRepository.save(order);
                
                log.warn("Order {} rejected - insufficient margin: ${} required, ${} available", 
                    event.getOrderId(), marginRequirement, availableMargin);
                    
                throw new RuntimeException("Insufficient margin for trade");
            }
        }
        
        // Check good faith violation (cash accounts)
        if ("CASH".equals(event.getAccountType()) && "SELL".equals(event.getOrderType())) {
            boolean goodFaithViolation = complianceService.checkGoodFaithViolation(
                event.getUserId(),
                event.getSymbol(),
                LocalDateTime.now().minusDays(3)
            );
            
            if (goodFaithViolation) {
                order.addComplianceFlag("GOOD_FAITH_VIOLATION_RISK");
                order.setRequiresManualReview(true);
            }
        }
        
        investmentOrderRepository.save(order);
        
        log.info("Trading rules applied for order {}: PDT: {}, Margin req: ${}", 
            event.getOrderId(), isPatternDayTrader, 
            order.getMarginRequirement() != null ? order.getMarginRequirement() : BigDecimal.ZERO);
    }
    
    private void executeOrderRoutingAndBestExecution(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Skip execution if manual review required
        if (order.isRequiresManualReview()) {
            order.setStatus(OrderStatus.PENDING_MANUAL_REVIEW);
            investmentOrderRepository.save(order);
            return;
        }
        
        try {
            // Determine optimal execution venue
            String executionVenue = orderExecutionService.determineOptimalVenue(
                event.getSymbol(),
                event.getQuantity(),
                event.getOrderType(),
                order.getCurrentPrice()
            );
            
            order.setExecutionVenue(executionVenue);
            
            // Execute order with best execution requirements
            Map<String, Object> executionResult = orderExecutionService.executeOrder(
                event.getOrderId(),
                event.getSymbol(),
                event.getQuantity(),
                event.getOrderType(),
                event.getLimitPrice(),
                event.getStopPrice(),
                executionVenue
            );
            
            order.setExecutionResult(executionResult);
            
            String executionStatus = (String) executionResult.get("status");
            BigDecimal executionPrice = (BigDecimal) executionResult.get("executionPrice");
            BigDecimal executedQuantity = (BigDecimal) executionResult.get("executedQuantity");
            
            order.setExecutionPrice(executionPrice);
            order.setExecutedQuantity(executedQuantity);
            
            switch (executionStatus) {
                case "FILLED" -> {
                    order.setStatus(OrderStatus.FILLED);
                    order.setFilledAt(LocalDateTime.now());
                }
                case "PARTIALLY_FILLED" -> {
                    order.setStatus(OrderStatus.PARTIALLY_FILLED);
                }
                case "PENDING" -> {
                    order.setStatus(OrderStatus.PENDING_EXECUTION);
                }
                case "CANCELLED" -> {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setCancellationReason((String) executionResult.get("reason"));
                }
                default -> {
                    order.setStatus(OrderStatus.EXECUTION_ERROR);
                    order.setExecutionError((String) executionResult.get("error"));
                }
            }
            
            // Calculate execution quality metrics
            if (executionPrice != null && order.getCurrentPrice() != null) {
                BigDecimal priceImprovement = calculatePriceImprovement(
                    event.getOrderType(),
                    executionPrice,
                    order.getCurrentPrice()
                );
                
                order.setPriceImprovement(priceImprovement);
            }
            
            investmentOrderRepository.save(order);
            
            log.info("Order execution completed for {}: Status: {}, Price: ${}, Quantity: {}", 
                event.getOrderId(), executionStatus, executionPrice, executedQuantity);
                
        } catch (Exception e) {
            order.setStatus(OrderStatus.EXECUTION_ERROR);
            order.setExecutionError(e.getMessage());
            investmentOrderRepository.save(order);
            
            log.error("Order execution failed for {}: {}", event.getOrderId(), e.getMessage());
            throw e;
        }
    }
    
    private void processFractionalSharesAndOddLots(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Handle fractional shares if supported
        if (event.getQuantity().scale() > 0) {
            boolean fractionalSupported = orderExecutionService.supportsFractionalShares(event.getSymbol());
            
            if (!fractionalSupported) {
                // Round down to whole shares
                BigDecimal wholeShares = event.getQuantity().setScale(0, RoundingMode.DOWN);
                order.setAdjustedQuantity(wholeShares);
                order.addRiskFlag("FRACTIONAL_SHARES_ROUNDED");
                
                log.info("Fractional shares rounded for order {}: {} -> {}", 
                    event.getOrderId(), event.getQuantity(), wholeShares);
            }
        }
        
        // Handle odd lots (less than 100 shares)
        if (event.getQuantity().compareTo(new BigDecimal("100")) < 0) {
            order.setIsOddLot(true);
            
            // Apply odd lot execution considerations
            orderExecutionService.applyOddLotExecutionRules(event.getOrderId());
        }
        
        investmentOrderRepository.save(order);
    }
    
    private void generateTradeConfirmationsAndTaxData(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
            
            // Generate trade confirmation
            String confirmationId = orderExecutionService.generateTradeConfirmation(
                event.getOrderId(),
                event.getSymbol(),
                order.getExecutedQuantity(),
                order.getExecutionPrice(),
                order.getFilledAt()
            );
            
            order.setTradeConfirmationId(confirmationId);
            
            // Calculate tax implications
            Map<String, Object> taxData = complianceService.calculateTaxImplications(
                event.getUserId(),
                event.getSymbol(),
                order.getExecutedQuantity(),
                order.getExecutionPrice(),
                event.getOrderType()
            );
            
            order.setTaxImplicationData(taxData);
            
            // Update cost basis records
            complianceService.updateCostBasis(
                event.getUserId(),
                event.getSymbol(),
                order.getExecutedQuantity(),
                order.getExecutionPrice(),
                order.getFilledAt()
            );
        }
        
        investmentOrderRepository.save(order);
        
        log.info("Trade confirmation generated for order {}: Confirmation ID: {}", 
            event.getOrderId(), order.getTradeConfirmationId());
    }
    
    private void updatePortfolioAnalyticsAndRebalancing(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        if (order.getStatus() == OrderStatus.FILLED) {
            
            // Update portfolio composition
            riskManagementService.updatePortfolioComposition(
                event.getUserId(),
                event.getAccountId(),
                event.getSymbol(),
                order.getExecutedQuantity(),
                order.getExecutionPrice(),
                event.getOrderType()
            );
            
            // Recalculate portfolio metrics
            Map<String, Object> updatedMetrics = riskManagementService.recalculatePortfolioMetrics(
                event.getUserId(),
                event.getAccountId()
            );
            
            order.setUpdatedPortfolioMetrics(updatedMetrics);
            
            // Check for rebalancing opportunities
            boolean rebalancingNeeded = riskManagementService.checkRebalancingNeeded(
                event.getUserId(),
                event.getAccountId(),
                updatedMetrics
            );
            
            if (rebalancingNeeded) {
                riskManagementService.createRebalancingRecommendation(
                    event.getUserId(),
                    event.getAccountId(),
                    updatedMetrics
                );
                order.setRebalancingRecommended(true);
            }
        }
        
        investmentOrderRepository.save(order);
        
        log.info("Portfolio analytics updated for order {}: Rebalancing needed: {}", 
            event.getOrderId(), order.isRebalancingRecommended());
    }
    
    private void sendInvestorNotificationsAndDisclosures(InvestmentOrder order, InvestmentOrderPlacedEvent event) {
        // Send order receipt confirmation
        notificationService.sendOrderReceiptConfirmation(
            event.getUserId(),
            event.getOrderId(),
            event.getSymbol(),
            event.getQuantity(),
            order.getStatus()
        );
        
        // Send execution notifications
        switch (order.getStatus()) {
            case FILLED -> {
                notificationService.sendOrderFillNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSymbol(),
                    order.getExecutedQuantity(),
                    order.getExecutionPrice(),
                    order.getTradeConfirmationId()
                );
            }
            case PARTIALLY_FILLED -> {
                notificationService.sendPartialFillNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSymbol(),
                    order.getExecutedQuantity(),
                    event.getQuantity(),
                    order.getExecutionPrice()
                );
            }
            case REJECTED -> {
                notificationService.sendOrderRejectionNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    order.getRejectionReason()
                );
            }
            case PENDING_MANUAL_REVIEW -> {
                notificationService.sendManualReviewNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    order.getComplianceFlags(),
                    order.getRiskFlags()
                );
            }
        }
        
        // Send risk disclosures for high-risk investments
        if (order.getRiskLevel() == RiskLevel.HIGH || order.getRiskLevel() == RiskLevel.SPECULATIVE) {
            notificationService.sendRiskDisclosure(
                event.getUserId(),
                event.getSymbol(),
                order.getRiskLevel(),
                order.getVolatility()
            );
        }
        
        // Send large order notifications
        if (event.getOrderAmount().compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            notificationService.sendLargeOrderAlert(
                event.getOrderId(),
                event.getSymbol(),
                event.getOrderAmount(),
                order.getStatus()
            );
        }
        
        log.info("Investor notifications sent for order {}: Status: {}", 
            event.getOrderId(), order.getStatus());
    }
    
    private OrderType mapOrderType(String orderTypeStr) {
        try {
            return OrderType.valueOf(orderTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OrderType.MARKET;
        }
    }
    
    private String determineRequiredAuthLevel(String symbol, String orderType, BigDecimal orderAmount) {
        if (orderAmount.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            return "LEVEL_3";
        }
        if ("SELL_SHORT".equals(orderType)) {
            return "LEVEL_2";
        }
        return "LEVEL_1";
    }
    
    private BigDecimal calculateRequiredAmount(InvestmentOrderPlacedEvent event, InvestmentOrder order) {
        if ("BUY".equals(event.getOrderType())) {
            return event.getOrderAmount();
        }
        // For margin accounts, may need additional requirements
        return BigDecimal.ZERO;
    }
    
    private BigDecimal calculatePriceImprovement(String orderType, BigDecimal executionPrice, BigDecimal currentPrice) {
        if ("BUY".equals(orderType)) {
            return currentPrice.subtract(executionPrice);
        } else {
            return executionPrice.subtract(currentPrice);
        }
    }
    
    private void createManualInterventionRecord(InvestmentOrderPlacedEvent event, Exception exception) {
        manualInterventionService.createTask(
            "INVESTMENT_ORDER_PROCESSING_FAILED",
            String.format(
                "Failed to process investment order. " +
                "Order ID: %s, User ID: %s, Symbol: %s, Quantity: %s, Amount: $%.2f. " +
                "Investor may not have received order status notification. " +
                "Exception: %s. Manual intervention required.",
                event.getOrderId(),
                event.getUserId(),
                event.getSymbol(),
                event.getQuantity(),
                event.getOrderAmount(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}