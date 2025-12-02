package com.waqiti.common.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Hibernate performance optimization configuration
 */
@Configuration
public class HibernatePerformanceConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return new HibernatePropertiesCustomizer() {
            @Override
            public void customize(Map<String, Object> hibernateProperties) {
                // Batch processing optimization
                hibernateProperties.put(AvailableSettings.STATEMENT_BATCH_SIZE, 50);
                hibernateProperties.put(AvailableSettings.ORDER_INSERTS, true);
                hibernateProperties.put(AvailableSettings.ORDER_UPDATES, true);
                hibernateProperties.put(AvailableSettings.BATCH_VERSIONED_DATA, true);
                
                // Connection pool optimization
                hibernateProperties.put(AvailableSettings.AUTOCOMMIT, false);
                // Connection handling is now automatic in Hibernate 6
                
                // Query optimization
                hibernateProperties.put(AvailableSettings.USE_QUERY_CACHE, true);
                hibernateProperties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, true);
                hibernateProperties.put(AvailableSettings.CACHE_REGION_FACTORY, 
                    "org.hibernate.cache.jcache.JCacheRegionFactory");
                
                // Statistics and monitoring (disable in production)
                hibernateProperties.put(AvailableSettings.GENERATE_STATISTICS, false);
                hibernateProperties.put(AvailableSettings.LOG_SLOW_QUERY, 100);
                
                // JDBC optimization
                hibernateProperties.put(AvailableSettings.USE_GET_GENERATED_KEYS, true);
                // Stream and metadata handling are now optimized by default in Hibernate 6
                
                // Lazy loading optimization
                hibernateProperties.put(AvailableSettings.ENABLE_LAZY_LOAD_NO_TRANS, false);
                hibernateProperties.put(AvailableSettings.MAX_FETCH_DEPTH, 3);
                
                // SQL optimization
                hibernateProperties.put(AvailableSettings.FORMAT_SQL, false);
                hibernateProperties.put(AvailableSettings.SHOW_SQL, false);
                hibernateProperties.put(AvailableSettings.USE_SQL_COMMENTS, false);
            }
        };
    }
}