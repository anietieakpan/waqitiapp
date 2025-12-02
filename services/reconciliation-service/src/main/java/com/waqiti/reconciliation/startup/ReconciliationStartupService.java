package com.waqiti.reconciliation.startup;

import com.waqiti.reconciliation.command.ReconciliationCommand;
import com.waqiti.reconciliation.executor.ReconciliationCommandExecutor;
import com.waqiti.reconciliation.model.ReconciliationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for startup reconciliation operations
 * Uses the secure command pattern instead of direct @Async methods
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationStartupService {
    
    private final ReconciliationCommandExecutor commandExecutor;
    
    @Value("${reconciliation.startup.enabled:true}")
    private boolean startupReconciliationEnabled;
    
    @Value("${reconciliation.startup.delay-seconds:30}")
    private int startupDelaySeconds;
    
    @Value("${reconciliation.startup.cutoff-hours:24}")
    private int startupCutoffHours;
    
    /**
     * Perform startup reconciliation after application is ready
     * This replaces the problematic @Async private method
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (!startupReconciliationEnabled) {
            log.info("Startup reconciliation is disabled");
            return;
        }
        
        log.info("Application ready - scheduling startup reconciliation in {} seconds", 
                 startupDelaySeconds);
        
        // Schedule startup reconciliation
        CompletableFuture.delayedExecutor(startupDelaySeconds, 
                                         java.util.concurrent.TimeUnit.SECONDS)
            .execute(this::executeStartupReconciliation);
    }
    
    /**
     * Execute startup reconciliation using the secure command pattern
     */
    private void executeStartupReconciliation() {
        try {
            log.info("Executing startup reconciliation");
            
            // Create startup reconciliation command
            ReconciliationCommand command = ReconciliationCommand.builder()
                    .type(ReconciliationCommand.ReconciliationType.STARTUP_RECONCILIATION)
                    .scope(ReconciliationCommand.ReconciliationScope.PENDING_ONLY)
                    .initiatedBy("SYSTEM")
                    .cutoffTime(LocalDateTime.now().minusHours(startupCutoffHours))
                    .priority(1) // High priority for startup
                    .reason("Automated startup reconciliation")
                    .context(ReconciliationCommand.ReconciliationContext.builder()
                            .correlationId("startup-" + System.currentTimeMillis())
                            .sourceSystem("reconciliation-startup-service")
                            .dryRun(false)
                            .generateReport(true)
                            .notifyOnCompletion(true)
                            .notificationRecipients(new String[]{"ops@example.com"})
                            .build())
                    .build();
            
            // Execute through the secure command executor
            CompletableFuture<ReconciliationResult> future = commandExecutor.execute(command);
            
            // Handle result asynchronously
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("Startup reconciliation failed", throwable);
                } else {
                    log.info("Startup reconciliation completed successfully: {} transactions processed", 
                             result != null ? result.getTotalTransactions() : 0);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to execute startup reconciliation", e);
        }
    }
    
    /**
     * Manual trigger for startup reconciliation (for admin use)
     */
    public CompletableFuture<ReconciliationResult> triggerManualStartupReconciliation(String initiatedBy) {
        log.info("Manual startup reconciliation triggered by: {}", initiatedBy);
        
        ReconciliationCommand command = ReconciliationCommand.builder()
                .type(ReconciliationCommand.ReconciliationType.MANUAL_RECONCILIATION)
                .scope(ReconciliationCommand.ReconciliationScope.ALL_TRANSACTIONS)
                .initiatedBy(initiatedBy)
                .cutoffTime(LocalDateTime.now().minusHours(startupCutoffHours))
                .priority(2)
                .reason("Manual startup reconciliation trigger")
                .forceRun(true)
                .context(ReconciliationCommand.ReconciliationContext.builder()
                        .correlationId("manual-startup-" + System.currentTimeMillis())
                        .sourceSystem("reconciliation-startup-service")
                        .dryRun(false)
                        .generateReport(true)
                        .notifyOnCompletion(true)
                        .build())
                .build();
        
        return commandExecutor.execute(command);
    }
}