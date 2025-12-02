package com.waqiti.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "waqiti.user")
public class WaqitiUserProperties {

    private Kyc kyc = new Kyc();
    private Security security = new Security();
    private RateLimiting rateLimiting = new RateLimiting();

    @Data
    public static class Kyc {
        private boolean autoVerificationEnabled = false;
        private String verificationTimeout = "24h";
    }

    @Data
    public static class Security {
        private Password password = new Password();
        private Mfa mfa = new Mfa();
        private DeviceFingerprinting deviceFingerprinting = new DeviceFingerprinting();

        @Data
        public static class Password {
            private int minLength = 8;
            private boolean requireSpecialChars = true;
        }

        @Data
        public static class Mfa {
            private boolean enabled = true;
            private int totpWindow = 30;
            private int backupCodesCount = 10;
        }

        @Data
        public static class DeviceFingerprinting {
            private boolean enabled = true;
            private int maxDevices = 5;
        }
    }

    @Data
    public static class RateLimiting {
        private int loginAttempts = 5;
        private int registrationAttempts = 3;
    }
}