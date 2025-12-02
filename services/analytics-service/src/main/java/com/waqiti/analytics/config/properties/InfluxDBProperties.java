package com.waqiti.analytics.config.properties;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * InfluxDB Configuration Properties
 *
 * <p>Production-grade configuration for InfluxDB time-series database integration.
 * Handles connection settings, authentication, and organizational parameters
 * with comprehensive validation and security considerations.
 *
 * <p>Features:
 * <ul>
 *   <li>URL validation with protocol enforcement</li>
 *   <li>Sensitive token masking for security</li>
 *   <li>Organization and bucket validation</li>
 *   <li>Environment variable override support</li>
 * </ul>
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "influxdb")
@Schema(description = "InfluxDB time-series database configuration")
public class InfluxDBProperties {

    @NotBlank(message = "InfluxDB URL cannot be blank")
    @Pattern(regexp = "^https?://.*", message = "InfluxDB URL must start with http:// or https://")
    @Schema(
        description = "InfluxDB server URL",
        example = "http://localhost:8086",
        pattern = "^https?://.*",
        required = true
    )
    private String url = "http://localhost:8086";

    @JsonIgnore // Hide from JSON serialization for security
    @Schema(
        description = "InfluxDB authentication token (sensitive)",
        example = "your-influxdb-token-here",
        accessMode = Schema.AccessMode.WRITE_ONLY
    )
    private String token = "";

    @NotBlank(message = "InfluxDB organization cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Organization must contain only alphanumeric characters, hyphens, and underscores")
    @Schema(
        description = "InfluxDB organization name",
        example = "waqiti",
        pattern = "^[a-zA-Z0-9_-]+$",
        required = true
    )
    private String org = "waqiti";

    @NotBlank(message = "InfluxDB bucket cannot be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Bucket must contain only alphanumeric characters, hyphens, and underscores")
    @Schema(
        description = "InfluxDB bucket name for storing analytics data",
        example = "analytics",
        pattern = "^[a-zA-Z0-9_-]+$",
        required = true
    )
    private String bucket = "analytics";

    /**
     * Returns a masked token for logging purposes
     *
     * @return masked token showing only first/last 4 characters
     */
    @JsonIgnore
    public String getMaskedToken() {
        if (token == null || token.isEmpty() || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    /**
     * Checks if InfluxDB is properly configured
     *
     * @return true if all required properties are set
     */
    @JsonIgnore
    public boolean isConfigured() {
        return url != null && !url.isEmpty()
                && token != null && !token.isEmpty()
                && org != null && !org.isEmpty()
                && bucket != null && !bucket.isEmpty();
    }
}
