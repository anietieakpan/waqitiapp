package com.waqiti.common.security.awareness;

import com.waqiti.common.security.awareness.domain.EmployeeSecurityProfile;
import com.waqiti.common.security.awareness.domain.EmployeeTrainingRecord;
import com.waqiti.common.security.awareness.domain.SecurityTrainingModule;
import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Extension methods for SecurityAwarenessService
@Service
@RequiredArgsConstructor
public class SecurityAwarenessServiceExtensions {

    private final SecurityTrainingModuleRepository moduleRepository;
    private final EmployeeTrainingRecordRepository trainingRecordRepository;
    private final EmployeeSecurityProfileRepository securityProfileRepository;

    @Transactional(readOnly = true)
    public List<TrainingModuleResponse> getAssignedTrainingModules(UUID employeeId) {
        List<EmployeeTrainingRecord> records = trainingRecordRepository
                .findByEmployeeId(employeeId);

        return records.stream()
                .map(record -> {
                    SecurityTrainingModule module = record.getModule();

                    return TrainingModuleResponse.builder()
                            .moduleId(module.getId())
                            .title(module.getTitle())
                            .description(module.getDescription())
                            .estimatedDurationMinutes(module.getEstimatedDurationMinutes())
                            .status(record.getStatus().name())
                            .scorePercentage(record.getScore())
                            .completedAt(record.getCompletedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public TrainingModuleContent startTrainingModule(UUID employeeId, UUID moduleId) {
        com.waqiti.common.security.awareness.model.SecurityTrainingModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new IllegalStateException("Module not found"));

        // Find the most recent training record for this employee and module
        List<EmployeeTrainingRecord> records = trainingRecordRepository.findByEmployeeId(employeeId);
        EmployeeTrainingRecord record = records.stream()
                .filter(r -> r.getModule().getId().equals(moduleId))
                .max(Comparator.comparing(EmployeeTrainingRecord::getCreatedAt))
                .orElseThrow(() -> new IllegalStateException("Training record not found"));

        record.startTraining();
        trainingRecordRepository.save(record);

        // Get content sections (already a List)
        List<Map<String, Object>> sections = module.getContentSections() != null ?
            module.getContentSections() : Collections.emptyList();

        return TrainingModuleContent.builder()
                .moduleId(module.getId())
                .title(module.getTitle())
                .sections(sections)
                .totalQuestions(10) // From module definition
                .passingScore(module.getPassingScorePercentage())
                .build();
    }

    @Transactional(readOnly = true)
    public EmployeeSecurityProfile getSecurityProfile(UUID employeeId) {
        return securityProfileRepository.findByEmployeeId(employeeId)
                .orElseThrow(() -> new IllegalStateException("Security profile not found"));
    }
}