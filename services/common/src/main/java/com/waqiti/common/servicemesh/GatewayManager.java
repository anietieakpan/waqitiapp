package com.waqiti.common.servicemesh;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * Gateway Manager for ingress/egress configuration
 * Manages API Gateway configurations for the service mesh
 */
@Slf4j
@Component
@Builder
public class GatewayManager {

    @Value("${gateway.ingress.enabled:true}")
    private boolean ingressEnabled;
    
    @Value("${gateway.egress.enabled:true}")
    private boolean egressEnabled;
    
    @Value("${gateway.ingress.port:80}")
    private int ingressPort;
    
    @Value("${gateway.egress.port:8080}")
    private int egressPort;
    
    @Value("${gateway.hosts:*}")
    private List<String> hosts;
    
    // Gateway registry
    private final Map<String, GatewayConfig> gateways = new ConcurrentHashMap<>();
    private final Map<String, ServerConfig> servers = new ConcurrentHashMap<>();
    
    // Thread pools
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);
    
    // State management
    private volatile ManagerState state = ManagerState.INITIALIZING;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Gateway Manager - Ingress: {}, Egress: {}", ingressEnabled, egressEnabled);
        
        if (ingressEnabled) {
            initializeIngressGateway();
        }
        
        if (egressEnabled) {
            initializeEgressGateway();
        }
        
        state = ManagerState.RUNNING;
        log.info("Gateway Manager initialized successfully");
    }

    /**
     * Configure ingress gateway
     */
    public CompletableFuture<GatewayResult> configureIngressGateway(IngressRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Configuring ingress gateway: {}", request.getName());
            
            try {
                GatewayConfig config = GatewayConfig.builder()
                        .name(request.getName())
                        .type(GatewayType.INGRESS)
                        .hosts(request.getHosts())
                        .port(request.getPort() != 0 ? request.getPort() : ingressPort)
                        .protocol(request.getProtocol())
                        .tlsConfig(request.getTlsConfig())
                        .createdAt(LocalDateTime.now())
                        .build();
                
                gateways.put(request.getName(), config);
                
                return GatewayResult.builder()
                        .success(true)
                        .gatewayName(request.getName())
                        .type(GatewayType.INGRESS)
                        .appliedAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to configure ingress gateway", e);
                return GatewayResult.builder()
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Configure egress gateway
     */
    public CompletableFuture<GatewayResult> configureEgressGateway(EgressRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Configuring egress gateway: {}", request.getName());
            
            try {
                GatewayConfig config = GatewayConfig.builder()
                        .name(request.getName())
                        .type(GatewayType.EGRESS)
                        .hosts(request.getHosts())
                        .port(request.getPort() != 0 ? request.getPort() : egressPort)
                        .protocol(request.getProtocol())
                        .destinationRules(request.getDestinationRules())
                        .createdAt(LocalDateTime.now())
                        .build();
                
                gateways.put(request.getName(), config);
                
                return GatewayResult.builder()
                        .success(true)
                        .gatewayName(request.getName())
                        .type(GatewayType.EGRESS)
                        .appliedAt(LocalDateTime.now())
                        .build();
                
            } catch (Exception e) {
                log.error("Failed to configure egress gateway", e);
                return GatewayResult.builder()
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build();
            }
        });
    }

    private void initializeIngressGateway() {
        log.info("Initializing default ingress gateway on port: {}", ingressPort);
        
        ServerConfig server = ServerConfig.builder()
                .port(ingressPort)
                .protocol("HTTP")
                .hosts(hosts != null ? hosts : Arrays.asList("*"))
                .build();
        
        servers.put("default-ingress", server);
    }

    private void initializeEgressGateway() {
        log.info("Initializing default egress gateway on port: {}", egressPort);
        
        ServerConfig server = ServerConfig.builder()
                .port(egressPort)
                .protocol("HTTP")
                .hosts(Arrays.asList("*"))
                .build();
        
        servers.put("default-egress", server);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Gateway Manager");
        state = ManagerState.SHUTTING_DOWN;
        
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        state = ManagerState.TERMINATED;
        log.info("Gateway Manager shutdown complete");
    }

    // Inner classes

    public enum ManagerState {
        INITIALIZING, RUNNING, SHUTTING_DOWN, TERMINATED
    }

    public enum GatewayType {
        INGRESS, EGRESS
    }

    @Data
    @Builder
    public static class GatewayConfig {
        private String name;
        private GatewayType type;
        private List<String> hosts;
        private int port;
        private String protocol;
        private TlsConfig tlsConfig;
        private Map<String, String> destinationRules;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    public static class ServerConfig {
        private int port;
        private String protocol;
        private List<String> hosts;
        private TlsConfig tls;
    }

    @Data
    @Builder
    public static class TlsConfig {
        private boolean enabled;
        private String mode;
        private String serverCertificate;
        private String privateKey;
        private String caCertificates;
        private int minProtocolVersion;
        private int maxProtocolVersion;
    }

    @Data
    @Builder
    public static class IngressRequest {
        private String name;
        private List<String> hosts;
        private int port;
        private String protocol;
        private TlsConfig tlsConfig;
    }

    @Data
    @Builder
    public static class EgressRequest {
        private String name;
        private List<String> hosts;
        private int port;
        private String protocol;
        private Map<String, String> destinationRules;
    }

    @Data
    @Builder
    public static class GatewayResult {
        private boolean success;
        private String gatewayName;
        private GatewayType type;
        private LocalDateTime appliedAt;
        private String errorMessage;
    }
}