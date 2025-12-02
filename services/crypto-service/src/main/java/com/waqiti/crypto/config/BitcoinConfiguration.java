package com.waqiti.crypto.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.RegTestParams;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Bitcoin network and node configuration
 */
@Configuration
@ConfigurationProperties(prefix = "waqiti.bitcoin")
@Data
@Validated
@Slf4j
public class BitcoinConfiguration {

    @NotBlank
    private String network = "testnet"; // mainnet, testnet, regtest
    
    @NotBlank
    private String nodeHost = "bitcoin-core";
    
    @Positive
    private int nodePort = 8332;
    
    @NotBlank
    private String rpcUser = "waqiti";
    
    @NotBlank
    private String rpcPassword;
    
    @Positive
    private int connectionTimeout = 30000;
    
    @Positive
    private int readTimeout = 60000;
    
    @NotNull
    private Boolean enableSsl = false;
    
    private String sslCertPath;
    
    // ZMQ settings for real-time notifications
    @NotBlank
    private String zmqHost = "bitcoin-core";
    
    @Positive
    private int zmqBlockPort = 28332;
    
    @Positive
    private int zmqTxPort = 28333;
    
    // Wallet settings
    private String walletName = "waqiti-wallet";
    
    private Boolean createWalletIfNotExists = true;
    
    // Fee estimation
    private Double defaultFeeRate = 1.0; // sat/byte
    
    private Double economyFeeRate = 0.5;
    
    private Double priorityFeeRate = 10.0;
    
    // Transaction settings
    @Positive
    private int minConfirmations = 3;
    
    @Positive
    private int maxConfirmations = 6;
    
    // Lightning specific
    private Boolean enableLightning = true;
    
    @PostConstruct
    public void validate() {
        if (!network.matches("mainnet|testnet|regtest")) {
            throw new IllegalArgumentException("Invalid Bitcoin network: " + network);
        }
        
        if (rpcPassword == null || rpcPassword.length() < 8) {
            throw new IllegalArgumentException("Bitcoin RPC password must be at least 8 characters");
        }
        
        log.info("Bitcoin configuration initialized for network: {}", network);
    }

    @Bean
    public NetworkParameters networkParameters() {
        return switch (network.toLowerCase()) {
            case "mainnet" -> MainNetParams.get();
            case "testnet" -> TestNet3Params.get();
            case "regtest" -> RegTestParams.get();
            default -> throw new IllegalArgumentException("Invalid network: " + network);
        };
    }

    @Bean
    public Context bitcoinContext(NetworkParameters networkParameters) {
        Context context = new Context(networkParameters);
        Context.propagate(context);
        return context;
    }

    /**
     * Get the full RPC URL for Bitcoin Core
     */
    public String getRpcUrl() {
        String protocol = enableSsl ? "https" : "http";
        return String.format("%s://%s:%s@%s:%d", protocol, rpcUser, rpcPassword, nodeHost, nodePort);
    }

    /**
     * Get ZMQ block notification URL
     */
    public String getZmqBlockUrl() {
        return String.format("tcp://%s:%d", zmqHost, zmqBlockPort);
    }

    /**
     * Get ZMQ transaction notification URL
     */
    public String getZmqTxUrl() {
        return String.format("tcp://%s:%d", zmqHost, zmqTxPort);
    }

    /**
     * Check if we're running on mainnet
     */
    public boolean isMainnet() {
        return "mainnet".equalsIgnoreCase(network);
    }

    /**
     * Check if we're running on testnet
     */
    public boolean isTestnet() {
        return "testnet".equalsIgnoreCase(network);
    }

    /**
     * Get appropriate fee rate based on priority
     */
    public double getFeeRate(FeePriority priority) {
        return switch (priority) {
            case ECONOMY -> economyFeeRate;
            case NORMAL -> defaultFeeRate;
            case PRIORITY -> priorityFeeRate;
        };
    }

    public enum FeePriority {
        ECONOMY, NORMAL, PRIORITY
    }
}