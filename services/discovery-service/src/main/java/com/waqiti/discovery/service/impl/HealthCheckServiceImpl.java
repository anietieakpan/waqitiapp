package com.waqiti.discovery.service.impl;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.waqiti.discovery.domain.HealthStatus;
import com.waqiti.discovery.domain.InstanceHealthCheckResult;
import com.waqiti.discovery.domain.ServiceMetrics;
import com.waqiti.discovery.service.HealthCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Health Check Service Implementation
 * Implements health checking logic for service instances
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HealthCheckServiceImpl implements HealthCheckService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public InstanceHealthCheckResult checkInstanceHealth(InstanceInfo instance) {
        log.debug("Checking health for instance: {}", instance.getId());

        long startTime = System.currentTimeMillis();
        InstanceHealthCheckResult result = InstanceHealthCheckResult.builder()
            .instanceId(instance.getId())
            .host(instance.getHostName())
            .port(instance.getPort())
            .checkTime(Instant.now())
            .build();

        try {
            String healthUrl = instance.getHealthCheckUrl();
            if (healthUrl == null || healthUrl.isEmpty()) {
                // No health check URL, assume healthy if instance is UP
                result.setHealthy(instance.getStatus() == InstanceInfo.InstanceStatus.UP);
                result.setStatus(instance.getStatus() == InstanceInfo.InstanceStatus.UP ?
                    HealthStatus.HEALTHY : HealthStatus.UNHEALTHY);
                result.setMessage("No health check URL configured");
                return result;
            }

            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            long responseTime = System.currentTimeMillis() - startTime;

            result.setResponseTime(responseTime);
            result.setStatusCode(response.getStatusCode().value());
            result.setHealthy(response.getStatusCode().is2xxSuccessful());
            result.setStatus(response.getStatusCode().is2xxSuccessful() ?
                HealthStatus.HEALTHY : HealthStatus.UNHEALTHY);
            result.setMessage("Health check successful");

            if (result.isHealthy()) {
                result.setConsecutiveSuccesses(result.getConsecutiveSuccesses() + 1);
                result.setConsecutiveFailures(0);
            }

        } catch (Exception e) {
            log.warn("Health check failed for instance {}: {}", instance.getId(), e.getMessage());
            long responseTime = System.currentTimeMillis() - startTime;

            result.setResponseTime(responseTime);
            result.setHealthy(false);
            result.setStatus(HealthStatus.UNHEALTHY);
            result.setErrorMessage(e.getMessage());
            result.setMessage("Health check failed: " + e.getMessage());
            result.setConsecutiveFailures(result.getConsecutiveFailures() + 1);
            result.setConsecutiveSuccesses(0);
        }

        return result;
    }

    @Override
    public ServiceMetrics collectServiceMetrics(Application application) {
        log.debug("Collecting metrics for service: {}", application.getName());

        ServiceMetrics metrics = ServiceMetrics.builder()
            .metricId(UUID.randomUUID().toString())
            .serviceId(application.getName())
            .metricName("service_metrics")
            .metricType("AGGREGATE")
            .timestamp(Instant.now())
            .build();

        try {
            int totalInstances = application.getInstances().size();
            int healthyInstances = (int) application.getInstances().stream()
                .filter(i -> i.getStatus() == InstanceInfo.InstanceStatus.UP)
                .count();

            // Calculate aggregate metrics
            double avgResponseTime = application.getInstances().stream()
                .mapToDouble(i -> 100.0) // Default value, would come from actual metrics
                .average()
                .orElse(0.0);

            metrics.setAverageResponseTime(avgResponseTime);
            metrics.setMetricValue((double) healthyInstances);
            metrics.setCpuUsage(0.0); // Would be collected from actual metrics endpoint
            metrics.setMemoryUsage(0.0); // Would be collected from actual metrics endpoint
            metrics.setRequestCount(0L); // Would be collected from actual metrics endpoint
            metrics.setErrorCount(0L); // Would be collected from actual metrics endpoint

        } catch (Exception e) {
            log.error("Failed to collect metrics for service {}: {}", application.getName(), e.getMessage());
            metrics.setMetricValue(0.0);
        }

        return metrics;
    }

    @Override
    public void enableHealthCheck(String serviceName, Integer interval) {
        log.info("Enabling health check for service: {} with interval: {}s", serviceName, interval);
        // Implementation would configure health check scheduling
    }

    @Override
    public void disableHealthCheck(String serviceName) {
        log.info("Disabling health check for service: {}", serviceName);
        // Implementation would stop health check scheduling
    }
}
