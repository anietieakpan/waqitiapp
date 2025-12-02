package com.waqiti.kyc.workflow;

import com.waqiti.kyc.dto.KYCApplicationRequest;
import com.waqiti.kyc.model.KYCApplication;
import com.waqiti.kyc.model.KYCStatus;
import com.waqiti.kyc.repository.KYCApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final KYCApplicationRepository kycApplicationRepository;

    private static final String KYC_PROCESS_KEY = "kyc-verification-workflow";

    @Transactional
    public ProcessInstance startKYCWorkflow(KYCApplicationRequest request) {
        log.info("Starting KYC workflow for user: {}", request.getUserId());

        // Create KYC application
        KYCApplication application = createKYCApplication(request);
        
        // Prepare process variables
        Map<String, Object> variables = prepareProcessVariables(request, application);
        
        // Start the workflow
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                KYC_PROCESS_KEY,
                application.getId(),
                variables
        );
        
        // Update application with process instance ID
        application.setProcessInstanceId(processInstance.getId());
        kycApplicationRepository.save(application);
        
        log.info("KYC workflow started with process instance ID: {} for application: {}", 
                processInstance.getId(), application.getId());
        
        return processInstance;
    }

    private KYCApplication createKYCApplication(KYCApplicationRequest request) {
        KYCApplication application = new KYCApplication();
        application.setId(UUID.randomUUID().toString());
        application.setUserId(request.getUserId());
        application.setApplicationType(request.getApplicationType());
        application.setStatus(KYCStatus.IN_PROGRESS);
        application.setCreatedAt(LocalDateTime.now());
        application.setLastUpdated(LocalDateTime.now());
        
        return kycApplicationRepository.save(application);
    }

    private Map<String, Object> prepareProcessVariables(KYCApplicationRequest request, KYCApplication application) {
        Map<String, Object> variables = new HashMap<>();
        
        // Basic information
        variables.put("userId", request.getUserId());
        variables.put("kycApplicationId", application.getId());
        variables.put("userType", request.getUserType());
        variables.put("applicationType", request.getApplicationType());
        
        // Personal information
        variables.put("firstName", request.getFirstName());
        variables.put("lastName", request.getLastName());
        variables.put("dateOfBirth", request.getDateOfBirth());
        variables.put("email", request.getEmail());
        variables.put("phone", request.getPhone());
        variables.put("nationality", request.getNationality());
        
        // Address information
        if (request.getAddress() != null) {
            variables.put("addressLine1", request.getAddress().getLine1());
            variables.put("addressLine2", request.getAddress().getLine2());
            variables.put("city", request.getAddress().getCity());
            variables.put("state", request.getAddress().getState());
            variables.put("postalCode", request.getAddress().getPostalCode());
            variables.put("country", request.getAddress().getCountry());
        }
        
        // Risk factors for workflow routing
        variables.put("expectedTransactionVolume", request.getExpectedTransactionVolume());
        variables.put("countryRisk", determineCountryRisk(request.getAddress().getCountry()));
        variables.put("isPEP", request.isPoliticallyExposed());
        
        // Provider preferences
        if (request.getPreferredProvider() != null) {
            variables.put("kycProvider", request.getPreferredProvider());
        }
        
        return variables;
    }

    private String determineCountryRisk(String country) {
        // Simplified country risk determination
        // In production, this would use a comprehensive risk assessment service
        Map<String, String> highRiskCountries = Map.of(
                "IR", "HIGH", // Iran
                "KP", "HIGH", // North Korea
                "SY", "HIGH", // Syria
                "CU", "HIGH", // Cuba
                "VE", "HIGH"  // Venezuela
        );
        
        Map<String, String> mediumRiskCountries = Map.of(
                "RU", "MEDIUM", // Russia
                "CN", "MEDIUM", // China
                "TR", "MEDIUM", // Turkey
                "BR", "MEDIUM", // Brazil
                "MX", "MEDIUM"  // Mexico
        );
        
        if (highRiskCountries.containsKey(country)) {
            return "HIGH";
        } else if (mediumRiskCountries.containsKey(country)) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    public List<Task> getUserTasks(String userId) {
        return taskService.createTaskQuery()
                .processDefinitionKey(KYC_PROCESS_KEY)
                .taskAssignee(userId)
                .active()
                .list();
    }

    public List<Task> getReviewerTasks(String reviewerGroup) {
        return taskService.createTaskQuery()
                .processDefinitionKey(KYC_PROCESS_KEY)
                .taskCandidateGroup(reviewerGroup)
                .active()
                .list();
    }

    @Transactional
    public void completeManualReview(String taskId, String decision, String notes, String reviewerId) {
        log.info("Completing manual review task: {} with decision: {}", taskId, decision);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("reviewDecision", decision);
        variables.put("reviewNotes", notes);
        variables.put("kycReviewer", reviewerId);
        variables.put("reviewCompletedAt", LocalDateTime.now());
        
        taskService.complete(taskId, variables);
        
        log.info("Manual review completed by: {}", reviewerId);
    }

    public ProcessInstance getProcessInstance(String processInstanceId) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    public Map<String, Object> getProcessVariables(String processInstanceId) {
        return runtimeService.getVariables(processInstanceId);
    }

    @Transactional
    public void cancelWorkflow(String processInstanceId, String reason) {
        log.info("Cancelling workflow: {} for reason: {}", processInstanceId, reason);
        
        runtimeService.deleteProcessInstance(processInstanceId, reason);
        
        // Update application status
        kycApplicationRepository.findByProcessInstanceId(processInstanceId)
                .ifPresent(application -> {
                    application.setStatus(KYCStatus.CANCELLED);
                    application.setLastUpdated(LocalDateTime.now());
                    kycApplicationRepository.save(application);
                });
    }
}