package com.waqiti.discovery.service;

import com.waqiti.discovery.domain.LoadBalancerStrategy;
import com.waqiti.discovery.domain.ServiceMetrics;
import org.springframework.cloud.client.ServiceInstance;

import java.util.List;

/**
 * Load Balancer Service Interface
 * Provides load balancing strategies for service instances
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
public interface LoadBalancerService {

    /**
     * Select instances based on load balancing strategy
     *
     * @param instances available instances
     * @param strategy load balancing strategy
     * @param metrics service metrics
     * @return selected instances
     */
    List<ServiceInstance> selectInstances(List<ServiceInstance> instances,
                                         LoadBalancerStrategy strategy,
                                         List<ServiceMetrics> metrics);

    /**
     * Update load balancing strategy for a service
     *
     * @param serviceName service name
     * @param strategy new strategy
     */
    void updateStrategy(String serviceName, LoadBalancerStrategy strategy);
}
