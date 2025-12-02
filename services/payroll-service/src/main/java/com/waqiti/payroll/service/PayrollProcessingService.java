package com.waqiti.payroll.service;

import com.waqiti.payroll.domain.PayrollBatch;
import com.waqiti.payroll.exception.PayrollProcessingException;
import com.waqiti.payroll.repository.PayrollBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payroll Processing Service
 * High-level orchestration for payroll batch processing
 * Provides API-level operations for payroll management
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PayrollProcessingService {

    private final PayrollBatchRepository payrollBatchRepository;
    private final TaxCalculationService taxCalculationService;
    private final DeductionService deductionService;
    private final ComplianceService complianceService;
    private final ValidationService validationService;
    private final BankTransferService bankTransferService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ReportingService reportingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PAYROLL_PROCESSING_TOPIC = "payroll-processing-events";

    /**
     * Submit payroll batch for processing (API entry point)
     */
    @Transactional
    public PayrollBatchSubmissionResult submitPayrollBatch(PayrollBatchRequest request) {
        log.info("Submitting payroll batch for company: {}, employees: {}",
                 request.getCompanyId(), request.getEmployees().size());

        String correlationId = UUID.randomUUID().toString();
        String batchId = generateBatchId(request.getCompanyId());

        try {
            // 1. Validate batch data
            ValidationService.PayrollBatchData batchData = new ValidationService.PayrollBatchData();
            batchData.setBatchId(batchId);
            batchData.setCompanyId(request.getCompanyId());
            batchData.setPayPeriod(request.getPayPeriod());
            batchData.setEmployees(request.getEmployees());
            batchData.setTotalAmount(request.getTotalAmount());

            ValidationService.PayrollBatchValidationResult validationResult =
                validationService.validatePayrollBatch(batchData);

            if (!validationResult.isValid()) {
                log.error("Batch validation failed: {}", validationResult.getErrors());
                throw new PayrollProcessingException("Batch validation failed", batchId);
            }

            // 2. Create payroll batch entity
            PayrollBatch batch = createPayrollBatch(request, batchId, correlationId);
            payrollBatchRepository.save(batch);

            // 3. Audit log
            auditService.logPayrollBatchInitiated(request.getCompanyId(), batchId,
                correlationId, request.getEmployees().size(), request.getTotalAmount());

            // 4. Publish to Kafka for async processing
            PayrollProcessingEvent event = buildProcessingEvent(request, batchId, correlationId);
            kafkaTemplate.send(PAYROLL_PROCESSING_TOPIC, batchId, event);

            log.info("Payroll batch submitted successfully - Batch: {}, Correlation: {}", batchId, correlationId);

            return PayrollBatchSubmissionResult.builder()
                .batchId(batchId)
                .correlationId(correlationId)
                .status("SUBMITTED")
                .message("Payroll batch submitted for processing")
                .employeeCount(request.getEmployees().size())
                .totalAmount(request.getTotalAmount())
                .build();

        } catch (Exception e) {
            log.error("Failed to submit payroll batch: {}", e.getMessage(), e);
            auditService.logPayrollBatchFailed(request.getCompanyId(), batchId,
                correlationId, "SUBMISSION_FAILED", e.getMessage());
            throw new PayrollProcessingException("Failed to submit payroll batch: " + e.getMessage(), batchId, e);
        }
    }

    /**
     * Get payroll batch status
     */
    public PayrollBatchStatus getBatchStatus(String batchId) {
        log.info("Retrieving status for batch: {}", batchId);

        PayrollBatch batch = payrollBatchRepository.findByPayrollBatchId(batchId)
            .orElseThrow(() -> new PayrollProcessingException("Batch not found: " + batchId, batchId));

        return PayrollBatchStatus.builder()
            .batchId(batchId)
            .companyId(batch.getCompanyId())
            .status(batch.getStatus().toString())
            .totalEmployees(batch.getTotalEmployees())
            .successfulPayments(batch.getSuccessfulPayments())
            .failedPayments(batch.getFailedPayments())
            .grossAmount(batch.getGrossAmount())
            .netAmount(batch.getNetAmount())
            .createdAt(batch.getCreatedAt())
            .completedAt(batch.getCompletedAt())
            .build();
    }

    /**
     * Retry failed payroll batch
     */
    @Transactional
    public PayrollBatchSubmissionResult retryPayrollBatch(String batchId) {
        log.info("Retrying payroll batch: {}", batchId);

        PayrollBatch batch = payrollBatchRepository.findByPayrollBatchId(batchId)
            .orElseThrow(() -> new PayrollProcessingException("Batch not found: " + batchId, batchId));

        // Only retry failed batches
        if (!batch.getStatus().toString().equals("FAILED")) {
            throw new PayrollProcessingException("Can only retry failed batches. Current status: " +
                batch.getStatus(), batchId);
        }

        // Increment retry count
        batch.setRetryCount(batch.getRetryCount() + 1);
        payrollBatchRepository.save(batch);

        // Republish to Kafka
        String correlationId = UUID.randomUUID().toString();
        PayrollProcessingEvent event = new PayrollProcessingEvent();
        event.setBatchId(batchId);
        event.setCompanyId(batch.getCompanyId());
        event.setCorrelationId(correlationId);
        event.setRetry(true);

        kafkaTemplate.send(PAYROLL_PROCESSING_TOPIC, batchId, event);

        log.info("Payroll batch retry submitted - Batch: {}, Retry: {}", batchId, batch.getRetryCount());

        return PayrollBatchSubmissionResult.builder()
            .batchId(batchId)
            .correlationId(correlationId)
            .status("RETRY_SUBMITTED")
            .message("Payroll batch resubmitted for processing (Retry #" + batch.getRetryCount() + ")")
            .build();
    }

    /**
     * Cancel pending payroll batch
     */
    @Transactional
    public void cancelPayrollBatch(String batchId, String reason) {
        log.info("Cancelling payroll batch: {}, reason: {}", batchId, reason);

        PayrollBatch batch = payrollBatchRepository.findByPayrollBatchId(batchId)
            .orElseThrow(() -> new PayrollProcessingException("Batch not found: " + batchId, batchId));

        // Can only cancel pending or processing batches
        if (batch.getStatus().toString().equals("COMPLETED") ||
            batch.getStatus().toString().equals("CANCELLED")) {
            throw new PayrollProcessingException("Cannot cancel batch in status: " +
                batch.getStatus(), batchId);
        }

        // Update batch status to cancelled
        // Note: This would require adding CANCELLED to BatchStatus enum
        // For now, we'll update metadata
        batch.getMetadata().put("cancelled_reason", reason);
        batch.getMetadata().put("cancelled_at", LocalDateTime.now().toString());
        payrollBatchRepository.save(batch);

        auditService.logEvent(AuditService.AuditEventType.GENERIC, batch.getCompanyId(),
            batchId, "BATCH_CANCELLED", batch.getCorrelationId());

        log.info("Payroll batch cancelled - Batch: {}, Reason: {}", batchId, reason);
    }

    /**
     * Get payroll statistics for company
     */
    public PayrollStatistics getCompanyPayrollStatistics(String companyId, LocalDate startDate, LocalDate endDate) {
        log.info("Retrieving payroll statistics for company: {}, period: {} to {}",
                 companyId, startDate, endDate);

        // TODO: Query repository for statistics
        // This would aggregate data from PayrollBatch and PayrollPayment tables

        PayrollStatistics stats = new PayrollStatistics();
        stats.setCompanyId(companyId);
        stats.setStartDate(startDate);
        stats.setEndDate(endDate);
        stats.setTotalBatches(0);
        stats.setTotalEmployeesPaid(0);
        stats.setTotalGrossAmount(BigDecimal.ZERO);
        stats.setTotalNetAmount(BigDecimal.ZERO);
        stats.setTotalTaxWithheld(BigDecimal.ZERO);
        stats.setAveragePaymentAmount(BigDecimal.ZERO);

        return stats;
    }

    /**
     * Approve payroll batch (if approval workflow enabled)
     */
    @Transactional
    public void approvePayrollBatch(String batchId, String approvedBy) {
        log.info("Approving payroll batch: {}, approved by: {}", batchId, approvedBy);

        PayrollBatch batch = payrollBatchRepository.findByPayrollBatchId(batchId)
            .orElseThrow(() -> new PayrollProcessingException("Batch not found: " + batchId, batchId));

        batch.setApprovedBy(approvedBy);
        batch.setApprovedAt(LocalDateTime.now());
        payrollBatchRepository.save(batch);

        auditService.logEvent(AuditService.AuditEventType.GENERIC, batch.getCompanyId(),
            batchId, "BATCH_APPROVED_BY_" + approvedBy, batch.getCorrelationId());

        log.info("Payroll batch approved - Batch: {}, Approver: {}", batchId, approvedBy);
    }

    // ============= Helper Methods =============

    private String generateBatchId(String companyId) {
        return String.format("PB-%s-%s",
            companyId.substring(0, Math.min(6, companyId.length())).toUpperCase(),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );
    }

    private PayrollBatch createPayrollBatch(PayrollBatchRequest request, String batchId, String correlationId) {
        PayrollBatch batch = new PayrollBatch();
        batch.setPayrollBatchId(batchId);
        batch.setCompanyId(request.getCompanyId());
        batch.setPayPeriod(request.getPayPeriod());
        batch.setPayrollType(request.getPayrollType());
        batch.setTotalEmployees(request.getEmployees().size());
        batch.setGrossAmount(request.getTotalAmount());
        batch.setCorrelationId(correlationId);
        batch.setRetryCount(0);
        batch.setCreatedAt(LocalDateTime.now());
        return batch;
    }

    private PayrollProcessingEvent buildProcessingEvent(PayrollBatchRequest request,
                                                        String batchId, String correlationId) {
        PayrollProcessingEvent event = new PayrollProcessingEvent();
        event.setBatchId(batchId);
        event.setCompanyId(request.getCompanyId());
        event.setPayPeriod(request.getPayPeriod());
        event.setPayrollType(request.getPayrollType().toString());
        event.setEmployees(request.getEmployees());
        event.setCorrelationId(correlationId);
        event.setRetry(false);
        return event;
    }

    // ============= DTOs =============

    @lombok.Data
    public static class PayrollBatchRequest {
        private String companyId;
        private LocalDate payPeriod;
        private com.waqiti.payroll.domain.PayrollType payrollType;
        private List<ValidationService.EmployeePayrollData> employees;
        private BigDecimal totalAmount;
    }

    @lombok.Builder
    @lombok.Data
    public static class PayrollBatchSubmissionResult {
        private String batchId;
        private String correlationId;
        private String status;
        private String message;
        private Integer employeeCount;
        private BigDecimal totalAmount;
    }

    @lombok.Builder
    @lombok.Data
    public static class PayrollBatchStatus {
        private String batchId;
        private String companyId;
        private String status;
        private Integer totalEmployees;
        private Integer successfulPayments;
        private Integer failedPayments;
        private BigDecimal grossAmount;
        private BigDecimal netAmount;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
    }

    @lombok.Data
    public static class PayrollStatistics {
        private String companyId;
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalBatches;
        private int totalEmployeesPaid;
        private BigDecimal totalGrossAmount;
        private BigDecimal totalNetAmount;
        private BigDecimal totalTaxWithheld;
        private BigDecimal averagePaymentAmount;
    }

    @lombok.Data
    public static class PayrollProcessingEvent {
        private String batchId;
        private String companyId;
        private LocalDate payPeriod;
        private String payrollType;
        private List<ValidationService.EmployeePayrollData> employees;
        private String correlationId;
        private boolean retry;
    }
}
