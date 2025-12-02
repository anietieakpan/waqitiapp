package com.waqiti.common.database;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * Read Replica Transaction Interceptor
 *
 * Automatically intercepts @Transactional methods and routes to appropriate datasource:
 * - @Transactional(readOnly = true) → Read replica
 * - @Transactional or @Transactional(readOnly = false) → Primary database
 *
 * CRITICAL: Order = 0 ensures this runs BEFORE Spring's transaction management
 *
 * PERFORMANCE IMPACT:
 * - Zero code changes required (just add @Transactional(readOnly=true))
 * - Automatic load balancing across read replicas
 * - Primary database freed for writes
 *
 * SAFETY:
 * - Read-write transactions always use primary (data consistency)
 * - Automatic fallback to primary if replica unavailable
 * - Thread-local context cleaned up after each transaction
 */
@Slf4j
@Aspect
@Component
@Order(0)  // CRITICAL: Execute before @Transactional
public class ReadReplicaTransactionInterceptor {

    /**
     * Intercept all methods annotated with @Transactional
     *
     * Determines read-only status and routes accordingly
     */
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object routeDataSource(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Get @Transactional annotation
        Transactional transactional = method.getAnnotation(Transactional.class);

        // If annotation not found on method, check class level
        if (transactional == null) {
            transactional = joinPoint.getTarget().getClass()
                .getAnnotation(Transactional.class);
        }

        boolean isReadOnly = transactional != null && transactional.readOnly();

        try {
            if (isReadOnly) {
                // Route to read replica
                log.debug("Read-only transaction detected: {} → routing to REPLICA",
                    method.getName());

                ReadReplicaRoutingDataSource.useReplicaDataSource();

            } else {
                // Route to primary database
                log.debug("Read-write transaction detected: {} → routing to PRIMARY",
                    method.getName());

                ReadReplicaRoutingDataSource.usePrimaryDataSource();
            }

            // Execute method with appropriate datasource
            return joinPoint.proceed();

        } finally {
            // CRITICAL: Clear thread-local context to prevent leaks
            ReadReplicaRoutingDataSource.clearDataSourceType();
        }
    }

    /**
     * Intercept methods annotated with custom @UseReadReplica annotation
     *
     * Alternative to @Transactional(readOnly=true) for non-transactional methods
     */
    @Around("@annotation(com.waqiti.common.database.UseReadReplica)")
    public Object routeToReadReplica(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        try {
            log.debug("@UseReadReplica annotation detected: {} → routing to REPLICA",
                method.getName());

            ReadReplicaRoutingDataSource.useReplicaDataSource();

            return joinPoint.proceed();

        } finally {
            ReadReplicaRoutingDataSource.clearDataSourceType();
        }
    }
}
