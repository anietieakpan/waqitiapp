package com.waqiti.common.config;

import com.waqiti.common.security.EncryptionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for encryption service beans
 */
@Configuration
@Slf4j
public class EncryptionConfiguration {
    
    /**
     * Provides an EncryptionService bean for encryption operations
     */
    @Bean
    @ConditionalOnMissingBean
    public EncryptionService encryptionService() {
        return new DefaultEncryptionService();
    }
    
    /**
     * Default implementation of EncryptionService
     */
    public static class DefaultEncryptionService extends EncryptionService {

        public DefaultEncryptionService() {
            super(null, null, null);
        }
    }
}