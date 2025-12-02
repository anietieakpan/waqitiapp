package com.waqiti.chaos.orchestrator;

import com.waqiti.chaos.core.ChaosExperiment;
import com.waqiti.chaos.core.ChaosResult;
import com.waqiti.chaos.metrics.ChaosMetrics;
import com.waqiti.chaos.reporting.ChaosReportGenerator;
import com.waqiti.chaos.resilience.ResilienceTestSuite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChaosOrchestrator {
    
    private final List<ChaosExperiment> experiments;
    private final ResilienceTestSuite resilienceTestSuite;
    private final ChaosMetrics chaosMetrics;
    private final ChaosReportGenerator reportGenerator;
    
    @Value("${chaos.enabled:false}")
    private boolean chaosEnabled;
    
    @Value("${chaos.schedule.enabled:false}")
    private boolean scheduleEnabled;
    
    @Value("${chaos.experiment.parallel:false}")
    private boolean runParallel;
    
    @Value("${chaos.experiment.random-order:true}")
    private boolean randomOrder;
    
    @Value("${chaos.experiment.selection:all}")
    private String experimentSelection;
    
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, ChaosResult> experimentHistory = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        log.info("Chaos Engineering initialized. Enabled: {}, Scheduled: {}", 
            chaosEnabled, scheduleEnabled);
        log.info("Found {} chaos experiments", experiments.size());
    }
    
    @Scheduled(cron = "${chaos.schedule.cron:0 0 2 * * ?}") // Default: 2 AM daily
    public void scheduledChaosRun() {
        if (!chaosEnabled || !scheduleEnabled) {
            log.debug("Scheduled chaos run skipped. Enabled: {}, Scheduled: {}", 
                chaosEnabled, scheduleEnabled);
            return;
        }
        
        log.info("Starting scheduled chaos engineering run");
        runChaosSession();
    }
    
    public ChaosSession runChaosSession() {
        return runChaosSession(null);
    }
    
    public ChaosSession runChaosSession(ChaosSessionConfig config) {
        if (config == null) {
            config = ChaosSessionConfig.defaultConfig();
        }
        
        log.info("Starting chaos session with config: {}", config);
        
        ChaosSession session = ChaosSession.builder()
            .sessionId(UUID.randomUUID().toString())
            .startTime(LocalDateTime.now())
            .config(config)
            .build();
        
        try {
            // Select experiments to run
            List<ChaosExperiment> selectedExperiments = selectExperiments(config);
            session.setTotalExperiments(selectedExperiments.size());
            
            // Run pre-chaos validation
            if (config.isRunValidation()) {
                log.info("Running pre-chaos validation");
                ValidationResult validation = runValidation();
                session.setPreValidation(validation);
                
                if (!validation.isSystemHealthy()) {
                    log.error("System not healthy for chaos testing. Aborting.");
                    session.setAborted(true);
                    return session;
                }
            }
            
            // Run experiments
            List<ChaosResult> results;
            if (runParallel && config.isAllowParallel()) {
                results = runExperimentsParallel(selectedExperiments);
            } else {
                results = runExperimentsSequential(selectedExperiments);
            }
            
            session.setResults(results);
            
            // Run resilience tests
            if (config.isIncludeResilienceTests()) {
                log.info("Running resilience test suite");
                ChaosResult resilienceResult = resilienceTestSuite.runFullResilienceTest();
                session.setResilienceTestResult(resilienceResult);
            }
            
            // Run post-chaos validation
            if (config.isRunValidation()) {
                log.info("Running post-chaos validation");
                ValidationResult postValidation = runValidation();
                session.setPostValidation(postValidation);
                
                // Check for degradation
                if (postValidation.getHealthScore() < 
                    session.getPreValidation().getHealthScore() * 0.9) {
                    log.warn("System health degraded by more than 10% after chaos");
                    session.setDegraded(true);
                }
            }
            
            // Store in history
            experimentHistory.put(session.getSessionId(), 
                aggregateResults(session.getResults()));
            
        } catch (Exception e) {
            log.error("Chaos session failed", e);
            session.setError(e);
        } finally {
            session.setEndTime(LocalDateTime.now());
            
            // Generate report
            if (config.isGenerateReport()) {
                reportGenerator.generateReport(session);
            }
            
            // Cleanup
            cleanupAfterSession();
        }
        
        return session;
    }
    
    private List<ChaosExperiment> selectExperiments(ChaosSessionConfig config) {
        List<ChaosExperiment> selected = new ArrayList<>();
        
        if ("all".equals(experimentSelection)) {
            selected.addAll(experiments);
        } else if ("random".equals(experimentSelection)) {
            // Select random subset
            int count = Math.min(config.getMaxExperiments(), experiments.size());
            List<ChaosExperiment> shuffled = new ArrayList<>(experiments);
            Collections.shuffle(shuffled);
            selected.addAll(shuffled.subList(0, count));
        } else {
            // Select by name pattern
            String[] patterns = experimentSelection.split(",");
            for (ChaosExperiment exp : experiments) {
                for (String pattern : patterns) {
                    if (exp.getName().toLowerCase().contains(pattern.trim().toLowerCase())) {
                        selected.add(exp);
                        break;
                    }
                }
            }
        }
        
        // Filter by safety check
        selected = selected.stream()
            .filter(exp -> exp.canRun())
            .collect(Collectors.toList());
        
        // Randomize order if configured
        if (randomOrder) {
            Collections.shuffle(selected);
        }
        
        return selected;
    }
    
    private List<ChaosResult> runExperimentsSequential(List<ChaosExperiment> experiments) {
        List<ChaosResult> results = new ArrayList<>();
        
        for (ChaosExperiment experiment : experiments) {
            log.info("Running experiment: {}", experiment.getName());
            
            try {
                ChaosResult result = experiment.execute();
                results.add(result);
                
                // Check if we should continue
                if (!result.isSuccess() && shouldStopOnFailure()) {
                    log.warn("Stopping chaos session due to experiment failure");
                    break;
                }
                
                // Delay between experiments
                Thread.sleep(getDelayBetweenExperiments());
                
            } catch (Exception e) {
                log.error("Failed to run experiment: {}", experiment.getName(), e);
                ChaosResult failureResult = ChaosResult.builder()
                    .experimentName(experiment.getName())
                    .success(false)
                    .message("Experiment failed: " + e.getMessage())
                    .error(e)
                    .build();
                results.add(failureResult);
                
                if (shouldStopOnFailure()) {
                    break;
                }
            } finally {
                // Always cleanup
                try {
                    experiment.cleanup();
                } catch (Exception e) {
                    log.error("Cleanup failed for experiment: {}", experiment.getName(), e);
                }
            }
        }
        
        return results;
    }
    
    private List<ChaosResult> runExperimentsParallel(List<ChaosExperiment> experiments) {
        List<CompletableFuture<ChaosResult>> futures = experiments.stream()
            .map(exp -> CompletableFuture.supplyAsync(() -> {
                log.info("Running experiment in parallel: {}", exp.getName());
                try {
                    return exp.execute();
                } catch (Exception e) {
                    log.error("Failed to run experiment: {}", exp.getName(), e);
                    return ChaosResult.builder()
                        .experimentName(exp.getName())
                        .success(false)
                        .message("Experiment failed: " + e.getMessage())
                        .error(e)
                        .build();
                } finally {
                    try {
                        exp.cleanup();
                    } catch (Exception e) {
                        log.error("Cleanup failed for experiment: {}", exp.getName(), e);
                    }
                }
            }, executor))
            .collect(Collectors.toList());
        
        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }
    
    private ValidationResult runValidation() {
        ValidationResult result = new ValidationResult();
        
        try {
            // Check system metrics
            result.setCpuUsage(getSystemCpuUsage());
            result.setMemoryUsage(getSystemMemoryUsage());
            result.setDiskUsage(getSystemDiskUsage());
            
            // Check service health
            Map<String, Boolean> serviceHealth = checkAllServicesHealth();
            result.setServiceHealth(serviceHealth);
            
            // Calculate overall health score
            double healthScore = calculateHealthScore(result);
            result.setHealthScore(healthScore);
            result.setSystemHealthy(healthScore > 0.7); // 70% threshold
            
        } catch (Exception e) {
            log.error("Validation failed", e);
            result.setSystemHealthy(false);
            result.setError(e.getMessage());
        }
        
        return result;
    }
    
    private double getSystemCpuUsage() {
        // In real implementation, query system metrics
        return 0.3 + ThreadLocalRandom.current().nextDouble() * 0.4; // 30-70%
    }
    
    private double getSystemMemoryUsage() {
        return 0.4 + ThreadLocalRandom.current().nextDouble() * 0.3; // 40-70%
    }
    
    private double getSystemDiskUsage() {
        return 0.2 + ThreadLocalRandom.current().nextDouble() * 0.3; // 20-50%
    }
    
    private Map<String, Boolean> checkAllServicesHealth() {
        Map<String, Boolean> health = new HashMap<>();
        
        // Check critical services
        health.put("payment-service", checkServiceHealth("payment-service"));
        health.put("wallet-service", checkServiceHealth("wallet-service"));
        health.put("transaction-service", checkServiceHealth("transaction-service"));
        health.put("user-service", checkServiceHealth("user-service"));
        health.put("notification-service", checkServiceHealth("notification-service"));
        
        return health;
    }
    
    private boolean checkServiceHealth(String service) {
        // In real implementation, call health endpoint
        return ThreadLocalRandom.current().nextDouble() > 0.1; // 90% healthy
    }
    
    private double calculateHealthScore(ValidationResult validation) {
        double score = 1.0;
        
        // Deduct for high resource usage
        if (validation.getCpuUsage() > 0.8) score -= 0.2;
        if (validation.getMemoryUsage() > 0.8) score -= 0.2;
        if (validation.getDiskUsage() > 0.8) score -= 0.1;
        
        // Deduct for unhealthy services
        long unhealthyServices = validation.getServiceHealth().values().stream()
            .filter(healthy -> !healthy)
            .count();
        score -= unhealthyServices * 0.1;
        
        return Math.max(0, score);
    }
    
    private ChaosResult aggregateResults(List<ChaosResult> results) {
        Map<String, Object> aggregatedMetrics = new HashMap<>();
        
        int successCount = 0;
        int failureCount = 0;
        long totalDuration = 0;
        
        for (ChaosResult result : results) {
            if (result.isSuccess()) {
                successCount++;
            } else {
                failureCount++;
            }
            
            if (result.getDuration() != null) {
                totalDuration += result.getDuration().toMillis();
            }
            
            if (result.getMetrics() != null) {
                result.getMetrics().forEach((key, value) -> {
                    aggregatedMetrics.put(result.getExperimentName() + "." + key, value);
                });
            }
        }
        
        aggregatedMetrics.put("total_experiments", results.size());
        aggregatedMetrics.put("successful_experiments", successCount);
        aggregatedMetrics.put("failed_experiments", failureCount);
        aggregatedMetrics.put("total_duration_ms", totalDuration);
        
        return ChaosResult.builder()
            .experimentName("Aggregated Results")
            .success(failureCount == 0)
            .metrics(aggregatedMetrics)
            .build();
    }
    
    private void cleanupAfterSession() {
        log.info("Cleaning up after chaos session");
        // Any global cleanup needed
    }
    
    @Value("${chaos.stop-on-failure:true}")
    private boolean stopOnFailure;
    
    private boolean shouldStopOnFailure() {
        return stopOnFailure;
    }
    
    @Value("${chaos.delay-between-experiments:5000}")
    private long delayBetweenExperiments;
    
    private long getDelayBetweenExperiments() {
        return delayBetweenExperiments;
    }
    
    public List<String> getAvailableExperiments() {
        return experiments.stream()
            .map(ChaosExperiment::getName)
            .collect(Collectors.toList());
    }
    
    public Map<String, ChaosResult> getExperimentHistory() {
        return new HashMap<>(experimentHistory);
    }
}