package com.waqiti.corebanking.api;

import com.waqiti.corebanking.service.StatementGenerationService;
import com.waqiti.corebanking.service.StatementGenerationService.StatementFormat;
import com.waqiti.corebanking.service.StatementGenerationService.StatementResult;
import com.waqiti.corebanking.service.StatementGenerationService.StatementPeriod;
import com.waqiti.corebanking.service.StatementJobService;
import com.waqiti.corebanking.entity.StatementJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Statement API Controller
 * 
 * Provides endpoints for generating and retrieving account statements
 * in various formats including PDF, CSV, and JSON.
 */
@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Statements", description = "Account statement generation and management")
public class StatementController {
    
    private final StatementGenerationService statementGenerationService;
    private final StatementJobService statementJobService;
    
    /**
     * Generate statement for custom date range
     */
    @PostMapping("/generate/{accountId}")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Generate custom statement", 
               description = "Generates statement for specified date range and format")
    public ResponseEntity<byte[]> generateStatement(
            @Parameter(description = "Account ID") @PathVariable UUID accountId,
            @Parameter(description = "Start date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Statement format") @RequestParam(defaultValue = "PDF") StatementFormat format) {
        
        log.info("Statement generation requested for account: {}, period: {} to {}, format: {}", 
            accountId, startDate, endDate, format);
        
        try {
            // Validate date range
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            if (startDate.isBefore(LocalDate.now().minusYears(7))) {
                return ResponseEntity.badRequest().build(); // Limit to 7 years history
            }
            
            StatementResult result = statementGenerationService.generateStatement(
                accountId, startDate, endDate, format);
            
            if (!"SUCCESS".equals(result.getStatus())) {
                log.error("Statement generation failed for account: {}, error: {}", 
                    accountId, result.getError());
                return ResponseEntity.internalServerError().build();
            }
            
            // Set appropriate headers based on format
            HttpHeaders headers = new HttpHeaders();
            String filename = String.format("statement_%s_%s_to_%s", 
                result.getAccountNumber(), startDate, endDate);
            
            switch (format) {
                case PDF:
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", filename + ".pdf");
                    break;
                case CSV:
                    headers.setContentType(MediaType.parseMediaType("text/csv"));
                    headers.setContentDispositionFormData("attachment", filename + ".csv");
                    break;
                case JSON:
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentDispositionFormData("attachment", filename + ".json");
                    break;
            }
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(result.getStatementData());
                
        } catch (Exception e) {
            log.error("Error generating statement for account: {}", accountId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate monthly statement
     */
    @PostMapping("/generate/{accountId}/monthly")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Generate monthly statement", 
               description = "Generates statement for a specific month")
    public ResponseEntity<byte[]> generateMonthlyStatement(
            @Parameter(description = "Account ID") @PathVariable UUID accountId,
            @Parameter(description = "Year") @RequestParam int year,
            @Parameter(description = "Month (1-12)") @RequestParam int month,
            @Parameter(description = "Statement format") @RequestParam(defaultValue = "PDF") StatementFormat format) {
        
        log.info("Monthly statement requested for account: {}, {}/{}", accountId, month, year);
        
        try {
            // Validate month and year
            if (month < 1 || month > 12) {
                return ResponseEntity.badRequest().build();
            }
            
            if (year < 2020 || year > LocalDate.now().getYear()) {
                return ResponseEntity.badRequest().build();
            }
            
            // Don't allow future months
            LocalDate requestedMonth = LocalDate.of(year, month, 1);
            if (requestedMonth.isAfter(LocalDate.now().withDayOfMonth(1))) {
                return ResponseEntity.badRequest().build();
            }
            
            StatementResult result = statementGenerationService.generateMonthlyStatement(accountId, year, month);
            
            if (!"SUCCESS".equals(result.getStatus())) {
                log.error("Monthly statement generation failed for account: {}, error: {}", 
                    accountId, result.getError());
                return ResponseEntity.internalServerError().build();
            }
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            String filename = String.format("statement_%s_%04d_%02d", 
                result.getAccountNumber(), year, month);
            
            switch (format) {
                case PDF:
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", filename + ".pdf");
                    break;
                case CSV:
                    headers.setContentType(MediaType.parseMediaType("text/csv"));
                    headers.setContentDispositionFormData("attachment", filename + ".csv");
                    break;
                case JSON:
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentDispositionFormData("attachment", filename + ".json");
                    break;
            }
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(result.getStatementData());
                
        } catch (Exception e) {
            log.error("Error generating monthly statement for account: {}", accountId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate statement asynchronously
     */
    @PostMapping("/generate/{accountId}/async")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Generate statement asynchronously", 
               description = "Starts asynchronous statement generation and returns job ID")
    public ResponseEntity<StatementJobResponse> generateStatementAsync(
            @Parameter(description = "Account ID") @PathVariable UUID accountId,
            @Parameter(description = "Start date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Statement format") @RequestParam(defaultValue = "PDF") StatementFormat format) {
        
        log.info("Async statement generation requested for account: {}", accountId);
        
        try {
            // Validate date range
            if (startDate.isAfter(endDate)) {
                return ResponseEntity.badRequest().build();
            }
            
            if (startDate.isBefore(LocalDate.now().minusYears(7))) {
                return ResponseEntity.badRequest().build(); // Limit to 7 years history
            }
            
            // Get user ID from security context (simplified for now)
            UUID userId = UUID.randomUUID(); // In production, get from authentication
            
            // Create statement job
            StatementJob.StatementFormat jobFormat = StatementJob.StatementFormat.valueOf(format.name());
            StatementJob job = statementJobService.createStatementJob(accountId, userId, startDate, endDate, jobFormat);
            
            // Start async processing
            statementJobService.processStatementJobAsync(job.getJobId());
            
            return ResponseEntity.accepted()
                .body(StatementJobResponse.builder()
                    .jobId(job.getJobId().toString())
                    .accountId(accountId)
                    .status(job.getStatus().toString())
                    .message(job.getMessage())
                    .estimatedCompletionTime(job.getEstimatedCompletionTime())
                    .build());
                
        } catch (Exception e) {
            log.error("Error starting async statement generation for account: {}", accountId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get available statement periods for an account
     */
    @GetMapping("/periods/{accountId}")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Get available statement periods", 
               description = "Returns list of available statement periods for account")
    public ResponseEntity<List<StatementPeriod>> getAvailableStatementPeriods(
            @Parameter(description = "Account ID") @PathVariable UUID accountId) {
        
        log.info("Available statement periods requested for account: {}", accountId);
        
        try {
            List<StatementPeriod> periods = statementGenerationService.getAvailableStatementPeriods(accountId);
            return ResponseEntity.ok(periods);
            
        } catch (Exception e) {
            log.error("Error getting available statement periods for account: {}", accountId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get statement job status (for async operations)
     */
    @GetMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get statement job status", 
               description = "Gets status of asynchronous statement generation job")
    public ResponseEntity<StatementJobResponse> getStatementJobStatus(
            @Parameter(description = "Job ID") @PathVariable String jobId) {
        
        log.info("Statement job status requested for job: {}", jobId);
        
        try {
            // Parse job ID as UUID
            UUID jobUuid = UUID.fromString(jobId);
            
            // Get actual job from database
            StatementJob job = statementJobService.getStatementJob(jobUuid);
            
            // Build response from actual job data
            StatementJobResponse.StatementJobResponseBuilder responseBuilder = StatementJobResponse.builder()
                .jobId(jobId)
                .accountId(job.getAccountId())
                .status(job.getStatus().toString())
                .message(job.getMessage())
                .estimatedCompletionTime(job.getEstimatedCompletionTime());
            
            // Set completion time if job is completed
            if (job.getCompletedAt() != null) {
                responseBuilder.completionTime(job.getCompletedAt());
            }
            
            // Set download URL if available
            if (job.getDownloadUrl() != null && !job.isDownloadExpired()) {
                responseBuilder.downloadUrl(job.getDownloadUrl());
            }
            
            // Set error message if job failed
            if (job.getErrorMessage() != null) {
                responseBuilder.error(job.getErrorMessage());
            }
            
            log.debug("Statement job status retrieved: {} (status: {})", jobId, job.getStatus());
            
            return ResponseEntity.ok(responseBuilder.build());
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid job ID format: {}", jobId);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting statement job status for job: {}", jobId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Download generated statement file
     */
    @GetMapping("/download/{jobId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Download generated statement", 
               description = "Downloads the generated statement file")
    public ResponseEntity<byte[]> downloadStatement(
            @Parameter(description = "Job ID") @PathVariable String jobId) {
        
        log.info("Statement download requested for job: {}", jobId);
        
        try {
            UUID jobUuid = UUID.fromString(jobId);
            StatementJob job = statementJobService.getStatementJob(jobUuid);
            
            // Validate job is completed and download is available
            if (job.getStatus() != StatementJob.JobStatus.COMPLETED) {
                log.warn("Statement job {} is not completed (status: {})", jobId, job.getStatus());
                return ResponseEntity.notFound().build();
            }
            
            if (job.getDownloadUrl() == null || job.isDownloadExpired()) {
                log.warn("Download not available or expired for job: {}", jobId);
                return ResponseEntity.notFound().build();
            }
            
            // In production, this would read the actual file from storage
            // For now, generate a sample statement
            StatementGenerationService.StatementResult result = statementGenerationService.generateStatement(
                job.getAccountId(), job.getStartDate(), job.getEndDate(),
                StatementGenerationService.StatementFormat.valueOf(job.getFormat().name()));
            
            if (!"SUCCESS".equals(result.getStatus())) {
                log.error("Failed to retrieve statement file for job: {}", jobId);
                return ResponseEntity.internalServerError().build();
            }
            
            // Set appropriate headers
            HttpHeaders headers = new HttpHeaders();
            String filename = String.format("statement_%s_%s_to_%s", 
                job.getAccountId().toString().substring(0, 8),
                job.getStartDate(), job.getEndDate());
            
            switch (job.getFormat()) {
                case PDF:
                    headers.setContentType(MediaType.APPLICATION_PDF);
                    headers.setContentDispositionFormData("attachment", filename + ".pdf");
                    break;
                case CSV:
                    headers.setContentType(MediaType.parseMediaType("text/csv"));
                    headers.setContentDispositionFormData("attachment", filename + ".csv");
                    break;
                case JSON:
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setContentDispositionFormData("attachment", filename + ".json");
                    break;
                case EXCEL:
                    headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
                    headers.setContentDispositionFormData("attachment", filename + ".xlsx");
                    break;
            }
            
            log.info("Statement download successful for job: {}", jobId);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(result.getStatementData());
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid job ID format: {}", jobId);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error downloading statement for job: {}", jobId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Cancel statement generation job
     */
    @DeleteMapping("/jobs/{jobId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cancel statement job", 
               description = "Cancels a pending or in-progress statement generation job")
    public ResponseEntity<StatementJobResponse> cancelStatementJob(
            @Parameter(description = "Job ID") @PathVariable String jobId) {
        
        log.info("Statement job cancellation requested for job: {}", jobId);
        
        try {
            UUID jobUuid = UUID.fromString(jobId);
            StatementJob cancelledJob = statementJobService.cancelStatementJob(jobUuid);
            
            return ResponseEntity.ok(StatementJobResponse.builder()
                .jobId(jobId)
                .accountId(cancelledJob.getAccountId())
                .status(cancelledJob.getStatus().toString())
                .message(cancelledJob.getMessage())
                .completionTime(cancelledJob.getCompletedAt())
                .build());
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid job ID format: {}", jobId);
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.warn("Cannot cancel job: {} - {}", jobId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error cancelling statement job: {}", jobId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get statement jobs for account
     */
    @GetMapping("/jobs/account/{accountId}")
    @PreAuthorize("hasRole('USER') and @accountService.isAccountOwner(#accountId, authentication.principal.id)")
    @Operation(summary = "Get account statement jobs", 
               description = "Gets all statement generation jobs for an account")
    public ResponseEntity<List<StatementJobResponse>> getAccountStatementJobs(
            @Parameter(description = "Account ID") @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Account statement jobs requested for account: {}", accountId);
        
        try {
            Page<StatementJob> jobs = statementJobService.getAccountStatementJobs(
                accountId, org.springframework.data.domain.PageRequest.of(page, size));
            
            List<StatementJobResponse> responses = jobs.getContent().stream()
                .map(this::convertToJobResponse)
                .collect(java.util.stream.Collectors.toList());
            
            return ResponseEntity.ok(responses);
                
        } catch (Exception e) {
            log.error("Error getting statement jobs for account: {}", accountId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Trigger auto-generation of statements (Admin only)
     */
    @PostMapping("/auto-generate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger auto statement generation", 
               description = "Manually triggers auto-generation of monthly statements")
    public ResponseEntity<AutoGenerationResponse> triggerAutoGeneration() {
        
        log.info("Manual trigger of auto statement generation requested");
        
        try {
            // This would normally be called by the scheduler
            statementGenerationService.autoGenerateMonthlyStatements();
            
            return ResponseEntity.ok(AutoGenerationResponse.builder()
                .status("SUCCESS")
                .message("Auto statement generation completed")
                .timestamp(java.time.LocalDateTime.now())
                .build());
                
        } catch (Exception e) {
            log.error("Error during auto statement generation", e);
            return ResponseEntity.internalServerError()
                .body(AutoGenerationResponse.builder()
                    .status("FAILED")
                    .message("Auto statement generation failed: " + e.getMessage())
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
    
    // Helper methods
    
    private StatementJobResponse convertToJobResponse(StatementJob job) {
        StatementJobResponse.StatementJobResponseBuilder builder = StatementJobResponse.builder()
            .jobId(job.getJobId().toString())
            .accountId(job.getAccountId())
            .status(job.getStatus().toString())
            .message(job.getMessage())
            .estimatedCompletionTime(job.getEstimatedCompletionTime());
        
        if (job.getCompletedAt() != null) {
            builder.completionTime(job.getCompletedAt());
        }
        
        if (job.getDownloadUrl() != null && !job.isDownloadExpired()) {
            builder.downloadUrl(job.getDownloadUrl());
        }
        
        if (job.getErrorMessage() != null) {
            builder.error(job.getErrorMessage());
        }
        
        return builder.build();
    }
    
    // DTOs
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StatementJobResponse {
        private String jobId;
        private UUID accountId;
        private String status;
        private String message;
        private java.time.LocalDateTime estimatedCompletionTime;
        private java.time.LocalDateTime completionTime;
        private String downloadUrl;
        private String error;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AutoGenerationResponse {
        private String status;
        private String message;
        private java.time.LocalDateTime timestamp;
        private Integer processedAccounts;
        private Integer generatedStatements;
    }
}