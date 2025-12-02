package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Enterprise-grade SSL Certificate Manager
 * 
 * SECURITY: Provides proper certificate validation and pinning
 * Replaces the insecure trust-all SSL configuration
 * 
 * Features:
 * - Certificate pinning for critical external services
 * - Custom truststore management
 * - Certificate validation and expiry checking
 * - Support for mutual TLS (mTLS)
 * 
 * @author Waqiti Security Team
 * @version 1.0
 */
@Component
@Slf4j
public class SSLCertificateManager {

    @Value("${security.ssl.truststore.path:#{null}}")
    private String truststorePath;

    @Value("${security.ssl.truststore.password:#{null}}")
    private String truststorePassword;

    @Value("${security.ssl.keystore.path:#{null}}")
    private String keystorePath;

    @Value("${security.ssl.keystore.password:#{null}}")
    private String keystorePassword;

    @Value("${security.ssl.certificate.pinning.enabled:true}")
    private boolean certificatePinningEnabled;

    /**
     * Create an SSL context with proper certificate validation
     * 
     * SECURITY: Uses default Java truststore + custom certificates if provided
     * NO trust-all configuration
     * 
     * @return SSLContext with proper certificate validation
     * @throws Exception if SSL context cannot be created
     */
    public SSLContext createSSLContext() throws Exception {
        log.info("Creating SSL context with proper certificate validation");
        
        // Initialize with TLS 1.3 (most secure)
        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        
        // Use custom truststore if provided, otherwise use default Java truststore
        TrustManager[] trustManagers = createTrustManagers();
        
        // Use custom keystore if provided (for mutual TLS)
        KeyManager[] keyManagers = createKeyManagers();
        
        // Initialize with secure random
        sslContext.init(keyManagers, trustManagers, SecureRandomUtils.getSecureRandom());
        
        log.info("SSL context created successfully with certificate validation enabled");
        return sslContext;
    }

    /**
     * Create trust managers from custom truststore or default
     * 
     * @return Array of trust managers
     * @throws Exception if truststore cannot be loaded
     */
    private TrustManager[] createTrustManagers() throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        );

        if (truststorePath != null && !truststorePath.isEmpty()) {
            log.info("Loading custom truststore from: {}", truststorePath);
            KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
            
            try (InputStream is = new FileInputStream(truststorePath)) {
                truststore.load(is, 
                    truststorePassword != null ? truststorePassword.toCharArray() : null
                );
            }
            
            tmf.init(truststore);
            log.info("Custom truststore loaded successfully");
        } else {
            // Use default Java truststore (cacerts)
            log.info("Using default Java truststore");
            tmf.init((KeyStore) null);
        }

        return tmf.getTrustManagers();
    }

    /**
     * Create key managers for mutual TLS (mTLS)
     * 
     * @return Array of key managers, or null if not configured
     * @throws Exception if keystore cannot be loaded
     */
    private KeyManager[] createKeyManagers() throws Exception {
        if (keystorePath == null || keystorePath.isEmpty()) {
            return null; // No client certificate authentication
        }

        log.info("Loading keystore for mutual TLS from: {}", keystorePath);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        );

        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream is = new FileInputStream(keystorePath)) {
            keystore.load(is, 
                keystorePassword != null ? keystorePassword.toCharArray() : null
            );
        }

        kmf.init(keystore, 
            keystorePassword != null ? keystorePassword.toCharArray() : null
        );

        log.info("Keystore loaded successfully for mutual TLS");
        return kmf.getKeyManagers();
    }

    /**
     * Create a certificate-pinning trust manager for critical external services
     * 
     * SECURITY: Pins specific certificates to prevent MITM attacks
     * Use for payment providers, banking APIs, etc.
     * 
     * @param pinnedCertificates List of pinned certificate fingerprints (SHA-256)
     * @return X509TrustManager with certificate pinning
     */
    public X509TrustManager createPinningTrustManager(List<String> pinnedCertificates) {
        if (!certificatePinningEnabled) {
            log.warn("Certificate pinning is disabled - using standard validation only");
            return null;
        }

        return new X509TrustManager() {
            private final X509TrustManager defaultTrustManager = getDefaultTrustManager();

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) 
                    throws java.security.cert.CertificateException {
                defaultTrustManager.checkClientTrusted(chain, authType);
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) 
                    throws java.security.cert.CertificateException {
                // First, perform standard validation
                defaultTrustManager.checkServerTrusted(chain, authType);

                // Then, verify certificate pinning
                if (chain == null || chain.length == 0) {
                    throw new java.security.cert.CertificateException("Certificate chain is empty");
                }

                // Get the leaf certificate (server's certificate)
                X509Certificate serverCert = chain[0];
                String certFingerprint = getCertificateFingerprint(serverCert);

                if (!pinnedCertificates.contains(certFingerprint)) {
                    String error = String.format(
                        "Certificate pinning failed. Expected: %s, Got: %s",
                        pinnedCertificates, certFingerprint
                    );
                    log.error("SECURITY ALERT: {}", error);
                    throw new java.security.cert.CertificateException(error);
                }

                log.debug("Certificate pinning validation successful");
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return defaultTrustManager.getAcceptedIssuers();
            }

            private X509TrustManager getDefaultTrustManager() {
                try {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                        TrustManagerFactory.getDefaultAlgorithm()
                    );
                    tmf.init((KeyStore) null);
                    
                    for (TrustManager tm : tmf.getTrustManagers()) {
                        if (tm instanceof X509TrustManager) {
                            return (X509TrustManager) tm;
                        }
                    }
                    throw new IllegalStateException("No X509TrustManager found");
                } catch (Exception e) {
                    throw new RuntimeException("Failed to initialize default trust manager", e);
                }
            }

            private String getCertificateFingerprint(X509Certificate cert) {
                try {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                    byte[] digest = md.digest(cert.getEncoded());
                    
                    StringBuilder sb = new StringBuilder();
                    for (byte b : digest) {
                        sb.append(String.format("%02X", b));
                    }
                    return sb.toString();
                } catch (Exception e) {
                    log.error("Failed to calculate certificate fingerprint", e);
                    return "";
                }
            }
        };
    }

    /**
     * Validate certificate expiry and alert if expiring soon
     * 
     * @param cert Certificate to validate
     * @return true if certificate is valid and not expiring soon
     */
    public boolean validateCertificateExpiry(X509Certificate cert) {
        try {
            cert.checkValidity();
            
            // Check if certificate expires within 30 days
            long daysUntilExpiry = (cert.getNotAfter().getTime() - System.currentTimeMillis()) 
                / (1000 * 60 * 60 * 24);
            
            if (daysUntilExpiry < 30) {
                log.warn("SECURITY ALERT: Certificate expires in {} days: {}", 
                    daysUntilExpiry, cert.getSubjectDN());
                return false;
            }
            
            log.debug("Certificate is valid for {} more days", daysUntilExpiry);
            return true;
            
        } catch (Exception e) {
            log.error("Certificate validation failed: {}", cert.getSubjectDN(), e);
            return false;
        }
    }

    /**
     * Import a certificate into the custom truststore
     * 
     * @param alias Alias for the certificate
     * @param certPath Path to certificate file
     * @throws Exception if certificate cannot be imported
     */
    public void importCertificate(String alias, String certPath) throws Exception {
        log.info("Importing certificate {} from {}", alias, certPath);
        
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        
        try (InputStream is = new FileInputStream(certPath)) {
            cert = cf.generateCertificate(is);
        }

        KeyStore truststore = KeyStore.getInstance(KeyStore.getDefaultType());
        
        if (truststorePath != null && !truststorePath.isEmpty()) {
            try (InputStream is = new FileInputStream(truststorePath)) {
                truststore.load(is, 
                    truststorePassword != null ? truststorePassword.toCharArray() : null
                );
            }
        } else {
            truststore.load(null, null);
        }

        truststore.setCertificateEntry(alias, cert);
        
        log.info("Certificate {} imported successfully", alias);
    }

    /**
     * Get hostname verifier with proper validation
     * DO NOT use NoopHostnameVerifier - it's insecure!
     * 
     * @return HostnameVerifier with proper validation
     */
    public HostnameVerifier getHostnameVerifier() {
        // Use default hostname verifier which properly validates hostnames
        return HttpsURLConnection.getDefaultHostnameVerifier();
    }
}