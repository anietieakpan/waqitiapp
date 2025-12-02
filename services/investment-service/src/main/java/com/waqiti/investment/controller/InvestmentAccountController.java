package com.waqiti.investment.controller;

import com.waqiti.investment.dto.request.CreateAccountRequest;
import com.waqiti.investment.dto.request.FundAccountRequest;
import com.waqiti.investment.dto.response.InvestmentAccountDto;
import com.waqiti.investment.dto.response.TransferDto;
import com.waqiti.investment.service.InvestmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/investment/accounts")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Investment Accounts", description = "Investment account management APIs")
public class InvestmentAccountController {

    private final InvestmentService investmentService;

    @PostMapping
    @Operation(summary = "Create investment account", description = "Create a new investment account for a customer")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<InvestmentAccountDto> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        log.info("Creating investment account for customer: {}", request.getCustomerId());
        InvestmentAccountDto account = investmentService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details", description = "Get investment account details by ID")
    @PreAuthorize("hasRole('USER') and @securityService.hasAccountAccess(#accountId)")
    public ResponseEntity<InvestmentAccountDto> getAccount(@PathVariable String accountId) {
        log.info("Fetching investment account: {}", accountId);
        InvestmentAccountDto account = investmentService.getAccountById(accountId);
        return ResponseEntity.ok(account);
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get account by customer", description = "Get investment account by customer ID")
    @PreAuthorize("hasRole('USER') and @securityService.isCustomer(#customerId)")
    public ResponseEntity<InvestmentAccountDto> getAccountByCustomer(@PathVariable String customerId) {
        log.info("Fetching investment account for customer: {}", customerId);
        InvestmentAccountDto account = investmentService.getAccountByCustomerId(customerId);
        return ResponseEntity.ok(account);
    }

    @PostMapping("/{accountId}/fund")
    @Operation(summary = "Fund account", description = "Deposit funds into investment account")
    @PreAuthorize("hasRole('USER') and @securityService.hasAccountAccess(#accountId)")
    public ResponseEntity<TransferDto> fundAccount(
            @PathVariable String accountId,
            @Valid @RequestBody FundAccountRequest request) {
        log.info("Funding account {} with amount: {}", accountId, request.getAmount());
        request.setAccountId(accountId);
        TransferDto transfer = investmentService.fundAccount(request);
        return ResponseEntity.ok(transfer);
    }

    @PostMapping("/{accountId}/withdraw")
    @Operation(summary = "Withdraw funds", description = "Withdraw funds from investment account")
    @PreAuthorize("hasRole('USER') and @securityService.hasAccountAccess(#accountId)")
    public ResponseEntity<TransferDto> withdrawFunds(
            @PathVariable String accountId,
            @RequestParam @DecimalMin(value = "0.01", message = "Amount must be greater than 0") BigDecimal amount,
            @RequestParam(required = false) String description) {
        log.info("Withdrawing {} from account: {}", amount, accountId);
        TransferDto transfer = investmentService.withdrawFunds(accountId, amount, description);
        return ResponseEntity.ok(transfer);
    }

    @PutMapping("/{accountId}/kyc/verify")
    @Operation(summary = "Verify KYC", description = "Mark account as KYC verified")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvestmentAccountDto> verifyKyc(@PathVariable String accountId) {
        log.info("Verifying KYC for account: {}", accountId);
        InvestmentAccountDto account = investmentService.verifyKyc(accountId);
        return ResponseEntity.ok(account);
    }

    @PutMapping("/{accountId}/activate")
    @Operation(summary = "Activate account", description = "Activate investment account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvestmentAccountDto> activateAccount(@PathVariable String accountId) {
        log.info("Activating account: {}", accountId);
        InvestmentAccountDto account = investmentService.activateAccount(accountId);
        return ResponseEntity.ok(account);
    }

    @PutMapping("/{accountId}/suspend")
    @Operation(summary = "Suspend account", description = "Suspend investment account")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvestmentAccountDto> suspendAccount(
            @PathVariable String accountId,
            @RequestParam String reason) {
        log.info("Suspending account: {} for reason: {}", accountId, reason);
        InvestmentAccountDto account = investmentService.suspendAccount(accountId, reason);
        return ResponseEntity.ok(account);
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Close account", description = "Close investment account")
    @PreAuthorize("hasRole('USER') and @securityService.hasAccountAccess(#accountId)")
    public ResponseEntity<Void> closeAccount(
            @PathVariable String accountId,
            @RequestParam String reason) {
        log.info("Closing account: {} for reason: {}", accountId, reason);
        investmentService.closeAccount(accountId, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}/balance")
    @Operation(summary = "Get account balance", description = "Get current account balance and value")
    @PreAuthorize("hasRole('USER') and @securityService.hasAccountAccess(#accountId)")
    public ResponseEntity<AccountBalanceDto> getAccountBalance(@PathVariable String accountId) {
        log.info("Fetching balance for account: {}", accountId);
        AccountBalanceDto balance = investmentService.getAccountBalance(accountId);
        return ResponseEntity.ok(balance);
    }

    @GetMapping("/{accountId}/performance")
    @Operation(summary = "Get account performance", description = "Get account performance metrics")
    @PreAuthorize("hasRole('USER') and @securityService.hasAccountAccess(#accountId)")
    public ResponseEntity<AccountPerformanceDto> getAccountPerformance(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "1M") String period) {
        log.info("Fetching performance for account: {} for period: {}", accountId, period);
        AccountPerformanceDto performance = investmentService.getAccountPerformance(accountId, period);
        return ResponseEntity.ok(performance);
    }

    @PostMapping("/{accountId}/sync")
    @Operation(summary = "Sync account", description = "Sync account with brokerage provider")
    @PreAuthorize("hasRole('USER') and @securityService.hasAccountAccess(#accountId)")
    public ResponseEntity<InvestmentAccountDto> syncAccount(@PathVariable String accountId) {
        log.info("Syncing account: {}", accountId);
        InvestmentAccountDto account = investmentService.syncAccountWithBrokerage(accountId);
        return ResponseEntity.ok(account);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalanceDto {
        private BigDecimal cashBalance;
        private BigDecimal investedAmount;
        private BigDecimal totalValue;
        private BigDecimal availableToBuy;
        private BigDecimal availableToWithdraw;
        private BigDecimal pendingDeposits;
        private BigDecimal pendingWithdrawals;
        private LocalDateTime lastUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountPerformanceDto {
        private String period;
        private BigDecimal startingValue;
        private BigDecimal endingValue;
        private BigDecimal netChange;
        private BigDecimal percentChange;
        private BigDecimal totalDeposits;
        private BigDecimal totalWithdrawals;
        private BigDecimal totalDividends;
        private BigDecimal realizedGains;
        private BigDecimal unrealizedGains;
        private BigDecimal timeWeightedReturn;
        private BigDecimal moneyWeightedReturn;
        private List<PerformanceDataPoint> dataPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceDataPoint {
        private LocalDateTime date;
        private BigDecimal value;
        private BigDecimal dayChange;
        private BigDecimal percentChange;
    }
}