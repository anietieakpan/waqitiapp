package com.waqiti.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    private boolean enabled = true;
    private String realm = "waqiti";
    private String authServerUrl = "https://localhost:8080";
    private String sslRequired = "external";
    private String resource = "user-service";
    private boolean bearerOnly = true;
    private boolean useResourceRoleMappings = true;
    private boolean cors = true;
    private String corsAllowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
    private String corsAllowedHeaders = "Authorization,Content-Type,X-Requested-With";
    private int corsMaxAge = 3600;
    private boolean publicClient = false;
    private Credentials credentials = new Credentials();
    private Admin admin = new Admin();

    @Data
    public static class Credentials {
        private String secret;
    }

    @Data
    public static class Admin {
        private String username;
        private String password;
        private String clientId = "admin-cli";
    }
}