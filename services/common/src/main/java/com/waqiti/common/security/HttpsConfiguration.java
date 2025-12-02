/**
 * HTTPS Configuration
 * Configures SSL/TLS settings for production security
 */
package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Slf4j
@Profile({"production", "staging"})
public class HttpsConfiguration {

    @Value("${server.http.port:8080}")
    private int httpPort;

    @Value("${server.port:8443}")
    private int httpsPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    /**
     * Configure HTTP to HTTPS redirect
     * Automatically redirects all HTTP traffic to HTTPS
     */
    @Bean
    @ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpsRedirectCustomizer() {
        return factory -> {
            if (sslEnabled) {
                factory.addAdditionalTomcatConnectors(createHttpConnector());
                log.info("Configured HTTP to HTTPS redirect from port {} to {}", httpPort, httpsPort);
            }
        };
    }

    /**
     * Create HTTP connector that redirects to HTTPS
     */
    private Connector createHttpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        
        // Security settings
        connector.setProperty("maxParameterCount", "1000");
        connector.setProperty("maxPostSize", "2097152"); // 2MB
        connector.setProperty("maxHttpHeaderSize", "8192"); // 8KB
        connector.setProperty("connectionTimeout", "20000"); // 20 seconds
        connector.setProperty("maxConnections", "8192");
        connector.setProperty("acceptCount", "100");
        
        return connector;
    }
}