package com.waqiti.investment.websocket;

import com.waqiti.investment.dto.StockQuoteDto;
import com.waqiti.investment.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for broadcasting real-time market data via WebSocket
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final MarketDataService marketDataService;
    
    // Track subscribed symbols per user
    private final Map<String, Set<String>> userSubscriptions = new ConcurrentHashMap<>();
    
    // Track all subscribed symbols
    private final Set<String> allSubscribedSymbols = ConcurrentHashMap.newKeySet();

    /**
     * Subscribe user to real-time updates for specific symbols
     */
    public void subscribeToSymbols(String userId, List<String> symbols) {
        log.info("User {} subscribing to symbols: {}", userId, symbols);
        
        Set<String> userSymbols = userSubscriptions.computeIfAbsent(userId, k -> new HashSet<>());
        userSymbols.addAll(symbols);
        allSubscribedSymbols.addAll(symbols);
    }

    /**
     * Unsubscribe user from specific symbols
     */
    public void unsubscribeFromSymbols(String userId, List<String> symbols) {
        log.info("User {} unsubscribing from symbols: {}", userId, symbols);
        
        Set<String> userSymbols = userSubscriptions.get(userId);
        if (userSymbols != null) {
            userSymbols.removeAll(symbols);
            if (userSymbols.isEmpty()) {
                userSubscriptions.remove(userId);
            }
        }
        
        // Check if any other users are subscribed to these symbols
        for (String symbol : symbols) {
            boolean stillSubscribed = userSubscriptions.values().stream()
                    .anyMatch(set -> set.contains(symbol));
            if (!stillSubscribed) {
                allSubscribedSymbols.remove(symbol);
            }
        }
    }

    /**
     * Unsubscribe user from all symbols
     */
    public void unsubscribeUser(String userId) {
        log.info("Unsubscribing user {} from all symbols", userId);
        
        Set<String> userSymbols = userSubscriptions.remove(userId);
        if (userSymbols != null) {
            // Check if any other users are subscribed to these symbols
            for (String symbol : userSymbols) {
                boolean stillSubscribed = userSubscriptions.values().stream()
                        .anyMatch(set -> set.contains(symbol));
                if (!stillSubscribed) {
                    allSubscribedSymbols.remove(symbol);
                }
            }
        }
    }

    /**
     * Broadcast real-time market data updates
     */
    @Scheduled(fixedDelay = 5000) // Update every 5 seconds
    public void broadcastMarketData() {
        if (allSubscribedSymbols.isEmpty()) {
            return;
        }

        try {
            // Fetch quotes for all subscribed symbols
            List<String> symbolList = new ArrayList<>(allSubscribedSymbols);
            Map<String, StockQuoteDto> quotes = marketDataService.getBatchStockQuotes(symbolList);

            // Broadcast to all users topic
            messagingTemplate.convertAndSend("/topic/market-data", quotes);

            // Send personalized updates to each user
            userSubscriptions.forEach((userId, symbols) -> {
                Map<String, StockQuoteDto> userQuotes = new HashMap<>();
                for (String symbol : symbols) {
                    StockQuoteDto quote = quotes.get(symbol);
                    if (quote != null) {
                        userQuotes.put(symbol, quote);
                    }
                }
                
                if (!userQuotes.isEmpty()) {
                    messagingTemplate.convertAndSendToUser(
                            userId, 
                            "/queue/market-data", 
                            userQuotes
                    );
                }
            });

        } catch (Exception e) {
            log.error("Error broadcasting market data: {}", e.getMessage());
        }
    }

    /**
     * Broadcast portfolio updates to specific user
     */
    public void broadcastPortfolioUpdate(String userId, Object portfolioUpdate) {
        log.debug("Broadcasting portfolio update to user: {}", userId);
        messagingTemplate.convertAndSendToUser(userId, "/queue/portfolio-updates", portfolioUpdate);
    }

    /**
     * Broadcast order execution updates
     */
    public void broadcastOrderUpdate(String userId, Object orderUpdate) {
        log.debug("Broadcasting order update to user: {}", userId);
        messagingTemplate.convertAndSendToUser(userId, "/queue/order-updates", orderUpdate);
    }

    /**
     * Broadcast market indices updates
     */
    @Scheduled(fixedDelay = 30000) // Update every 30 seconds
    public void broadcastMarketIndices() {
        try {
            Map<String, Object> indices = (Map<String, Object>) marketDataService.getMarketIndices();
            messagingTemplate.convertAndSend("/topic/market-indices", indices);
        } catch (Exception e) {
            log.error("Error broadcasting market indices: {}", e.getMessage());
        }
    }

    /**
     * Broadcast top movers
     */
    @Scheduled(fixedDelay = 60000) // Update every minute
    public void broadcastTopMovers() {
        try {
            Map<String, List<StockQuoteDto>> topMovers = marketDataService.getTopMovers();
            messagingTemplate.convertAndSend("/topic/top-movers", topMovers);
        } catch (Exception e) {
            log.error("Error broadcasting top movers: {}", e.getMessage());
        }
    }
}