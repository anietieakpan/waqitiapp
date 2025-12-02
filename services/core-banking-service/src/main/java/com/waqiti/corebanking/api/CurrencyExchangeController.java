package com.waqiti.corebanking.api;

import com.waqiti.corebanking.service.CurrencyExchangeService;
import com.waqiti.corebanking.service.CurrencyExchangeService.CurrencyConversionResult;
import com.waqiti.corebanking.service.CurrencyExchangeService.ExchangeServiceHealthStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Currency Exchange API Controller
 * 
 * Provides endpoints for currency exchange rates and conversions.
 */
@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Currency Exchange", description = "Currency exchange rates and conversion operations")
public class CurrencyExchangeController {
    
    private final CurrencyExchangeService currencyExchangeService;
    
    /**
     * Get current exchange rate between two currencies
     */
    @GetMapping("/rates/{fromCurrency}/{toCurrency}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Get exchange rate",
               description = "Gets current exchange rate between two currencies")
    public ResponseEntity<ExchangeRateResponse> getExchangeRate(
            @Parameter(description = "From currency code") @PathVariable String fromCurrency,
            @Parameter(description = "To currency code") @PathVariable String toCurrency) {
        
        log.info("Exchange rate requested: {} to {}", fromCurrency, toCurrency);
        
        try {
            // Validate currency codes
            if (!isValidCurrencyCode(fromCurrency) || !isValidCurrencyCode(toCurrency)) {
                return ResponseEntity.badRequest()
                    .body(ExchangeRateResponse.builder()
                        .error("Invalid currency code")
                        .build());
            }
            
            BigDecimal rate = currencyExchangeService.getExchangeRate(
                fromCurrency.toUpperCase(), toCurrency.toUpperCase());
            
            return ResponseEntity.ok(ExchangeRateResponse.builder()
                .fromCurrency(fromCurrency.toUpperCase())
                .toCurrency(toCurrency.toUpperCase())
                .exchangeRate(rate)
                .timestamp(LocalDateTime.now())
                .build());
                
        } catch (IllegalArgumentException e) {
            log.error("Invalid currency specified: {}/{}", fromCurrency, toCurrency, e);
            return ResponseEntity.badRequest()
                .body(ExchangeRateResponse.builder()
                    .fromCurrency(fromCurrency.toUpperCase())
                    .toCurrency(toCurrency.toUpperCase())
                    .error("Invalid currency: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (RuntimeException e) {
            log.error("Error getting exchange rate for {}/{}", fromCurrency, toCurrency, e);
            return ResponseEntity.internalServerError()
                .body(ExchangeRateResponse.builder()
                    .fromCurrency(fromCurrency.toUpperCase())
                    .toCurrency(toCurrency.toUpperCase())
                    .error("Service temporarily unavailable")
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }
    
    /**
     * Convert currency amount
     */
    @PostMapping("/convert")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Convert currency",
               description = "Converts amount from one currency to another")
    public ResponseEntity<CurrencyConversionResponse> convertCurrency(
            @RequestBody CurrencyConversionRequest request) {
        
        log.info("Currency conversion requested: {} {} to {}", 
            request.getAmount(), request.getFromCurrency(), request.getToCurrency());
        
        try {
            // Validate request
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest()
                    .body(CurrencyConversionResponse.builder()
                        .error("Amount must be greater than zero")
                        .build());
            }
            
            if (!isValidCurrencyCode(request.getFromCurrency()) || 
                !isValidCurrencyCode(request.getToCurrency())) {
                return ResponseEntity.badRequest()
                    .body(CurrencyConversionResponse.builder()
                        .error("Invalid currency code")
                        .build());
            }
            
            CurrencyConversionResult result = currencyExchangeService.convertCurrency(
                request.getAmount(),
                request.getFromCurrency().toUpperCase(),
                request.getToCurrency().toUpperCase()
            );
            
            return ResponseEntity.ok(CurrencyConversionResponse.builder()
                .originalAmount(result.getOriginalAmount())
                .convertedAmount(result.getConvertedAmount())
                .fromCurrency(result.getFromCurrency())
                .toCurrency(result.getToCurrency())
                .exchangeRate(result.getExchangeRate())
                .midMarketRate(result.getMidMarketRate())
                .spread(result.getSpread())
                .spreadPercentage(result.getSpreadPercentage())
                .conversionTimestamp(result.getConversionTimestamp())
                .build());
                
        } catch (Exception e) {
            log.error("Error converting currency", e);
            return ResponseEntity.internalServerError()
                .body(CurrencyConversionResponse.builder()
                    .error(e.getMessage())
                    .build());
        }
    }
    
    /**
     * Get all exchange rates for a base currency
     */
    @GetMapping("/rates/{baseCurrency}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Get all exchange rates",
               description = "Gets all available exchange rates for a base currency")
    public ResponseEntity<AllRatesResponse> getAllExchangeRates(
            @Parameter(description = "Base currency code") @PathVariable String baseCurrency) {
        
        log.info("All exchange rates requested for base currency: {}", baseCurrency);
        
        try {
            if (!isValidCurrencyCode(baseCurrency)) {
                return ResponseEntity.badRequest()
                    .body(AllRatesResponse.builder()
                        .error("Invalid currency code")
                        .build());
            }
            
            Map<String, BigDecimal> rates = currencyExchangeService.getAllExchangeRates(
                baseCurrency.toUpperCase());
            
            return ResponseEntity.ok(AllRatesResponse.builder()
                .baseCurrency(baseCurrency.toUpperCase())
                .rates(rates)
                .rateCount(rates.size())
                .timestamp(LocalDateTime.now())
                .build());
                
        } catch (Exception e) {
            log.error("Error getting all exchange rates for {}", baseCurrency, e);
            return ResponseEntity.internalServerError()
                .body(AllRatesResponse.builder()
                    .baseCurrency(baseCurrency.toUpperCase())
                    .error(e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }
    
    /**
     * Get supported currencies
     */
    @GetMapping("/supported")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    @Operation(summary = "Get supported currencies",
               description = "Returns list of supported currency codes")
    public ResponseEntity<SupportedCurrenciesResponse> getSupportedCurrencies() {
        
        try {
            List<CurrencyInfo> supportedCurrencies = List.of(
                new CurrencyInfo("USD", "US Dollar", "$"),
                new CurrencyInfo("EUR", "Euro", "€"),
                new CurrencyInfo("GBP", "British Pound", "£"),
                new CurrencyInfo("CAD", "Canadian Dollar", "CA$"),
                new CurrencyInfo("AUD", "Australian Dollar", "AU$"),
                new CurrencyInfo("JPY", "Japanese Yen", "¥"),
                new CurrencyInfo("CHF", "Swiss Franc", "CHF"),
                new CurrencyInfo("CNY", "Chinese Yuan", "¥"),
                new CurrencyInfo("INR", "Indian Rupee", "₹"),
                new CurrencyInfo("NGN", "Nigerian Naira", "₦"),
                new CurrencyInfo("KES", "Kenyan Shilling", "KSh"),
                new CurrencyInfo("GHS", "Ghanaian Cedi", "GH₵")
            );
            
            return ResponseEntity.ok(SupportedCurrenciesResponse.builder()
                .currencies(supportedCurrencies)
                .count(supportedCurrencies.size())
                .build());
                
        } catch (Exception e) {
            log.error("Error getting supported currencies", e);
            return ResponseEntity.internalServerError()
                .body(SupportedCurrenciesResponse.builder()
                    .error(e.getMessage())
                    .build());
        }
    }
    
    /**
     * Refresh exchange rates manually (Admin only)
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refresh exchange rates", 
               description = "Manually triggers refresh of exchange rates")
    public ResponseEntity<RefreshResponse> refreshExchangeRates() {
        
        log.info("Manual refresh of exchange rates requested");
        
        try {
            currencyExchangeService.refreshExchangeRates();
            
            return ResponseEntity.ok(RefreshResponse.builder()
                .status("SUCCESS")
                .message("Exchange rates refreshed successfully")
                .timestamp(LocalDateTime.now())
                .build());
                
        } catch (Exception e) {
            log.error("Error refreshing exchange rates", e);
            return ResponseEntity.internalServerError()
                .body(RefreshResponse.builder()
                    .status("FAILED")
                    .message("Failed to refresh exchange rates: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());
        }
    }
    
    /**
     * Get exchange service health status (Admin only)
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get exchange service health", 
               description = "Returns health status of currency exchange service")
    public ResponseEntity<ExchangeServiceHealthStatus> getHealthStatus() {
        
        try {
            ExchangeServiceHealthStatus status = currencyExchangeService.getHealthStatus();
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting exchange service health status", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Calculate conversion preview with fees
     */
    @PostMapping("/preview")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Preview currency conversion", 
               description = "Previews currency conversion with fees and spread")
    public ResponseEntity<ConversionPreviewResponse> previewConversion(
            @RequestBody CurrencyConversionRequest request) {
        
        log.info("Conversion preview requested: {} {} to {}", 
            request.getAmount(), request.getFromCurrency(), request.getToCurrency());
        
        try {
            CurrencyConversionResult result = currencyExchangeService.convertCurrency(
                request.getAmount(),
                request.getFromCurrency().toUpperCase(),
                request.getToCurrency().toUpperCase()
            );
            
            // Calculate estimated fees (this would integrate with fee service)
            BigDecimal estimatedFee = result.getOriginalAmount().multiply(new BigDecimal("0.01")); // 1% fee
            BigDecimal totalCost = result.getOriginalAmount().add(estimatedFee);
            
            return ResponseEntity.ok(ConversionPreviewResponse.builder()
                .originalAmount(result.getOriginalAmount())
                .convertedAmount(result.getConvertedAmount())
                .fromCurrency(result.getFromCurrency())
                .toCurrency(result.getToCurrency())
                .exchangeRate(result.getExchangeRate())
                .midMarketRate(result.getMidMarketRate())
                .spread(result.getSpread())
                .estimatedFee(estimatedFee)
                .totalCost(totalCost)
                .previewTimestamp(LocalDateTime.now())
                .build());
                
        } catch (Exception e) {
            log.error("Error generating conversion preview", e);
            return ResponseEntity.internalServerError()
                .body(ConversionPreviewResponse.builder()
                    .error(e.getMessage())
                    .build());
        }
    }
    
    // Helper methods
    
    private boolean isValidCurrencyCode(String currencyCode) {
        if (currencyCode == null || currencyCode.length() != 3) {
            return false;
        }
        
        String[] validCurrencies = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "CNY", "INR", "NGN", "KES", "GHS"};
        String upperCode = currencyCode.toUpperCase();
        
        for (String valid : validCurrencies) {
            if (valid.equals(upperCode)) {
                return true;
            }
        }
        
        return false;
    }
    
    // DTOs
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CurrencyConversionRequest {
        private BigDecimal amount;
        private String fromCurrency;
        private String toCurrency;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExchangeRateResponse {
        private String fromCurrency;
        private String toCurrency;
        private BigDecimal exchangeRate;
        private LocalDateTime timestamp;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CurrencyConversionResponse {
        private BigDecimal originalAmount;
        private BigDecimal convertedAmount;
        private String fromCurrency;
        private String toCurrency;
        private BigDecimal exchangeRate;
        private BigDecimal midMarketRate;
        private BigDecimal spread;
        private BigDecimal spreadPercentage;
        private LocalDateTime conversionTimestamp;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AllRatesResponse {
        private String baseCurrency;
        private Map<String, BigDecimal> rates;
        private int rateCount;
        private LocalDateTime timestamp;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SupportedCurrenciesResponse {
        private List<CurrencyInfo> currencies;
        private int count;
        private String error;
    }
    
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CurrencyInfo {
        private String code;
        private String name;
        private String symbol;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RefreshResponse {
        private String status;
        private String message;
        private LocalDateTime timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConversionPreviewResponse {
        private BigDecimal originalAmount;
        private BigDecimal convertedAmount;
        private String fromCurrency;
        private String toCurrency;
        private BigDecimal exchangeRate;
        private BigDecimal midMarketRate;
        private BigDecimal spread;
        private BigDecimal estimatedFee;
        private BigDecimal totalCost;
        private LocalDateTime previewTimestamp;
        private String error;
    }
}