package com.waqiti.gdpr.kafka;

import com.waqiti.common.events.DataExportEvent;
import com.waqiti.gdpr.domain.DataExportRequest;
import com.waqiti.gdpr.repository.DataExportRequestRepository;
import com.waqiti.gdpr.service.DataExportService;
import com.waqiti.gdpr.service.DataEncryptionService;
import com.waqiti.gdpr.service.DataRetentionService;
import com.waqiti.gdpr.metrics.GdprMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataExportEventsConsumer {
    
    private final DataExportRequestRepository exportRequestRepository;
    private final DataExportService exportService;
    private final DataEncryptionService encryptionService;
    private final DataRetentionService retentionService;
    private final GdprMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final long EXPORT_EXPIRATION_DAYS = 7;
    private static final long MAX_EXPORT_TIME_HOURS = 2;
    
    @KafkaListener(
        topics = {"data-export-events", "gdpr-export-events", "user-data-portability"},
        groupId = "data-export-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleDataExportEvent(
            @Payload DataExportEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("export-%s-%s-p%d-o%d", 
            event.getUserId(), event.getExportId(), partition, offset);
        
        log.info("Processing data export event: userId={}, exportId={}, type={}, status={}",
            event.getUserId(), event.getExportId(), event.getExportType(), event.getStatus());
        
        try {
            switch (event.getStatus()) {
                case "REQUESTED":
                    processExportRequest(event, correlationId);
                    break;
                    
                case "QUEUED":
                    queueExport(event, correlationId);
                    break;
                    
                case "PROCESSING":
                    processExport(event, correlationId);
                    break;
                    
                case "ENCRYPTING":
                    encryptExport(event, correlationId);
                    break;
                    
                case "COMPLETED":
                    completeExport(event, correlationId);
                    break;
                    
                case "FAILED":
                    handleExportFailure(event, correlationId);
                    break;
                    
                case "DOWNLOADED":
                    markExportDownloaded(event, correlationId);
                    break;
                    
                case "EXPIRED":
                    expireExport(event, correlationId);
                    break;
                    
                case "DELETED":
                    deleteExport(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown data export status: {}", event.getStatus());
                    break;
            }
            
            auditService.logGdprEvent("DATA_EXPORT_EVENT_PROCESSED", event.getUserId(),
                Map.of("exportId", event.getExportId(), "exportType", event.getExportType(),
                    "status", event.getStatus(), "correlationId", correlationId, 
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process data export event: {}", e.getMessage(), e);
            kafkaTemplate.send("data-export-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processExportRequest(DataExportEvent event, String correlationId) {
        DataExportRequest request = DataExportRequest.builder()
            .exportId(event.getExportId())
            .userId(event.getUserId())
            .exportType(event.getExportType())
            .format(event.getFormat())
            .dataCategories(event.getDataCategories())
            .status("REQUESTED")
            .requestedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(EXPORT_EXPIRATION_DAYS))
            .correlationId(correlationId)
            .build();
        exportRequestRepository.save(request);
        
        notificationService.sendNotification(event.getUserId(), "Data Export Request Received",
            String.format("We've received your request to export your %s data. You'll receive a download link within 48 hours.", 
                event.getExportType()),
            correlationId);
        
        kafkaTemplate.send("data-export-events", Map.of(
            "userId", event.getUserId(),
            "exportId", event.getExportId(),
            "exportType", event.getExportType(),
            "status", "QUEUED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordExportRequested(event.getExportType());
        
        log.info("Data export requested: userId={}, exportId={}, type={}", 
            event.getUserId(), event.getExportId(), event.getExportType());
    }
    
    private void queueExport(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setStatus("QUEUED");
        request.setQueuedAt(LocalDateTime.now());
        exportRequestRepository.save(request);
        
        metricsService.recordExportQueued(event.getExportType());
        
        log.info("Data export queued: userId={}, exportId={}", event.getUserId(), event.getExportId());
    }
    
    private void processExport(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setStatus("PROCESSING");
        request.setProcessingStartedAt(LocalDateTime.now());
        exportRequestRepository.save(request);
        
        exportService.collectUserData(event.getUserId(), event.getDataCategories());
        
        Duration processingDuration = Duration.between(request.getProcessingStartedAt(), LocalDateTime.now());
        if (processingDuration.toHours() > MAX_EXPORT_TIME_HOURS) {
            kafkaTemplate.send("data-export-events", Map.of(
                "userId", event.getUserId(),
                "exportId", event.getExportId(),
                "status", "FAILED",
                "reason", "PROCESSING_TIMEOUT",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            return;
        }
        
        kafkaTemplate.send("data-export-events", Map.of(
            "userId", event.getUserId(),
            "exportId", event.getExportId(),
            "status", "ENCRYPTING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordExportProcessing(event.getExportType());
        
        log.info("Processing data export: userId={}, exportId={}, categories={}", 
            event.getUserId(), event.getExportId(), event.getDataCategories());
    }
    
    private void encryptExport(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setStatus("ENCRYPTING");
        exportRequestRepository.save(request);
        
        String encryptionKey = encryptionService.encryptExportFile(event.getExportId());
        request.setEncryptionKey(encryptionKey);
        exportRequestRepository.save(request);
        
        kafkaTemplate.send("data-export-events", Map.of(
            "userId", event.getUserId(),
            "exportId", event.getExportId(),
            "status", "COMPLETED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordExportEncrypted(event.getExportType());
        
        log.info("Data export encrypted: userId={}, exportId={}", event.getUserId(), event.getExportId());
    }
    
    private void completeExport(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setStatus("COMPLETED");
        request.setCompletedAt(LocalDateTime.now());
        request.setFileUrl(event.getFileUrl());
        request.setFileSizeBytes(event.getFileSizeBytes());
        exportRequestRepository.save(request);
        
        Duration exportDuration = Duration.between(request.getRequestedAt(), request.getCompletedAt());
        
        notificationService.sendNotification(event.getUserId(), "Your Data Export is Ready",
            String.format("Your %s data export is ready. Download it here: %s (Link expires in %d days)", 
                event.getExportType(), event.getFileUrl(), EXPORT_EXPIRATION_DAYS),
            correlationId);
        
        metricsService.recordExportCompleted(event.getExportType(), exportDuration.toMillis());
        
        log.info("Data export completed: userId={}, exportId={}, durationMs={}, sizeBytes={}", 
            event.getUserId(), event.getExportId(), exportDuration.toMillis(), event.getFileSizeBytes());
    }
    
    private void handleExportFailure(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setStatus("FAILED");
        request.setFailedAt(LocalDateTime.now());
        request.setErrorMessage(event.getErrorMessage());
        exportRequestRepository.save(request);
        
        notificationService.sendNotification(event.getUserId(), "Data Export Failed",
            "We encountered an issue while processing your data export request. Please try again or contact support.",
            correlationId);
        
        metricsService.recordExportFailed(event.getExportType(), event.getErrorMessage());
        
        log.error("Data export failed: userId={}, exportId={}, error={}", 
            event.getUserId(), event.getExportId(), event.getErrorMessage());
    }
    
    private void markExportDownloaded(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setDownloadedAt(LocalDateTime.now());
        request.setDownloadCount(request.getDownloadCount() + 1);
        exportRequestRepository.save(request);
        
        metricsService.recordExportDownloaded(event.getExportType());
        
        log.info("Data export downloaded: userId={}, exportId={}, downloadCount={}", 
            event.getUserId(), event.getExportId(), request.getDownloadCount());
    }
    
    private void expireExport(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setStatus("EXPIRED");
        request.setExpiredAt(LocalDateTime.now());
        exportRequestRepository.save(request);
        
        retentionService.deleteExportFile(event.getExportId());
        
        if (request.getDownloadCount() == 0) {
            notificationService.sendNotification(event.getUserId(), "Data Export Expired",
                String.format("Your data export has expired after %d days without being downloaded. Please submit a new request if needed.", 
                    EXPORT_EXPIRATION_DAYS),
                correlationId);
        }
        
        metricsService.recordExportExpired(event.getExportType());
        
        log.info("Data export expired: userId={}, exportId={}, wasDownloaded={}", 
            event.getUserId(), event.getExportId(), request.getDownloadCount() > 0);
    }
    
    private void deleteExport(DataExportEvent event, String correlationId) {
        DataExportRequest request = exportRequestRepository.findByExportId(event.getExportId())
            .orElseThrow(() -> new RuntimeException("Export request not found"));
        
        request.setStatus("DELETED");
        request.setDeletedAt(LocalDateTime.now());
        exportRequestRepository.save(request);
        
        retentionService.deleteExportFile(event.getExportId());
        retentionService.securelyWipeEncryptionKeys(event.getExportId());
        
        metricsService.recordExportDeleted(event.getExportType());
        
        log.info("Data export deleted: userId={}, exportId={}", event.getUserId(), event.getExportId());
    }
}