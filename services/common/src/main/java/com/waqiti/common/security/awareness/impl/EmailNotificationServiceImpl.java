package com.waqiti.common.security.awareness.impl;

import com.waqiti.common.security.awareness.EmailNotificationService;
import com.waqiti.common.security.awareness.EmailService;
import com.waqiti.common.security.awareness.dto.QuestionResult;
import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationServiceImpl implements EmailNotificationService {

    private final EmailService emailService;



    @Override
    public void sendTrainingReminder(String email, List<com.waqiti.common.security.awareness.domain.SecurityTrainingModule> overdueModules) {

    }

    @Override
    public void sendAssessmentCompletionNotification(String email, UUID assessmentId, BigDecimal score, boolean passed) {

    }

    @Override
    public void sendPhishingTestResults(String email, boolean passed, String campaignName) {

    }

    @Override
    public void sendAssessmentResultsWithFeedback(UUID employeeId, UUID assessmentId, BigDecimal score, List<QuestionResult> results) {

    }

    @Override
    public void sendComplianceAlert(UUID employeeId, String alertMessage) {

    }
}