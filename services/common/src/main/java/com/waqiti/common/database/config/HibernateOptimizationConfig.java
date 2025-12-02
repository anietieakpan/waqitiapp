package com.waqiti.common.database.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Hibernate configuration for query optimization and N+1 prevention
 */
@Configuration
public class HibernateOptimizationConfig {
    
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            // Enable batch fetching to prevent N+1 queries
            hibernateProperties.put("hibernate.default_batch_fetch_size", "16");
            
            // Enable lazy loading outside transactions (with caution)
            hibernateProperties.put("hibernate.enable_lazy_load_no_trans", "false");
            
            // Enable second-level cache
            hibernateProperties.put("hibernate.cache.use_second_level_cache", "true");
            hibernateProperties.put("hibernate.cache.use_query_cache", "true");
            hibernateProperties.put("hibernate.cache.region.factory_class", 
                "org.hibernate.cache.jcache.JCacheRegionFactory");
            
            // Enable statistics for monitoring
            hibernateProperties.put("hibernate.generate_statistics", "true");
            
            // Batch inserts and updates
            hibernateProperties.put("hibernate.jdbc.batch_size", "25");
            hibernateProperties.put("hibernate.order_inserts", "true");
            hibernateProperties.put("hibernate.order_updates", "true");
            hibernateProperties.put("hibernate.jdbc.batch_versioned_data", "true");
            
            // Enable IN clause parameter padding for better query plan caching
            hibernateProperties.put("hibernate.query.in_clause_parameter_padding", "true");
            
            // Fail fast on N+1 queries in development
            String profile = System.getProperty("spring.profiles.active", "");
            if (profile.contains("dev") || profile.contains("test")) {
                hibernateProperties.put("hibernate.query.fail_on_pagination_over_collection_fetch", "true");
            }
            
            // Connection pool optimization
            hibernateProperties.put("hibernate.connection.provider_disables_autocommit", "true");
            
            // Query plan cache
            hibernateProperties.put("hibernate.query.plan_cache_max_size", "2048");
            hibernateProperties.put("hibernate.query.plan_parameter_metadata_max_size", "128");
        };
    }
}