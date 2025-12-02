package com.waqiti.customer.kafka;

import com.waqiti.common.events.CustomerMigrationEvent;
import com.waqiti.customer.domain.MigrationProcess;
import com.waqiti.customer.repository.MigrationProcessRepository;
import com.waqiti.customer.service.AccountMigrationService;
import com.waqiti.customer.service.DataMigrationService;
import com.waqiti.customer.service.LegacySystemIntegrationService;
import com.waqiti.customer.metrics.MigrationMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

/**
 * Customer Migration Events Consumer
 * Processes customer account migrations between systems, platforms, or tiers
 * Implements 12-step zero-tolerance processing for seamless migrations
 * 
 * Business Context:
 * - Platform migrations (legacy to modern system)
 * - Tier upgrades/downgrades (Basic → Premium → VIP)
 * - Account type conversions (Individual → Business)
 * - Regional migrations (cross-border account transfers)
 * - Bank mergers and acquisitions
 * - System consolidations
 * 
 * @author Waqiti Customer Success Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerMigrationEventsConsumer {
    
    private final MigrationProcessRepository migrationRepository;
    private final AccountMigrationService accountMigrationService;
    private final DataMigrationService dataMigrationService;
    private final LegacySystemIntegrationService legacySystemService;
    private final MigrationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MIGRATION_BATCH_SIZE = 100;
    private static final int MAX_MIGRATION_DURATION_HOURS = 24;
    
    @KafkaListener(
        topics = {"customer-migration-events", "account-migration-events", "platform-migration-events"},
        groupId = "customer-migration-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 5000, multiplier = 2.0, maxDelay = 60000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 300)
    public void handleCustomerMigrationEvent(
            @Payload CustomerMigrationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("migration-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing migration event: userId={}, type={}, from={}, to={}", 
            event.getUserId(), event.getMigrationType(), 
            event.getSourceSystem(), event.getTargetSystem());
        
        try {
            switch (event.getEventType()) {
                case MIGRATION_INITIATED:
                    processMigrationInitiated(event, correlationId);
                    break;
                case ELIGIBILITY_VERIFIED:
                    processEligibilityVerified(event, correlationId);
                    break;
                case PRE_MIGRATION_CHECKS_COMPLETED:
                    processPreMigrationChecksCompleted(event, correlationId);
                    break;
                case DATA_EXTRACTION_STARTED:
                    processDataExtractionStarted(event, correlationId);
                    break;
                case DATA_EXTRACTION_COMPLETED:
                    processDataExtractionCompleted(event, correlationId);
                    break;
                case DATA_TRANSFORMATION_COMPLETED:
                    processDataTransformationCompleted(event, correlationId);
                    break;
                case DATA_VALIDATION_COMPLETED:
                    processDataValidationCompleted(event, correlationId);
                    break;
                case DATA_LOADING_STARTED:
                    processDataLoadingStarted(event, correlationId);
                    break;
                case DATA_LOADING_COMPLETED:
                    processDataLoadingCompleted(event, correlationId);
                    break;
                case POST_MIGRATION_VERIFICATION:
                    processPostMigrationVerification(event, correlationId);
                    break;
                case MIGRATION_COMPLETED:
                    processMigrationCompleted(event, correlationId);
                    break;
                case MIGRATION_FAILED:
                    processMigrationFailed(event, correlationId);
                    break;
                case ROLLBACK_INITIATED:
                    processRollbackInitiated(event, correlationId);
                    break;
                case ROLLBACK_COMPLETED:
                    processRollbackCompleted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown migration event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logAccountEvent(
                "MIGRATION_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "migrationType", event.getMigrationType(),
                    "migrationId", event.getMigrationId() != null ? event.getMigrationId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process migration event: userId={}, error={}", 
                event.getUserId(), e.getMessage(), e);
            kafkaTemplate.send("customer-migration-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processMigrationInitiated(CustomerMigrationEvent event, String correlationId) {
        log.info("Migration initiated: userId={}, type={}, from={} to {}", 
            event.getUserId(), event.getMigrationType(), 
            event.getSourceSystem(), event.getTargetSystem());
        
        MigrationProcess migration = MigrationProcess.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .migrationType(event.getMigrationType())
            .sourceSystem(event.getSourceSystem())
            .targetSystem(event.getTargetSystem())
            .sourceTier(event.getSourceTier())
            .targetTier(event.getTargetTier())
            .initiatedAt(LocalDateTime.now())
            .initiatedBy(event.getInitiatedBy())
            .migrationReason(event.getMigrationReason())
            .status("INITIATED")
            .estimatedDuration(Duration.ofHours(event.getEstimatedDurationHours()))
            .scheduledStartTime(event.getScheduledStartTime())
            .priority(event.getPriority())
            .correlationId(correlationId)
            .build();
        
        migrationRepository.save(migration);
        
        accountMigrationService.createMigrationPlan(migration.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Migration Scheduled",
            String.format("Your account migration from %s to %s has been scheduled for %s. " +
                "Estimated duration: %d hours. We'll keep you updated throughout the process.",
                event.getSourceSystem(), event.getTargetSystem(), 
                event.getScheduledStartTime(), event.getEstimatedDurationHours()),
            correlationId
        );
        
        metricsService.recordMigrationInitiated(event.getMigrationType(), 
            event.getSourceSystem(), event.getTargetSystem());
    }
    
    private void processEligibilityVerified(CustomerMigrationEvent event, String correlationId) {
        log.info("Eligibility verified: migrationId={}", event.getMigrationId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setEligibilityVerified(true);
        migration.setEligibilityVerifiedAt(LocalDateTime.now());
        migration.setEligibilityChecks(event.getEligibilityChecks());
        migrationRepository.save(migration);
        
        accountMigrationService.prepareSourceAccount(migration.getId());
        metricsService.recordEligibilityVerified();
    }
    
    private void processPreMigrationChecksCompleted(CustomerMigrationEvent event, String correlationId) {
        log.info("Pre-migration checks completed: migrationId={}", event.getMigrationId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setPreMigrationChecksCompleted(true);
        migration.setPreMigrationChecksCompletedAt(LocalDateTime.now());
        migration.setChecksPerformed(event.getChecksPerformed());
        migration.setIssuesFound(event.getIssuesFound());
        migrationRepository.save(migration);
        
        if (event.getIssuesFound() > 0) {
            log.warn("Pre-migration issues found: count={}", event.getIssuesFound());
            accountMigrationService.resolvePreMigrationIssues(migration.getId(), event.getIssueDetails());
        }
        
        metricsService.recordPreMigrationChecksCompleted(event.getIssuesFound());
    }
    
    private void processDataExtractionStarted(CustomerMigrationEvent event, String correlationId) {
        log.info("Data extraction started: migrationId={}", event.getMigrationId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setStatus("EXTRACTING_DATA");
        migration.setDataExtractionStartedAt(LocalDateTime.now());
        migration.setRecordsToExtract(event.getRecordCount());
        migrationRepository.save(migration);
        
        dataMigrationService.extractSourceData(migration.getId());
        metricsService.recordDataExtractionStarted(event.getRecordCount());
    }
    
    private void processDataExtractionCompleted(CustomerMigrationEvent event, String correlationId) {
        log.info("Data extraction completed: migrationId={}, recordsExtracted={}", 
            event.getMigrationId(), event.getRecordsExtracted());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setDataExtractionCompleted(true);
        migration.setDataExtractionCompletedAt(LocalDateTime.now());
        migration.setRecordsExtracted(event.getRecordsExtracted());
        
        Duration extractionDuration = Duration.between(
            migration.getDataExtractionStartedAt(),
            migration.getDataExtractionCompletedAt()
        );
        migration.setExtractionDuration(extractionDuration);
        
        migrationRepository.save(migration);
        
        dataMigrationService.transformData(migration.getId());
        metricsService.recordDataExtractionCompleted(event.getRecordsExtracted(), extractionDuration);
    }
    
    private void processDataTransformationCompleted(CustomerMigrationEvent event, String correlationId) {
        log.info("Data transformation completed: migrationId={}, recordsTransformed={}", 
            event.getMigrationId(), event.getRecordsTransformed());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setDataTransformationCompleted(true);
        migration.setDataTransformationCompletedAt(LocalDateTime.now());
        migration.setRecordsTransformed(event.getRecordsTransformed());
        migration.setTransformationErrors(event.getTransformationErrors());
        migrationRepository.save(migration);
        
        if (event.getTransformationErrors() > 0) {
            log.warn("Transformation errors detected: count={}", event.getTransformationErrors());
            dataMigrationService.handleTransformationErrors(migration.getId());
        }
        
        dataMigrationService.validateTransformedData(migration.getId());
        metricsService.recordDataTransformationCompleted(
            event.getRecordsTransformed(), event.getTransformationErrors());
    }
    
    private void processDataValidationCompleted(CustomerMigrationEvent event, String correlationId) {
        log.info("Data validation completed: migrationId={}, validationsPassed={}/{}", 
            event.getMigrationId(), event.getValidationsPassed(), event.getTotalValidations());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setDataValidationCompleted(true);
        migration.setDataValidationCompletedAt(LocalDateTime.now());
        migration.setValidationsPassed(event.getValidationsPassed());
        migration.setValidationsFailed(event.getTotalValidations() - event.getValidationsPassed());
        migrationRepository.save(migration);
        
        double validationRate = (double) event.getValidationsPassed() / event.getTotalValidations();
        
        if (validationRate < 0.95) {
            log.error("Data validation below threshold: rate={}%, migrationId={}", 
                validationRate * 100, event.getMigrationId());
            accountMigrationService.abortMigration(migration.getId(), 
                "Data validation threshold not met");
        } else {
            log.info("Data validation passed: rate={}%", validationRate * 100);
            dataMigrationService.prepareForDataLoad(migration.getId());
        }
        
        metricsService.recordDataValidationCompleted(validationRate);
    }
    
    private void processDataLoadingStarted(CustomerMigrationEvent event, String correlationId) {
        log.info("Data loading started: migrationId={}", event.getMigrationId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setStatus("LOADING_DATA");
        migration.setDataLoadingStartedAt(LocalDateTime.now());
        migrationRepository.save(migration);
        
        dataMigrationService.loadDataToTarget(migration.getId());
        metricsService.recordDataLoadingStarted();
    }
    
    private void processDataLoadingCompleted(CustomerMigrationEvent event, String correlationId) {
        log.info("Data loading completed: migrationId={}, recordsLoaded={}", 
            event.getMigrationId(), event.getRecordsLoaded());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setDataLoadingCompleted(true);
        migration.setDataLoadingCompletedAt(LocalDateTime.now());
        migration.setRecordsLoaded(event.getRecordsLoaded());
        migration.setLoadingErrors(event.getLoadingErrors());
        
        Duration loadingDuration = Duration.between(
            migration.getDataLoadingStartedAt(),
            migration.getDataLoadingCompletedAt()
        );
        migration.setLoadingDuration(loadingDuration);
        
        migrationRepository.save(migration);
        
        accountMigrationService.performPostMigrationVerification(migration.getId());
        metricsService.recordDataLoadingCompleted(event.getRecordsLoaded(), loadingDuration);
    }
    
    private void processPostMigrationVerification(CustomerMigrationEvent event, String correlationId) {
        log.info("Post-migration verification: migrationId={}", event.getMigrationId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setPostMigrationVerificationCompleted(true);
        migration.setPostMigrationVerificationCompletedAt(LocalDateTime.now());
        migration.setVerificationResult(event.getVerificationResult());
        migrationRepository.save(migration);
        
        if ("FAILED".equals(event.getVerificationResult())) {
            log.error("Post-migration verification failed: migrationId={}", event.getMigrationId());
            accountMigrationService.initiateRollback(migration.getId());
        } else {
            log.info("Post-migration verification passed");
            accountMigrationService.finalizeMigration(migration.getId());
        }
        
        metricsService.recordPostMigrationVerification(event.getVerificationResult());
    }
    
    private void processMigrationCompleted(CustomerMigrationEvent event, String correlationId) {
        log.info("Migration completed: migrationId={}, userId={}", 
            event.getMigrationId(), event.getUserId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setStatus("COMPLETED");
        migration.setCompletedAt(LocalDateTime.now());
        
        Duration totalDuration = Duration.between(
            migration.getInitiatedAt(),
            migration.getCompletedAt()
        );
        migration.setTotalDuration(totalDuration);
        
        migrationRepository.save(migration);
        
        accountMigrationService.decommissionSourceAccount(migration.getId());
        accountMigrationService.activateTargetAccount(migration.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Migration Completed Successfully",
            String.format("Your account migration to %s has been completed successfully. " +
                "You can now access your account with all your data intact. Total time: %d minutes.",
                migration.getTargetSystem(), totalDuration.toMinutes()),
            correlationId
        );
        
        metricsService.recordMigrationCompleted(
            migration.getMigrationType(), 
            totalDuration,
            migration.getRecordsLoaded()
        );
    }
    
    private void processMigrationFailed(CustomerMigrationEvent event, String correlationId) {
        log.error("Migration failed: migrationId={}, reason={}", 
            event.getMigrationId(), event.getFailureReason());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setStatus("FAILED");
        migration.setFailedAt(LocalDateTime.now());
        migration.setFailureReason(event.getFailureReason());
        migration.setFailureDetails(event.getFailureDetails());
        migrationRepository.save(migration);
        
        accountMigrationService.initiateRollback(migration.getId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Migration Requires Attention",
            "We encountered an issue during your account migration. Our team is working on it. " +
                "Your original account remains active and unaffected.",
            correlationId
        );
        
        metricsService.recordMigrationFailed(
            migration.getMigrationType(), 
            event.getFailureReason()
        );
    }
    
    private void processRollbackInitiated(CustomerMigrationEvent event, String correlationId) {
        log.warn("Rollback initiated: migrationId={}", event.getMigrationId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setRollbackInitiated(true);
        migration.setRollbackInitiatedAt(LocalDateTime.now());
        migration.setRollbackReason(event.getRollbackReason());
        migrationRepository.save(migration);
        
        dataMigrationService.rollbackChanges(migration.getId());
        metricsService.recordRollbackInitiated();
    }
    
    private void processRollbackCompleted(CustomerMigrationEvent event, String correlationId) {
        log.info("Rollback completed: migrationId={}", event.getMigrationId());
        
        MigrationProcess migration = migrationRepository.findById(event.getMigrationId())
            .orElseThrow();
        
        migration.setStatus("ROLLED_BACK");
        migration.setRollbackCompletedAt(LocalDateTime.now());
        migrationRepository.save(migration);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Migration Rolled Back",
            "Your account migration has been rolled back. Your account is fully functional. " +
                "We'll reach out to reschedule the migration.",
            correlationId
        );
        
        metricsService.recordRollbackCompleted();
    }
}