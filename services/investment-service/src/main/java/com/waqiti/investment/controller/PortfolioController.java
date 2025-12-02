package com.waqiti.investment.controller;

import com.waqiti.investment.dto.PortfolioRiskMetricsDto;
import com.waqiti.investment.dto.TransactionHistoryDto;
import com.waqiti.investment.dto.response.PortfolioDto;
import com.waqiti.investment.dto.response.PortfolioPerformanceDto;
import com.waqiti.investment.service.AutoInvestService;
import com.waqiti.investment.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for portfolio management
 */
@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portfolio", description = "Portfolio management API")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {
    "${cors.allowed-origins:http://localhost:3000,https://app.example.com,https://admin.example.com}"
})
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final AutoInvestService autoInvestService;

    @GetMapping("/{accountId}")
    @Operation(summary = "Get portfolio", description = "Get portfolio for an investment account")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<PortfolioDto> getPortfolio(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId) {
        
        log.info("Getting portfolio for account: {}", accountId);
        PortfolioDto portfolio = portfolioService.getPortfolio(accountId);
        return ResponseEntity.ok(portfolio);
    }

    @GetMapping("/{accountId}/detailed")
    @Operation(summary = "Get detailed portfolio", description = "Get portfolio with real-time market data")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<PortfolioDto> getDetailedPortfolio(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId) {
        
        log.info("Getting detailed portfolio for account: {}", accountId);
        PortfolioDto portfolio = portfolioService.getDetailedPortfolio(accountId);
        return ResponseEntity.ok(portfolio);
    }

    @GetMapping("/{accountId}/performance")
    @Operation(summary = "Get portfolio performance", description = "Calculate portfolio performance metrics")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<PortfolioPerformanceDto> getPortfolioPerformance(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId,
            @Parameter(description = "Start date for performance calculation")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for performance calculation")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Calculating portfolio performance for account {} from {} to {}", 
                accountId, startDate, endDate);
        
        PortfolioPerformanceDto performance = portfolioService.calculatePortfolioPerformance(
                accountId, startDate, endDate);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/{accountId}/allocation")
    @Operation(summary = "Get portfolio allocation", description = "Get asset allocation breakdown")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<Map<String, BigDecimal>> getPortfolioAllocation(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId) {
        
        log.info("Getting portfolio allocation for account: {}", accountId);
        Map<String, BigDecimal> allocation = portfolioService.getPortfolioAllocation(accountId);
        return ResponseEntity.ok(allocation);
    }

    @GetMapping("/{accountId}/sectors")
    @Operation(summary = "Get sector allocation", description = "Get portfolio sector breakdown")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<Map<String, BigDecimal>> getSectorAllocation(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId) {
        
        log.info("Getting sector allocation for account: {}", accountId);
        Map<String, BigDecimal> sectors = portfolioService.getSectorAllocation(accountId);
        return ResponseEntity.ok(sectors);
    }

    @PostMapping("/{accountId}/rebalance")
    @Operation(summary = "Generate rebalance recommendations", 
              description = "Generate recommendations to rebalance portfolio")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<List<AutoInvestService.RebalanceRecommendationDto>> generateRebalanceRecommendations(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId,
            @Parameter(description = "Target allocation percentages", required = true)
            @RequestBody @Valid Map<String, BigDecimal> targetAllocation) {
        
        log.info("Generating rebalance recommendations for account: {}", accountId);
        List<AutoInvestService.RebalanceRecommendationDto> recommendations = 
                portfolioService.generateRebalanceRecommendations(accountId, targetAllocation);
        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/{accountId}/risk-metrics")
    @Operation(summary = "Get risk metrics", description = "Calculate portfolio risk metrics")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<PortfolioRiskMetricsDto> getPortfolioRiskMetrics(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId) {
        
        log.info("Calculating risk metrics for account: {}", accountId);
        PortfolioRiskMetricsDto riskMetrics = portfolioService.calculateRiskMetrics(accountId);
        return ResponseEntity.ok(riskMetrics);
    }

    @GetMapping("/{accountId}/transactions")
    @Operation(summary = "Get transaction history", description = "Get portfolio transaction history")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<Page<TransactionHistoryDto>> getPortfolioTransactions(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId,
            @Parameter(description = "Start date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime startDate,
            @Parameter(description = "End date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) 
            LocalDateTime endDate,
            Pageable pageable) {
        
        log.info("Getting transaction history for account: {}", accountId);
        
        if (startDate == null) {
            startDate = LocalDateTime.now().minusMonths(3);
        }
        if (endDate == null) {
            endDate = LocalDateTime.now();
        }
        
        Page<TransactionHistoryDto> transactions = portfolioService.getPortfolioTransactions(
                accountId, startDate, endDate, pageable);
        return ResponseEntity.ok(transactions);
    }

    @PutMapping("/{accountId}/holdings/{holdingId}/price")
    @Operation(summary = "Update holding price", description = "Update market price for a holding")
    @PreAuthorize("@investmentSecurityService.canAccessAccount(#accountId)")
    public ResponseEntity<Void> updateHoldingPrice(
            @Parameter(description = "Investment account ID", required = true)
            @PathVariable Long accountId,
            @Parameter(description = "Holding ID", required = true)
            @PathVariable Long holdingId,
            @Parameter(description = "New market price", required = true)
            @RequestParam BigDecimal price) {
        
        log.info("Updating holding {} price to {}", holdingId, price);
        portfolioService.updateHoldingPrice(holdingId, price);
        return ResponseEntity.ok().build();
    }
}