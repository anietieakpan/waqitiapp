package com.waqiti.common.config;

import com.waqiti.common.repository.MissingRepositoryConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Main configuration class that imports all Qodana fixes
 * This ensures all missing beans and configurations are properly loaded
 */
@Configuration
@ComponentScan(basePackages = {
    "com.waqiti.common.config",
    "com.waqiti.common.repository", 
    "com.waqiti.common.fixes"
})
@Import({
    MissingBeansConfiguration.class,
    MissingRepositoryConfiguration.class,
    MetricsConfigurationFix.class
})
public class QodanaFixesApplicationConfiguration {
    // This configuration class serves as the main entry point for all Qodana fixes
}