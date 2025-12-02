package com.waqiti.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "waqiti")
public class WaqitiCommonProperties {

    private Financial financial = new Financial();
    private Security security = new Security();
    private Monitoring monitoring = new Monitoring();
    private ExternalServices externalServices = new ExternalServices();

    @Data
    public static class Financial {
        private int decimalPlaces = 4;
        private String roundingMode = "HALF_UP";
        private String defaultCurrency = "USD";
    }

    @Data
    public static class Security {
        private Encryption encryption = new Encryption();
        private Jwt jwt = new Jwt();
        private ServiceAuth service = new ServiceAuth();

        @Data
        public static class Encryption {
            private boolean enabled = true;
        }

        @Data
        public static class Jwt {
            private boolean vaultEnabled = true;
        }

        @Data
        public static class ServiceAuth {
            private Authentication authentication = new Authentication();

            @Data
            public static class Authentication {
                private boolean enabled = true;
            }
        }
    }

    @Data
    public static class Monitoring {
        private boolean enabled = true;
    }

    @Data
    public static class ExternalServices {
        private String timeout = "30s";
        private int retryAttempts = 3;
    }
}