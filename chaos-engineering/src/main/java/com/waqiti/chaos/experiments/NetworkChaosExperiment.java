package com.waqiti.chaos.experiments;

import com.waqiti.chaos.core.ChaosExperiment;
import com.waqiti.chaos.core.ChaosResult;
import com.waqiti.chaos.metrics.ChaosMetrics;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class NetworkChaosExperiment implements ChaosExperiment {
    
    private final ToxiproxyClient toxiproxyClient;
    private final ChaosMetrics chaosMetrics;
    
    @Value("${chaos.network.target-service:payment-service}")
    private String targetService;
    
    @Value("${chaos.network.duration:60}")
    private int durationSeconds;
    
    @Override
    public String getName() {
        return "Network Chaos Experiment";
    }
    
    @Override
    public String getDescription() {
        return "Introduces network failures including latency, packet loss, and connection failures";
    }
    
    @Override
    public ChaosResult execute() {
        log.info("Starting network chaos experiment for service: {}", targetService);
        ChaosResult.Builder resultBuilder = ChaosResult.builder()
            .experimentName(getName())
            .startTime(System.currentTimeMillis());
        
        try {
            // Create proxy for target service
            Proxy proxy = getOrCreateProxy(targetService);
            
            // Run different network chaos scenarios
            CompletableFuture<Void> latencyTest = runLatencyTest(proxy);
            CompletableFuture<Void> packetLossTest = runPacketLossTest(proxy);
            CompletableFuture<Void> bandwidthTest = runBandwidthLimitTest(proxy);
            CompletableFuture<Void> connectionTest = runConnectionFailureTest(proxy);
            
            // Wait for all tests to complete
            CompletableFuture.allOf(latencyTest, packetLossTest, bandwidthTest, connectionTest)
                .get(durationSeconds * 4, TimeUnit.SECONDS);
            
            resultBuilder
                .success(true)
                .message("Network chaos experiment completed successfully")
                .addMetric("total_tests", 4)
                .addMetric("duration_seconds", durationSeconds * 4);
            
        } catch (Exception e) {
            log.error("Network chaos experiment failed", e);
            resultBuilder
                .success(false)
                .message("Network chaos experiment failed: " + e.getMessage())
                .error(e);
        }
        
        ChaosResult result = resultBuilder
            .endTime(System.currentTimeMillis())
            .build();
        
        chaosMetrics.recordExperiment(result);
        return result;
    }
    
    private Proxy getOrCreateProxy(String serviceName) throws IOException {
        String proxyName = serviceName + "-proxy";
        try {
            return toxiproxyClient.getProxy(proxyName);
        } catch (IOException e) {
            // Create proxy if it doesn't exist
            return toxiproxyClient.createProxy(
                proxyName,
                "0.0.0.0:8474",
                serviceName + ":8080"
            );
        }
    }
    
    private CompletableFuture<Void> runLatencyTest(Proxy proxy) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Introducing latency to {}", proxy.getName());
                
                // Add 500ms latency with 100ms jitter
                proxy.toxics()
                    .latency("latency-toxic", ToxicDirection.DOWNSTREAM, 500)
                    .setJitter(100);
                
                // Measure impact
                long startTime = System.currentTimeMillis();
                chaosMetrics.startLatencyMeasurement(targetService);
                
                Thread.sleep(Duration.ofSeconds(durationSeconds).toMillis());
                
                chaosMetrics.recordLatencyImpact(
                    targetService,
                    System.currentTimeMillis() - startTime
                );
                
                // Remove toxic
                proxy.toxics().get("latency-toxic").remove();
                
                log.info("Latency test completed for {}", proxy.getName());
                
            } catch (Exception e) {
                log.error("Latency test failed", e);
            }
        });
    }
    
    private CompletableFuture<Void> runPacketLossTest(Proxy proxy) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Introducing packet loss to {}", proxy.getName());
                
                // Add 25% packet loss
                proxy.toxics()
                    .bandwidth("packet-loss-toxic", ToxicDirection.DOWNSTREAM, 0.75f);
                
                // Measure impact
                long startTime = System.currentTimeMillis();
                int errorCount = 0;
                int totalRequests = 0;
                
                long endTime = startTime + Duration.ofSeconds(durationSeconds).toMillis();
                while (System.currentTimeMillis() < endTime) {
                    totalRequests++;
                    boolean success = simulateRequest(proxy.getName());
                    if (!success) {
                        errorCount++;
                    }
                    Thread.sleep(100); // 10 requests per second
                }
                
                double errorRate = (double) errorCount / totalRequests;
                chaosMetrics.recordErrorRate(targetService, errorRate);
                
                // Remove toxic
                proxy.toxics().get("packet-loss-toxic").remove();
                
                log.info("Packet loss test completed. Error rate: {}", errorRate);
                
            } catch (Exception e) {
                log.error("Packet loss test failed", e);
            }
        });
    }
    
    private CompletableFuture<Void> runBandwidthLimitTest(Proxy proxy) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Limiting bandwidth for {}", proxy.getName());
                
                // Limit bandwidth to 64KB/s
                proxy.toxics()
                    .bandwidth("bandwidth-toxic", ToxicDirection.DOWNSTREAM, 64);
                
                // Measure throughput
                long startTime = System.currentTimeMillis();
                long bytesTransferred = 0;
                
                long endTime = startTime + Duration.ofSeconds(durationSeconds).toMillis();
                while (System.currentTimeMillis() < endTime) {
                    bytesTransferred += simulateLargeDataTransfer(proxy.getName());
                    Thread.sleep(1000);
                }
                
                double throughput = bytesTransferred / (double) durationSeconds;
                chaosMetrics.recordThroughput(targetService, throughput);
                
                // Remove toxic
                proxy.toxics().get("bandwidth-toxic").remove();
                
                log.info("Bandwidth test completed. Throughput: {} bytes/s", throughput);
                
            } catch (Exception e) {
                log.error("Bandwidth test failed", e);
            }
        });
    }
    
    private CompletableFuture<Void> runConnectionFailureTest(Proxy proxy) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Simulating connection failures for {}", proxy.getName());
                
                // Simulate connection reset
                proxy.toxics()
                    .resetPeer("reset-toxic", ToxicDirection.DOWNSTREAM, 10000);
                
                // Measure recovery time
                long startTime = System.currentTimeMillis();
                int failures = 0;
                int recoveries = 0;
                
                long endTime = startTime + Duration.ofSeconds(durationSeconds).toMillis();
                while (System.currentTimeMillis() < endTime) {
                    if (!isServiceHealthy(proxy.getName())) {
                        failures++;
                        long recoveryStart = System.currentTimeMillis();
                        
                        // Wait for recovery
                        while (!isServiceHealthy(proxy.getName()) && 
                               System.currentTimeMillis() < endTime) {
                            Thread.sleep(100);
                        }
                        
                        if (isServiceHealthy(proxy.getName())) {
                            recoveries++;
                            long recoveryTime = System.currentTimeMillis() - recoveryStart;
                            chaosMetrics.recordRecoveryTime(targetService, recoveryTime);
                        }
                    }
                    
                    Thread.sleep(5000); // Check every 5 seconds
                }
                
                // Remove toxic
                proxy.toxics().get("reset-toxic").remove();
                
                log.info("Connection failure test completed. Failures: {}, Recoveries: {}", 
                    failures, recoveries);
                
            } catch (Exception e) {
                log.error("Connection failure test failed", e);
            }
        });
    }
    
    private boolean simulateRequest(String serviceName) {
        // Simulate HTTP request to service
        try {
            // In real implementation, make actual HTTP call
            Thread.sleep(10);
            return ThreadLocalRandom.current().nextDouble() > 0.1; // 90% success rate baseline
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    private long simulateLargeDataTransfer(String serviceName) {
        // Simulate large data transfer
        try {
            Thread.sleep(100);
            return 1024 * 1024; // 1MB
        } catch (InterruptedException e) {
            return 0;
        }
    }
    
    private boolean isServiceHealthy(String serviceName) {
        // Check service health
        try {
            // In real implementation, call health endpoint
            return ThreadLocalRandom.current().nextDouble() > 0.2; // 80% healthy baseline
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public void cleanup() {
        try {
            // Clean up all proxies
            toxiproxyClient.getProxies().forEach(proxy -> {
                try {
                    proxy.delete();
                } catch (IOException e) {
                    log.error("Failed to delete proxy: {}", proxy.getName(), e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to cleanup proxies", e);
        }
    }
}