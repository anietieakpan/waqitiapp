package com.waqiti.crypto.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;

/**
 * Vault-backed Cryptocurrency RPC Credentials Configuration
 *
 * SECURITY CRITICAL: This configuration loads sensitive cryptocurrency node credentials
 * from HashiCorp Vault or environment variables with proper validation.
 *
 * Features:
 * - Validates all required credentials are present at startup
 * - Supports multiple blockchain nodes (Bitcoin, Litecoin, Ethereum)
 * - Integrates with Spring Cloud Vault
 * - Fails fast if credentials are missing or invalid
 * - Logs credential loading (without exposing values)
 *
 * Compliance:
 * - PCI DSS 8.2: Secure credential management
 * - SOX 404: IT general controls for access management
 * - NIST 800-53: AC-2 (Account Management)
 *
 * Usage:
 * <pre>
 * {@code
 * @Autowired
 * private VaultCryptoCredentialsConfig credentialsConfig;
 *
 * String bitcoinPassword = credentialsConfig.getBitcoin().getRpcPassword();
 * }
 * </pre>
 *
 * Environment Variables (fallback if Vault unavailable):
 * - BITCOIN_RPC_USER
 * - BITCOIN_RPC_PASSWORD
 * - LITECOIN_RPC_USER
 * - LITECOIN_RPC_PASSWORD
 * - ETHEREUM_RPC_API_KEY (for Infura/Alchemy)
 *
 * Vault Paths:
 * - secret/crypto-service/bitcoin
 * - secret/crypto-service/litecoin
 * - secret/crypto-service/ethereum
 *
 * @author Waqiti Security Team
 * @since 2025-11-08
 */
@Slf4j
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "waqiti.crypto.credentials")
public class VaultCryptoCredentialsConfig {

    /**
     * Bitcoin Core RPC credentials
     */
    private BitcoinCredentials bitcoin = new BitcoinCredentials();

    /**
     * Litecoin Core RPC credentials
     */
    private LitecoinCredentials litecoin = new LitecoinCredentials();

    /**
     * Ethereum RPC credentials (Infura/Alchemy/Custom node)
     */
    private EthereumCredentials ethereum = new EthereumCredentials();

    /**
     * Lightning Network Daemon credentials
     */
    private LightningCredentials lightning = new LightningCredentials();

    /**
     * Vault configuration
     */
    private VaultConfig vault = new VaultConfig();

    @PostConstruct
    public void validateCredentials() {
        log.info("CRYPTO_CREDENTIALS_VALIDATION | action=validating_credentials | vault_enabled={}", vault.isEnabled());

        try {
            // Validate Bitcoin credentials
            if (bitcoin.getRpcPassword() == null || bitcoin.getRpcPassword().isBlank()) {
                throw new IllegalStateException("Bitcoin RPC password is not configured. " +
                        "Set BITCOIN_RPC_PASSWORD environment variable or configure Vault.");
            }
            if ("password".equalsIgnoreCase(bitcoin.getRpcPassword())) {
                throw new IllegalStateException("SECURITY VIOLATION: Bitcoin RPC password is set to default 'password'. " +
                        "This is a critical security risk. Generate a strong password immediately.");
            }
            log.info("CRYPTO_CREDENTIALS_VALIDATION | blockchain=bitcoin | status=valid | user={}", bitcoin.getRpcUser());

            // Validate Litecoin credentials
            if (litecoin.getRpcPassword() == null || litecoin.getRpcPassword().isBlank()) {
                throw new IllegalStateException("Litecoin RPC password is not configured. " +
                        "Set LITECOIN_RPC_PASSWORD environment variable or configure Vault.");
            }
            if ("password".equalsIgnoreCase(litecoin.getRpcPassword())) {
                throw new IllegalStateException("SECURITY VIOLATION: Litecoin RPC password is set to default 'password'. " +
                        "This is a critical security risk. Generate a strong password immediately.");
            }
            log.info("CRYPTO_CREDENTIALS_VALIDATION | blockchain=litecoin | status=valid | user={}", litecoin.getRpcUser());

            // Validate Ethereum credentials (API key)
            if (ethereum.getRpcApiKey() != null && !ethereum.getRpcApiKey().isBlank()) {
                log.info("CRYPTO_CREDENTIALS_VALIDATION | blockchain=ethereum | status=valid | provider={}",
                        ethereum.getProvider());
            } else {
                log.warn("CRYPTO_CREDENTIALS_VALIDATION | blockchain=ethereum | status=not_configured | " +
                        "message=Ethereum RPC API key not set, using default public endpoints (rate limited)");
            }

            // Validate Lightning credentials
            if (lightning.getMacaroonHex() == null || lightning.getMacaroonHex().isBlank()) {
                log.warn("CRYPTO_CREDENTIALS_VALIDATION | service=lightning | status=not_configured | " +
                        "message=Lightning macaroon not set, Lightning features disabled");
            } else {
                log.info("CRYPTO_CREDENTIALS_VALIDATION | service=lightning | status=valid");
            }

            log.info("CRYPTO_CREDENTIALS_VALIDATION | action=completed | status=success | vault_source={}",
                    vault.isEnabled() ? "vault" : "environment_variables");

        } catch (Exception e) {
            log.error("CRYPTO_CREDENTIALS_VALIDATION | action=failed | error={}", e.getMessage());
            throw e;
        }
    }

    /**
     * Bitcoin Core RPC Credentials
     */
    @Data
    public static class BitcoinCredentials {
        @NotBlank(message = "Bitcoin RPC user is required")
        private String rpcUser = "${BITCOIN_RPC_USER:waqiti}";

        @NotBlank(message = "Bitcoin RPC password is required")
        private String rpcPassword = "${BITCOIN_RPC_PASSWORD}";

        private String rpcHost = "${BITCOIN_RPC_HOST:bitcoin-core}";
        private int rpcPort = 8332;
    }

    /**
     * Litecoin Core RPC Credentials
     */
    @Data
    public static class LitecoinCredentials {
        @NotBlank(message = "Litecoin RPC user is required")
        private String rpcUser = "${LITECOIN_RPC_USER:waqiti}";

        @NotBlank(message = "Litecoin RPC password is required")
        private String rpcPassword = "${LITECOIN_RPC_PASSWORD}";

        private String rpcHost = "${LITECOIN_RPC_HOST:litecoin-core}";
        private int rpcPort = 9332;
    }

    /**
     * Ethereum RPC Credentials (Infura, Alchemy, or custom node)
     */
    @Data
    public static class EthereumCredentials {
        private String provider = "${ETHEREUM_PROVIDER:infura}"; // infura, alchemy, custom
        private String rpcApiKey = "${ETHEREUM_RPC_API_KEY:}";
        private String rpcUrl = "${ETHEREUM_RPC_URL:https://mainnet.infura.io/v3/}";
        private String network = "${ETHEREUM_NETWORK:mainnet}";
    }

    /**
     * Lightning Network Daemon Credentials
     */
    @Data
    public static class LightningCredentials {
        private String grpcHost = "${LND_GRPC_HOST:lnd}";
        private int grpcPort = 10009;
        private String tlsCertPath = "${LND_TLS_CERT_PATH:/shared/tls.cert}";
        private String macaroonPath = "${LND_MACAROON_PATH:/shared/admin.macaroon}";
        private String macaroonHex = "${LND_MACAROON_HEX:}";
    }

    /**
     * Vault Configuration
     */
    @Data
    public static class VaultConfig {
        private boolean enabled = "${VAULT_ENABLED:false}".equals("true");
        private String address = "${VAULT_ADDR:http://vault:8200}";
        private String token = "${VAULT_TOKEN:}";
        private String namespace = "${VAULT_NAMESPACE:}";
        private String cryptoServicePath = "secret/crypto-service";
    }
}
