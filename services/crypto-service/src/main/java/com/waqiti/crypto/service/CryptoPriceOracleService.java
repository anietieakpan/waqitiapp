package com.waqiti.crypto.service;

import com.waqiti.crypto.domain.CryptoCurrency;
import com.waqiti.crypto.domain.PriceData;
import com.waqiti.crypto.dto.PriceHistoryRequest;
import com.waqiti.crypto.dto.PriceHistoryResponse;
import com.waqiti.crypto.exception.PriceUnavailableException;
import com.waqiti.common.config.VaultTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoPriceOracleService {
    
    @org.springframework.context.annotation.Lazy
    private final CryptoPriceOracleService self;

    private final WebClient.Builder webClientBuilder;
    private final VaultTemplate vaultTemplate;

    @Value("${crypto.price.cache.ttl-seconds:30}")
    private int cacheTtlSeconds;

    @Value("${crypto.price.fallback.enabled:true}")
    private boolean fallbackEnabled;

    @Value("${crypto.price.alerts.enabled:true}")
    private boolean alertsEnabled;

    private WebClient chainlinkClient;
    private WebClient coinGeckoClient;
    private WebClient coinbaseClient;
    private WebClient binanceClient;

    private final Map<CryptoCurrency, PriceData> priceCache = new ConcurrentHashMap<>();
    private final Map<CryptoCurrency, List<PriceData>> historicalPrices = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void initializePriceOracles() {
        try {
            var secrets = vaultTemplate.read("secret/crypto-price-oracles").getData();
            
            this.chainlinkClient = webClientBuilder
                .baseUrl("https://api.chain.link")
                .defaultHeader("X-API-Key", secrets.get("chainlink-api-key").toString())
                .build();

            this.coinGeckoClient = webClientBuilder
                .baseUrl("https://api.coingecko.com/api/v3")
                .defaultHeader("X-API-Key", secrets.get("coingecko-api-key").toString())
                .build();

            this.coinbaseClient = webClientBuilder
                .baseUrl("https://api.coinbase.com/v2")
                .build();

            this.binanceClient = webClientBuilder
                .baseUrl("https://api.binance.com/api/v3")
                .build();

            log.info("Crypto price oracle services initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize price oracles", e);
            throw new IllegalStateException("Cannot start without price oracle configuration", e);
        }
    }

    @Cacheable(value = "crypto-prices", key = "#currency")
    public Mono<BigDecimal> getCurrentPrice(CryptoCurrency currency) {
        log.debug("Fetching current price for {}", currency);

        return getChainlinkPrice(currency)
            .onErrorResume(error -> {
                log.warn("Chainlink price fetch failed for {}, falling back to CoinGecko: {}", 
                    currency, error.getMessage());
                return getCoinGeckoPrice(currency);
            })
            .onErrorResume(error -> {
                log.warn("CoinGecko price fetch failed for {}, falling back to Coinbase: {}", 
                    currency, error.getMessage());
                return getCoinbasePrice(currency);
            })
            .onErrorResume(error -> {
                log.warn("Coinbase price fetch failed for {}, falling back to Binance: {}", 
                    currency, error.getMessage());
                return getBinancePrice(currency);
            })
            .onErrorResume(error -> {
                log.error("All price sources failed for {}, using cached price", currency);
                return getCachedPrice(currency);
            })
            .doOnSuccess(price -> {
                if (price != null) {
                    updatePriceCache(currency, price);
                }
            })
            .doOnError(error -> {
                log.error("Failed to get price for {}", currency, error);
            });
    }

    public Mono<Map<CryptoCurrency, BigDecimal>> getBatchPrices(Set<CryptoCurrency> currencies) {
        log.debug("Fetching batch prices for {} currencies", currencies.size());

        List<Mono<Map.Entry<CryptoCurrency, BigDecimal>>> priceMonos = currencies.stream()
            .map(currency -> self.getCurrentPrice(currency)
                .map(price -> Map.entry(currency, price))
                .onErrorReturn(Map.entry(currency, BigDecimal.ZERO)))
            .toList();

        return Flux.merge(priceMonos)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .doOnSuccess(prices -> log.debug("Retrieved {} prices successfully", prices.size()));
    }

    public Mono<PriceHistoryResponse> getPriceHistory(PriceHistoryRequest request) {
        log.debug("Fetching price history for {} from {} to {}", 
            request.getCurrency(), request.getFromDate(), request.getToDate());

        return coinGeckoClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/coins/{id}/market_chart")
                .queryParam("vs_currency", "usd")
                .queryParam("from", request.getFromDate().toEpochSecond(java.time.ZoneOffset.UTC))
                .queryParam("to", request.getToDate().toEpochSecond(java.time.ZoneOffset.UTC))
                .build(getCoinGeckoId(request.getCurrency())))
            .retrieve()
            .bodyToMono(Map.class)
            .map(this::parseHistoricalData)
            .map(priceHistory -> PriceHistoryResponse.builder()
                .currency(request.getCurrency())
                .prices(priceHistory)
                .fromDate(request.getFromDate())
                .toDate(request.getToDate())
                .dataPoints(priceHistory.size())
                .build())
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
            .onErrorMap(error -> new PriceUnavailableException("Failed to fetch price history", error));
    }

    @Scheduled(fixedRate = 30000)
    public void refreshPriceCache() {
        log.debug("Refreshing price cache for all supported currencies");

        Set<CryptoCurrency> supportedCurrencies = Set.of(
            CryptoCurrency.BITCOIN, CryptoCurrency.ETHEREUM, CryptoCurrency.LITECOIN,
            CryptoCurrency.USDC, CryptoCurrency.USDT, CryptoCurrency.BNB,
            CryptoCurrency.ADA, CryptoCurrency.DOT, CryptoCurrency.LINK
        );

        self.getBatchPrices(supportedCurrencies)
            .subscribe(
                prices -> {
                    log.debug("Price cache refreshed with {} prices", prices.size());
                    detectPriceAlerts(prices);
                },
                error -> log.error("Failed to refresh price cache", error)
            );
    }

    private Mono<BigDecimal> getChainlinkPrice(CryptoCurrency currency) {
        String feedAddress = getChainlinkFeedAddress(currency);
        if (feedAddress == null) {
            return Mono.error(new PriceUnavailableException("No Chainlink feed for " + currency));
        }

        return chainlinkClient.get()
            .uri("/feeds/{address}/price", feedAddress)
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                String priceStr = data.get("price").toString();
                return new BigDecimal(priceStr).movePointLeft(8);
            })
            .timeout(Duration.ofSeconds(5))
            .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)));
    }

    private Mono<BigDecimal> getCoinGeckoPrice(CryptoCurrency currency) {
        String coinId = getCoinGeckoId(currency);

        return coinGeckoClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/simple/price")
                .queryParam("ids", coinId)
                .queryParam("vs_currencies", "usd")
                .queryParam("include_24hr_change", "true")
                .build())
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                Map<String, Object> coinData = (Map<String, Object>) response.get(coinId);
                return new BigDecimal(coinData.get("usd").toString());
            })
            .timeout(Duration.ofSeconds(5))
            .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)));
    }

    private Mono<BigDecimal> getCoinbasePrice(CryptoCurrency currency) {
        String pair = currency.getSymbol() + "-USD";

        return coinbaseClient.get()
            .uri("/exchange-rates?currency={currency}", currency.getSymbol())
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                Map<String, Object> rates = (Map<String, Object>) data.get("rates");
                return new BigDecimal(rates.get("USD").toString());
            })
            .timeout(Duration.ofSeconds(5))
            .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)));
    }

    private Mono<BigDecimal> getBinancePrice(CryptoCurrency currency) {
        String symbol = currency.getSymbol() + "USDT";

        return binanceClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/ticker/price")
                .queryParam("symbol", symbol)
                .build())
            .retrieve()
            .bodyToMono(Map.class)
            .map(response -> new BigDecimal(response.get("price").toString()))
            .timeout(Duration.ofSeconds(5))
            .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1)));
    }

    private Mono<BigDecimal> getCachedPrice(CryptoCurrency currency) {
        PriceData cached = priceCache.get(currency);
        if (cached != null && !cached.isExpired(Duration.ofMinutes(5))) {
            log.info("Using cached price for {}: ${}", currency, cached.getPrice());
            return Mono.just(cached.getPrice());
        }

        return Mono.error(new PriceUnavailableException("No cached price available for " + currency));
    }

    private void updatePriceCache(CryptoCurrency currency, BigDecimal price) {
        PriceData priceData = PriceData.builder()
            .currency(currency)
            .price(price)
            .timestamp(LocalDateTime.now())
            .source("ORACLE")
            .build();

        priceCache.put(currency, priceData);

        historicalPrices.computeIfAbsent(currency, k -> new ArrayList<>()).add(priceData);

        if (historicalPrices.get(currency).size() > 1440) {
            historicalPrices.get(currency).remove(0);
        }
    }

    private void detectPriceAlerts(Map<CryptoCurrency, BigDecimal> currentPrices) {
        if (!alertsEnabled) return;

        currentPrices.forEach((currency, price) -> {
            PriceData lastPrice = priceCache.get(currency);
            if (lastPrice != null) {
                BigDecimal changePercent = price.subtract(lastPrice.getPrice())
                    .divide(lastPrice.getPrice(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

                if (changePercent.abs().compareTo(BigDecimal.valueOf(5.0)) > 0) {
                    log.warn("PRICE ALERT: {} moved {}% to ${}", 
                        currency, changePercent, price);
                }
            }
        });
    }

    private List<PriceData> parseHistoricalData(Map<String, Object> response) {
        List<List<Object>> prices = (List<List<Object>>) response.get("prices");
        
        return prices.stream()
            .map(pricePoint -> PriceData.builder()
                .price(new BigDecimal(pricePoint.get(1).toString()))
                .timestamp(LocalDateTime.ofEpochSecond(
                    ((Number) pricePoint.get(0)).longValue() / 1000, 0, java.time.ZoneOffset.UTC))
                .source("COINGECKO")
                .build())
            .toList();
    }

    private String getChainlinkFeedAddress(CryptoCurrency currency) {
        return switch (currency) {
            case BITCOIN -> "0xF4030086522a5bEEa4988F8cA5B36dbC97BeE88c";
            case ETHEREUM -> "0x5f4eC3Df9cbd43714FE2740f5E3616155c5b8419";
            case LITECOIN -> "0x6AF09DF7563C363B5763b9102712EbeD3b9e859B";
            case LINK -> "0x2c1d072e956AFFC0D435Cb7AC38EF18d24d9127c";
            case BNB -> "0x14e613AC84a31f709eadbdF89C6CC390fDc9540A";
            case ADA -> "0xAE48c91dF1fE419994FFDa27da09D5aC69c30f55";
            default -> null;
        };
    }

    private String getCoinGeckoId(CryptoCurrency currency) {
        return switch (currency) {
            case BITCOIN -> "bitcoin";
            case ETHEREUM -> "ethereum";
            case LITECOIN -> "litecoin";
            case USDC -> "usd-coin";
            case USDT -> "tether";
            case BNB -> "binancecoin";
            case ADA -> "cardano";
            case DOT -> "polkadot";
            case LINK -> "chainlink";
            default -> currency.name().toLowerCase();
        };
    }
}