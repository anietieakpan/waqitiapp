package com.waqiti.analytics.config.properties;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Feature Flag Configuration Properties
 *
 * <p>Production-grade feature flag configuration for controlling feature
 * rollouts, A/B testing, and gradual migration strategies.
 *
 * <p>Features:
 * <ul>
 *   <li>Dual authentication mode support</li>
 *   <li>Keycloak-only mode toggle</li>
 *   <li>Legacy JWT deprecation warnings</li>
 *   <li>Runtime feature toggling without deployment</li>
 * </ul>
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "feature.flags")
@Schema(description = "Feature flag configuration for feature toggles and gradual rollouts")
public class FeatureFlagProperties {

    @Valid
    @NotNull(message = "Dual auth mode configuration cannot be null")
    private FeatureFlag dualAuthMode = new FeatureFlag();

    @Valid
    @NotNull(message = "Keycloak-only configuration cannot be null")
    private FeatureFlag keycloakOnly = new FeatureFlag();

    @Valid
    @NotNull(message = "Legacy JWT deprecation warning configuration cannot be null")
    private FeatureFlag legacyJwtDeprecationWarning = new FeatureFlag();

    @Data
    @Schema(description = "Individual feature flag configuration")
    public static class FeatureFlag {

        @Schema(
            description = "Enable this feature flag",
            example = "false"
        )
        private boolean enabled = false;
    }

    /**
     * Checks if dual authentication mode is active
     *
     * @return true if dual auth mode is enabled
     */
    public boolean isDualAuthModeEnabled() {
        return dualAuthMode != null && dualAuthMode.isEnabled();
    }

    /**
     * Checks if Keycloak-only mode is active
     *
     * @return true if Keycloak-only mode is enabled
     */
    public boolean isKeycloakOnlyMode() {
        return keycloakOnly != null && keycloakOnly.isEnabled();
    }

    /**
     * Checks if legacy JWT deprecation warnings should be shown
     *
     * @return true if deprecation warnings are enabled
     */
    public boolean shouldShowLegacyJwtWarning() {
        return legacyJwtDeprecationWarning != null && legacyJwtDeprecationWarning.isEnabled();
    }
}
