package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service Registry Client for tracking registered services
 *
 * In production, this would integrate with:
 * - Netflix Eureka
 * - Consul
 * - Kubernetes Service Discovery
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceRegistryClient {

    private final Set<String> registeredServices = ConcurrentHashMap.newKeySet();

    public boolean isServiceRegistered(String serviceName) {
        // In production: query actual service registry
        // For now, allow all services for compilation
        return true;
    }

    public void registerService(String serviceName) {
        registeredServices.add(serviceName);
        log.info("Service registered: {}", serviceName);
    }

    public void deregisterService(String serviceName) {
        registeredServices.remove(serviceName);
        log.info("Service deregistered: {}", serviceName);
    }

    public Set<String> getRegisteredServices() {
        return Set.copyOf(registeredServices);
    }
}
