package com.waqiti.common.security.awareness;

import com.waqiti.common.security.awareness.domain.AssessmentResult;
import com.waqiti.common.security.awareness.domain.Employee;
import com.waqiti.common.security.awareness.domain.QuarterlySecurityAssessment;
import com.waqiti.common.security.awareness.domain.SecurityAwarenessAuditLog;
import com.waqiti.common.security.awareness.domain.*;
import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import com.waqiti.common.security.awareness.model.AssessmentQuestion;
import com.waqiti.common.security.awareness.model.QuestionResult;
import com.waqiti.common.security.awareness.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * PCI DSS REQ 12.6.3 - Quarterly Security Assessment Service
 *
 * Manages quarterly security assessments for personnel with security responsibilities,
 * including threat and vulnerability landscape awareness testing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuarterlyAssessmentService {

    private final QuarterlySecurityAssessmentRepository assessmentRepository;
    private final AssessmentResultRepository resultRepository;
    private final EmployeeRepository employeeRepository;
    private final SecurityAwarenessService awarenessService;
    private final EmailNotificationService emailService;
    private final SecurityAwarenessAuditRepository auditRepository;

    /**
     * Create quarterly security assessment (PCI DSS REQ 12.6.3)
     */
    @Transactional
    public UUID createQuarterlyAssessment(QuarterlyAssessmentRequest request) {
        log.info("PCI_DSS_12.6.3: Creating quarterly assessment for Q{} {}", request.getQuarter(), request.getYear());

        // Validate quarter and year
        if (request.getQuarter() < 1 || request.getQuarter() > 4) {
            throw new IllegalArgumentException("Quarter must be between 1 and 4");
        }

        // Check if assessment already exists for this quarter
        Optional<QuarterlySecurityAssessment> existing = assessmentRepository
                .findByQuarterAndYearAndAssessmentType(
                        request.getQuarter(),
                        request.getYear(),
                        request.getAssessmentType()
                );

        if (existing.isPresent()) {
            throw new IllegalStateException(
                    String.format("Assessment already exists for Q%d %d - %s",
                            request.getQuarter(), request.getYear(), request.getAssessmentType())
            );
        }

        QuarterlySecurityAssessment assessment = QuarterlySecurityAssessment.builder()
                .title(request.getAssessmentName())
                .quarter(request.getQuarter())
                .year(request.getYear())
                .assessmentType(QuarterlySecurityAssessment.AssessmentType.valueOf(request.getAssessmentType().name()))
                .availableFrom(request.getAvailableFrom())
                .availableUntil(request.getAvailableUntil())
                .totalQuestions(request.getQuestions().size())
                .passingScore(request.getPassingScorePercentage())
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .status(QuarterlySecurityAssessment.AssessmentStatus.DRAFT)
                .createdBy(request.getCreatedBy())
                .questions(request.getQuestions()) // Store questions as JSONB
                .build();

        assessment = assessmentRepository.save(assessment);

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("QUARTERLY_ASSESSMENT_CREATED")
                .entityType("ASSESSMENT")
                .entityId(assessment.getId())
                .pciRequirement("12.6.3")
                .complianceStatus("DRAFT")
                .eventData(Map.of(
                        "quarter", request.getQuarter(),
                        "year", request.getYear(),
                        "type", request.getAssessmentType(),
                        "target_roles", String.join(",", request.getTargetRoles()),
                        "total_questions", request.getQuestions().size()
                ))
                .build());

        log.info("✅ Created quarterly assessment: id={}, Q{} {}", assessment.getId(), request.getQuarter(), request.getYear());
        return assessment.getId();
    }

    /**
     * Publish assessment and notify target employees
     */
    @Transactional
    public void publishAssessment(UUID assessmentId) {
        QuarterlySecurityAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalStateException("Assessment not found"));

        if (assessment.getStatus() != QuarterlySecurityAssessment.AssessmentStatus.DRAFT) {
            throw new IllegalStateException("Only draft assessments can be published");
        }

        assessment.setStatus(QuarterlySecurityAssessment.AssessmentStatus.PUBLISHED);
        assessmentRepository.save(assessment);

        // Get target employees
        List<Employee> targetEmployees = employeeRepository.findByRolesIn(assessment.getTargetRoles());

        // Send notifications
        for (Employee employee : targetEmployees) {
            emailService.sendAssessmentNotification(
                    employee.getEmail(),
                    assessment.getTitle(),
                    assessment.getQuarter(),
                    assessment.getYear(),
                    assessment.getAvailableUntil()
            );
        }

        log.info("✅ Published quarterly assessment: id={}, notified {} employees", assessmentId, targetEmployees.size());

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("QUARTERLY_ASSESSMENT_PUBLISHED")
                .entityType("ASSESSMENT")
                .entityId(assessmentId)
                .pciRequirement("12.6.3")
                .complianceStatus("PUBLISHED")
                .eventData(Map.of(
                        "employees_notified", targetEmployees.size()
                ))
                .build());
    }

    /**
     * Start assessment for employee
     */
    @Transactional
    public AssessmentSession startAssessment(UUID employeeId, UUID assessmentId) {
        QuarterlySecurityAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalStateException("Assessment not found"));

        // Validate assessment is available
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(assessment.getAvailableFrom()) || now.isAfter(assessment.getAvailableUntil())) {
            throw new IllegalStateException("Assessment is not currently available");
        }

        // Check if employee already completed this assessment
        Optional<AssessmentResult> existingResult = resultRepository
                .findByAssessmentIdAndEmployeeId(assessmentId, employeeId);

        if (existingResult.isPresent() && existingResult.get().isPassed()) {
            throw new IllegalStateException("Assessment already completed");
        }

        // Create new assessment result
        AssessmentResult result = AssessmentResult.builder()
                .assessmentId(assessmentId)
                .employeeId(employeeId)
                .startedAt(LocalDateTime.now())
                .build();

        result = resultRepository.save(result);

        // Calculate deadline (if time limit exists)
        LocalDateTime deadline = assessment.getTimeLimitMinutes() != null
                ? LocalDateTime.now().plusMinutes(assessment.getTimeLimitMinutes())
                : assessment.getAvailableUntil();

        log.info("📝 Started assessment: employeeId={}, assessmentId={}", employeeId, assessmentId);

        // Randomize question order for integrity
        List<AssessmentQuestion> shuffledQuestions = new ArrayList<>(assessment.getQuestions());
        Collections.shuffle(shuffledQuestions);

        return AssessmentSession.builder()
                .sessionId(result.getId())
                .assessmentTitle(assessment.getTitle())
                .questions(shuffledQuestions)
                .totalQuestions(assessment.getTotalQuestions())
                .passingScore(assessment.getPassingScore())
                .deadline(deadline)
                .build();
    }

    /**
     * Submit assessment answers and calculate score
     */
    @Transactional
    public AssessmentCompletionResult submitAssessment(UUID resultId, Map<UUID, String> answers) {
        AssessmentResult result = resultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalStateException("Assessment result not found"));

        if (result.getCompletedAt() != null) {
            throw new IllegalStateException("Assessment already submitted");
        }

        QuarterlySecurityAssessment assessment = assessmentRepository.findById(result.getAssessmentId())
                .orElseThrow(() -> new IllegalStateException("Assessment not found"));

        // Check time limit
        LocalDateTime now = LocalDateTime.now();
        long timeTakenMinutes = ChronoUnit.MINUTES.between(result.getStartedAt(), now);

        if (assessment.getTimeLimitMinutes() != null && timeTakenMinutes > assessment.getTimeLimitMinutes()) {
            // Time expired - auto-fail
            result.setCompletedAt(now);
            result.setTimeTakenMinutes((int) timeTakenMinutes);
            result.setScorePercentage(0);
            result.setPassed(false);
            result.setRequiresRemediation(true);
            result.setFeedbackProvided("Assessment exceeded time limit");
            resultRepository.save(result);

            log.warn("⏰ Assessment time limit exceeded: employeeId={}, timeTaken={}min",
                    result.getEmployeeId(), timeTakenMinutes);

            return AssessmentCompletionResult.builder()
                    .passed(false)
                    .timeLimitExceeded(true)
                    .build();
        }

        // Calculate score
        int correctAnswers = 0;
        List<QuestionResult> questionResults = new ArrayList<>();

        for (AssessmentQuestion question : assessment.getQuestions()) {
            String employeeAnswer = answers.get(question.getQuestionId());
            boolean correct = question.getCorrectAnswer().equals(employeeAnswer);

            if (correct) {
                correctAnswers++;
            }

            questionResults.add(QuestionResult.builder()
                    .questionId(question.getQuestionId())
                    .employeeAnswer(employeeAnswer)
                    .correctAnswer(question.getCorrectAnswer())
                    .isCorrect(correct)
                    .explanation(question.getExplanation())
                    .build());
        }

        int scorePercentage = (int) Math.round((correctAnswers * 100.0) / assessment.getTotalQuestions());
        boolean passed = scorePercentage >= assessment.getPassingScorePercentage();

        // Update result
        result.setCompletedAt(now);
        result.setTimeTakenMinutes((int) timeTakenMinutes);
        result.setScorePercentage(scorePercentage);
        result.setPassed(passed);
        result.setRequiresRemediation(!passed);
        result.setAnswersData(questionResults); // Store detailed results as JSONB
        resultRepository.save(result);

        // Update employee security profile
        awarenessService.updateEmployeeSecurityProfile(result.getEmployeeId());

        log.info("✅ Assessment submitted: employeeId={}, score={}%, passed={}",
                result.getEmployeeId(), scorePercentage, passed);

        // Send result email with detailed feedback
        emailService.sendAssessmentResultsWithFeedback(
                result.getEmployeeId(),
                assessmentId,
                BigDecimal.valueOf(scorePercentage),
                questionResults
        );

        // Audit log
        auditRepository.save(SecurityAwarenessAuditLog.builder()
                .eventType("QUARTERLY_ASSESSMENT_COMPLETED")
                .entityType("ASSESSMENT_RESULT")
                .entityId(result.getId())
                .employeeId(result.getEmployeeId())
                .pciRequirement("12.6.3")
                .complianceStatus(passed ? "PASSED" : "FAILED")
                .eventData(Map.of(
                        "assessment_id", assessment.getId().toString(),
                        "score", scorePercentage,
                        "passed", passed,
                        "time_taken_minutes", timeTakenMinutes
                ))
                .build());

        return AssessmentCompletionResult.builder()
                .passed(passed)
                .score(BigDecimal.valueOf(scorePercentage))
                .timeTaken(timeTakenMinutes)
                .correctAnswers(correctAnswers)
                .totalQuestions(assessment.getTotalQuestions())
                .questionResults(questionResults)
                .requiresRemediation(!passed)
                .build();
    }

    /**
     * Get assessment statistics for compliance reporting
     */
    @Transactional(readOnly = true)
    public AssessmentStatistics getAssessmentStatistics(UUID assessmentId) {
        QuarterlySecurityAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalStateException("Assessment not found"));

        long totalAssigned = employeeRepository.countByRolesIn(assessment.getTargetRoles());
        long totalCompleted = resultRepository.countByAssessmentIdAndCompletedAtIsNotNull(assessmentId);
        long totalPassed = resultRepository.countByAssessmentIdAndPassedTrue(assessmentId);
        long totalFailed = resultRepository.countByAssessmentIdAndPassedFalse(assessmentId);

        BigDecimal completionRate = totalAssigned > 0
                ? BigDecimal.valueOf(totalCompleted)
                        .divide(BigDecimal.valueOf(totalAssigned), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal passRate = totalCompleted > 0
                ? BigDecimal.valueOf(totalPassed)
                        .divide(BigDecimal.valueOf(totalCompleted), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Double averageScore = resultRepository.calculateAverageScoreByAssessmentId(assessmentId);

        return AssessmentStatistics.builder()
                .assessmentId(assessmentId)
                .assessmentTitle(assessment.getTitle())
                .averageScore(averageScore != null ? BigDecimal.valueOf(averageScore) : BigDecimal.ZERO)
                .quarter(assessment.getQuarter())
                .year(assessment.getYear())
                .totalAssigned(totalAssigned)
                .totalCompleted(totalCompleted)
                .totalPassed(totalPassed)
                .totalFailed(totalFailed)
                .completionRate(completionRate)
                .passRate(passRate)
                .averageScore(averageScore != null ? BigDecimal.valueOf(averageScore) : BigDecimal.ZERO)
                .build();
    }

    /**
     * Automatic quarterly assessment scheduler
     * Creates assessments at the beginning of each quarter
     */
    @Scheduled(cron = "0 0 9 1 1,4,7,10 *") // 9 AM on first day of each quarter
    @Transactional
    public void autoCreateQuarterlyAssessments() {
        LocalDateTime now = LocalDateTime.now();
        int quarter = (now.getMonthValue() - 1) / 3 + 1;
        int year = now.getYear();

        log.info("PCI_DSS_12.6.3: Auto-creating quarterly assessments for Q{} {}", quarter, year);

        // Create Knowledge Check Assessment
        createStandardQuarterlyAssessment(
                quarter,
                year,
                AssessmentType.KNOWLEDGE_CHECK,
                "Q" + quarter + " " + year + " Security Knowledge Check",
                Arrays.asList("DEVELOPER", "DEVOPS", "DBA", "SECURITY_ADMIN")
        );

        // Create Vulnerability Awareness Assessment
        createStandardQuarterlyAssessment(
                quarter,
                year,
                AssessmentType.VULNERABILITY_AWARENESS,
                "Q" + quarter + " " + year + " Vulnerability Awareness",
                Arrays.asList("SECURITY_ADMIN", "DEVOPS", "DBA")
        );

        // Create Threat Landscape Assessment (PCI DSS REQ 12.6.3.1)
        createStandardQuarterlyAssessment(
                quarter,
                year,
                AssessmentType.THREAT_LANDSCAPE,
                "Q" + quarter + " " + year + " Threat Landscape Awareness",
                Arrays.asList("SECURITY_ADMIN", "SECURITY_ANALYST", "SOC_ANALYST")
        );

        log.info("✅ Auto-created quarterly assessments for Q{} {}", quarter, year);
    }

    /**
     * Create standard quarterly assessment with predefined questions
     */
    private void createStandardQuarterlyAssessment(
            int quarter,
            int year,
            AssessmentType type,
            String name,
            List<String> targetRoles
    ) {
        List<AssessmentQuestion> questions = getStandardQuestionsForType(type, quarter, year);

        QuarterlyAssessmentRequest request = QuarterlyAssessmentRequest.builder()
                .assessmentName(name)
                .quarter(quarter)
                .year(year)
                .assessmentType(com.waqiti.common.security.awareness.model.AssessmentType.valueOf(type.name()))
                .targetRoles(targetRoles)
                .availableFrom(getQuarterStartDate(quarter, year))
                .availableUntil(getQuarterEndDate(quarter, year))
                .questions(questions)
                .passingScorePercentage(80)
                .timeLimitMinutes(30)
                .createdBy(UUID.fromString("00000000-0000-0000-0000-000000000000")) // System user
                .build();

        createQuarterlyAssessment(request);
    }

    /**
     * Get standard questions based on assessment type
     */
    private List<AssessmentQuestion> getStandardQuestionsForType(AssessmentType type, int quarter, int year) {
        // In production, these would be loaded from a question bank
        // with rotating questions updated quarterly based on current threat landscape

        return switch (type) {
            case KNOWLEDGE_CHECK -> Arrays.asList(
                    AssessmentQuestion.builder()
                            .questionId(UUID.randomUUID())
                            .questionText("What is the maximum number of login attempts allowed before account lockout?")
                            .options(Arrays.asList("3", "5", "10", "Unlimited"))
                            .correctAnswer("5")
                            .explanation("PCI DSS requires account lockout after maximum 6 attempts. We use 5.")
                            .build(),
                    AssessmentQuestion.builder()
                            .questionId(UUID.randomUUID())
                            .questionText("How often must passwords be changed according to our security policy?")
                            .options(Arrays.asList("30 days", "60 days", "90 days", "180 days"))
                            .correctAnswer("90 days")
                            .explanation("PCI DSS REQ 8.2.4 requires password changes at least every 90 days.")
                            .build()
                    // ... more questions
            );

            case VULNERABILITY_AWARENESS -> Arrays.asList(
                    AssessmentQuestion.builder()
                            .questionId(UUID.randomUUID())
                            .questionText("What is SQL Injection?")
                            .options(Arrays.asList(
                                    "Injecting malicious SQL code through user input",
                                    "Running SQL queries in production",
                                    "Using stored procedures",
                                    "Database backup process"
                            ))
                            .correctAnswer("Injecting malicious SQL code through user input")
                            .explanation("SQL Injection is a code injection technique that exploits security vulnerabilities in the database layer.")
                            .build()
                    // ... more questions
            );

            case THREAT_LANDSCAPE -> Arrays.asList(
                    AssessmentQuestion.builder()
                            .questionId(UUID.randomUUID())
                            .questionText("Which ransomware group has been most active targeting financial institutions in Q" + quarter + " " + year + "?")
                            .options(Arrays.asList("LockBit", "BlackCat", "REvil", "Conti"))
                            .correctAnswer("BlackCat") // This would be updated based on current threat intelligence
                            .explanation("BlackCat (ALPHV) has been highly active targeting financial services with sophisticated double-extortion tactics.")
                            .build()
                    // ... more current threat landscape questions
            );
        };
    }

    /**
     * Helper methods for quarter date calculations
     */
    private LocalDateTime getQuarterStartDate(int quarter, int year) {
        int month = (quarter - 1) * 3 + 1;
        return LocalDateTime.of(year, month, 1, 0, 0);
    }

    private LocalDateTime getQuarterEndDate(int quarter, int year) {
        int month = quarter * 3;
        int lastDay = LocalDateTime.of(year, month, 1, 0, 0).toLocalDate().lengthOfMonth();
        return LocalDateTime.of(year, month, lastDay, 23, 59, 59);
    }

    /**
     * Send reminders for incomplete assessments
     */
    @Scheduled(cron = "0 0 10 * * MON") // Every Monday at 10 AM
    @Transactional(readOnly = true)
    public void sendAssessmentReminders() {
        LocalDateTime now = LocalDateTime.now();

        List<QuarterlySecurityAssessment> activeAssessments = assessmentRepository
                .findByStatusAndAvailableFromBeforeAndAvailableUntilAfter(
                        QuarterlySecurityAssessment.AssessmentStatus.PUBLISHED,
                        now,
                        now
                );

        for (QuarterlySecurityAssessment assessment : activeAssessments) {
            List<Employee> targetEmployees = employeeRepository.findByRolesIn(assessment.getTargetRoles());

            for (Employee employee : targetEmployees) {
                Optional<AssessmentResult> result = resultRepository
                        .findByAssessmentIdAndEmployeeId(assessment.getId(), employee.getId());

                // Send reminder if not completed
                if (result.isEmpty() || result.get().getCompletedAt() == null) {
                    long daysRemaining = ChronoUnit.DAYS.between(now, assessment.getAvailableUntil());

                    emailService.sendAssessmentReminderEmail(
                            employee.getEmail(),
                            assessment.getAssessmentName(),
                            daysRemaining
                    );
                }
            }
        }

        log.info("📧 Sent weekly assessment reminders for {} active assessments", activeAssessments.size());
    }
}
