package com.waqiti.discovery.service.impl;

import com.waqiti.discovery.domain.LoadBalancerStrategy;
import com.waqiti.discovery.domain.ServiceMetrics;
import com.waqiti.discovery.service.LoadBalancerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load Balancer Service Implementation
 * Implements various load balancing strategies
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoadBalancerServiceImpl implements LoadBalancerService {

    private final Map<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();
    private final Map<String, LoadBalancerStrategy> serviceStrategies = new ConcurrentHashMap<>();

    @Override
    public List<ServiceInstance> selectInstances(List<ServiceInstance> instances,
                                                LoadBalancerStrategy strategy,
                                                List<ServiceMetrics> metrics) {
        if (instances == null || instances.isEmpty()) {
            return Collections.emptyList();
        }

        if (strategy == null) {
            strategy = LoadBalancerStrategy.ROUND_ROBIN;
        }

        switch (strategy) {
            case ROUND_ROBIN:
                return selectRoundRobin(instances);
            case RANDOM:
                return selectRandom(instances);
            case LEAST_CONNECTIONS:
                return selectLeastConnections(instances);
            case WEIGHTED_ROUND_ROBIN:
                return selectWeightedRoundRobin(instances, metrics);
            default:
                log.warn("Unknown load balancing strategy: {}, using ROUND_ROBIN", strategy);
                return selectRoundRobin(instances);
        }
    }

    @Override
    public void updateStrategy(String serviceName, LoadBalancerStrategy strategy) {
        log.info("Updating load balancing strategy for service {} to {}", serviceName, strategy);
        serviceStrategies.put(serviceName, strategy);
    }

    private List<ServiceInstance> selectRoundRobin(List<ServiceInstance> instances) {
        if (instances.size() == 1) {
            return instances;
        }

        String serviceId = instances.get(0).getServiceId();
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(serviceId, k -> new AtomicInteger(0));

        int index = Math.abs(counter.getAndIncrement() % instances.size());
        ServiceInstance selected = instances.get(index);

        log.debug("Round-robin selected instance {} for service {}", selected.getInstanceId(), serviceId);
        return Collections.singletonList(selected);
    }

    private List<ServiceInstance> selectRandom(List<ServiceInstance> instances) {
        Random random = new Random();
        int index = random.nextInt(instances.size());
        ServiceInstance selected = instances.get(index);

        log.debug("Random selected instance {}", selected.getInstanceId());
        return Collections.singletonList(selected);
    }

    private List<ServiceInstance> selectLeastConnections(List<ServiceInstance> instances) {
        // Simplified implementation - would track actual connection counts
        ServiceInstance selected = instances.get(0);
        log.debug("Least connections selected instance {}", selected.getInstanceId());
        return Collections.singletonList(selected);
    }

    private List<ServiceInstance> selectWeightedRoundRobin(List<ServiceInstance> instances,
                                                          List<ServiceMetrics> metrics) {
        // Simplified implementation - would use actual weights from metrics
        return selectRoundRobin(instances);
    }
}
