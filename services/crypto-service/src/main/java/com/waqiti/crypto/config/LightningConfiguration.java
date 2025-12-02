package com.waqiti.crypto.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

/**
 * Lightning Network Daemon (LND) configuration
 */
@Configuration
@ConfigurationProperties(prefix = "waqiti.lightning")
@Data
@Validated
@Slf4j
public class LightningConfiguration {

    @NotBlank
    private String nodeHost = "lnd";
    
    @Positive
    private int grpcPort = 10009;
    
    @Positive
    private int restPort = 8080;
    
    private String tlsCertPath = "/root/.lnd/tls.cert";
    
    private String macaroonPath = "/root/.lnd/data/chain/bitcoin/testnet/admin.macaroon";
    
    @Positive
    private long connectionTimeoutSeconds = 30;
    
    @Positive
    private long callTimeoutSeconds = 60;
    
    @Positive
    private int maxInboundMessageSize = 50 * 1024 * 1024; // 50MB
    
    @Positive
    private int maxInboundMetadataSize = 8192;
    
    // Node settings
    private String nodeAlias = "Waqiti-Lightning-Node";
    
    private String nodeColor = "#3399FF";
    
    // Channel settings
    @Positive
    private long minChannelSize = 100000; // 100k sats
    
    @Positive
    private long maxChannelSize = 16777215; // Max channel size in sats
    
    private Boolean autoAcceptChannels = false;
    
    // Fee settings
    @Positive
    private long baseFee = 1000; // Base fee in millisats
    
    @Positive
    private long feeRate = 1; // Fee rate per million
    
    @Positive
    private int timeLockDelta = 144; // ~24 hours
    
    // Payment settings
    @Positive
    private long maxPaymentSat = 1000000; // 1M sats max payment
    
    @Positive
    private int maxPaymentAttempts = 3;
    
    @Positive
    private long paymentTimeoutSeconds = 300; // 5 minutes
    
    // Routing settings
    @Positive
    private int maxRoutingHops = 20;
    
    private Double routingBudget = 0.01; // 1% of payment amount for routing fees
    
    // Security settings
    private Boolean requireMacaroon = true;
    
    private Boolean requireTls = true;
    
    // Monitoring settings
    private Boolean enableMetrics = true;
    
    private Boolean enableHealthChecks = true;
    
    @Positive
    private int healthCheckIntervalSeconds = 30;
    
    private ManagedChannel grpcChannel;

    @PostConstruct
    public void initialize() {
        log.info("Initializing Lightning Network configuration for node: {}:{}", nodeHost, grpcPort);
        
        // Validate paths
        validatePaths();
        
        // Initialize gRPC channel
        try {
            this.grpcChannel = createGrpcChannel();
            log.info("Lightning gRPC channel initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Lightning gRPC channel", e);
            throw new RuntimeException("Lightning configuration failed", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (grpcChannel != null && !grpcChannel.isShutdown()) {
            log.info("Shutting down Lightning gRPC channel");
            grpcChannel.shutdown();
            try {
                if (!grpcChannel.awaitTermination(5, TimeUnit.SECONDS)) {
                    grpcChannel.shutdownNow();
                }
            } catch (InterruptedException e) {
                grpcChannel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Bean
    public ManagedChannel lightningGrpcChannel() {
        return grpcChannel;
    }

    private ManagedChannel createGrpcChannel() throws Exception {
        NettyChannelBuilder channelBuilder = NettyChannelBuilder
            .forAddress(nodeHost, grpcPort)
            .maxInboundMessageSize(maxInboundMessageSize)
            .maxInboundMetadataSize(maxInboundMetadataSize)
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(5, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .idleTimeout(connectionTimeoutSeconds, TimeUnit.SECONDS);

        if (requireTls && Files.exists(new File(tlsCertPath).toPath())) {
            log.info("Configuring TLS for Lightning connection");
            SslContext sslContext = GrpcSslContexts.forClient()
                .trustManager(new File(tlsCertPath))
                .build();
            channelBuilder.sslContext(sslContext);
        } else {
            log.warn("Lightning connection configured without TLS - not recommended for production");
            channelBuilder.usePlaintext();
        }

        return channelBuilder.build();
    }

    private void validatePaths() {
        if (requireTls) {
            File tlsCert = new File(tlsCertPath);
            if (!tlsCert.exists()) {
                log.warn("TLS certificate not found at: {}", tlsCertPath);
            }
        }

        if (requireMacaroon) {
            File macaroon = new File(macaroonPath);
            if (!macaroon.exists()) {
                log.warn("Admin macaroon not found at: {}", macaroonPath);
            }
        }
    }

    /**
     * Get the REST API URL for LND
     */
    public String getRestUrl() {
        String protocol = requireTls ? "https" : "http";
        return String.format("%s://%s:%d", protocol, nodeHost, restPort);
    }

    /**
     * Get macaroon as hex string
     */
    public String getMacaroonHex() {
        try {
            if (!requireMacaroon) {
                log.debug("Macaroon not required for Lightning configuration");
                return ""; // Return empty string instead of null
            }
            
            File macaroonFile = new File(macaroonPath);
            if (!macaroonFile.exists()) {
                log.error("CRITICAL: Macaroon file not found: {} - Lightning payments will fail", macaroonPath);
                throw new IllegalStateException("Lightning macaroon file not found: " + macaroonPath);
            }
            
            byte[] macaroonBytes = Files.readAllBytes(macaroonFile.toPath());
            return bytesToHex(macaroonBytes);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to read Lightning macaroon - payments will fail", e);
            throw new RuntimeException("Failed to read Lightning macaroon: " + e.getMessage(), e);
        }
    }

    /**
     * Calculate max routing fee for a payment
     */
    public long calculateMaxRoutingFee(long paymentAmountSat) {
        return Math.max(1000, (long) (paymentAmountSat * routingBudget)); // Min 1 sat
    }

    /**
     * Check if channel size is within limits
     */
    public boolean isValidChannelSize(long channelSizeSat) {
        return channelSizeSat >= minChannelSize && channelSizeSat <= maxChannelSize;
    }

    /**
     * Check if payment amount is within limits
     */
    public boolean isValidPaymentAmount(long paymentAmountSat) {
        return paymentAmountSat > 0 && paymentAmountSat <= maxPaymentSat;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}