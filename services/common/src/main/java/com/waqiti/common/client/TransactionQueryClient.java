package com.waqiti.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Component
public class TransactionQueryClient {
    
    private final RestTemplate restTemplate;
    private final String transactionServiceUrl;
    
    public TransactionQueryClient(
            RestTemplate restTemplate,
            @Value("${services.transaction-service.url:http://transaction-service:8080}") String transactionServiceUrl) {
        this.restTemplate = restTemplate;
        this.transactionServiceUrl = transactionServiceUrl;
    }
    
    @Cacheable(value = "userDailyTotal", key = "#userId + '-' + T(java.time.LocalDate).now()")
    public BigDecimal getTodayTransactionTotal(String userId) {
        try {
            log.debug("Fetching today's transaction total for user: {}", userId);
            
            String url = UriComponentsBuilder
                .fromHttpUrl(transactionServiceUrl + "/api/internal/transactions/totals/daily")
                .queryParam("userId", userId)
                .queryParam("date", LocalDate.now().toString())
                .build()
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object total = response.getBody().get("total");
                if (total instanceof Number) {
                    return new BigDecimal(total.toString());
                }
            }
            
            log.warn("Failed to fetch daily total for user: {}, returning 0", userId);
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Error fetching daily transaction total for user: {}", userId, e);
            return BigDecimal.ZERO;
        }
    }
    
    @Cacheable(value = "userMonthlyTotal", key = "#userId + '-' + T(java.time.YearMonth).now()")
    public BigDecimal getMonthTransactionTotal(String userId) {
        try {
            log.debug("Fetching this month's transaction total for user: {}", userId);
            
            String url = UriComponentsBuilder
                .fromHttpUrl(transactionServiceUrl + "/api/internal/transactions/totals/monthly")
                .queryParam("userId", userId)
                .queryParam("year", LocalDate.now().getYear())
                .queryParam("month", LocalDate.now().getMonthValue())
                .build()
                .toUriString();
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object total = response.getBody().get("total");
                if (total instanceof Number) {
                    return new BigDecimal(total.toString());
                }
            }
            
            log.warn("Failed to fetch monthly total for user: {}, returning 0", userId);
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("Error fetching monthly transaction total for user: {}", userId, e);
            return BigDecimal.ZERO;
        }
    }
    
    public Map<String, BigDecimal> getUserTransactionLimits(String userId) {
        try {
            log.debug("Fetching transaction limits for user: {}", userId);

            String url = transactionServiceUrl + "/api/internal/users/" + userId + "/limits";

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            return Map.of(
                "dailyLimit", new BigDecimal("5000"),
                "monthlyLimit", new BigDecimal("50000")
            );

        } catch (Exception e) {
            log.error("Error fetching transaction limits for user: {}", userId, e);
            return Map.of(
                "dailyLimit", new BigDecimal("5000"),
                "monthlyLimit", new BigDecimal("50000")
            );
        }
    }

    /**
     * Get transaction details for authorization checks
     */
    public Map<String, Object> getTransactionDetails(String transactionId) {
        try {
            log.debug("Fetching transaction details for: {}", transactionId);

            String url = transactionServiceUrl + "/api/internal/transactions/" + transactionId;

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            log.warn("Transaction not found: {}", transactionId);
            return null;

        } catch (Exception e) {
            log.error("Error fetching transaction details for: {}", transactionId, e);
            return null;
        }
    }
}