package com.waqiti.config.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;

@Component
public class VaultHealthIndicator implements HealthIndicator {

    public VaultHealthIndicator(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    private final VaultTemplate vaultTemplate;

    @Override
    public Health health() {
        if (vaultTemplate == null) {
            return Health.up()
                    .withDetail("status", "Vault not configured")
                    .build();
        }

        try {
            vaultTemplate.opsForSys().health();
            return Health.up()
                    .withDetail("status", "Vault is accessible")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("status", "Vault is not accessible")
                    .withException(e)
                    .build();
        }
    }
}