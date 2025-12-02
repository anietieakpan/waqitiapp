package com.waqiti.nft.security;

import com.azure.security.keyvault.keys.KeyClient;
import com.azure.security.keyvault.keys.models.KeyVaultKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Production-ready health check for Azure Key Vault connectivity
 * Monitors KEK availability, permissions, and response times
 *
 * @author Waqiti Security Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureKeyVaultHealthCheck implements HealthIndicator {

    private final KeyClient keyClient;
    private final AzureKeyVaultConfig config;

    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WARNING_THRESHOLD = Duration.ofMillis(500);

    @Override
    public Health health() {
        try {
            Instant start = Instant.now();

            // Attempt to read the KEK metadata (no key material retrieved)
            KeyVaultKey key = keyClient.getKey(config.getKekName());

            Duration elapsed = Duration.between(start, Instant.now());

            // Check if key is enabled and not expired
            if (!key.getProperties().isEnabled()) {
                return Health.down()
                    .withDetail("error", "KEK is disabled")
                    .withDetail("kekName", config.getKekName())
                    .withDetail("keyId", key.getId())
                    .build();
            }

            if (key.getProperties().getExpiresOn() != null &&
                key.getProperties().getExpiresOn().isBefore(Instant.now().atOffset(java.time.ZoneOffset.UTC))) {
                return Health.down()
                    .withDetail("error", "KEK has expired")
                    .withDetail("kekName", config.getKekName())
                    .withDetail("expiresOn", key.getProperties().getExpiresOn())
                    .build();
            }

            // Build health response
            Health.Builder builder = Health.up();

            if (elapsed.compareTo(WARNING_THRESHOLD) > 0) {
                log.warn("Azure Key Vault health check slow: {}ms", elapsed.toMillis());
                builder.withDetail("warning", "Slow response time");
            }

            return builder
                .withDetail("vaultUrl", config.getVaultUrl())
                .withDetail("kekName", config.getKekName())
                .withDetail("keyType", key.getKeyType())
                .withDetail("keySize", key.getKey().getN() != null ? key.getKey().getN().length * 8 : "N/A")
                .withDetail("hsmBacked", key.getProperties().isHardwareProtected())
                .withDetail("responseTimeMs", elapsed.toMillis())
                .withDetail("enabled", key.getProperties().isEnabled())
                .withDetail("createdOn", key.getProperties().getCreatedOn())
                .withDetail("updatedOn", key.getProperties().getUpdatedOn())
                .build();

        } catch (Exception e) {
            log.error("Azure Key Vault health check failed", e);

            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("errorType", e.getClass().getSimpleName())
                .withDetail("vaultUrl", config.getVaultUrl())
                .withDetail("kekName", config.getKekName())
                .build();
        }
    }
}
