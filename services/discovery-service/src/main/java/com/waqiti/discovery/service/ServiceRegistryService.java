package com.waqiti.discovery.service;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.waqiti.discovery.domain.*;
import com.waqiti.discovery.dto.*;
import com.waqiti.discovery.repository.*;
import com.waqiti.discovery.event.EventPublisher;
import com.waqiti.discovery.event.ServiceDiscoveryEvent;
import com.waqiti.discovery.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service Registry Service - Enhanced Eureka server with additional features
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRegistryService {

    private final PeerAwareInstanceRegistry registry;
    private final DiscoveryClient discoveryClient;
    private final ServiceMetricsRepository metricsRepository;
    private final ServiceHealthRepository healthRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final ServiceConfigRepository configRepository;
    private final HealthCheckService healthCheckService;
    private final LoadBalancerService loadBalancerService;
    private final NotificationService notificationService;
    private final EventPublisher eventPublisher;
    
    // Service health status cache
    private final ConcurrentHashMap<String, ServiceHealthStatus> healthStatusCache = new ConcurrentHashMap<>();
    
    @Value("${eureka.server.health-check-interval:30}")
    private int healthCheckInterval;
    
    @Value("${eureka.server.metrics-collection-interval:60}")
    private int metricsCollectionInterval;
    
    @Value("${eureka.server.unhealthy-threshold:3}")
    private int unhealthyThreshold;
    
    @Value("${eureka.server.enable-self-preservation:true}")
    private boolean enableSelfPreservation;

    /**
     * Get all registered services
     */
    @Transactional(readOnly = true)
    public List<ServiceInfoDto> getAllServices() {
        List<Application> applications = new ArrayList<>(registry.getSortedApplications());
        
        return applications.stream()
            .map(this::toServiceInfoDto)
            .collect(Collectors.toList());
    }

    /**
     * Get service details by name
     */
    @Transactional(readOnly = true)
    public ServiceDetailsDto getServiceDetails(String serviceName) {
        Application application = registry.getApplication(serviceName.toUpperCase());
        
        if (application == null) {
            throw new ServiceNotFoundException("Service not found: " + serviceName);
        }
        
        List<InstanceInfo> instances = application.getInstances();
        ServiceHealthStatus healthStatus = getServiceHealthStatus(serviceName);
        ServiceMetrics metrics = getLatestServiceMetrics(serviceName);
        List<ServiceDependency> dependencies = getServiceDependencies(serviceName);
        
        return ServiceDetailsDto.builder()
            .serviceName(serviceName)
            .instances(instances.stream()
                .map(this::toInstanceDto)
                .collect(Collectors.toList()))
            .healthStatus(healthStatus)
            .metrics(metrics)
            .dependencies(dependencies.stream()
                .map(this::toDependencyDto)
                .collect(Collectors.toList()))
            .totalInstances(instances.size())
            .healthyInstances((int) instances.stream()
                .filter(i -> i.getStatus() == InstanceInfo.InstanceStatus.UP)
                .count())
            .build();
    }

    /**
     * Get service instances with load balancing
     */
    @Cacheable(value = "service-instances", key = "#serviceName")
    public List<ServiceInstanceDto> getServiceInstances(String serviceName, LoadBalancerStrategy strategy) {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        
        if (instances.isEmpty()) {
            throw new ServiceNotFoundException("No instances found for service: " + serviceName);
        }
        
        // Apply load balancing strategy
        List<ServiceInstance> balancedInstances = loadBalancerService.selectInstances(
            instances, strategy, getServiceMetrics(serviceName)
        );
        
        return balancedInstances.stream()
            .map(this::toServiceInstanceDto)
            .collect(Collectors.toList());
    }

    /**
     * Register service dependencies
     */
    @Transactional
    public void registerServiceDependencies(String serviceName, RegisterDependenciesRequest request) {
        // Remove existing dependencies
        dependencyRepository.deleteByServiceName(serviceName);
        
        // Register new dependencies
        for (String dependency : request.getDependencies()) {
            ServiceDependency serviceDependency = ServiceDependency.builder()
                .serviceName(serviceName)
                .dependencyName(dependency)
                .type(request.getType() != null ? request.getType() : DependencyType.RUNTIME)
                .critical(request.getCriticalDependencies() != null && 
                    request.getCriticalDependencies().contains(dependency))
                .createdAt(Instant.now())
                .build();
            
            dependencyRepository.save(serviceDependency);
        }
        
        log.info("Registered {} dependencies for service {}", 
            request.getDependencies().size(), serviceName);
    }

    /**
     * Get service dependency graph
     */
    @Transactional(readOnly = true)
    public ServiceDependencyGraphDto getServiceDependencyGraph() {
        List<ServiceDependency> allDependencies = dependencyRepository.findAll();
        
        // Build adjacency list
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Set<String>> reverseGraph = new HashMap<>();
        
        for (ServiceDependency dep : allDependencies) {
            graph.computeIfAbsent(dep.getServiceName(), k -> new ArrayList<>())
                .add(dep.getDependencyName());
            
            reverseGraph.computeIfAbsent(dep.getDependencyName(), k -> new HashSet<>())
                .add(dep.getServiceName());
        }
        
        // Find circular dependencies
        List<List<String>> circularDependencies = findCircularDependencies(graph);
        
        // Find isolated services
        Set<String> allServices = new HashSet<>();
        allServices.addAll(graph.keySet());
        allServices.addAll(reverseGraph.keySet());
        
        List<String> isolatedServices = allServices.stream()
            .filter(service -> !graph.containsKey(service) && !reverseGraph.containsKey(service))
            .collect(Collectors.toList());
        
        return ServiceDependencyGraphDto.builder()
            .nodes(allServices.stream()
                .map(service -> ServiceNodeDto.builder()
                    .name(service)
                    .type(getServiceType(service))
                    .healthy(isServiceHealthy(service))
                    .instanceCount(getInstanceCount(service))
                    .build())
                .collect(Collectors.toList()))
            .edges(allDependencies.stream()
                .map(dep -> ServiceEdgeDto.builder()
                    .source(dep.getServiceName())
                    .target(dep.getDependencyName())
                    .type(dep.getType())
                    .critical(dep.isCritical())
                    .build())
                .collect(Collectors.toList()))
            .circularDependencies(circularDependencies)
            .isolatedServices(isolatedServices)
            .build();
    }

    /**
     * Perform health check on service
     */
    @Transactional
    public ServiceHealthCheckResultDto performHealthCheck(String serviceName) {
        Application application = registry.getApplication(serviceName.toUpperCase());
        
        if (application == null) {
            throw new ServiceNotFoundException("Service not found: " + serviceName);
        }
        
        List<InstanceHealthCheckResult> instanceResults = new ArrayList<>();
        
        for (InstanceInfo instance : application.getInstances()) {
            InstanceHealthCheckResult result = healthCheckService.checkInstanceHealth(instance);
            instanceResults.add(result);
            
            // Update instance status if needed
            if (!result.isHealthy() && instance.getStatus() == InstanceInfo.InstanceStatus.UP) {
                instance.setStatus(InstanceInfo.InstanceStatus.DOWN);
                registry.statusUpdate(instance.getAppName(), instance.getId(), 
                    InstanceInfo.InstanceStatus.DOWN, String.valueOf(System.currentTimeMillis()), 
                    false);
            }
        }
        
        // Calculate overall health
        boolean overallHealthy = instanceResults.stream()
            .filter(InstanceHealthCheckResult::isHealthy)
            .count() >= (instanceResults.size() / 2.0); // At least 50% healthy
        
        ServiceHealthStatus healthStatus = ServiceHealthStatus.builder()
            .serviceName(serviceName)
            .status(overallHealthy ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY)
            .healthyInstances((int) instanceResults.stream()
                .filter(InstanceHealthCheckResult::isHealthy)
                .count())
            .totalInstances(instanceResults.size())
            .lastCheckTime(Instant.now())
            .details(instanceResults)
            .build();
        
        // Update cache
        healthStatusCache.put(serviceName, healthStatus);
        
        // Store in database
        ServiceHealth health = ServiceHealth.builder()
            .serviceName(serviceName)
            .status(healthStatus.getStatus())
            .healthyInstances(healthStatus.getHealthyInstances())
            .totalInstances(healthStatus.getTotalInstances())
            .checkTime(Instant.now())
            .responseTime(calculateAverageResponseTime(instanceResults))
            .errorRate(calculateErrorRate(instanceResults))
            .build();
        
        healthRepository.save(health);
        
        // Send alerts if unhealthy
        if (!overallHealthy) {
            notificationService.sendServiceUnhealthyAlert(serviceName, healthStatus);
        }
        
        return ServiceHealthCheckResultDto.builder()
            .serviceName(serviceName)
            .overallHealthy(overallHealthy)
            .healthStatus(healthStatus.getStatus())
            .instanceResults(instanceResults)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Get service metrics
     */
    @Transactional(readOnly = true)
    public ServiceMetricsDto getServiceMetrics(String serviceName, MetricsTimeframe timeframe) {
        Instant startTime = calculateStartTime(timeframe);
        
        List<ServiceMetrics> metrics = metricsRepository.findByServiceNameAndTimestampAfter(
            serviceName, startTime);
        
        if (metrics.isEmpty()) {
            return ServiceMetricsDto.builder()
                .serviceName(serviceName)
                .timeframe(timeframe)
                .noDataAvailable(true)
                .build();
        }
        
        // Calculate aggregated metrics
        double avgResponseTime = metrics.stream()
            .mapToDouble(ServiceMetrics::getAverageResponseTime)
            .average()
            .orElse(0.0);
        
        double avgCpuUsage = metrics.stream()
            .mapToDouble(ServiceMetrics::getCpuUsage)
            .average()
            .orElse(0.0);
        
        double avgMemoryUsage = metrics.stream()
            .mapToDouble(ServiceMetrics::getMemoryUsage)
            .average()
            .orElse(0.0);
        
        long totalRequests = metrics.stream()
            .mapToLong(ServiceMetrics::getRequestCount)
            .sum();
        
        long totalErrors = metrics.stream()
            .mapToLong(ServiceMetrics::getErrorCount)
            .sum();
        
        double errorRate = totalRequests > 0 ? 
            (double) totalErrors / totalRequests * 100 : 0.0;
        
        return ServiceMetricsDto.builder()
            .serviceName(serviceName)
            .timeframe(timeframe)
            .averageResponseTime(avgResponseTime)
            .averageCpuUsage(avgCpuUsage)
            .averageMemoryUsage(avgMemoryUsage)
            .totalRequests(totalRequests)
            .totalErrors(totalErrors)
            .errorRate(errorRate)
            .uptime(calculateUptime(serviceName, startTime))
            .dataPoints(metrics.stream()
                .map(this::toMetricsDataPoint)
                .collect(Collectors.toList()))
            .build();
    }

    /**
     * Enable/disable service
     */
    @Transactional
    public void toggleServiceStatus(String serviceName, boolean enable) {
        Application application = registry.getApplication(serviceName.toUpperCase());
        
        if (application == null) {
            throw new ServiceNotFoundException("Service not found: " + serviceName);
        }
        
        InstanceInfo.InstanceStatus newStatus = enable ? 
            InstanceInfo.InstanceStatus.UP : InstanceInfo.InstanceStatus.OUT_OF_SERVICE;
        
        for (InstanceInfo instance : application.getInstances()) {
            registry.statusUpdate(instance.getAppName(), instance.getId(), 
                newStatus, String.valueOf(System.currentTimeMillis()), false);
        }
        
        // Publish event
        eventPublisher.publish(ServiceDiscoveryEvent.serviceStatusChanged(
            serviceName, enable ? "ENABLED" : "DISABLED"));
        
        log.info("Service {} has been {}", serviceName, enable ? "enabled" : "disabled");
    }

    /**
     * Get service configuration
     */
    @Transactional(readOnly = true)
    public ServiceConfigurationDto getServiceConfiguration(String serviceName) {
        ServiceConfig config = configRepository.findByServiceName(serviceName)
            .orElse(ServiceConfig.builder()
                .serviceName(serviceName)
                .loadBalancerStrategy(LoadBalancerStrategy.ROUND_ROBIN)
                .healthCheckEnabled(true)
                .healthCheckInterval(30)
                .timeout(5000)
                .retryAttempts(3)
                .circuitBreakerEnabled(true)
                .build());
        
        return toServiceConfigurationDto(config);
    }

    /**
     * Update service configuration
     */
    @Transactional
    public ServiceConfigurationDto updateServiceConfiguration(String serviceName, 
                                                            UpdateServiceConfigRequest request) {
        ServiceConfig config = configRepository.findByServiceName(serviceName)
            .orElse(ServiceConfig.builder()
                .serviceName(serviceName)
                .createdAt(Instant.now())
                .build());
        
        // Update configuration
        if (request.getLoadBalancerStrategy() != null) {
            config.setLoadBalancerStrategy(request.getLoadBalancerStrategy());
        }
        if (request.getHealthCheckEnabled() != null) {
            config.setHealthCheckEnabled(request.getHealthCheckEnabled());
        }
        if (request.getHealthCheckInterval() != null) {
            config.setHealthCheckInterval(request.getHealthCheckInterval());
        }
        if (request.getTimeout() != null) {
            config.setTimeout(request.getTimeout());
        }
        if (request.getRetryAttempts() != null) {
            config.setRetryAttempts(request.getRetryAttempts());
        }
        if (request.getCircuitBreakerEnabled() != null) {
            config.setCircuitBreakerEnabled(request.getCircuitBreakerEnabled());
        }
        if (request.getMetadata() != null) {
            config.setMetadata(request.getMetadata());
        }
        
        config.setUpdatedAt(Instant.now());
        config = configRepository.save(config);
        
        // Apply configuration changes
        applyServiceConfiguration(config);
        
        log.info("Updated configuration for service {}", serviceName);
        
        return toServiceConfigurationDto(config);
    }

    /**
     * Scheduled health check for all services
     */
    @Scheduled(fixedRateString = "${eureka.server.health-check-interval:30}000")
    @Transactional
    public void performScheduledHealthChecks() {
        log.debug("Performing scheduled health checks");
        
        List<Application> applications = new ArrayList<>(registry.getSortedApplications());
        
        for (Application app : applications) {
            try {
                performHealthCheck(app.getName());
            } catch (Exception e) {
                log.error("Failed to perform health check for service {}", app.getName(), e);
            }
        }
    }

    /**
     * Scheduled metrics collection
     */
    @Scheduled(fixedRateString = "${eureka.server.metrics-collection-interval:60}000")
    @Transactional
    public void collectServiceMetrics() {
        log.debug("Collecting service metrics");
        
        List<Application> applications = new ArrayList<>(registry.getSortedApplications());
        
        for (Application app : applications) {
            try {
                ServiceMetrics metrics = healthCheckService.collectServiceMetrics(app);
                metricsRepository.save(metrics);
            } catch (Exception e) {
                log.error("Failed to collect metrics for service {}", app.getName(), e);
            }
        }
    }

    /**
     * Clean up old metrics and health data
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldData() {
        Instant cutoffTime = Instant.now().minus(Duration.ofDays(30));
        
        int deletedMetrics = metricsRepository.deleteByTimestampBefore(cutoffTime);
        int deletedHealth = healthRepository.deleteByCheckTimeBefore(cutoffTime);
        
        log.info("Cleaned up {} metric records and {} health records older than 30 days", 
            deletedMetrics, deletedHealth);
    }

    private ServiceHealthStatus getServiceHealthStatus(String serviceName) {
        return healthStatusCache.computeIfAbsent(serviceName, k -> {
            ServiceHealth latestHealth = healthRepository
                .findFirstByServiceNameOrderByCheckTimeDesc(serviceName)
                .orElse(null);
            
            if (latestHealth == null) {
                return ServiceHealthStatus.builder()
                    .serviceName(serviceName)
                    .status(HealthStatus.UNKNOWN)
                    .build();
            }
            
            return ServiceHealthStatus.builder()
                .serviceName(serviceName)
                .status(latestHealth.getStatus())
                .healthyInstances(latestHealth.getHealthyInstances())
                .totalInstances(latestHealth.getTotalInstances())
                .lastCheckTime(latestHealth.getCheckTime())
                .build();
        });
    }

    private ServiceMetrics getLatestServiceMetrics(String serviceName) {
        return metricsRepository.findFirstByServiceNameOrderByTimestampDesc(serviceName)
            .orElse(null);
    }

    private List<ServiceDependency> getServiceDependencies(String serviceName) {
        return dependencyRepository.findByServiceName(serviceName);
    }

    private List<ServiceMetrics> getServiceMetrics(String serviceName) {
        return metricsRepository.findByServiceNameAndTimestampAfter(
            serviceName, Instant.now().minus(Duration.ofMinutes(5)));
    }

    private List<List<String>> findCircularDependencies(Map<String, List<String>> graph) {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                List<String> path = new ArrayList<>();
                findCyclesUtil(node, graph, visited, recursionStack, path, cycles);
            }
        }
        
        return cycles;
    }

    private boolean findCyclesUtil(String node, Map<String, List<String>> graph, 
                                 Set<String> visited, Set<String> recursionStack,
                                 List<String> path, List<List<String>> cycles) {
        visited.add(node);
        recursionStack.add(node);
        path.add(node);
        
        List<String> neighbors = graph.getOrDefault(node, Collections.emptyList());
        for (String neighbor : neighbors) {
            if (!visited.contains(neighbor)) {
                if (findCyclesUtil(neighbor, graph, visited, recursionStack, path, cycles)) {
                    return true;
                }
            } else if (recursionStack.contains(neighbor)) {
                // Found a cycle
                int cycleStart = path.indexOf(neighbor);
                cycles.add(new ArrayList<>(path.subList(cycleStart, path.size())));
            }
        }
        
        path.remove(path.size() - 1);
        recursionStack.remove(node);
        return false;
    }

    private boolean isServiceHealthy(String serviceName) {
        ServiceHealthStatus status = getServiceHealthStatus(serviceName);
        return status.getStatus() == HealthStatus.HEALTHY;
    }

    private int getInstanceCount(String serviceName) {
        Application app = registry.getApplication(serviceName.toUpperCase());
        return app != null ? app.size() : 0;
    }

    private String getServiceType(String serviceName) {
        // Determine service type based on naming convention or configuration
        if (serviceName.endsWith("-service")) {
            return "MICROSERVICE";
        } else if (serviceName.endsWith("-gateway")) {
            return "GATEWAY";
        } else if (serviceName.endsWith("-server")) {
            return "INFRASTRUCTURE";
        }
        return "SERVICE";
    }

    private double calculateAverageResponseTime(List<InstanceHealthCheckResult> results) {
        return results.stream()
            .mapToLong(InstanceHealthCheckResult::getResponseTime)
            .average()
            .orElse(0.0);
    }

    private double calculateErrorRate(List<InstanceHealthCheckResult> results) {
        long totalChecks = results.size();
        long failedChecks = results.stream()
            .filter(r -> !r.isHealthy())
            .count();
        
        return totalChecks > 0 ? (double) failedChecks / totalChecks * 100 : 0.0;
    }

    private Instant calculateStartTime(MetricsTimeframe timeframe) {
        Instant now = Instant.now();
        switch (timeframe) {
            case LAST_HOUR:
                return now.minus(Duration.ofHours(1));
            case LAST_24_HOURS:
                return now.minus(Duration.ofDays(1));
            case LAST_7_DAYS:
                return now.minus(Duration.ofDays(7));
            case LAST_30_DAYS:
                return now.minus(Duration.ofDays(30));
            default:
                return now.minus(Duration.ofHours(1));
        }
    }

    private double calculateUptime(String serviceName, Instant startTime) {
        List<ServiceHealth> healthRecords = healthRepository
            .findByServiceNameAndCheckTimeAfterOrderByCheckTimeAsc(serviceName, startTime);
        
        if (healthRecords.isEmpty()) {
            return 0.0;
        }
        
        long totalTime = Duration.between(startTime, Instant.now()).toMillis();
        long healthyTime = 0;
        
        for (int i = 0; i < healthRecords.size() - 1; i++) {
            ServiceHealth current = healthRecords.get(i);
            ServiceHealth next = healthRecords.get(i + 1);
            
            if (current.getStatus() == HealthStatus.HEALTHY) {
                healthyTime += Duration.between(current.getCheckTime(), next.getCheckTime()).toMillis();
            }
        }
        
        // Add time from last record to now
        ServiceHealth last = healthRecords.get(healthRecords.size() - 1);
        if (last.getStatus() == HealthStatus.HEALTHY) {
            healthyTime += Duration.between(last.getCheckTime(), Instant.now()).toMillis();
        }
        
        return totalTime > 0 ? (double) healthyTime / totalTime * 100 : 0.0;
    }

    private void applyServiceConfiguration(ServiceConfig config) {
        // Apply load balancer configuration
        loadBalancerService.updateStrategy(config.getServiceName(), config.getLoadBalancerStrategy());
        
        // Apply health check configuration
        if (config.isHealthCheckEnabled()) {
            healthCheckService.enableHealthCheck(config.getServiceName(), config.getHealthCheckInterval());
        } else {
            healthCheckService.disableHealthCheck(config.getServiceName());
        }
        
        // Apply circuit breaker configuration
        if (config.isCircuitBreakerEnabled()) {
            // Implementation would integrate with circuit breaker library
        }
    }

    // DTO conversion methods
    private ServiceInfoDto toServiceInfoDto(Application application) {
        List<InstanceInfo> instances = application.getInstances();
        int healthyCount = (int) instances.stream()
            .filter(i -> i.getStatus() == InstanceInfo.InstanceStatus.UP)
            .count();
        
        return ServiceInfoDto.builder()
            .name(application.getName())
            .instanceCount(instances.size())
            .healthyInstanceCount(healthyCount)
            .status(healthyCount > 0 ? "UP" : "DOWN")
            .instances(instances.stream()
                .map(this::toInstanceDto)
                .collect(Collectors.toList()))
            .build();
    }

    private InstanceDto toInstanceDto(InstanceInfo instance) {
        return InstanceDto.builder()
            .instanceId(instance.getId())
            .hostName(instance.getHostName())
            .ipAddress(instance.getIPAddr())
            .port(instance.getPort())
            .securePort(instance.getSecurePort())
            .status(instance.getStatus().toString())
            .homePageUrl(instance.getHomePageUrl())
            .healthCheckUrl(instance.getHealthCheckUrl())
            .metadata(instance.getMetadata())
            .lastUpdatedTimestamp(instance.getLastUpdatedTimestamp())
            .build();
    }

    private ServiceInstanceDto toServiceInstanceDto(ServiceInstance instance) {
        return ServiceInstanceDto.builder()
            .instanceId(instance.getInstanceId())
            .serviceId(instance.getServiceId())
            .host(instance.getHost())
            .port(instance.getPort())
            .secure(instance.isSecure())
            .uri(instance.getUri())
            .metadata(instance.getMetadata())
            .build();
    }

    private ServiceDependencyDto toDependencyDto(ServiceDependency dependency) {
        return ServiceDependencyDto.builder()
            .dependencyName(dependency.getDependencyName())
            .type(dependency.getType())
            .critical(dependency.isCritical())
            .healthy(isServiceHealthy(dependency.getDependencyName()))
            .build();
    }

    private MetricsDataPoint toMetricsDataPoint(ServiceMetrics metrics) {
        return MetricsDataPoint.builder()
            .timestamp(metrics.getTimestamp())
            .responseTime(metrics.getAverageResponseTime())
            .cpuUsage(metrics.getCpuUsage())
            .memoryUsage(metrics.getMemoryUsage())
            .requestCount(metrics.getRequestCount())
            .errorCount(metrics.getErrorCount())
            .build();
    }

    private ServiceConfigurationDto toServiceConfigurationDto(ServiceConfig config) {
        return ServiceConfigurationDto.builder()
            .serviceName(config.getServiceName())
            .loadBalancerStrategy(config.getLoadBalancerStrategy())
            .healthCheckEnabled(config.isHealthCheckEnabled())
            .healthCheckInterval(config.getHealthCheckInterval())
            .timeout(config.getTimeout())
            .retryAttempts(config.getRetryAttempts())
            .circuitBreakerEnabled(config.isCircuitBreakerEnabled())
            .metadata(config.getMetadata())
            .build();
    }
}