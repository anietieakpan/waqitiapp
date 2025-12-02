package com.waqiti.currency.controller;

import com.waqiti.common.api.ApiResponse;
import com.waqiti.currency.dto.*;
import com.waqiti.currency.service.CurrencyService;
import com.waqiti.currency.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Currency Management", description = "Multi-currency support and exchange rate services")
public class CurrencyController {

    private final CurrencyService currencyService;
    private final ExchangeRateService exchangeRateService;

    // Currency Configuration
    @GetMapping("/supported")
    @Operation(summary = "Get all supported currencies")
    public ResponseEntity<ApiResponse<List<SupportedCurrencyResponse>>> getSupportedCurrencies() {
        log.info("Retrieving all supported currencies");
        
        List<SupportedCurrencyResponse> response = currencyService.getSupportedCurrencies();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/configure")
    @Operation(summary = "Configure currency for account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyConfigurationResponse>> configureCurrency(
            @Valid @RequestBody ConfigureCurrencyRequest request) {
        log.info("Configuring currency {} for account: {}", 
                request.getCurrencyCode(), request.getAccountId());
        
        CurrencyConfigurationResponse response = currencyService.configureCurrency(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/configuration")
    @Operation(summary = "Get currency configuration for account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyConfigurationResponse>> getAccountCurrencyConfiguration(
            @PathVariable UUID accountId) {
        log.info("Retrieving currency configuration for account: {}", accountId);
        
        CurrencyConfigurationResponse response = currencyService.getAccountCurrencyConfiguration(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Exchange Rates
    @GetMapping("/exchange-rates/current")
    @Operation(summary = "Get current exchange rates")
    public ResponseEntity<ApiResponse<List<ExchangeRateResponse>>> getCurrentExchangeRates(
            @RequestParam(required = false) String baseCurrency,
            @RequestParam(required = false) List<String> targetCurrencies) {
        log.info("Retrieving current exchange rates - base: {}, targets: {}", 
                baseCurrency, targetCurrencies);
        
        List<ExchangeRateResponse> response = exchangeRateService.getCurrentExchangeRates(
                baseCurrency, targetCurrencies);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/exchange-rates/historical")
    @Operation(summary = "Get historical exchange rates")
    public ResponseEntity<ApiResponse<Page<ExchangeRateResponse>>> getHistoricalExchangeRates(
            @RequestParam String baseCurrency,
            @RequestParam String targetCurrency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable) {
        log.info("Retrieving historical exchange rates {} to {} from {} to {}", 
                baseCurrency, targetCurrency, startDate, endDate);
        
        Page<ExchangeRateResponse> response = exchangeRateService.getHistoricalExchangeRates(
                baseCurrency, targetCurrency, startDate, endDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/exchange-rates/refresh")
    @Operation(summary = "Refresh exchange rates from external sources")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ExchangeRateRefreshResponse>> refreshExchangeRates(
            @RequestParam(required = false) List<String> currencies) {
        log.info("Refreshing exchange rates for currencies: {}", currencies);
        
        ExchangeRateRefreshResponse response = exchangeRateService.refreshExchangeRates(currencies);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Currency Conversion
    @PostMapping("/convert")
    @Operation(summary = "Convert amount between currencies")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyConversionResponse>> convertCurrency(
            @Valid @RequestBody CurrencyConversionRequest request) {
        log.info("Converting {} {} to {}", 
                request.getAmount(), request.getFromCurrency(), request.getToCurrency());
        
        CurrencyConversionResponse response = currencyService.convertCurrency(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/convert/batch")
    @Operation(summary = "Batch convert multiple amounts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<CurrencyConversionResponse>>> batchConvertCurrency(
            @Valid @RequestBody BatchCurrencyConversionRequest request) {
        log.info("Batch converting {} conversions", request.getConversions().size());
        
        List<CurrencyConversionResponse> response = currencyService.batchConvertCurrency(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/convert/calculator")
    @Operation(summary = "Currency conversion calculator")
    public ResponseEntity<ApiResponse<CurrencyCalculatorResponse>> currencyCalculator(
            @RequestParam BigDecimal amount,
            @RequestParam String fromCurrency,
            @RequestParam String toCurrency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Currency calculator: {} {} to {} on {}", 
                amount, fromCurrency, toCurrency, date);
        
        CurrencyCalculatorResponse response = currencyService.calculateConversion(
                amount, fromCurrency, toCurrency, date);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Multi-Currency Account Management
    @PostMapping("/accounts/{accountId}/enable-multi-currency")
    @Operation(summary = "Enable multi-currency support for account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<MultiCurrencyAccountResponse>> enableMultiCurrency(
            @PathVariable UUID accountId,
            @Valid @RequestBody EnableMultiCurrencyRequest request) {
        log.info("Enabling multi-currency for account: {}", accountId);
        
        MultiCurrencyAccountResponse response = currencyService.enableMultiCurrency(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/accounts/{accountId}/balances")
    @Operation(summary = "Get multi-currency balances for account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<CurrencyBalanceResponse>>> getAccountBalances(
            @PathVariable UUID accountId) {
        log.info("Retrieving multi-currency balances for account: {}", accountId);
        
        List<CurrencyBalanceResponse> response = currencyService.getAccountBalances(accountId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/accounts/{accountId}/convert-balance")
    @Operation(summary = "Convert balance between currencies within account")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<BalanceConversionResponse>> convertAccountBalance(
            @PathVariable UUID accountId,
            @Valid @RequestBody ConvertBalanceRequest request) {
        log.info("Converting balance for account: {} from {} to {}", 
                accountId, request.getFromCurrency(), request.getToCurrency());
        
        BalanceConversionResponse response = currencyService.convertAccountBalance(accountId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Currency Analytics
    @GetMapping("/analytics/volatility")
    @Operation(summary = "Get currency volatility analysis")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyVolatilityResponse>> getCurrencyVolatility(
            @RequestParam String baseCurrency,
            @RequestParam String targetCurrency,
            @RequestParam(defaultValue = "30") Integer days) {
        log.info("Analyzing volatility for {} to {} over {} days", 
                baseCurrency, targetCurrency, days);
        
        CurrencyVolatilityResponse response = currencyService.analyzeCurrencyVolatility(
                baseCurrency, targetCurrency, days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/analytics/trends")
    @Operation(summary = "Get currency trends analysis")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyTrendsResponse>> getCurrencyTrends(
            @RequestParam String baseCurrency,
            @RequestParam List<String> targetCurrencies,
            @RequestParam(defaultValue = "90") Integer days) {
        log.info("Analyzing trends for {} against {} over {} days", 
                baseCurrency, targetCurrencies, days);
        
        CurrencyTrendsResponse response = currencyService.analyzeCurrencyTrends(
                baseCurrency, targetCurrencies, days);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Currency Alerts
    @PostMapping("/alerts")
    @Operation(summary = "Create currency rate alert")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyAlertResponse>> createCurrencyAlert(
            @Valid @RequestBody CreateCurrencyAlertRequest request) {
        log.info("Creating currency alert for {} to {} at rate {}", 
                request.getBaseCurrency(), request.getTargetCurrency(), request.getTargetRate());
        
        CurrencyAlertResponse response = currencyService.createCurrencyAlert(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/alerts")
    @Operation(summary = "Get user currency alerts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<CurrencyAlertResponse>>> getUserCurrencyAlerts(
            @PathVariable UUID userId) {
        log.info("Retrieving currency alerts for user: {}", userId);
        
        List<CurrencyAlertResponse> response = currencyService.getUserCurrencyAlerts(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/alerts/{alertId}")
    @Operation(summary = "Delete currency alert")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Void>> deleteCurrencyAlert(
            @PathVariable UUID alertId) {
        log.info("Deleting currency alert: {}", alertId);
        
        currencyService.deleteCurrencyAlert(alertId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // Currency Preferences
    @PostMapping("/users/{userId}/preferences")
    @Operation(summary = "Set user currency preferences")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyPreferencesResponse>> setCurrencyPreferences(
            @PathVariable UUID userId,
            @Valid @RequestBody SetCurrencyPreferencesRequest request) {
        log.info("Setting currency preferences for user: {}", userId);
        
        CurrencyPreferencesResponse response = currencyService.setCurrencyPreferences(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/users/{userId}/preferences")
    @Operation(summary = "Get user currency preferences")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<CurrencyPreferencesResponse>> getCurrencyPreferences(
            @PathVariable UUID userId) {
        log.info("Retrieving currency preferences for user: {}", userId);
        
        CurrencyPreferencesResponse response = currencyService.getCurrencyPreferences(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // Health Check
    @GetMapping("/health")
    @Operation(summary = "Health check for currency service")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("Currency Service is healthy"));
    }
}