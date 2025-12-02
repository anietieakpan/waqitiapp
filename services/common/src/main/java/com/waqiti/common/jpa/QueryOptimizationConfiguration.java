package com.waqiti.common.jpa;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Map;

/**
 * JPA Query Optimization Configuration
 * Configures Hibernate for optimal performance and N+1 query prevention
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.waqiti",
    repositoryImplementationPostfix = "Impl"
)
public class QueryOptimizationConfiguration {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return (Map<String, Object> hibernateProperties) -> {
            // Enable batch fetching to reduce N+1 queries
            hibernateProperties.put(AvailableSettings.DEFAULT_BATCH_FETCH_SIZE, 16);
            hibernateProperties.put(AvailableSettings.MAX_FETCH_DEPTH, 3);
            
            // Enable query result caching
            hibernateProperties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, true);
            hibernateProperties.put(AvailableSettings.USE_QUERY_CACHE, true);
            hibernateProperties.put(AvailableSettings.CACHE_REGION_FACTORY, 
                "org.hibernate.cache.jcache.JCacheRegionFactory");
            
            // Enable SQL query logging for development/debugging
            hibernateProperties.put(AvailableSettings.SHOW_SQL, false);
            hibernateProperties.put(AvailableSettings.FORMAT_SQL, true);
            hibernateProperties.put(AvailableSettings.USE_SQL_COMMENTS, true);
            
            // Enable statistics for monitoring
            hibernateProperties.put(AvailableSettings.GENERATE_STATISTICS, true);
            
            // Optimize connection handling
            hibernateProperties.put(AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, true);
            hibernateProperties.put(AvailableSettings.STATEMENT_BATCH_SIZE, 25);
            hibernateProperties.put(AvailableSettings.ORDER_INSERTS, true);
            hibernateProperties.put(AvailableSettings.ORDER_UPDATES, true);
            hibernateProperties.put(AvailableSettings.BATCH_VERSIONED_DATA, true);
            
            // Enable lazy loading optimizations
            hibernateProperties.put(AvailableSettings.ENABLE_LAZY_LOAD_NO_TRANS, false);
            hibernateProperties.put(AvailableSettings.BATCH_FETCH_STYLE, "PADDED");
            
            // JDBC optimizations
            hibernateProperties.put(AvailableSettings.STATEMENT_FETCH_SIZE, 50);
            hibernateProperties.put(AvailableSettings.USE_GET_GENERATED_KEYS, true);
            
            // Query optimization
            hibernateProperties.put(AvailableSettings.QUERY_PLAN_CACHE_MAX_SIZE, 2048);
            hibernateProperties.put(AvailableSettings.QUERY_PLAN_CACHE_PARAMETER_METADATA_MAX_SIZE, 128);
            
            // Multi-tenancy preparation
            hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, 
                "com.waqiti.common.tenant.CurrentTenantIdentifierResolver");
        };
    }

    @Bean
    public QueryPerformanceInterceptor queryPerformanceInterceptor() {
        return new QueryPerformanceInterceptor();
    }
}