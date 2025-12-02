package com.waqiti.crypto.health;

import com.waqiti.crypto.config.LightningConfiguration;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator for Lightning Network Daemon (LND) connectivity and status
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "waqiti.lightning.enable-health-checks", havingValue = "true", matchIfMissing = true)
public class LightningHealthIndicator implements HealthIndicator {

    private final LightningConfiguration lightningConfig;
    private final ManagedChannel lightningGrpcChannel;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public Health health() {
        try {
            Health.Builder builder = new Health.Builder();
            Map<String, Object> details = new HashMap<>();
            
            // Check LND connectivity and status
            LightningNodeInfo nodeInfo = checkLightningNode();
            details.put("lightning", nodeInfo.toMap());
            
            // Check certificate and macaroon files
            CertificateInfo certInfo = checkCertificates();
            details.put("certificates", certInfo.toMap());
            
            // Determine overall health
            if (nodeInfo.isConnected() && nodeInfo.isSynced()) {
                builder.up();
                details.put("status", "Lightning node is healthy and synced");
            } else if (nodeInfo.isConnected()) {
                builder.status("SYNCING");
                details.put("status", "Lightning node is connected but not fully synced");
            } else {
                builder.down();
                details.put("status", "Lightning node is not accessible");
            }
            
            // Add warnings for certificate issues
            if (!certInfo.isTlsCertValid()) {
                builder.status("DEGRADED");
                details.put("warning", "TLS certificate issues detected");
            }
            
            return builder.withDetails(details).build();
            
        } catch (Exception e) {
            log.error("Error checking Lightning health", e);
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("status", "Health check failed")
                .build();
        }
    }

    private LightningNodeInfo checkLightningNode() {
        LightningNodeInfo info = new LightningNodeInfo();
        
        try {
            // Try REST API first (more reliable than gRPC for health checks)
            if (checkRestApi(info)) {
                info.setConnected(true);
            } else {
                // Fallback to gRPC check
                checkGrpcConnection(info);
            }
            
        } catch (Exception e) {
            log.debug("Lightning node connection failed: {}", e.getMessage());
            info.setConnected(false);
            info.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        
        return info;
    }

    private boolean checkRestApi(LightningNodeInfo info) {
        try {
            String restUrl = lightningConfig.getRestUrl() + "/v1/getinfo";
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(restUrl))
                .timeout(Duration.ofSeconds(10))
                .GET();
            
            // Add macaroon if available
            String macaroonHex = lightningConfig.getMacaroonHex();
            if (macaroonHex != null) {
                requestBuilder.header("Grpc-Metadata-macaroon", macaroonHex);
            }
            
            HttpRequest request = requestBuilder.build();
            
            CompletableFuture<HttpResponse<String>> futureResponse = httpClient.sendAsync(
                request, HttpResponse.BodyHandlers.ofString());
            
            HttpResponse<String> response = futureResponse.get(10, TimeUnit.SECONDS);
            
            if (response.statusCode() == 200) {
                parseGetInfoResponse(response.body(), info);
                return true;
            } else {
                log.warn("Lightning REST API returned status: {}", response.statusCode());
                info.setError("HTTP " + response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.debug("Lightning REST API check failed: {}", e.getMessage());
            info.setError("REST API: " + e.getMessage());
            return false;
        }
    }

    private void checkGrpcConnection(LightningNodeInfo info) {
        try {
            if (lightningGrpcChannel == null) {
                info.setConnected(false);
                info.setError("gRPC channel not initialized");
                return;
            }
            
            // Check channel state
            if (lightningGrpcChannel.isShutdown()) {
                info.setConnected(false);
                info.setError("gRPC channel is shutdown");
                return;
            }
            
            if (lightningGrpcChannel.isTerminated()) {
                info.setConnected(false);
                info.setError("gRPC channel is terminated");
                return;
            }
            
            // Simple connectivity check
            var state = lightningGrpcChannel.getState(false);
            info.setConnected(state.name().equals("READY") || state.name().equals("IDLE"));
            info.setGrpcState(state.name());
            
            if (!info.isConnected()) {
                info.setError("gRPC state: " + state.name());
            }
            
        } catch (StatusRuntimeException e) {
            info.setConnected(false);
            info.setError("gRPC error: " + e.getStatus().getCode());
        } catch (Exception e) {
            info.setConnected(false);
            info.setError("gRPC check failed: " + e.getMessage());
        }
    }

    private void parseGetInfoResponse(String jsonResponse, LightningNodeInfo info) {
        try {
            // Simple JSON parsing for health check purposes
            info.setAlias(extractStringValue(jsonResponse, "alias"));
            info.setIdentityPubkey(extractStringValue(jsonResponse, "identity_pubkey"));
            info.setNumActiveChannels(extractIntValue(jsonResponse, "num_active_channels"));
            info.setNumPeers(extractIntValue(jsonResponse, "num_peers"));
            info.setBlockHeight(extractIntValue(jsonResponse, "block_height"));
            info.setSynced(extractBooleanValue(jsonResponse, "synced_to_chain"));
            info.setVersion(extractStringValue(jsonResponse, "version"));
            
            // Check if node is properly synced and operational
            if (info.isSynced() && info.getBlockHeight() > 0) {
                info.setOperational(true);
            }
            
        } catch (Exception e) {
            log.warn("Error parsing Lightning node info", e);
            info.setError("Failed to parse node info: " + e.getMessage());
        }
    }

    private CertificateInfo checkCertificates() {
        CertificateInfo certInfo = new CertificateInfo();
        
        // Check TLS certificate
        try {
            Path tlsCertPath = Path.of(lightningConfig.getTlsCertPath());
            if (Files.exists(tlsCertPath)) {
                certInfo.setTlsCertExists(true);
                certInfo.setTlsCertValid(isValidCertificate(tlsCertPath));
                certInfo.setTlsCertSize(Files.size(tlsCertPath));
            } else {
                certInfo.setTlsCertExists(false);
                certInfo.setTlsCertValid(false);
            }
        } catch (Exception e) {
            log.debug("Error checking TLS certificate", e);
            certInfo.setTlsCertValid(false);
            certInfo.setTlsCertError(e.getMessage());
        }
        
        // Check macaroon file
        try {
            Path macaroonPath = Path.of(lightningConfig.getMacaroonPath());
            if (Files.exists(macaroonPath)) {
                certInfo.setMacaroonExists(true);
                certInfo.setMacaroonSize(Files.size(macaroonPath));
                certInfo.setMacaroonValid(Files.size(macaroonPath) > 0);
            } else {
                certInfo.setMacaroonExists(false);
                certInfo.setMacaroonValid(false);
            }
        } catch (Exception e) {
            log.debug("Error checking macaroon file", e);
            certInfo.setMacaroonValid(false);
            certInfo.setMacaroonError(e.getMessage());
        }
        
        return certInfo;
    }

    private boolean isValidCertificate(Path certPath) {
        try {
            byte[] certBytes = Files.readAllBytes(certPath);
            String certContent = new String(certBytes);
            
            // Basic validation - check if it looks like a valid certificate
            return certContent.contains("BEGIN CERTIFICATE") && 
                   certContent.contains("END CERTIFICATE") &&
                   certContent.length() > 500; // Minimum reasonable size
                   
        } catch (IOException e) {
            log.debug("Error reading certificate file", e);
            return false;
        }
    }

    // Utility methods for JSON parsing (simplified)
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

    private int extractIntValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(\\d+)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            log.debug("Failed to extract int value for key: {}", key);
        }
        return 0;
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

    /**
     * Lightning node information holder
     */
    public static class LightningNodeInfo {
        private boolean connected = false;
        private boolean synced = false;
        private boolean operational = false;
        private String alias = "";
        private String identityPubkey = "";
        private String version = "";
        private int numActiveChannels = 0;
        private int numPeers = 0;
        private int blockHeight = 0;
        private String grpcState = "";
        private String error = null;

        // Getters and setters
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        
        public boolean isSynced() { return synced; }
        public void setSynced(boolean synced) { this.synced = synced; }
        
        public boolean isOperational() { return operational; }
        public void setOperational(boolean operational) { this.operational = operational; }
        
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        
        public String getIdentityPubkey() { return identityPubkey; }
        public void setIdentityPubkey(String identityPubkey) { this.identityPubkey = identityPubkey; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public int getNumActiveChannels() { return numActiveChannels; }
        public void setNumActiveChannels(int numActiveChannels) { this.numActiveChannels = numActiveChannels; }
        
        public int getNumPeers() { return numPeers; }
        public void setNumPeers(int numPeers) { this.numPeers = numPeers; }
        
        public int getBlockHeight() { return blockHeight; }
        public void setBlockHeight(int blockHeight) { this.blockHeight = blockHeight; }
        
        public String getGrpcState() { return grpcState; }
        public void setGrpcState(String grpcState) { this.grpcState = grpcState; }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("connected", connected);
            map.put("synced", synced);
            map.put("operational", operational);
            map.put("alias", alias);
            map.put("version", version);
            map.put("numActiveChannels", numActiveChannels);
            map.put("numPeers", numPeers);
            map.put("blockHeight", blockHeight);
            if (!grpcState.isEmpty()) {
                map.put("grpcState", grpcState);
            }
            if (error != null) {
                map.put("error", error);
            }
            return map;
        }
    }

    /**
     * Certificate information holder
     */
    public static class CertificateInfo {
        private boolean tlsCertExists = false;
        private boolean tlsCertValid = false;
        private long tlsCertSize = 0;
        private String tlsCertError = null;
        
        private boolean macaroonExists = false;
        private boolean macaroonValid = false;
        private long macaroonSize = 0;
        private String macaroonError = null;

        // Getters and setters
        public boolean isTlsCertExists() { return tlsCertExists; }
        public void setTlsCertExists(boolean tlsCertExists) { this.tlsCertExists = tlsCertExists; }
        
        public boolean isTlsCertValid() { return tlsCertValid; }
        public void setTlsCertValid(boolean tlsCertValid) { this.tlsCertValid = tlsCertValid; }
        
        public long getTlsCertSize() { return tlsCertSize; }
        public void setTlsCertSize(long tlsCertSize) { this.tlsCertSize = tlsCertSize; }
        
        public String getTlsCertError() { return tlsCertError; }
        public void setTlsCertError(String tlsCertError) { this.tlsCertError = tlsCertError; }
        
        public boolean isMacaroonExists() { return macaroonExists; }
        public void setMacaroonExists(boolean macaroonExists) { this.macaroonExists = macaroonExists; }
        
        public boolean isMacaroonValid() { return macaroonValid; }
        public void setMacaroonValid(boolean macaroonValid) { this.macaroonValid = macaroonValid; }
        
        public long getMacaroonSize() { return macaroonSize; }
        public void setMacaroonSize(long macaroonSize) { this.macaroonSize = macaroonSize; }
        
        public String getMacaroonError() { return macaroonError; }
        public void setMacaroonError(String macaroonError) { this.macaroonError = macaroonError; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("tlsCertExists", tlsCertExists);
            map.put("tlsCertValid", tlsCertValid);
            if (tlsCertSize > 0) {
                map.put("tlsCertSize", tlsCertSize);
            }
            if (tlsCertError != null) {
                map.put("tlsCertError", tlsCertError);
            }
            
            map.put("macaroonExists", macaroonExists);
            map.put("macaroonValid", macaroonValid);
            if (macaroonSize > 0) {
                map.put("macaroonSize", macaroonSize);
            }
            if (macaroonError != null) {
                map.put("macaroonError", macaroonError);
            }
            
            return map;
        }
    }
}