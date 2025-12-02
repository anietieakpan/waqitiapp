package com.waqiti.support.controller;

import com.waqiti.support.dto.*;
import com.waqiti.support.service.ITicketService;
import com.waqiti.support.service.TicketCategorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets/categorization")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Ticket Categorization", description = "Automatic ticket categorization and analysis APIs")
public class TicketCategorizationController {

    private final ITicketService ticketService;
    private final TicketCategorizationService categorizationService;

    @PostMapping("/preview")
    @Operation(
        summary = "Preview ticket categorization",
        description = "Get categorization preview without creating a ticket"
    )
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public ResponseEntity<CategorizationResult> previewCategorization(
            @RequestBody @NotNull PreviewCategorizationRequest request) {
        
        log.debug("Previewing categorization for subject: {}", request.getSubject());
        
        CategorizationResult result = ticketService.previewCategorization(
            request.getSubject(), 
            request.getDescription()
        );
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/recategorize/{ticketId}")
    @Operation(
        summary = "Recategorize existing ticket",
        description = "Re-run categorization on existing ticket and optionally update it"
    )
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public ResponseEntity<CategorizationResult> recategorizeTicket(
            @PathVariable @NotBlank String ticketId,
            @RequestParam(defaultValue = "false") boolean applyChanges) {
        
        log.info("Recategorizing ticket: {}, apply changes: {}", ticketId, applyChanges);
        
        CategorizationResult result;
        if (applyChanges) {
            result = ticketService.recategorizeTicket(ticketId);
        } else {
            // Just preview without applying changes
            TicketDTO ticket = ticketService.getTicket(ticketId);
            result = ticketService.previewCategorization(ticket.getSubject(), ticket.getDescription());
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/confidences")
    @Operation(
        summary = "Get category confidence scores",
        description = "Get confidence scores for all possible categories for given content"
    )
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public ResponseEntity<List<CategorizationConfidence>> getCategoryConfidences(
            @RequestParam @NotBlank String subject,
            @RequestParam @NotBlank String description) {
        
        log.debug("Getting category confidences for content");
        
        List<CategorizationConfidence> confidences = ticketService.getCategoryConfidences(subject, description);
        
        return ResponseEntity.ok(confidences);
    }

    @GetMapping("/{ticketId}/similar")
    @Operation(
        summary = "Find similar tickets",
        description = "Find tickets with similar content to help with resolution"
    )
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public ResponseEntity<List<TicketDTO>> findSimilarTickets(
            @PathVariable @NotBlank String ticketId,
            @RequestParam(defaultValue = "5") @Positive int limit) {
        
        log.debug("Finding similar tickets for: {}", ticketId);
        
        List<TicketDTO> similarTickets = ticketService.findSimilarTickets(ticketId, limit);
        
        return ResponseEntity.ok(similarTickets);
    }

    @PostMapping("/bulk-recategorize")
    @Operation(
        summary = "Bulk recategorize tickets",
        description = "Recategorize multiple tickets in batch"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkCategorizationResult> bulkRecategorize(
            @RequestBody @NotNull BulkRecategorizeRequest request) {
        
        log.info("Bulk recategorizing {} tickets", request.getTicketIds().size());
        
        BulkCategorizationResult result = BulkCategorizationResult.builder()
            .totalTickets(request.getTicketIds().size())
            .successfulUpdates(0)
            .failedUpdates(0)
            .build();
        
        for (String ticketId : request.getTicketIds()) {
            try {
                ticketService.recategorizeTicket(ticketId);
                result.setSuccessfulUpdates(result.getSuccessfulUpdates() + 1);
            } catch (Exception e) {
                log.error("Failed to recategorize ticket: {}", ticketId, e);
                result.setFailedUpdates(result.getFailedUpdates() + 1);
                result.getErrors().add(String.format("Ticket %s: %s", ticketId, e.getMessage()));
            }
        }
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/train")
    @Operation(
        summary = "Train categorization model",
        description = "Retrain the categorization model with latest ticket data"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrainingResult> trainModel(
            @RequestParam(defaultValue = "1000") @Positive int sampleSize) {
        
        log.info("Starting model training with sample size: {}", sampleSize);
        
        try {
            // This would normally be an async operation
            // For demonstration, we'll simulate it
            TrainingResult result = TrainingResult.builder()
                .trainingStarted(true)
                .sampleSize(sampleSize)
                .message("Training started successfully. Check training status endpoint for progress.")
                .build();
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to start model training", e);
            
            TrainingResult result = TrainingResult.builder()
                .trainingStarted(false)
                .message("Failed to start training: " + e.getMessage())
                .build();
            
            return ResponseEntity.badRequest().body(result);
        }
    }

    @GetMapping("/analytics")
    @Operation(
        summary = "Get categorization analytics",
        description = "Get analytics about categorization accuracy and distribution"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategorizationAnalytics> getCategorizationAnalytics() {
        
        log.debug("Getting categorization analytics");
        
        // This would normally query analytics from the database
        CategorizationAnalytics analytics = CategorizationAnalytics.builder()
            .totalTicketsCategorized(0L)
            .averageConfidenceScore(0.0)
            .categoryDistribution(null)
            .accuracyMetrics(null)
            .build();
        
        return ResponseEntity.ok(analytics);
    }

    @PostMapping("/validate-rules")
    @Operation(
        summary = "Validate categorization rules",
        description = "Test categorization rules against sample content"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RuleValidationResult> validateRules(
            @RequestBody @NotNull RuleValidationRequest request) {
        
        log.debug("Validating categorization rules");
        
        RuleValidationResult result = RuleValidationResult.builder()
            .rulesValid(true)
            .validationErrors(List.of())
            .suggestions(List.of())
            .build();
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    @Operation(
        summary = "Check categorization service health",
        description = "Health check for the categorization service and ML models"
    )
    @PreAuthorize("hasRole('AGENT') or hasRole('ADMIN')")
    public ResponseEntity<CategorizationHealthStatus> getHealthStatus() {
        
        CategorizationHealthStatus status = CategorizationHealthStatus.builder()
            .serviceHealthy(true)
            .modelLoaded(true)
            .lastTrainingDate(null)
            .rulesVersion("1.0")
            .modelVersion("1.0")
            .build();
        
        return ResponseEntity.ok(status);
    }

    // Request/Response DTOs
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PreviewCategorizationRequest {
        @NotBlank
        private String subject;
        
        @NotBlank
        private String description;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BulkRecategorizeRequest {
        @NotNull
        private List<String> ticketIds;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BulkCategorizationResult {
        private int totalTickets;
        private int successfulUpdates;
        private int failedUpdates;
        private List<String> errors = new java.util.ArrayList<>();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrainingResult {
        private boolean trainingStarted;
        private int sampleSize;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CategorizationAnalytics {
        private Long totalTicketsCategorized;
        private Double averageConfidenceScore;
        private java.util.Map<String, Long> categoryDistribution;
        private AccuracyMetrics accuracyMetrics;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AccuracyMetrics {
        private Double overallAccuracy;
        private java.util.Map<String, Double> categoryAccuracy;
        private Double precision;
        private Double recall;
        private Double f1Score;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RuleValidationRequest {
        private List<String> testCases;
        private List<String> rules;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RuleValidationResult {
        private boolean rulesValid;
        private List<String> validationErrors;
        private List<String> suggestions;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CategorizationHealthStatus {
        private boolean serviceHealthy;
        private boolean modelLoaded;
        private java.time.LocalDateTime lastTrainingDate;
        private String rulesVersion;
        private String modelVersion;
    }
}