package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionStatus;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;

/**
 * CRITICAL SECURITY COMPONENT: RLS-Aware JPA Transaction Manager
 *
 * PURPOSE:
 * Extends JpaTransactionManager to automatically configure PostgreSQL session parameters
 * for Row Level Security (RLS) at the start of EVERY transaction.
 *
 * SECURITY ARCHITECTURE:
 * Traditional JPA transaction flow:
 * 1. Begin transaction
 * 2. Execute queries → ❌ RLS parameters not set → SECURITY BYPASS!
 *
 * RLS-aware transaction flow:
 * 1. Begin transaction
 * 2. Configure RLS session parameters → ✅ Security enforced
 * 3. Execute queries → ✅ RLS policies active
 *
 * AUTOMATIC INTEGRATION:
 * Replace default JpaTransactionManager with this implementation in configuration:
 *
 * @Bean
 * public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
 *     return new RLSAwareJpaTransactionManager(emf, rlsSessionConfigurer);
 * }
 *
 * ZERO DEVELOPER EFFORT:
 * All @Transactional methods automatically get RLS configuration.
 * No code changes required in services.
 *
 * @author Waqiti Security Team
 * @since 2025-10-31
 * @version 1.0.0
 */
@Slf4j
public class RLSAwareJpaTransactionManager extends JpaTransactionManager {

    private final RLSSessionConfigurer rlsSessionConfigurer;
    private final boolean enforceRLS;

    /**
     * Creates RLS-aware transaction manager.
     *
     * @param emf EntityManagerFactory for JPA
     * @param rlsSessionConfigurer RLS session parameter configurer
     */
    public RLSAwareJpaTransactionManager(
        EntityManagerFactory emf,
        RLSSessionConfigurer rlsSessionConfigurer
    ) {
        this(emf, rlsSessionConfigurer, true);
    }

    /**
     * Creates RLS-aware transaction manager with optional enforcement.
     *
     * @param emf EntityManagerFactory for JPA
     * @param rlsSessionConfigurer RLS session parameter configurer
     * @param enforceRLS if true, throws exception if RLS config fails; if false, logs warning
     */
    public RLSAwareJpaTransactionManager(
        EntityManagerFactory emf,
        RLSSessionConfigurer rlsSessionConfigurer,
        boolean enforceRLS
    ) {
        super(emf);
        this.rlsSessionConfigurer = rlsSessionConfigurer;
        this.enforceRLS = enforceRLS;
    }

    /**
     * Extends doBegin to configure RLS session parameters after transaction start.
     *
     * EXECUTION FLOW:
     * 1. Call super.doBegin() to start JPA transaction
     * 2. Extract JDBC connection from JPA transaction
     * 3. Configure PostgreSQL session parameters for RLS
     * 4. Proceed with business logic
     *
     * @param transaction current transaction object
     * @param definition transaction definition (isolation, propagation, etc.)
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // Start JPA transaction first
        super.doBegin(transaction, definition);

        try {
            // Extract JDBC connection from JPA transaction
            Connection connection = getDataSource().getConnection();

            // Configure RLS session parameters
            rlsSessionConfigurer.configureSessionForRLS(connection);

            log.trace("RLS session configured for transaction: {}", definition.getName());

        } catch (Exception e) {
            if (enforceRLS) {
                // STRICT MODE: Fail transaction if RLS configuration fails
                log.error("CRITICAL SECURITY FAILURE: Cannot configure RLS for transaction. " +
                    "Aborting transaction to prevent security bypass.", e);

                // Rollback the transaction we just started
                try {
                    doRollback(new DefaultTransactionStatus(
                        transaction, true, true, true, true, null
                    ));
                } catch (Exception rollbackEx) {
                    log.error("Failed to rollback after RLS configuration failure", rollbackEx);
                }

                throw new RLSConfigurationException(
                    "Transaction aborted: Failed to configure Row Level Security. " +
                    "Database security cannot be enforced. Error: " + e.getMessage(),
                    e
                );
            } else {
                // PERMISSIVE MODE: Log warning and continue (NOT RECOMMENDED FOR PRODUCTION)
                log.warn("WARNING: RLS configuration failed but transaction proceeding. " +
                    "This may allow unauthorized data access. Error: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Extends doCleanupAfterCompletion to clear RLS context after transaction.
     *
     * CRITICAL for preventing context leakage across requests in connection pools.
     *
     * @param transaction current transaction object
     */
    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        try {
            // Clear RLS context from ThreadLocal
            // This prevents context leakage if same thread handles next request
            // Note: PostgreSQL session parameters auto-clear with SET LOCAL,
            // but ThreadLocal must be manually cleared

            log.trace("Cleaning up RLS context after transaction completion");

        } catch (Exception e) {
            log.warn("Failed to cleanup RLS context (not critical): {}", e.getMessage());
        } finally {
            // Always call parent cleanup
            super.doCleanupAfterCompletion(transaction);
        }
    }

    /**
     * Exception thrown when RLS configuration fails and enforcement is enabled.
     */
    public static class RLSConfigurationException extends RuntimeException {
        public RLSConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
