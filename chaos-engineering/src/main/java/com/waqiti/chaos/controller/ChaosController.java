package com.waqiti.chaos.controller;

import com.waqiti.chaos.core.ChaosResult;
import com.waqiti.chaos.orchestrator.ChaosOrchestrator;
import com.waqiti.chaos.orchestrator.ChaosSession;
import com.waqiti.chaos.orchestrator.ChaosSessionConfig;
import com.waqiti.chaos.resilience.ResilienceTestSuite;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chaos")
@Tag(name = "Chaos Engineering", description = "Chaos engineering and resilience testing APIs")
@RequiredArgsConstructor
@Slf4j
public class ChaosController {
    
    private final ChaosOrchestrator chaosOrchestrator;
    private final ResilienceTestSuite resilienceTestSuite;
    
    @PostMapping("/run")
    @Operation(summary = "Run chaos engineering session")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChaosSession> runChaosSession(
            @RequestBody(required = false) ChaosSessionConfig config) {
        
        log.info("Starting chaos session via API");
        ChaosSession session = chaosOrchestrator.runChaosSession(config);
        return ResponseEntity.ok(session);
    }
    
    @PostMapping("/run/minimal")
    @Operation(summary = "Run minimal chaos session for testing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChaosSession> runMinimalChaosSession() {
        
        log.info("Starting minimal chaos session via API");
        ChaosSessionConfig config = ChaosSessionConfig.minimalConfig();
        ChaosSession session = chaosOrchestrator.runChaosSession(config);
        return ResponseEntity.ok(session);
    }
    
    @PostMapping("/resilience/run")
    @Operation(summary = "Run resilience test suite")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChaosResult> runResilienceTests() {
        
        log.info("Starting resilience test suite via API");
        ChaosResult result = resilienceTestSuite.runFullResilienceTest();
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/experiments")
    @Operation(summary = "Get available chaos experiments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getAvailableExperiments() {
        
        List<String> experiments = chaosOrchestrator.getAvailableExperiments();
        return ResponseEntity.ok(experiments);
    }
    
    @GetMapping("/history")
    @Operation(summary = "Get chaos experiment history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, ChaosResult>> getExperimentHistory() {
        
        Map<String, ChaosResult> history = chaosOrchestrator.getExperimentHistory();
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/config/default")
    @Operation(summary = "Get default chaos session configuration")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChaosSessionConfig> getDefaultConfig() {
        
        ChaosSessionConfig config = ChaosSessionConfig.defaultConfig();
        return ResponseEntity.ok(config);
    }
    
    @GetMapping("/status")
    @Operation(summary = "Get chaos engineering system status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        
        Map<String, Object> status = Map.of(
            "available_experiments", chaosOrchestrator.getAvailableExperiments().size(),
            "history_entries", chaosOrchestrator.getExperimentHistory().size(),
            "system_status", "healthy"
        );
        
        return ResponseEntity.ok(status);
    }
}