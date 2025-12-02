package com.waqiti.wallet.api;

import com.waqiti.wallet.dto.CurrencyConversionRequest;
import com.waqiti.wallet.dto.CurrencyConversionResponse;
import com.waqiti.wallet.service.CurrencyConversionService;
import com.waqiti.common.ratelimit.RateLimited;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
@Slf4j
public class CurrencyController {

    private final CurrencyConversionService currencyConversionService;

    @PostMapping("/convert")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 30, refillTokens = 30, refillPeriodMinutes = 1)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CurrencyConversionResponse> convertCurrency(
            @Valid @RequestBody CurrencyConversionRequest request) {
        log.info("Currency conversion request received: {} {} to {}",
                request.getAmount(), request.getSourceCurrency(), request.getTargetCurrency());

        BigDecimal exchangeRate = currencyConversionService.getExchangeRate(
                request.getSourceCurrency(), request.getTargetCurrency());

        BigDecimal convertedAmount = currencyConversionService.convert(
                request.getAmount(), request.getSourceCurrency(), request.getTargetCurrency());

        CurrencyConversionResponse response = CurrencyConversionResponse.builder()
                .sourceAmount(request.getAmount())
                .sourceCurrency(request.getSourceCurrency())
                .targetAmount(convertedAmount)
                .targetCurrency(request.getTargetCurrency())
                .exchangeRate(exchangeRate)
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/rate")
    @RateLimited(keyType = RateLimited.KeyType.USER, capacity = 100, refillTokens = 100, refillPeriodMinutes = 1)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<BigDecimal> getExchangeRate(
            @RequestParam String sourceCurrency,
            @RequestParam String targetCurrency) {
        log.info("Exchange rate request received: {} to {}", sourceCurrency, targetCurrency);

        BigDecimal exchangeRate = currencyConversionService.getExchangeRate(
                sourceCurrency, targetCurrency);

        return ResponseEntity.ok(exchangeRate);
    }
}