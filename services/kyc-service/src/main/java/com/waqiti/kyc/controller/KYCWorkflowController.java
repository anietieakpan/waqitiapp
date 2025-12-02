package com.waqiti.kyc.controller;

import com.waqiti.kyc.dto.KYCApplicationRequest;
import com.waqiti.kyc.workflow.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/kyc/workflow")
@RequiredArgsConstructor
@Validated
@Tag(name = "KYC Workflow", description = "KYC workflow management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class KYCWorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/start")
    @Operation(summary = "Start KYC workflow", description = "Initiates a new KYC verification workflow")
    public ResponseEntity<Map<String, Object>> startWorkflow(@Valid @RequestBody KYCApplicationRequest request) {
        log.info("Starting KYC workflow for user: {}", request.getUserId());
        
        ProcessInstance processInstance = workflowService.startKYCWorkflow(request);
        
        Map<String, Object> response = Map.of(
                "processInstanceId", processInstance.getId(),
                "businessKey", processInstance.getBusinessKey(),
                "status", "STARTED",
                "message", "KYC workflow initiated successfully"
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tasks/user/{userId}")
    @Operation(summary = "Get user tasks", description = "Retrieves pending tasks for a specific user")
    public ResponseEntity<List<Map<String, Object>>> getUserTasks(@PathVariable String userId) {
        List<Task> tasks = workflowService.getUserTasks(userId);
        
        List<Map<String, Object>> taskList = tasks.stream()
                .map(task -> Map.of(
                        "id", task.getId(),
                        "name", task.getName(),
                        "description", task.getDescription() != null ? task.getDescription() : "",
                        "assignee", task.getAssignee() != null ? task.getAssignee() : "",
                        "created", task.getCreateTime(),
                        "priority", task.getPriority()
                ))
                .toList();
        
        return ResponseEntity.ok(taskList);
    }

    @GetMapping("/tasks/review")
    @Operation(summary = "Get review tasks", description = "Retrieves pending manual review tasks")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ResponseEntity<List<Map<String, Object>>> getReviewTasks(
            @RequestParam(defaultValue = "kyc-reviewers") String reviewerGroup) {
        
        List<Task> tasks = workflowService.getReviewerTasks(reviewerGroup);
        
        List<Map<String, Object>> taskList = tasks.stream()
                .map(task -> Map.of(
                        "id", task.getId(),
                        "name", task.getName(),
                        "processInstanceId", task.getProcessInstanceId(),
                        "created", task.getCreateTime(),
                        "priority", task.getPriority(),
                        "candidateGroups", task.getCandidates()
                ))
                .toList();
        
        return ResponseEntity.ok(taskList);
    }

    @PostMapping("/tasks/{taskId}/complete")
    @Operation(summary = "Complete manual review", description = "Completes a manual review task")
    @PreAuthorize("hasRole('KYC_REVIEWER')")
    public ResponseEntity<Map<String, Object>> completeReview(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> reviewData,
            @RequestHeader("X-User-Id") String reviewerId) {
        
        String decision = (String) reviewData.get("decision");
        String notes = (String) reviewData.get("notes");
        
        workflowService.completeManualReview(taskId, decision, notes, reviewerId);
        
        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", "COMPLETED",
                "reviewedBy", reviewerId,
                "decision", decision
        ));
    }

    @GetMapping("/process/{processInstanceId}")
    @Operation(summary = "Get process status", description = "Retrieves the current status of a KYC workflow")
    public ResponseEntity<Map<String, Object>> getProcessStatus(@PathVariable String processInstanceId) {
        ProcessInstance instance = workflowService.getProcessInstance(processInstanceId);
        
        if (instance == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> variables = workflowService.getProcessVariables(processInstanceId);
        
        Map<String, Object> response = Map.of(
                "processInstanceId", processInstanceId,
                "isEnded", instance.isEnded(),
                "isSuspended", instance.isSuspended(),
                "businessKey", instance.getBusinessKey() != null ? instance.getBusinessKey() : "",
                "variables", Map.of(
                        "kycStatus", variables.getOrDefault("kycStatus", "IN_PROGRESS"),
                        "kycLevel", variables.getOrDefault("kycLevel", "PENDING"),
                        "riskScore", variables.getOrDefault("riskScore", 0)
                )
        );
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/process/{processInstanceId}")
    @Operation(summary = "Cancel workflow", description = "Cancels an active KYC workflow")
    @PreAuthorize("hasRole('KYC_ADMIN')")
    public ResponseEntity<Map<String, Object>> cancelWorkflow(
            @PathVariable String processInstanceId,
            @RequestParam String reason) {
        
        workflowService.cancelWorkflow(processInstanceId, reason);
        
        return ResponseEntity.ok(Map.of(
                "processInstanceId", processInstanceId,
                "status", "CANCELLED",
                "reason", reason
        ));
    }
}