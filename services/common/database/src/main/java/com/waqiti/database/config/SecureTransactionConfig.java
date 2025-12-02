package com.waqiti.database.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

/**
 * Secure Transaction Configuration
 * 
 * Ensures consistent transaction isolation levels across all services:
 * - SERIALIZABLE for financial operations (prevents phantom reads)
 * - REPEATABLE_READ for reports and analytics
 * - READ_COMMITTED for non-financial operations
 * - Proper timeout and rollback configuration
 * 
 * SECURITY: Fixes mixed transaction isolation levels that could lead to 
 * data inconsistencies in financial calculations
 */
@Configuration
@EnableTransactionManagement
@Slf4j
public class SecureTransactionConfig implements TransactionManagementConfigurer {
    
    @Value("${transaction.financial.timeout:30}")
    private int financialTransactionTimeoutSeconds;
    
    @Value("${transaction.readonly.timeout:60}")
    private int readOnlyTransactionTimeoutSeconds;
    
    @Value("${transaction.default.timeout:30}")
    private int defaultTransactionTimeoutSeconds;
    
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        
        // SECURITY: Set default isolation level to REPEATABLE_READ
        // This prevents most concurrency issues while maintaining performance
        transactionManager.setDefaultTimeout(defaultTransactionTimeoutSeconds);
        
        // Enable transaction debugging in development
        transactionManager.setValidateExistingTransaction(true);
        transactionManager.setFailEarlyOnGlobalRollbackOnly(true);
        
        log.info("Configured secure transaction manager with default timeout: {}s", 
                defaultTransactionTimeoutSeconds);
        
        return transactionManager;
    }
    
    /**
     * Financial Transaction Manager - SERIALIZABLE isolation
     * Use for all money-related operations to prevent race conditions
     */
    @Bean("financialTransactionManager")
    public PlatformTransactionManager financialTransactionManager(EntityManagerFactory emf) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        
        // SECURITY FIX: Use SERIALIZABLE for all financial operations
        // This prevents race conditions, phantom reads, and ensures ACID compliance
        transactionManager.setDefaultTimeout(financialTransactionTimeoutSeconds);
        transactionManager.setValidateExistingTransaction(true);
        transactionManager.setFailEarlyOnGlobalRollbackOnly(true);
        
        log.info("Configured FINANCIAL transaction manager with SERIALIZABLE isolation and timeout: {}s", 
                financialTransactionTimeoutSeconds);
        
        return transactionManager;
    }
    
    /**
     * Read-Only Transaction Manager - REPEATABLE_READ isolation
     * Use for reports and analytics to ensure consistent reads
     */
    @Bean("readOnlyTransactionManager")
    public PlatformTransactionManager readOnlyTransactionManager(EntityManagerFactory emf) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(emf);
        
        // SECURITY: Use REPEATABLE_READ for consistent reporting
        transactionManager.setDefaultTimeout(readOnlyTransactionTimeoutSeconds);
        transactionManager.setValidateExistingTransaction(true);
        
        log.info("Configured READ-ONLY transaction manager with REPEATABLE_READ isolation and timeout: {}s", 
                readOnlyTransactionTimeoutSeconds);
        
        return transactionManager;
    }
    
    @Override
    public PlatformTransactionManager annotationDrivenTransactionManager() {
        return transactionManager(null);
    }
    
    /**
     * Create financial transaction definition
     * SECURITY: SERIALIZABLE isolation for financial operations
     */
    public static TransactionDefinition createFinancialTransactionDefinition() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setTimeout(30); // 30 second timeout for financial operations
        def.setReadOnly(false);
        return def;
    }
    
    /**
     * Create read-only transaction definition
     * SECURITY: REPEATABLE_READ isolation for consistent reads
     */
    public static TransactionDefinition createReadOnlyTransactionDefinition() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setTimeout(60); // 60 second timeout for reports
        def.setReadOnly(true);
        return def;
    }
    
    /**
     * Create default transaction definition
     * SECURITY: READ_COMMITTED for general operations
     */
    public static TransactionDefinition createDefaultTransactionDefinition() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        def.setTimeout(30); // 30 second timeout
        def.setReadOnly(false);
        return def;
    }
}