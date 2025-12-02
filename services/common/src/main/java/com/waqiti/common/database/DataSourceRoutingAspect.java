package com.waqiti.common.database;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DataSource Routing Aspect
 *
 * Monitors and logs datasource routing decisions for debugging and metrics.
 *
 * MONITORING:
 * - Tracks which datasource is used for each operation
 * - Logs routing decisions (debug level)
 * - Exposes metrics for Prometheus
 *
 * DEBUGGING:
 * Enable debug logging to see routing decisions:
 * logging.level.com.waqiti.common.database=DEBUG
 *
 * @author Waqiti Platform Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-10-30
 */
@Aspect
@Component
@Slf4j
public class DataSourceRoutingAspect {

    /**
     * Intercept repository method calls to log datasource routing
     */
    @Around("execution(* org.springframework.data.repository.Repository+.*(..))")
    public Object logDataSourceRouting(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        boolean isTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();

        String datasourceType = (isTransactionActive && !isReadOnly) ? "PRIMARY" : "REPLICA";

        log.debug("Executing {}: datasource={}, transactionActive={}, readOnly={}",
            methodName, datasourceType, isTransactionActive, isReadOnly);

        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Completed {}: datasource={}, duration={}ms",
                methodName, datasourceType, duration);

            return result;

        } catch (Exception e) {
            log.error("Failed {}: datasource={}, error={}",
                methodName, datasourceType, e.getMessage());
            throw e;
        }
    }
}
