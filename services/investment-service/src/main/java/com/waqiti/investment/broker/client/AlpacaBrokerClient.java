package com.waqiti.investment.broker.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.investment.broker.dto.BrokerAccountInfo;
import com.waqiti.investment.broker.dto.BrokerOrderRequest;
import com.waqiti.investment.broker.dto.BrokerOrderResponse;
import com.waqiti.investment.broker.enums.BrokerProvider;
import com.waqiti.investment.broker.enums.ExecutionStatus;
import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderType;
import com.waqiti.investment.domain.enums.TimeInForce;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Alpaca Markets Broker Client
 *
 * Alpaca Trading API Documentation: https://alpaca.markets/docs/api-references/trading-api/
 *
 * Features:
 * - Commission-free stock trading
 * - Fractional shares support
 * - Paper trading for testing
 * - Real-time order status updates
 * - Extended hours trading
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
@Slf4j
@Component
public class AlpacaBrokerClient implements BrokerClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${waqiti.brokerage.providers.alpaca.api-key:}")
    private String apiKey;

    @Value("${waqiti.brokerage.providers.alpaca.api-secret:}")
    private String apiSecret;

    @Value("${waqiti.brokerage.providers.alpaca.base-url:https://paper-api.alpaca.markets}")
    private String baseUrl;

    public AlpacaBrokerClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @CircuitBreaker(name = "alpaca-broker", fallbackMethod = "submitOrderFallback")
    @Retry(name = "alpaca-broker")
    @TimeLimiter(name = "alpaca-broker")
    public BrokerOrderResponse submitOrder(BrokerOrderRequest request) {
        log.info("Submitting order to Alpaca: symbol={}, side={}, qty={}, type={}",
            request.getSymbol(), request.getSide(), request.getQuantity(), request.getType());

        try {
            // Build Alpaca API request
            Map<String, Object> alpacaOrder = new HashMap<>();
            alpacaOrder.put("symbol", request.getSymbol());
            alpacaOrder.put("qty", request.getQuantity().toPlainString());
            alpacaOrder.put("side", mapOrderSide(request.getSide()));
            alpacaOrder.put("type", mapOrderType(request.getType()));
            alpacaOrder.put("time_in_force", mapTimeInForce(request.getTimeInForce()));
            alpacaOrder.put("client_order_id", request.getClientOrderId());

            if (request.getLimitPrice() != null) {
                alpacaOrder.put("limit_price", request.getLimitPrice().toPlainString());
            }
            if (request.getStopPrice() != null) {
                alpacaOrder.put("stop_price", request.getStopPrice().toPlainString());
            }
            if (request.getExtendedHours() != null) {
                alpacaOrder.put("extended_hours", request.getExtendedHours());
            }

            // Submit to Alpaca API
            HttpHeaders headers = createAlpacaHeaders();
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(alpacaOrder, headers);

            String url = baseUrl + "/v2/orders";
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);

            // Parse response
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            return BrokerOrderResponse.builder()
                .clientOrderId(request.getClientOrderId())
                .brokerOrderId(responseJson.get("id").asText())
                .status(mapAlpacaStatus(responseJson.get("status").asText()))
                .symbol(responseJson.get("symbol").asText())
                .totalQuantity(new BigDecimal(responseJson.get("qty").asText()))
                .filledQuantity(new BigDecimal(responseJson.get("filled_qty").asText()))
                .averageFillPrice(responseJson.has("filled_avg_price") &&
                    !responseJson.get("filled_avg_price").isNull()
                    ? new BigDecimal(responseJson.get("filled_avg_price").asText())
                    : null)
                .commission(BigDecimal.ZERO) // Alpaca is commission-free
                .fees(BigDecimal.ZERO)
                .createdAt(parseAlpacaTimestamp(responseJson.get("created_at").asText()))
                .filledAt(responseJson.has("filled_at") && !responseJson.get("filled_at").isNull()
                    ? parseAlpacaTimestamp(responseJson.get("filled_at").asText())
                    : null)
                .rawResponse(response.getBody())
                .build();

        } catch (Exception e) {
            log.error("Failed to submit order to Alpaca", e);
            return BrokerOrderResponse.builder()
                .clientOrderId(request.getClientOrderId())
                .status(ExecutionStatus.FAILED)
                .rejectionReason("Alpaca API error: " + e.getMessage())
                .build();
        }
    }

    @Override
    @CircuitBreaker(name = "alpaca-broker", fallbackMethod = "getOrderStatusFallback")
    @Retry(name = "alpaca-broker")
    public BrokerOrderResponse getOrderStatus(String brokerOrderId) {
        log.info("Getting order status from Alpaca: orderId={}", brokerOrderId);

        try {
            HttpHeaders headers = createAlpacaHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = baseUrl + "/v2/orders/" + brokerOrderId;
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

            JsonNode orderJson = objectMapper.readTree(response.getBody());

            return BrokerOrderResponse.builder()
                .clientOrderId(orderJson.get("client_order_id").asText())
                .brokerOrderId(orderJson.get("id").asText())
                .status(mapAlpacaStatus(orderJson.get("status").asText()))
                .symbol(orderJson.get("symbol").asText())
                .totalQuantity(new BigDecimal(orderJson.get("qty").asText()))
                .filledQuantity(new BigDecimal(orderJson.get("filled_qty").asText()))
                .averageFillPrice(orderJson.has("filled_avg_price") &&
                    !orderJson.get("filled_avg_price").isNull()
                    ? new BigDecimal(orderJson.get("filled_avg_price").asText())
                    : null)
                .commission(BigDecimal.ZERO)
                .fees(BigDecimal.ZERO)
                .createdAt(parseAlpacaTimestamp(orderJson.get("created_at").asText()))
                .filledAt(orderJson.has("filled_at") && !orderJson.get("filled_at").isNull()
                    ? parseAlpacaTimestamp(orderJson.get("filled_at").asText())
                    : null)
                .rawResponse(response.getBody())
                .build();

        } catch (Exception e) {
            log.error("Failed to get order status from Alpaca", e);
            return BrokerOrderResponse.builder()
                .brokerOrderId(brokerOrderId)
                .status(ExecutionStatus.FAILED)
                .rejectionReason("Alpaca API error: " + e.getMessage())
                .build();
        }
    }

    @Override
    @CircuitBreaker(name = "alpaca-broker", fallbackMethod = "cancelOrderFallback")
    @Retry(name = "alpaca-broker")
    public BrokerOrderResponse cancelOrder(String brokerOrderId) {
        log.info("Cancelling order at Alpaca: orderId={}", brokerOrderId);

        try {
            HttpHeaders headers = createAlpacaHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = baseUrl + "/v2/orders/" + brokerOrderId;
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.DELETE, entity, String.class);

            // Return current status
            return getOrderStatus(brokerOrderId);

        } catch (Exception e) {
            log.error("Failed to cancel order at Alpaca", e);
            return BrokerOrderResponse.builder()
                .brokerOrderId(brokerOrderId)
                .status(ExecutionStatus.FAILED)
                .rejectionReason("Alpaca API error: " + e.getMessage())
                .build();
        }
    }

    @Override
    @CircuitBreaker(name = "alpaca-broker", fallbackMethod = "getAccountInfoFallback")
    @Retry(name = "alpaca-broker")
    public BrokerAccountInfo getAccountInfo(String brokerAccountId) {
        log.info("Getting account info from Alpaca");

        try {
            HttpHeaders headers = createAlpacaHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = baseUrl + "/v2/account";
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

            JsonNode accountJson = objectMapper.readTree(response.getBody());

            return BrokerAccountInfo.builder()
                .accountId(accountJson.get("id").asText())
                .status(accountJson.get("status").asText())
                .cashBalance(new BigDecimal(accountJson.get("cash").asText()))
                .buyingPower(new BigDecimal(accountJson.get("buying_power").asText()))
                .portfolioValue(new BigDecimal(accountJson.get("portfolio_value").asText()))
                .patternDayTrader(accountJson.get("pattern_day_trader").asBoolean())
                .dayTradesRemaining(accountJson.has("daytrade_count")
                    ? 3 - accountJson.get("daytrade_count").asInt()
                    : null)
                .lastUpdated(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Failed to get account info from Alpaca", e);
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpHeaders headers = createAlpacaHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            String url = baseUrl + "/v2/account";
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.error("Alpaca broker is unavailable", e);
            return false;
        }
    }

    @Override
    public BrokerProvider getProvider() {
        return BrokerProvider.ALPACA;
    }

    /**
     * Create HTTP headers for Alpaca API authentication
     */
    private HttpHeaders createAlpacaHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("APCA-API-KEY-ID", apiKey);
        headers.set("APCA-API-SECRET-KEY", apiSecret);
        return headers;
    }

    /**
     * Map our OrderSide to Alpaca's side format
     */
    private String mapOrderSide(OrderSide side) {
        return side == OrderSide.BUY ? "buy" : "sell";
    }

    /**
     * Map our OrderType to Alpaca's type format
     */
    private String mapOrderType(OrderType type) {
        return switch (type) {
            case MARKET -> "market";
            case LIMIT -> "limit";
            case STOP -> "stop";
            case STOP_LIMIT -> "stop_limit";
            default -> "market";
        };
    }

    /**
     * Map our TimeInForce to Alpaca's format
     */
    private String mapTimeInForce(TimeInForce tif) {
        return switch (tif) {
            case DAY -> "day";
            case GTC -> "gtc";
            case IOC -> "ioc";
            case FOK -> "fok";
            default -> "day";
        };
    }

    /**
     * Map Alpaca status to our ExecutionStatus
     */
    private ExecutionStatus mapAlpacaStatus(String alpacaStatus) {
        return switch (alpacaStatus.toLowerCase()) {
            case "accepted", "pending_new", "new" -> ExecutionStatus.ACCEPTED;
            case "partially_filled" -> ExecutionStatus.PARTIALLY_FILLED;
            case "filled" -> ExecutionStatus.FILLED;
            case "done_for_day", "canceled" -> ExecutionStatus.CANCELLED;
            case "expired" -> ExecutionStatus.EXPIRED;
            case "rejected", "pending_cancel", "pending_replace" -> ExecutionStatus.REJECTED;
            default -> ExecutionStatus.PENDING;
        };
    }

    /**
     * Parse Alpaca's RFC3339 timestamp format
     */
    private LocalDateTime parseAlpacaTimestamp(String timestamp) {
        try {
            return ZonedDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse Alpaca timestamp: {}", timestamp);
            return LocalDateTime.now();
        }
    }

    /**
     * Fallback method for order submission
     */
    private BrokerOrderResponse submitOrderFallback(BrokerOrderRequest request, Exception e) {
        log.error("Alpaca order submission fallback triggered", e);
        return BrokerOrderResponse.builder()
            .clientOrderId(request.getClientOrderId())
            .status(ExecutionStatus.FAILED)
            .rejectionReason("Broker temporarily unavailable: " + e.getMessage())
            .build();
    }

    /**
     * Fallback method for order status
     */
    private BrokerOrderResponse getOrderStatusFallback(String brokerOrderId, Exception e) {
        log.error("Alpaca order status fallback triggered", e);
        return BrokerOrderResponse.builder()
            .brokerOrderId(brokerOrderId)
            .status(ExecutionStatus.PENDING)
            .rejectionReason("Unable to retrieve status: " + e.getMessage())
            .build();
    }

    /**
     * Fallback method for order cancellation
     */
    private BrokerOrderResponse cancelOrderFallback(String brokerOrderId, Exception e) {
        log.error("Alpaca order cancellation fallback triggered", e);
        return BrokerOrderResponse.builder()
            .brokerOrderId(brokerOrderId)
            .status(ExecutionStatus.FAILED)
            .rejectionReason("Cancellation failed: " + e.getMessage())
            .build();
    }

    /**
     * Fallback method for account info
     */
    private BrokerAccountInfo getAccountInfoFallback(String brokerAccountId, Exception e) {
        log.error("Alpaca account info fallback triggered", e);
        return null;
    }
}
