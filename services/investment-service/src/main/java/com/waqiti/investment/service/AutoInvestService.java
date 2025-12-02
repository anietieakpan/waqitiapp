package com.waqiti.investment.service;

import com.waqiti.investment.domain.*;
import com.waqiti.investment.domain.enums.*;
import com.waqiti.investment.dto.request.CreateAutoInvestRequest;
import com.waqiti.investment.dto.request.CreateOrderRequest;
import com.waqiti.investment.dto.response.AutoInvestDto;
import com.waqiti.investment.exception.*;
import com.waqiti.investment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing automated investment strategies
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AutoInvestService {

    @Lazy
    private final AutoInvestService self;

    private final AutoInvestRepository autoInvestRepository;
    private final InvestmentAccountRepository accountRepository;
    private final OrderExecutionService orderExecutionService;
    private final MarketDataService marketDataService;
    private final PortfolioService portfolioService;

    /**
     * Create a new auto-invest plan
     */
    @Transactional
    public AutoInvestDto createAutoInvestPlan(CreateAutoInvestRequest request) {
        log.info("Creating auto-invest plan for account: {}", request.getAccountId());

        // Validate account
        InvestmentAccount account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Investment account not found"));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvestmentException("Account must be active to create auto-invest plan");
        }

        // Validate allocations
        validateAllocations(request.getAllocations());

        // Create auto-invest plan
        AutoInvest autoInvest = AutoInvest.builder()
                .account(account)
                .name(request.getName())
                .investmentAmount(request.getInvestmentAmount())
                .frequency(request.getFrequency())
                .status(AutoInvestStatus.ACTIVE)
                .nextExecutionDate(calculateNextExecutionDate(request.getFrequency(), request.getStartDate()))
                .createdAt(LocalDateTime.now())
                .build();

        // Create allocations
        List<AutoInvestAllocation> allocations = request.getAllocations().stream()
                .map(allocationRequest -> AutoInvestAllocation.builder()
                        .autoInvest(autoInvest)
                        .symbol(allocationRequest.getSymbol().toUpperCase())
                        .percentage(allocationRequest.getPercentage())
                        .build())
                .collect(Collectors.toList());

        autoInvest.setAllocations(allocations);

        // Save
        autoInvest = autoInvestRepository.save(autoInvest);

        return mapToAutoInvestDto(autoInvest);
    }

    /**
     * Update auto-invest plan
     */
    @Transactional
    public AutoInvestDto updateAutoInvestPlan(Long planId, CreateAutoInvestRequest request) {
        AutoInvest autoInvest = autoInvestRepository.findById(planId)
                .orElseThrow(() -> new InvestmentException("Auto-invest plan not found"));

        // Update basic fields
        autoInvest.setName(request.getName());
        autoInvest.setInvestmentAmount(request.getInvestmentAmount());
        autoInvest.setFrequency(request.getFrequency());

        // Update allocations
        autoInvest.getAllocations().clear();
        List<AutoInvestAllocation> newAllocations = request.getAllocations().stream()
                .map(allocationRequest -> AutoInvestAllocation.builder()
                        .autoInvest(autoInvest)
                        .symbol(allocationRequest.getSymbol().toUpperCase())
                        .percentage(allocationRequest.getPercentage())
                        .build())
                .collect(Collectors.toList());
        autoInvest.setAllocations(newAllocations);

        // Recalculate next execution date
        autoInvest.setNextExecutionDate(calculateNextExecutionDate(request.getFrequency(), LocalDate.now()));
        autoInvest.setUpdatedAt(LocalDateTime.now());

        autoInvest = autoInvestRepository.save(autoInvest);

        return mapToAutoInvestDto(autoInvest);
    }

    /**
     * Pause auto-invest plan
     */
    @Transactional
    public AutoInvestDto pauseAutoInvestPlan(Long planId) {
        AutoInvest autoInvest = autoInvestRepository.findById(planId)
                .orElseThrow(() -> new InvestmentException("Auto-invest plan not found"));

        if (autoInvest.getStatus() != AutoInvestStatus.ACTIVE) {
            throw new InvestmentException("Can only pause active plans");
        }

        autoInvest.setStatus(AutoInvestStatus.PAUSED);
        autoInvest.setUpdatedAt(LocalDateTime.now());
        autoInvest = autoInvestRepository.save(autoInvest);

        return mapToAutoInvestDto(autoInvest);
    }

    /**
     * Resume auto-invest plan
     */
    @Transactional
    public AutoInvestDto resumeAutoInvestPlan(Long planId) {
        AutoInvest autoInvest = autoInvestRepository.findById(planId)
                .orElseThrow(() -> new InvestmentException("Auto-invest plan not found"));

        if (autoInvest.getStatus() != AutoInvestStatus.PAUSED) {
            throw new InvestmentException("Can only resume paused plans");
        }

        autoInvest.setStatus(AutoInvestStatus.ACTIVE);
        autoInvest.setNextExecutionDate(calculateNextExecutionDate(autoInvest.getFrequency(), LocalDate.now()));
        autoInvest.setUpdatedAt(LocalDateTime.now());
        autoInvest = autoInvestRepository.save(autoInvest);

        return mapToAutoInvestDto(autoInvest);
    }

    /**
     * Delete auto-invest plan
     */
    @Transactional
    public void deleteAutoInvestPlan(Long planId) {
        AutoInvest autoInvest = autoInvestRepository.findById(planId)
                .orElseThrow(() -> new InvestmentException("Auto-invest plan not found"));

        autoInvest.setStatus(AutoInvestStatus.CANCELLED);
        autoInvest.setUpdatedAt(LocalDateTime.now());
        autoInvestRepository.save(autoInvest);
    }

    /**
     * Execute auto-invest plans (scheduled job)
     */
    @Scheduled(cron = "0 0 9 * * *") // Run daily at 9 AM
    @Transactional
    public void executeAutoInvestPlans() {
        log.info("Starting auto-invest execution job");

        LocalDate today = LocalDate.now();
        List<AutoInvest> plansToExecute = autoInvestRepository.findByStatusAndNextExecutionDate(
                AutoInvestStatus.ACTIVE, today);

        log.info("Found {} auto-invest plans to execute", plansToExecute.size());

        for (AutoInvest plan : plansToExecute) {
            try {
                self.executePlan(plan);
                
                // Update next execution date
                plan.setNextExecutionDate(calculateNextExecutionDate(plan.getFrequency(), today));
                plan.setLastExecutionDate(today);
                autoInvestRepository.save(plan);
                
            } catch (Exception e) {
                log.error("Failed to execute auto-invest plan {}: {}", plan.getId(), e.getMessage(), e);
                // Continue with other plans
            }
        }

        log.info("Auto-invest execution job completed");
    }

    /**
     * Execute a single auto-invest plan
     */
    @Transactional
    public void executePlan(AutoInvest plan) {
        log.info("Executing auto-invest plan {} for account {}", plan.getId(), plan.getAccount().getId());

        InvestmentAccount account = plan.getAccount();
        
        // Check if account has sufficient funds
        if (account.getCashBalance().compareTo(plan.getInvestmentAmount()) < 0) {
            log.warn("Insufficient funds for auto-invest plan {}. Required: {}, Available: {}", 
                    plan.getId(), plan.getInvestmentAmount(), account.getCashBalance());
            return;
        }

        // Execute orders for each allocation
        BigDecimal totalInvested = BigDecimal.ZERO;
        List<InvestmentOrder> orders = new ArrayList<>();

        for (AutoInvestAllocation allocation : plan.getAllocations()) {
            try {
                BigDecimal allocationAmount = plan.getInvestmentAmount()
                        .multiply(allocation.getPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

                if (allocationAmount.compareTo(BigDecimal.ONE) < 0) {
                    log.warn("Allocation amount too small for {}: {}", allocation.getSymbol(), allocationAmount);
                    continue;
                }

                // Get current price
                StockQuoteDto quote = marketDataService.getStockQuote(allocation.getSymbol());
                BigDecimal currentPrice = quote.getPrice();

                // Calculate quantity (fractional shares supported)
                BigDecimal quantity = allocationAmount.divide(currentPrice, 6, RoundingMode.DOWN);

                if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                    // Create order
                    CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                            .accountId(account.getId())
                            .symbol(allocation.getSymbol())
                            .orderType(OrderType.MARKET)
                            .orderSide(OrderSide.BUY)
                            .quantity(quantity)
                            .build();

                    orderExecutionService.createOrder(orderRequest);
                    totalInvested = totalInvested.add(allocationAmount);
                }

            } catch (Exception e) {
                log.error("Failed to execute allocation for {}: {}", allocation.getSymbol(), e.getMessage());
                // Continue with other allocations
            }
        }

        log.info("Auto-invest plan {} executed. Total invested: {}", plan.getId(), totalInvested);
    }

    /**
     * Get auto-invest plan details
     */
    @Transactional(readOnly = true)
    public AutoInvestDto getAutoInvestPlan(Long planId) {
        AutoInvest autoInvest = autoInvestRepository.findById(planId)
                .orElseThrow(() -> new InvestmentException("Auto-invest plan not found"));
        
        return mapToAutoInvestDto(autoInvest);
    }

    /**
     * Get all auto-invest plans for an account
     */
    @Transactional(readOnly = true)
    public List<AutoInvestDto> getAccountAutoInvestPlans(Long accountId) {
        List<AutoInvest> plans = autoInvestRepository.findByAccountId(accountId);
        
        return plans.stream()
                .map(this::mapToAutoInvestDto)
                .collect(Collectors.toList());
    }

    /**
     * Calculate performance of auto-invest plan
     */
    @Transactional(readOnly = true)
    public AutoInvestPerformanceDto calculateAutoInvestPerformance(Long planId) {
        AutoInvest plan = autoInvestRepository.findById(planId)
                .orElseThrow(() -> new InvestmentException("Auto-invest plan not found"));

        // Get all holdings related to this auto-invest plan
        Portfolio portfolio = plan.getAccount().getPortfolio();
        
        BigDecimal totalInvested = calculateTotalInvested(plan);
        BigDecimal currentValue = calculateCurrentValue(plan, portfolio);
        BigDecimal totalReturn = currentValue.subtract(totalInvested);
        BigDecimal returnPercentage = calculateReturnPercentage(totalInvested, currentValue);

        return AutoInvestPerformanceDto.builder()
                .planId(planId)
                .totalInvested(totalInvested)
                .currentValue(currentValue)
                .totalReturn(totalReturn)
                .returnPercentage(returnPercentage)
                .numberOfExecutions(calculateNumberOfExecutions(plan))
                .build();
    }

    /**
     * Rebalance portfolio based on auto-invest allocations
     */
    @Transactional
    public List<RebalanceRecommendationDto> rebalancePortfolio(Long planId) {
        AutoInvest plan = autoInvestRepository.findById(planId)
                .orElseThrow(() -> new InvestmentException("Auto-invest plan not found"));

        Portfolio portfolio = plan.getAccount().getPortfolio();
        Map<String, BigDecimal> currentAllocation = calculateCurrentAllocation(portfolio, plan);
        Map<String, BigDecimal> targetAllocation = plan.getAllocations().stream()
                .collect(Collectors.toMap(
                        AutoInvestAllocation::getSymbol,
                        AutoInvestAllocation::getPercentage
                ));

        List<RebalanceRecommendationDto> recommendations = new ArrayList<>();

        for (AutoInvestAllocation allocation : plan.getAllocations()) {
            String symbol = allocation.getSymbol();
            BigDecimal targetPercentage = allocation.getPercentage();
            BigDecimal currentPercentage = currentAllocation.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal difference = targetPercentage.subtract(currentPercentage);

            if (Math.abs(difference.doubleValue()) > 1.0) { // 1% threshold
                recommendations.add(RebalanceRecommendationDto.builder()
                        .assetClass(symbol)
                        .currentAllocation(currentPercentage)
                        .targetAllocation(targetPercentage)
                        .adjustmentValue(calculateAdjustmentValue(portfolio, difference))
                        .action(difference.compareTo(BigDecimal.ZERO) > 0 ? "BUY" : "SELL")
                        .build());
            }
        }

        return recommendations;
    }

    // Helper methods

    private void validateAllocations(List<CreateAutoInvestRequest.AllocationRequest> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new InvestmentException("At least one allocation is required");
        }

        BigDecimal totalPercentage = allocations.stream()
                .map(CreateAutoInvestRequest.AllocationRequest::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPercentage.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new InvestmentException("Total allocation percentage must equal 100%");
        }

        // Validate each allocation
        for (CreateAutoInvestRequest.AllocationRequest allocation : allocations) {
            if (allocation.getPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvestmentException("Allocation percentage must be positive");
            }
            
            // Validate symbol exists
            try {
                marketDataService.getStockQuote(allocation.getSymbol());
            } catch (Exception e) {
                throw new InvestmentException("Invalid symbol: " + allocation.getSymbol());
            }
        }
    }

    private LocalDate calculateNextExecutionDate(AutoInvestFrequency frequency, LocalDate startDate) {
        LocalDate baseDate = startDate.isAfter(LocalDate.now()) ? startDate : LocalDate.now();
        
        return switch (frequency) {
            case DAILY -> baseDate.plusDays(1);
            case WEEKLY -> baseDate.plusWeeks(1);
            case BIWEEKLY -> baseDate.plusWeeks(2);
            case MONTHLY -> baseDate.plusMonths(1);
            case QUARTERLY -> baseDate.plusMonths(3);
        };
    }

    private BigDecimal calculateTotalInvested(AutoInvest plan) {
        if (plan.getLastExecutionDate() == null) {
            return BigDecimal.ZERO;
        }

        int executions = calculateNumberOfExecutions(plan);
        return plan.getInvestmentAmount().multiply(BigDecimal.valueOf(executions));
    }

    private int calculateNumberOfExecutions(AutoInvest plan) {
        if (plan.getCreatedAt() == null || plan.getLastExecutionDate() == null) {
            return 0;
        }

        // Simplified calculation based on frequency
        LocalDate startDate = plan.getCreatedAt().toLocalDate();
        LocalDate endDate = plan.getLastExecutionDate();
        
        return switch (plan.getFrequency()) {
            case DAILY -> (int) startDate.until(endDate).getDays();
            case WEEKLY -> (int) startDate.until(endDate).toTotalMonths() * 4;
            case BIWEEKLY -> (int) startDate.until(endDate).toTotalMonths() * 2;
            case MONTHLY -> (int) startDate.until(endDate).toTotalMonths();
            case QUARTERLY -> (int) startDate.until(endDate).toTotalMonths() / 3;
        };
    }

    private BigDecimal calculateCurrentValue(AutoInvest plan, Portfolio portfolio) {
        BigDecimal totalValue = BigDecimal.ZERO;
        
        for (AutoInvestAllocation allocation : plan.getAllocations()) {
            InvestmentHolding holding = portfolio.getHoldings().stream()
                    .filter(h -> h.getSymbol().equals(allocation.getSymbol()))
                    .findFirst()
                    .orElse(null);
            
            if (holding != null) {
                totalValue = totalValue.add(holding.getMarketValue());
            }
        }
        
        return totalValue;
    }

    private BigDecimal calculateReturnPercentage(BigDecimal cost, BigDecimal value) {
        if (cost.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.subtract(cost)
                .divide(cost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private Map<String, BigDecimal> calculateCurrentAllocation(Portfolio portfolio, AutoInvest plan) {
        Map<String, BigDecimal> allocation = new HashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;
        
        // Calculate total value for plan symbols
        for (AutoInvestAllocation alloc : plan.getAllocations()) {
            InvestmentHolding holding = portfolio.getHoldings().stream()
                    .filter(h -> h.getSymbol().equals(alloc.getSymbol()))
                    .findFirst()
                    .orElse(null);
            
            if (holding != null) {
                allocation.put(alloc.getSymbol(), holding.getMarketValue());
                totalValue = totalValue.add(holding.getMarketValue());
            }
        }
        
        // Convert to percentages
        if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
            allocation.replaceAll((symbol, value) -> 
                    value.divide(totalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            );
        }
        
        return allocation;
    }

    private BigDecimal calculateAdjustmentValue(Portfolio portfolio, BigDecimal percentageDifference) {
        BigDecimal portfolioValue = portfolio.getTotalValue();
        return portfolioValue.multiply(percentageDifference).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private AutoInvestDto mapToAutoInvestDto(AutoInvest autoInvest) {
        List<AutoInvestDto.AllocationDto> allocations = autoInvest.getAllocations().stream()
                .map(allocation -> AutoInvestDto.AllocationDto.builder()
                        .symbol(allocation.getSymbol())
                        .percentage(allocation.getPercentage())
                        .build())
                .collect(Collectors.toList());

        return AutoInvestDto.builder()
                .id(autoInvest.getId())
                .accountId(autoInvest.getAccount().getId())
                .name(autoInvest.getName())
                .investmentAmount(autoInvest.getInvestmentAmount())
                .frequency(autoInvest.getFrequency())
                .status(autoInvest.getStatus())
                .allocations(allocations)
                .nextExecutionDate(autoInvest.getNextExecutionDate())
                .lastExecutionDate(autoInvest.getLastExecutionDate())
                .createdAt(autoInvest.getCreatedAt())
                .updatedAt(autoInvest.getUpdatedAt())
                .build();
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    public static class AutoInvestPerformanceDto {
        private Long planId;
        private BigDecimal totalInvested;
        private BigDecimal currentValue;
        private BigDecimal totalReturn;
        private BigDecimal returnPercentage;
        private Integer numberOfExecutions;
    }

    @lombok.Data
    @lombok.Builder
    public static class RebalanceRecommendationDto {
        private String assetClass;
        private BigDecimal currentAllocation;
        private BigDecimal targetAllocation;
        private BigDecimal adjustmentValue;
        private String action;
        private List<StockRecommendation> specificRecommendations;

        @lombok.Data
        @lombok.Builder
        public static class StockRecommendation {
            private String symbol;
            private BigDecimal adjustmentValue;
        }
    }
}