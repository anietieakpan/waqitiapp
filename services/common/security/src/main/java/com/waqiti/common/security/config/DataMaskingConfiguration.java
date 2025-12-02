package com.waqiti.common.security.config;

import com.waqiti.common.security.masking.DataMaskingAspect;
import com.waqiti.common.security.masking.DataMaskingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Configuration for enabling data masking features
 */
@Configuration
@EnableAspectJAutoProxy
@ConditionalOnProperty(
    prefix = "security.data-masking",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DataMaskingConfiguration {

    @Bean
    public DataMaskingService dataMaskingService() {
        return new DataMaskingService();
    }

    @Bean
    public DataMaskingAspect dataMaskingAspect(DataMaskingService dataMaskingService) {
        return new DataMaskingAspect(dataMaskingService);
    }
}