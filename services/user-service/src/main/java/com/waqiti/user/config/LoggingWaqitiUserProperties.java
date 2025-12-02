package com.waqiti.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Logging-specific user configuration properties to resolve Qodana configuration issues
 */
@Data
@Component
@ConfigurationProperties(prefix = "logging.waqiti.user")
public class LoggingWaqitiUserProperties {

    private Security security = new Security();

    @Data
    public static class Security {
        private DeviceFingerprinting deviceFingerprinting = new DeviceFingerprinting();
        private Mfa mfa = new Mfa();

        @Data
        public static class DeviceFingerprinting {
            private boolean enabled = true;
        }

        @Data
        public static class Mfa {
            private boolean enabled = true;
        }
    }
}