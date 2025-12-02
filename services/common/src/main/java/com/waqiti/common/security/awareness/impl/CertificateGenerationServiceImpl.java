package com.waqiti.common.security.awareness.impl;

import com.waqiti.common.security.awareness.CertificateGenerationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class CertificateGenerationServiceImpl implements CertificateGenerationService {

    @Override
    public String generateTrainingCertificate(UUID employeeId, UUID moduleId, LocalDateTime completionDate) {
        return "";
    }

    @Override
    public String generateAssessmentCertificate(UUID employeeId, UUID assessmentId, LocalDateTime completionDate) {
        return "";
    }
}