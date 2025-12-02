package com.waqiti.chaos.experiments;

import com.waqiti.chaos.core.ChaosExperiment;
import com.waqiti.chaos.core.ChaosResult;
import com.waqiti.chaos.metrics.ChaosMetrics;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class KubernetesChaosExperiment implements ChaosExperiment {
    
    private final KubernetesClient kubernetesClient;
    private final ChaosMetrics chaosMetrics;
    
    @Value("${chaos.kubernetes.namespace:waqiti}")
    private String namespace;
    
    @Value("${chaos.kubernetes.pod-kill-percentage:30}")
    private int podKillPercentage;
    
    @Value("${chaos.kubernetes.duration:300}")
    private int durationSeconds;
    
    @Value("${chaos.kubernetes.services:payment-service,wallet-service,transaction-service}")
    private List<String> targetServices;
    
    @Override
    public String getName() {
        return "Kubernetes Chaos Experiment";
    }
    
    @Override
    public String getDescription() {
        return "Randomly kills pods, introduces resource constraints, and tests pod recovery";
    }
    
    @Override
    public ChaosResult execute() {
        log.info("Starting Kubernetes chaos experiment in namespace: {}", namespace);
        ChaosResult.Builder resultBuilder = ChaosResult.builder()
            .experimentName(getName())
            .startTime(System.currentTimeMillis());
        
        try {
            // Run different chaos scenarios
            CompletableFuture<Map<String, Object>> podKillResult = runPodKillExperiment();
            CompletableFuture<Map<String, Object>> resourceResult = runResourceStarvationExperiment();
            CompletableFuture<Map<String, Object>> networkPolicyResult = runNetworkPolicyExperiment();
            CompletableFuture<Map<String, Object>> nodeFailureResult = runNodeFailureSimulation();
            
            // Collect results
            CompletableFuture.allOf(podKillResult, resourceResult, networkPolicyResult, nodeFailureResult)
                .get(durationSeconds, TimeUnit.SECONDS);
            
            // Aggregate metrics
            Map<String, Object> aggregatedMetrics = new HashMap<>();
            aggregatedMetrics.putAll(podKillResult.get());
            aggregatedMetrics.putAll(resourceResult.get());
            aggregatedMetrics.putAll(networkPolicyResult.get());
            aggregatedMetrics.putAll(nodeFailureResult.get());
            
            resultBuilder
                .success(true)
                .message("Kubernetes chaos experiment completed successfully")
                .metrics(aggregatedMetrics);
            
        } catch (Exception e) {
            log.error("Kubernetes chaos experiment failed", e);
            resultBuilder
                .success(false)
                .message("Kubernetes chaos experiment failed: " + e.getMessage())
                .error(e);
        }
        
        ChaosResult result = resultBuilder
            .endTime(System.currentTimeMillis())
            .build();
        
        chaosMetrics.recordExperiment(result);
        return result;
    }
    
    private CompletableFuture<Map<String, Object>> runPodKillExperiment() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            int totalPodsKilled = 0;
            Map<String, Integer> recoveryTimes = new HashMap<>();
            
            try {
                log.info("Starting pod kill experiment");
                
                for (String service : targetServices) {
                    // Get pods for service
                    PodList podList = kubernetesClient.pods()
                        .inNamespace(namespace)
                        .withLabel("app", service)
                        .list();
                    
                    List<Pod> pods = podList.getItems();
                    int podsToKill = Math.max(1, (pods.size() * podKillPercentage) / 100);
                    
                    log.info("Found {} pods for service {}, will kill {}", 
                        pods.size(), service, podsToKill);
                    
                    // Randomly select and kill pods
                    Collections.shuffle(pods);
                    for (int i = 0; i < podsToKill && i < pods.size(); i++) {
                        Pod pod = pods.get(i);
                        String podName = pod.getMetadata().getName();
                        
                        long killTime = System.currentTimeMillis();
                        
                        // Delete pod
                        kubernetesClient.pods()
                            .inNamespace(namespace)
                            .withName(podName)
                            .delete();
                        
                        totalPodsKilled++;
                        log.info("Killed pod: {}", podName);
                        
                        // Monitor recovery
                        CompletableFuture.runAsync(() -> {
                            long recoveryTime = monitorPodRecovery(service, killTime);
                            recoveryTimes.put(podName, (int) recoveryTime);
                            chaosMetrics.recordRecoveryTime(service, recoveryTime);
                        });
                    }
                }
                
                // Wait for recovery monitoring to complete
                Thread.sleep(60000); // 1 minute
                
                metrics.put("pods_killed", totalPodsKilled);
                metrics.put("average_recovery_time_ms", 
                    recoveryTimes.values().stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0));
                
                log.info("Pod kill experiment completed. Killed {} pods", totalPodsKilled);
                
            } catch (Exception e) {
                log.error("Pod kill experiment failed", e);
                metrics.put("pod_kill_error", e.getMessage());
            }
            
            return metrics;
        });
    }
    
    private CompletableFuture<Map<String, Object>> runResourceStarvationExperiment() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting resource starvation experiment");
                
                for (String service : targetServices) {
                    // Get deployment
                    var deployment = kubernetesClient.apps().deployments()
                        .inNamespace(namespace)
                        .withName(service)
                        .get();
                    
                    if (deployment != null) {
                        // Store original resource limits
                        var originalResources = deployment.getSpec().getTemplate()
                            .getSpec().getContainers().get(0).getResources();
                        
                        // Reduce resources to 10% of original
                        deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                            .getResources().setLimits(Map.of(
                                "cpu", "100m",
                                "memory", "128Mi"
                            ));
                        
                        kubernetesClient.apps().deployments()
                            .inNamespace(namespace)
                            .withName(service)
                            .patch(deployment);
                        
                        log.info("Applied resource constraints to {}", service);
                        
                        // Monitor performance degradation
                        long degradationStart = System.currentTimeMillis();
                        double errorRate = monitorServiceErrorRate(service, Duration.ofSeconds(60));
                        
                        metrics.put(service + "_error_rate_under_stress", errorRate);
                        metrics.put(service + "_degradation_time_ms", 
                            System.currentTimeMillis() - degradationStart);
                        
                        // Restore original resources
                        deployment.getSpec().getTemplate().getSpec().getContainers().get(0)
                            .setResources(originalResources);
                        
                        kubernetesClient.apps().deployments()
                            .inNamespace(namespace)
                            .withName(service)
                            .patch(deployment);
                        
                        log.info("Restored resources for {}", service);
                    }
                }
                
                metrics.put("resource_starvation_completed", true);
                
            } catch (Exception e) {
                log.error("Resource starvation experiment failed", e);
                metrics.put("resource_starvation_error", e.getMessage());
            }
            
            return metrics;
        });
    }
    
    private CompletableFuture<Map<String, Object>> runNetworkPolicyExperiment() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting network policy chaos experiment");
                
                // Create network policy to block traffic
                String policyName = "chaos-network-policy-" + System.currentTimeMillis();
                
                // Block traffic between payment and wallet services
                kubernetesClient.network().v1().networkPolicies()
                    .inNamespace(namespace)
                    .createNew()
                    .withNewMetadata()
                        .withName(policyName)
                    .endMetadata()
                    .withNewSpec()
                        .withNewPodSelector()
                            .addToMatchLabels("app", "payment-service")
                        .endPodSelector()
                        .addNewEgress()
                            .addNewTo()
                                .withNewPodSelector()
                                    .addToMatchLabels("app", "wallet-service")
                                .endPodSelector()
                            .endTo()
                        .endEgress()
                        .withPolicyTypes("Egress")
                    .endSpec()
                    .done();
                
                log.info("Applied network policy to block payment->wallet traffic");
                
                // Monitor impact
                long startTime = System.currentTimeMillis();
                int failedTransactions = 0;
                int totalTransactions = 0;
                
                for (int i = 0; i < 60; i++) { // 1 minute test
                    totalTransactions++;
                    if (!simulatePaymentTransaction()) {
                        failedTransactions++;
                    }
                    Thread.sleep(1000);
                }
                
                double failureRate = (double) failedTransactions / totalTransactions;
                metrics.put("network_isolation_failure_rate", failureRate);
                metrics.put("network_isolation_duration_ms", 
                    System.currentTimeMillis() - startTime);
                
                // Clean up network policy
                kubernetesClient.network().v1().networkPolicies()
                    .inNamespace(namespace)
                    .withName(policyName)
                    .delete();
                
                log.info("Network policy experiment completed. Failure rate: {}", failureRate);
                
            } catch (Exception e) {
                log.error("Network policy experiment failed", e);
                metrics.put("network_policy_error", e.getMessage());
            }
            
            return metrics;
        });
    }
    
    private CompletableFuture<Map<String, Object>> runNodeFailureSimulation() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> metrics = new HashMap<>();
            
            try {
                log.info("Starting node failure simulation");
                
                // Get nodes
                var nodes = kubernetesClient.nodes().list().getItems();
                if (nodes.size() < 2) {
                    log.warn("Not enough nodes for node failure simulation");
                    metrics.put("node_failure_skipped", true);
                    return metrics;
                }
                
                // Select a node to cordon (make unschedulable)
                var targetNode = nodes.get(ThreadLocalRandom.current().nextInt(nodes.size()));
                String nodeName = targetNode.getMetadata().getName();
                
                // Cordon node
                targetNode.getSpec().setUnschedulable(true);
                kubernetesClient.nodes().withName(nodeName).patch(targetNode);
                log.info("Cordoned node: {}", nodeName);
                
                // Evict pods from node
                var podsOnNode = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withField("spec.nodeName", nodeName)
                    .list()
                    .getItems();
                
                int evictedPods = 0;
                for (Pod pod : podsOnNode) {
                    try {
                        kubernetesClient.pods()
                            .inNamespace(namespace)
                            .withName(pod.getMetadata().getName())
                            .evict();
                        evictedPods++;
                    } catch (Exception e) {
                        log.warn("Failed to evict pod: {}", pod.getMetadata().getName());
                    }
                }
                
                metrics.put("pods_evicted", evictedPods);
                
                // Monitor rescheduling
                long rescheduleStart = System.currentTimeMillis();
                int rescheduledPods = monitorPodRescheduling(evictedPods);
                long rescheduleTime = System.currentTimeMillis() - rescheduleStart;
                
                metrics.put("pods_rescheduled", rescheduledPods);
                metrics.put("reschedule_time_ms", rescheduleTime);
                
                // Uncordon node
                targetNode.getSpec().setUnschedulable(false);
                kubernetesClient.nodes().withName(nodeName).patch(targetNode);
                log.info("Uncordoned node: {}", nodeName);
                
            } catch (Exception e) {
                log.error("Node failure simulation failed", e);
                metrics.put("node_failure_error", e.getMessage());
            }
            
            return metrics;
        });
    }
    
    private long monitorPodRecovery(String service, long killTime) {
        try {
            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < 300000) { // 5 minutes max
                PodList podList = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("app", service)
                    .list();
                
                long readyPods = podList.getItems().stream()
                    .filter(pod -> isPodReady(pod))
                    .count();
                
                if (readyPods >= 1) { // At least one pod is ready
                    return System.currentTimeMillis() - killTime;
                }
                
                Thread.sleep(1000);
            }
            
        } catch (Exception e) {
            log.error("Error monitoring pod recovery", e);
        }
        
        return -1; // Recovery failed
    }
    
    private boolean isPodReady(Pod pod) {
        return pod.getStatus().getConditions().stream()
            .anyMatch(condition -> 
                "Ready".equals(condition.getType()) && 
                "True".equals(condition.getStatus())
            );
    }
    
    private double monitorServiceErrorRate(String service, Duration duration) {
        // In real implementation, query metrics from Prometheus
        // For now, simulate error rate
        return 0.15 + ThreadLocalRandom.current().nextDouble(0.35);
    }
    
    private boolean simulatePaymentTransaction() {
        // Simulate payment transaction success/failure
        return ThreadLocalRandom.current().nextDouble() > 0.3;
    }
    
    private int monitorPodRescheduling(int expectedPods) {
        try {
            Thread.sleep(30000); // Wait 30 seconds for rescheduling
            
            // Count running pods
            return (int) kubernetesClient.pods()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(pod -> "Running".equals(pod.getStatus().getPhase()))
                .count();
                
        } catch (Exception e) {
            log.error("Error monitoring pod rescheduling", e);
            return 0;
        }
    }
    
    @Override
    public void cleanup() {
        log.info("Cleaning up Kubernetes chaos experiment");
        // Cleanup is handled within each experiment
    }
}