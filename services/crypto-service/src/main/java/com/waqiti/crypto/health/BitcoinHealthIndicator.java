package com.waqiti.crypto.health;

import com.waqiti.crypto.config.BitcoinConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Bitcoin Core node connectivity and status
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "waqiti.bitcoin.enable-lightning", havingValue = "true", matchIfMissing = true)
public class BitcoinHealthIndicator implements HealthIndicator {

    private final BitcoinConfiguration bitcoinConfig;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public Health health() {
        try {
            Health.Builder builder = new Health.Builder();
            Map<String, Object> details = new HashMap<>();
            
            // Check Bitcoin Core connectivity
            BitcoinNodeInfo nodeInfo = checkBitcoinNode();
            details.put("bitcoin", nodeInfo.toMap());
            
            // Determine overall health
            if (nodeInfo.isConnected()) {
                if (nodeInfo.isSynced() && nodeInfo.getBlockHeight() > 0) {
                    builder.up();
                    details.put("status", "Bitcoin node is healthy and synced");
                } else if (nodeInfo.getBlockHeight() > 0) {
                    builder.status("SYNCING");
                    details.put("status", "Bitcoin node is connected but syncing");
                } else {
                    builder.down();
                    details.put("status", "Bitcoin node is connected but has no blocks");
                }
            } else {
                builder.down();
                details.put("status", "Bitcoin node is not accessible");
            }
            
            return builder.withDetails(details).build();
            
        } catch (Exception e) {
            log.error("Error checking Bitcoin health", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Health check failed")
                .build();
        }
    }

    private BitcoinNodeInfo checkBitcoinNode() {
        BitcoinNodeInfo info = new BitcoinNodeInfo();
        
        try {
            // Create basic auth header
            String auth = bitcoinConfig.getRpcUser() + ":" + bitcoinConfig.getRpcPassword();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            
            // Prepare blockchain info request
            String jsonRpcRequest = """
                {
                    "jsonrpc": "1.0",
                    "id": "health-check",
                    "method": "getblockchaininfo",
                    "params": []
                }
                """;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bitcoinConfig.getRpcUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + encodedAuth)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonRpcRequest))
                .build();
            
            CompletableFuture<HttpResponse<String>> futureResponse = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());
            
            HttpResponse<String> response = futureResponse.get(15, TimeUnit.SECONDS);
            
            if (response.statusCode() == 200) {
                info.setConnected(true);
                parseBlockchainInfo(response.body(), info);
            } else {
                log.warn("Bitcoin RPC returned status: {}", response.statusCode());
                info.setConnected(false);
                info.setError("HTTP " + response.statusCode());
            }
            
        } catch (Exception e) {
            log.debug("Bitcoin node connection failed: {}", e.getMessage());
            info.setConnected(false);
            info.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        
        return info;
    }

    private void parseBlockchainInfo(String jsonResponse, BitcoinNodeInfo info) {
        try {
            // Simple JSON parsing (in production, use Jackson or Gson)
            if (jsonResponse.contains("\"result\"")) {
                // Extract key values using basic string operations
                info.setBlockHeight(extractLongValue(jsonResponse, "blocks"));
                info.setHeaderHeight(extractLongValue(jsonResponse, "headers"));
                info.setSynced(extractBooleanValue(jsonResponse, "initialblockdownload") == false);
                info.setChain(extractStringValue(jsonResponse, "chain"));
                info.setDifficulty(extractDoubleValue(jsonResponse, "difficulty"));
                info.setVerificationProgress(extractDoubleValue(jsonResponse, "verificationprogress"));
                
                // Calculate sync percentage
                if (info.getHeaderHeight() > 0) {
                    double syncPercentage = (double) info.getBlockHeight() / info.getHeaderHeight() * 100;
                    info.setSyncPercentage(Math.min(100.0, syncPercentage));
                }
            }
        } catch (Exception e) {
            log.warn("Error parsing Bitcoin blockchain info", e);
            info.setError("Failed to parse response: " + e.getMessage());
        }
    }

    // Utility methods for simple JSON parsing
    private long extractLongValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        } catch (Exception e) {
            log.debug("Failed to extract long value for key: {}", key);
        }
        return 0;
    }

    private double extractDoubleValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*([\\d.]+)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Exception e) {
            log.debug("Failed to extract double value for key: {}", key);
        }
        return 0.0;
    }

    private boolean extractBooleanValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Boolean.parseBoolean(m.group(1));
            }
        } catch (Exception e) {
            log.debug("Failed to extract boolean value for key: {}", key);
        }
        return false;
    }

    private String extractStringValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            log.debug("Failed to extract string value for key: {}", key);
        }
        return "";
    }

    /**
     * Bitcoin node information holder
     */
    public static class BitcoinNodeInfo {
        private boolean connected = false;
        private boolean synced = false;
        private long blockHeight = 0;
        private long headerHeight = 0;
        private String chain = "";
        private double difficulty = 0.0;
        private double verificationProgress = 0.0;
        private double syncPercentage = 0.0;
        private String error = null;

        // Getters and setters
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        
        public boolean isSynced() { return synced; }
        public void setSynced(boolean synced) { this.synced = synced; }
        
        public long getBlockHeight() { return blockHeight; }
        public void setBlockHeight(long blockHeight) { this.blockHeight = blockHeight; }
        
        public long getHeaderHeight() { return headerHeight; }
        public void setHeaderHeight(long headerHeight) { this.headerHeight = headerHeight; }
        
        public String getChain() { return chain; }
        public void setChain(String chain) { this.chain = chain; }
        
        public double getDifficulty() { return difficulty; }
        public void setDifficulty(double difficulty) { this.difficulty = difficulty; }
        
        public double getVerificationProgress() { return verificationProgress; }
        public void setVerificationProgress(double verificationProgress) { this.verificationProgress = verificationProgress; }
        
        public double getSyncPercentage() { return syncPercentage; }
        public void setSyncPercentage(double syncPercentage) { this.syncPercentage = syncPercentage; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("connected", connected);
            map.put("synced", synced);
            map.put("blockHeight", blockHeight);
            map.put("headerHeight", headerHeight);
            map.put("chain", chain);
            map.put("difficulty", difficulty);
            map.put("verificationProgress", verificationProgress);
            map.put("syncPercentage", String.format("%.2f%%", syncPercentage));
            if (error != null) {
                map.put("error", error);
            }
            return map;
        }
    }
}