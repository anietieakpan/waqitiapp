package com.waqiti.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

@Configuration
@Profile({"production", "staging"})
@Slf4j
public class MTLSConfiguration {

    @Value("${security.mtls.enabled:false}")
    private boolean mtlsEnabled;
    
    @Value("${security.mtls.keystore.path}")
    private String keystorePath;
    
    @Value("${security.mtls.keystore.password}")
    private String keystorePassword;
    
    @Value("${security.mtls.truststore.path}")
    private String truststorePath;
    
    @Value("${security.mtls.truststore.password}")
    private String truststorePassword;
    
    @Value("${security.mtls.client-auth:NEED}")
    private String clientAuth;
    
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainer() {
        return factory -> {
            if (mtlsEnabled) {
                log.info("Configuring mTLS for Tomcat");
                factory.addConnectorCustomizers(connector -> {
                    connector.setScheme("https");
                    connector.setSecure(true);
                    connector.setPort(8443);
                    
                    // Configure SSL
                    connector.setProperty("SSLEnabled", "true");
                    connector.setProperty("sslProtocol", "TLS");
                    connector.setProperty("sslEnabledProtocols", "TLSv1.2,TLSv1.3");
                    connector.setProperty("ciphers", getSecureCiphers());
                    
                    // Configure client certificate authentication
                    connector.setProperty("clientAuth", clientAuth);
                    connector.setProperty("keystoreFile", keystorePath);
                    connector.setProperty("keystorePass", keystorePassword);
                    connector.setProperty("keystoreType", "PKCS12");
                    connector.setProperty("truststoreFile", truststorePath);
                    connector.setProperty("truststorePass", truststorePassword);
                    connector.setProperty("truststoreType", "PKCS12");
                    
                    // Security enhancements
                    connector.setProperty("honorCipherOrder", "true");
                    connector.setProperty("compression", "off");
                    connector.setProperty("server", "Waqiti-Secure");
                });
            }
        };
    }
    
    @Bean
    public SSLContext customSSLContext() throws Exception {
        if (!mtlsEnabled) {
            return SSLContext.getDefault();
        }
        
        // Load keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream keystoreInput = new FileInputStream(keystorePath)) {
            keyStore.load(keystoreInput, keystorePassword.toCharArray());
        }
        
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
        
        // Load truststore
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream truststoreInput = new FileInputStream(truststorePath)) {
            trustStore.load(truststoreInput, truststorePassword.toCharArray());
        }
        
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        
        // Create SSL context
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(
                keyManagerFactory.getKeyManagers(),
                trustManagerFactory.getTrustManagers(),
                new java.security.SecureRandom()
        );
        
        log.info("Custom SSL context configured with mTLS");
        return sslContext;
    }
    
    private String getSecureCiphers() {
        return "TLS_AES_256_GCM_SHA384," +
               "TLS_AES_128_GCM_SHA256," +
               "TLS_CHACHA20_POLY1305_SHA256," +
               "ECDHE-RSA-AES256-GCM-SHA384," +
               "ECDHE-RSA-AES128-GCM-SHA256," +
               "ECDHE-RSA-CHACHA20-POLY1305," +
               "ECDHE-ECDSA-AES256-GCM-SHA384," +
               "ECDHE-ECDSA-AES128-GCM-SHA256," +
               "ECDHE-ECDSA-CHACHA20-POLY1305";
    }
}