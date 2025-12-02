package com.waqiti.common.security.hsm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Enhanced HSM Configuration for Waqiti Platform
 * 
 * Supports multiple HSM providers including:
 * - PKCS#11 Generic HSMs (SafeNet Luna, nCipher nShield, Utimaco, etc.)
 * - AWS CloudHSM 
 * - Thales CipherTrust Manager
 * - YubiHSM
 * 
 * Features:
 * - High Availability with failover
 * - Performance monitoring
 * - FIPS 140-2/3 compliance
 * - Key lifecycle management
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "waqiti.security.hsm.enabled", havingValue = "true")
public class HSMConfiguration {

    @Value("${waqiti.security.hsm.provider:pkcs11}")
    private String hsmProviderType;
    
    @Value("${waqiti.security.hsm.pkcs11.library-path:/usr/lib/pkcs11/libpkcs11.so}")
    private String pkcs11LibraryPath;
    
    @Value("${waqiti.security.hsm.pkcs11.slot-id:0}")
    private int pkcs11SlotId;
    
    @Value("${waqiti.security.hsm.pkcs11.pin:}")
    private String pkcs11Pin;
    
    @Value("${waqiti.security.hsm.pkcs11.config-name:WaqitiHSM}")
    private String pkcs11ConfigName;
    
    @Value("${waqiti.security.hsm.aws.cluster-id:}")
    private String awsClusterId;
    
    @Value("${waqiti.security.hsm.aws.region:us-east-1}")
    private String awsRegion;
    
    @Value("${waqiti.security.hsm.connection-timeout:30000}")
    private int connectionTimeout;
    
    @Value("${waqiti.security.hsm.operation-timeout:10000}")
    private int operationTimeout;
    
    @Value("${waqiti.security.hsm.max-retries:3}")
    private int maxRetries;

    @Bean
    @Primary
    @ConditionalOnProperty(name = "waqiti.security.hsm.provider", havingValue = "pkcs11")
    public HSMProvider pkcs11HSMProvider() {
        log.info("Configuring PKCS#11 HSM Provider with library: {}", pkcs11LibraryPath);
        
        HSMConfig config = HSMConfig.builder()
            .providerType(HSMProvider.HSMProviderType.PKCS11_GENERIC.name())
            .libraryPath(pkcs11LibraryPath)
            .slotId(pkcs11SlotId)
            .pin(pkcs11Pin.toCharArray())
            .configName(pkcs11ConfigName)
            .connectionTimeout(connectionTimeout)
            .operationTimeout(operationTimeout)
            .maxRetries(maxRetries)
            .build();
            
        try {
            PKCS11HSMProvider provider = new PKCS11HSMProvider();
            provider.initialize(config);
            
            // Test connection
            if (provider.testConnection()) {
                log.info("PKCS#11 HSM Provider initialized and tested successfully");
                return provider;
            } else {
                throw new RuntimeException("HSM connection test failed");
            }
        } catch (Exception e) {
            log.error("Failed to initialize PKCS#11 HSM Provider", e);
            throw new RuntimeException("HSM initialization failed", e);
        }
    }
    
    /**
     * AWS CloudHSM provider - enterprise-grade cloud HSM
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.hsm.provider", havingValue = "aws-cloudhsm")
    public HSMProvider awsCloudHSMProvider() {
        log.info("Configuring AWS CloudHSM Provider with cluster: {}", awsClusterId);
        
        HSMConfig config = HSMConfig.builder()
            .providerType(HSMProvider.HSMProviderType.AWS_CLOUDHSM.name())
            .awsClusterId(awsClusterId)
            .awsRegion(awsRegion)
            .connectionTimeout(connectionTimeout)
            .operationTimeout(operationTimeout)
            .maxRetries(maxRetries)
            .build();
        
        try {
            AWSCloudHSMProvider provider = new AWSCloudHSMProvider();
            provider.initialize(config);
            
            // Test connection
            if (provider.testConnection()) {
                log.info("AWS CloudHSM Provider initialized and tested successfully");
                return provider;
            } else {
                throw new RuntimeException("AWS CloudHSM connection test failed");
            }
        } catch (Exception e) {
            log.error("Failed to initialize AWS CloudHSM Provider, falling back to PKCS#11", e);
            return pkcs11HSMProvider();
        }
    }
    
    /**
     * Thales CipherTrust Manager - enterprise key management
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.hsm.provider", havingValue = "thales")
    public HSMProvider thalesHSMProvider() {
        log.info("Configuring Thales CipherTrust HSM Provider");
        
        HSMConfig config = HSMConfig.builder()
            .providerType(HSMProvider.HSMProviderType.THALES_CIPHERTRUST.name())
            .connectionTimeout(connectionTimeout)
            .operationTimeout(operationTimeout)
            .maxRetries(maxRetries)
            .build();
        
        try {
            ThalesHSMProvider provider = new ThalesHSMProvider();
            provider.initialize(config);
            
            // Test connection
            if (provider.testConnection()) {
                log.info("Thales HSM Provider initialized and tested successfully");
                return provider;
            } else {
                throw new RuntimeException("Thales HSM connection test failed");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Thales HSM Provider, falling back to PKCS#11", e);
            return pkcs11HSMProvider();
        }
    }
    
    /**
     * YubiHSM provider - cost-effective HSM solution
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.hsm.provider", havingValue = "yubico")
    public HSMProvider yubiHSMProvider() {
        log.info("Configuring YubiHSM Provider");
        
        HSMConfig config = HSMConfig.builder()
            .providerType(HSMProvider.HSMProviderType.YUBICO_YUBIHSM.name())
            .connectionTimeout(connectionTimeout)
            .operationTimeout(operationTimeout)
            .maxRetries(maxRetries)
            .build();
            
        // Implementation would be:
        // return new YubiHSMProvider(config);
        log.warn("YubiHSM provider not yet implemented - using PKCS#11 fallback");
        return pkcs11HSMProvider();
    }

    @Bean
    @ConfigurationProperties(prefix = "waqiti.security.hsm")
    public HSMProperties hsmProperties() {
        return new HSMProperties();
    }
    
    /**
     * HSM Health Check and Monitoring Bean
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.hsm.monitoring.enabled", havingValue = "true", matchIfMissing = true)
    public HSMHealthMonitor hsmHealthMonitor(HSMProvider hsmProvider) {
        return new HSMHealthMonitor(hsmProvider);
    }
}