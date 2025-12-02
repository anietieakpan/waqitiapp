package com.waqiti.discovery.service;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.waqiti.discovery.domain.InstanceHealthCheckResult;
import com.waqiti.discovery.domain.ServiceMetrics;

/**
 * Health Check Service Interface
 * Provides health checking functionality for service instances
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public interface HealthCheckService {

    /**
     * Check health of a service instance
     *
     * @param instance instance to check
     * @return health check result
     */
    InstanceHealthCheckResult checkInstanceHealth(InstanceInfo instance);

    /**
     * Collect metrics for a service application
     *
     * @param application service application
     * @return service metrics
     */
    ServiceMetrics collectServiceMetrics(Application application);

    /**
     * Enable health check for a service
     *
     * @param serviceName service name
     * @param interval check interval in seconds
     */
    void enableHealthCheck(String serviceName, Integer interval);

    /**
     * Disable health check for a service
     *
     * @param serviceName service name
     */
    void disableHealthCheck(String serviceName);
}
