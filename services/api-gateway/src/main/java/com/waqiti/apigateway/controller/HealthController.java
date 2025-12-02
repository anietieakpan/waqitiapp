package com.waqiti.apigateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Health check controller for API Gateway
 * Provides service discovery status and routing information
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class HealthController {

    private final DiscoveryClient discoveryClient;

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "api-gateway");
        
        return Mono.just(ResponseEntity.ok(health));
    }

    @GetMapping("/health/services")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM', 'DEVOPS')")
    public Mono<ResponseEntity<Map<String, Object>>> serviceHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now());
        
        // Get all registered services
        List<String> services = discoveryClient.getServices();
        Map<String, Object> serviceDetails = new HashMap<>();
        
        for (String service : services) {
            List<ServiceInstance> instances = discoveryClient.getInstances(service);
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("instanceCount", instances.size());
            serviceInfo.put("instances", instances.stream()
                .map(instance -> {
                    Map<String, Object> instanceInfo = new HashMap<>();
                    instanceInfo.put("uri", instance.getUri().toString());
                    instanceInfo.put("serviceId", instance.getServiceId());
                    instanceInfo.put("host", instance.getHost());
                    instanceInfo.put("port", instance.getPort());
                    instanceInfo.put("secure", instance.isSecure());
                    instanceInfo.put("metadata", instance.getMetadata());
                    return instanceInfo;
                })
                .collect(Collectors.toList()));
            serviceDetails.put(service, serviceInfo);
        }
        
        response.put("services", serviceDetails);
        response.put("totalServices", services.size());
        
        return Mono.just(ResponseEntity.ok(response));
    }

    @GetMapping("/routes")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM', 'DEVOPS')")
    public Mono<ResponseEntity<Map<String, Object>>> routes() {
        Map<String, Object> routes = new HashMap<>();
        routes.put("timestamp", LocalDateTime.now());
        
        // Document available routes
        Map<String, String> availableRoutes = new HashMap<>();
        availableRoutes.put("/api/v1/users/**", "User management and authentication");
        availableRoutes.put("/api/v1/auth/**", "Authentication endpoints");
        availableRoutes.put("/api/v1/payments/**", "Payment processing");
        availableRoutes.put("/api/v1/transactions/**", "Transaction management");
        availableRoutes.put("/api/v1/wallets/**", "Wallet operations");
        availableRoutes.put("/api/v1/balance/**", "Balance inquiries");
        availableRoutes.put("/api/v1/notifications/**", "Notification service");
        availableRoutes.put("/api/v1/integration/**", "External integrations");
        availableRoutes.put("/api/v1/banking/**", "Banking operations");
        availableRoutes.put("/api/v1/analytics/**", "Analytics and reporting");
        availableRoutes.put("/api/v1/reports/**", "Report generation");
        availableRoutes.put("/api/v1/security/**", "Security operations");
        availableRoutes.put("/api/v1/kyc/**", "KYC verification");
        availableRoutes.put("/api/v1/admin/**", "Admin operations");
        
        routes.put("routes", availableRoutes);
        routes.put("totalRoutes", availableRoutes.size());
        
        return Mono.just(ResponseEntity.ok(routes));
    }
}