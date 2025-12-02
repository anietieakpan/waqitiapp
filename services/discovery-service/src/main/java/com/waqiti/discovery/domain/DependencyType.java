package com.waqiti.discovery.domain;

/**
 * Type of service dependency
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public enum DependencyType {
    /**
     * Runtime dependency - required for service operation
     */
    RUNTIME,

    /**
     * Database dependency
     */
    DATABASE,

    /**
     * Message queue dependency
     */
    MESSAGE_QUEUE,

    /**
     * Cache dependency
     */
    CACHE,

    /**
     * External API dependency
     */
    EXTERNAL_API,

    /**
     * Internal microservice dependency
     */
    INTERNAL_SERVICE,

    /**
     * Optional/soft dependency
     */
    OPTIONAL,

    /**
     * Development/build-time dependency
     */
    BUILD_TIME,

    /**
     * Configuration dependency
     */
    CONFIGURATION;

    /**
     * Check if dependency is critical for runtime
     *
     * @return true if dependency is critical
     */
    public boolean isCritical() {
        return this == RUNTIME || this == DATABASE || this == INTERNAL_SERVICE;
    }
}
