package com.waqiti.lending.kafka;

import com.waqiti.common.events.StudentLoanEvent;
import com.waqiti.lending.domain.StudentLoan;
import com.waqiti.lending.domain.EducationVerification;
import com.waqiti.lending.repository.StudentLoanRepository;
import com.waqiti.lending.repository.EducationVerificationRepository;
import com.waqiti.lending.service.StudentLoanService;
import com.waqiti.lending.service.EducationVerificationService;
import com.waqiti.lending.service.RepaymentPlanService;
import com.waqiti.lending.metrics.LendingMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class StudentLoanEventsConsumer {
    
    private final StudentLoanRepository studentLoanRepository;
    private final EducationVerificationRepository educationVerificationRepository;
    private final StudentLoanService studentLoanService;
    private final EducationVerificationService educationVerificationService;
    private final RepaymentPlanService repaymentPlanService;
    private final LendingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MAX_STUDENT_LOAN_AMOUNT = new BigDecimal("100000");
    private static final int GRACE_PERIOD_MONTHS = 6;
    private static final BigDecimal STANDARD_INTEREST_RATE = new BigDecimal("6.5");
    
    @KafkaListener(
        topics = {"student-loan-events", "education-loan-events", "tuition-financing-events"},
        groupId = "lending-student-loan-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleStudentLoanEvent(
            @Payload StudentLoanEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("student-loan-%s-p%d-o%d", 
            event.getLoanId() != null ? event.getLoanId() : event.getStudentId(), 
            partition, offset);
        
        log.info("Processing student loan event: loanId={}, type={}, amount={}", 
            event.getLoanId(), event.getEventType(), event.getLoanAmount());
        
        try {
            switch (event.getEventType()) {
                case STUDENT_LOAN_APPLICATION_SUBMITTED:
                    processStudentLoanApplicationSubmitted(event, correlationId);
                    break;
                case ENROLLMENT_VERIFIED:
                    processEnrollmentVerified(event, correlationId);
                    break;
                case COSIGNER_ADDED:
                    processCosignerAdded(event, correlationId);
                    break;
                case STUDENT_LOAN_APPROVED:
                    processStudentLoanApproved(event, correlationId);
                    break;
                case TUITION_PAID:
                    processTuitionPaid(event, correlationId);
                    break;
                case IN_SCHOOL_DEFERMENT_STARTED:
                    processInSchoolDefermentStarted(event, correlationId);
                    break;
                case GRADUATION_REPORTED:
                    processGraduationReported(event, correlationId);
                    break;
                case GRACE_PERIOD_STARTED:
                    processGracePeriodStarted(event, correlationId);
                    break;
                case REPAYMENT_PLAN_SELECTED:
                    processRepaymentPlanSelected(event, correlationId);
                    break;
                case REPAYMENT_STARTED:
                    processRepaymentStarted(event, correlationId);
                    break;
                case INCOME_DRIVEN_REPAYMENT_APPLIED:
                    processIncomeDrivenRepaymentApplied(event, correlationId);
                    break;
                case LOAN_FORGIVENESS_APPLIED:
                    processLoanForgivenessApplied(event, correlationId);
                    break;
                default:
                    log.warn("Unknown student loan event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logLendingEvent(
                "STUDENT_LOAN_EVENT_PROCESSED",
                event.getLoanId() != null ? event.getLoanId() : "N/A",
                Map.of(
                    "eventType", event.getEventType(),
                    "studentId", event.getStudentId() != null ? event.getStudentId() : "N/A",
                    "schoolName", event.getSchoolName() != null ? event.getSchoolName() : "N/A",
                    "loanAmount", event.getLoanAmount() != null ? event.getLoanAmount().toString() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process student loan event: {}", e.getMessage(), e);
            kafkaTemplate.send("student-loan-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processStudentLoanApplicationSubmitted(StudentLoanEvent event, String correlationId) {
        log.info("Student loan application submitted: studentId={}, amount={}, school={}, degree={}", 
            event.getStudentId(), event.getLoanAmount(), event.getSchoolName(), event.getDegreeProgram());
        
        if (event.getLoanAmount().compareTo(MAX_STUDENT_LOAN_AMOUNT) > 0) {
            log.error("Loan amount exceeds maximum: amount={}, max={}", 
                event.getLoanAmount(), MAX_STUDENT_LOAN_AMOUNT);
            return;
        }
        
        StudentLoan loan = StudentLoan.builder()
            .id(UUID.randomUUID().toString())
            .studentId(event.getStudentId())
            .loanAmount(event.getLoanAmount())
            .schoolName(event.getSchoolName())
            .schoolId(event.getSchoolId())
            .degreeProgram(event.getDegreeProgram())
            .expectedGraduationDate(event.getExpectedGraduationDate())
            .interestRate(STANDARD_INTEREST_RATE)
            .applicationDate(LocalDateTime.now())
            .status("SUBMITTED")
            .loanType("STUDENT")
            .subsidized(event.isSubsidized())
            .correlationId(correlationId)
            .build();
        
        studentLoanRepository.save(loan);
        
        educationVerificationService.verifyEnrollment(loan.getId(), 
            event.getSchoolId(), event.getStudentId());
        
        notificationService.sendNotification(
            event.getStudentId(),
            "Student Loan Application Received",
            String.format("Your application for a %s student loan has been received. " +
                "School: %s, Program: %s. We're verifying your enrollment.",
                event.getLoanAmount(), event.getSchoolName(), event.getDegreeProgram()),
            correlationId
        );
        
        metricsService.recordStudentLoanApplicationSubmitted(
            event.getSchoolName(), event.getDegreeProgram(), event.getLoanAmount());
    }
    
    private void processEnrollmentVerified(StudentLoanEvent event, String correlationId) {
        log.info("Enrollment verified: loanId={}, studentId={}, enrollmentStatus={}, gpa={}", 
            event.getLoanId(), event.getStudentId(), event.getEnrollmentStatus(), event.getGpa());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        EducationVerification verification = EducationVerification.builder()
            .id(UUID.randomUUID().toString())
            .loanId(event.getLoanId())
            .studentId(event.getStudentId())
            .schoolId(event.getSchoolId())
            .enrollmentStatus(event.getEnrollmentStatus())
            .gpa(event.getGpa())
            .verifiedAt(LocalDateTime.now())
            .verificationMethod(event.getVerificationMethod())
            .isFullTime("FULL_TIME".equals(event.getEnrollmentStatus()))
            .build();
        
        educationVerificationRepository.save(verification);
        
        loan.setEnrollmentVerified(true);
        loan.setEnrollmentStatus(event.getEnrollmentStatus());
        loan.setGpa(event.getGpa());
        loan.setEnrollmentVerifiedAt(LocalDateTime.now());
        studentLoanRepository.save(loan);
        
        if (event.getGpa() != null && event.getGpa().compareTo(new BigDecimal("3.5")) >= 0) {
            BigDecimal discountedRate = loan.getInterestRate().multiply(new BigDecimal("0.95"));
            loan.setInterestRate(discountedRate);
            studentLoanRepository.save(loan);
            
            log.info("High GPA discount applied: gpa={}, newRate={}%", event.getGpa(), discountedRate);
        }
        
        studentLoanService.processLoanApproval(loan.getId());
        
        metricsService.recordEnrollmentVerified(event.getEnrollmentStatus());
    }
    
    private void processCosignerAdded(StudentLoanEvent event, String correlationId) {
        log.info("Cosigner added: loanId={}, cosignerId={}, relationship={}", 
            event.getLoanId(), event.getCosignerId(), event.getCosignerRelationship());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setCosignerId(event.getCosignerId());
        loan.setCosignerRelationship(event.getCosignerRelationship());
        loan.setCosignerCreditScore(event.getCosignerCreditScore());
        loan.setCosignerAddedAt(LocalDateTime.now());
        loan.setHasCosigner(true);
        studentLoanRepository.save(loan);
        
        if (event.getCosignerCreditScore() != null && event.getCosignerCreditScore() >= 750) {
            BigDecimal improvedRate = loan.getInterestRate().multiply(new BigDecimal("0.90"));
            loan.setInterestRate(improvedRate);
            studentLoanRepository.save(loan);
            
            log.info("Cosigner with excellent credit - rate reduced to {}%", improvedRate);
        }
        
        notificationService.sendNotification(
            event.getCosignerId(),
            "Student Loan Cosigner Confirmation",
            String.format("You've been added as a cosigner for a student loan of %s. " +
                "You're jointly responsible for loan repayment.",
                loan.getLoanAmount()),
            correlationId
        );
        
        metricsService.recordCosignerAdded(event.getCosignerRelationship());
    }
    
    private void processStudentLoanApproved(StudentLoanEvent event, String correlationId) {
        log.info("Student loan approved: loanId={}, approvedAmount={}, rate={}, term={} years", 
            event.getLoanId(), event.getApprovedAmount(), event.getInterestRate(), event.getTermYears());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setApprovedAmount(event.getApprovedAmount());
        loan.setInterestRate(event.getInterestRate());
        loan.setTermYears(event.getTermYears());
        loan.setApprovedAt(LocalDateTime.now());
        loan.setStatus("APPROVED");
        studentLoanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getStudentId(),
            "Student Loan Approved",
            String.format("Your student loan of %s has been approved! Rate: %s%% APR, Term: %d years. " +
                "Funds will be sent directly to %s for tuition payment.",
                event.getApprovedAmount(), event.getInterestRate(), event.getTermYears(),
                loan.getSchoolName()),
            correlationId
        );
        
        if (loan.getHasCosigner()) {
            notificationService.sendNotification(
                loan.getCosignerId(),
                "Student Loan Approved",
                String.format("The student loan you cosigned for %s has been approved.",
                    event.getApprovedAmount()),
                correlationId
            );
        }
        
        metricsService.recordStudentLoanApproved(event.getApprovedAmount(), event.getTermYears());
    }
    
    private void processTuitionPaid(StudentLoanEvent event, String correlationId) {
        log.info("Tuition paid: loanId={}, schoolId={}, paidAmount={}, semester={}", 
            event.getLoanId(), event.getSchoolId(), event.getTuitionAmount(), event.getSemester());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setDisbursedAmount(
            loan.getDisbursedAmount() != null ? 
                loan.getDisbursedAmount().add(event.getTuitionAmount()) : 
                event.getTuitionAmount()
        );
        loan.setLastDisbursementDate(LocalDateTime.now());
        loan.setStatus("DISBURSED");
        studentLoanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getStudentId(),
            "Tuition Payment Confirmed",
            String.format("Your tuition payment of %s for %s semester has been sent to %s. " +
                "Focus on your studies - repayment begins after graduation.",
                event.getTuitionAmount(), event.getSemester(), loan.getSchoolName()),
            correlationId
        );
        
        metricsService.recordTuitionPaid(event.getSchoolId(), event.getTuitionAmount());
    }
    
    private void processInSchoolDefermentStarted(StudentLoanEvent event, String correlationId) {
        log.info("In-school deferment started: loanId={}, studentId={}", 
            event.getLoanId(), event.getStudentId());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setInSchoolDeferment(true);
        loan.setDefermentStartDate(LocalDateTime.now());
        loan.setStatus("IN_SCHOOL");
        studentLoanRepository.save(loan);
        
        if (!loan.isSubsidized()) {
            log.info("Unsubsidized loan - interest accruing during deferment");
            loan.setInterestAccruing(true);
            studentLoanRepository.save(loan);
        }
        
        metricsService.recordInSchoolDefermentStarted();
    }
    
    private void processGraduationReported(StudentLoanEvent event, String correlationId) {
        log.info("Graduation reported: loanId={}, studentId={}, graduationDate={}, degree={}", 
            event.getLoanId(), event.getStudentId(), event.getGraduationDate(), event.getDegreeAwarded());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setGraduated(true);
        loan.setGraduationDate(event.getGraduationDate());
        loan.setDegreeAwarded(event.getDegreeAwarded());
        loan.setInSchoolDeferment(false);
        loan.setDefermentEndDate(LocalDateTime.now());
        studentLoanRepository.save(loan);
        
        studentLoanService.initiateGracePeriod(loan.getId());
        
        notificationService.sendNotification(
            loan.getStudentId(),
            "Congratulations Graduate!",
            String.format("Congratulations on earning your %s! You now have a %d-month grace period " +
                "before loan repayment begins. We'll help you choose the best repayment plan.",
                event.getDegreeAwarded(), GRACE_PERIOD_MONTHS),
            correlationId
        );
        
        metricsService.recordGraduationReported(event.getDegreeAwarded());
    }
    
    private void processGracePeriodStarted(StudentLoanEvent event, String correlationId) {
        log.info("Grace period started: loanId={}, duration={} months, endDate={}", 
            event.getLoanId(), GRACE_PERIOD_MONTHS, event.getGracePeriodEndDate());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setGracePeriodActive(true);
        loan.setGracePeriodStartDate(LocalDateTime.now());
        loan.setGracePeriodEndDate(event.getGracePeriodEndDate());
        loan.setStatus("GRACE_PERIOD");
        studentLoanRepository.save(loan);
        
        repaymentPlanService.generateRepaymentOptions(loan.getId());
        
        metricsService.recordGracePeriodStarted();
    }
    
    private void processRepaymentPlanSelected(StudentLoanEvent event, String correlationId) {
        log.info("Repayment plan selected: loanId={}, planType={}, monthlyPayment={}", 
            event.getLoanId(), event.getRepaymentPlan(), event.getMonthlyPayment());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setRepaymentPlan(event.getRepaymentPlan());
        loan.setMonthlyPayment(event.getMonthlyPayment());
        loan.setRepaymentPlanSelectedAt(LocalDateTime.now());
        studentLoanRepository.save(loan);
        
        String planDescription = switch (event.getRepaymentPlan()) {
            case "STANDARD" -> "Fixed payments over 10 years";
            case "GRADUATED" -> "Payments start low and increase every 2 years";
            case "EXTENDED" -> "Lower payments over 25 years";
            case "INCOME_DRIVEN" -> "Payments based on your income";
            default -> "Customized repayment plan";
        };
        
        notificationService.sendNotification(
            loan.getStudentId(),
            "Repayment Plan Confirmed",
            String.format("You've selected the %s repayment plan: %s. " +
                "Monthly payment: %s. First payment due: %s",
                event.getRepaymentPlan(), planDescription, 
                event.getMonthlyPayment(), loan.getGracePeriodEndDate()),
            correlationId
        );
        
        metricsService.recordRepaymentPlanSelected(event.getRepaymentPlan());
    }
    
    private void processRepaymentStarted(StudentLoanEvent event, String correlationId) {
        log.info("Repayment started: loanId={}, firstPaymentDate={}, monthlyPayment={}", 
            event.getLoanId(), event.getFirstPaymentDate(), event.getMonthlyPayment());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setGracePeriodActive(false);
        loan.setRepaymentStartDate(event.getFirstPaymentDate());
        loan.setStatus("IN_REPAYMENT");
        loan.setRemainingBalance(loan.getDisbursedAmount());
        studentLoanRepository.save(loan);
        
        notificationService.sendNotification(
            loan.getStudentId(),
            "Student Loan Repayment Started",
            String.format("Your student loan repayment has begun. First payment of %s is due on %s. " +
                "Set up autopay to never miss a payment and potentially get a rate discount.",
                event.getMonthlyPayment(), event.getFirstPaymentDate()),
            correlationId
        );
        
        metricsService.recordRepaymentStarted(loan.getRepaymentPlan());
    }
    
    private void processIncomeDrivenRepaymentApplied(StudentLoanEvent event, String correlationId) {
        log.info("Income-driven repayment applied: loanId={}, annualIncome={}, adjustedPayment={}", 
            event.getLoanId(), event.getAnnualIncome(), event.getAdjustedMonthlyPayment());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        BigDecimal oldPayment = loan.getMonthlyPayment();
        
        loan.setRepaymentPlan("INCOME_DRIVEN");
        loan.setAnnualIncome(event.getAnnualIncome());
        loan.setMonthlyPayment(event.getAdjustedMonthlyPayment());
        loan.setIncomeCertificationDate(LocalDateTime.now());
        loan.setNextRecertificationDate(LocalDateTime.now().plusYears(1));
        studentLoanRepository.save(loan);
        
        BigDecimal savings = oldPayment.subtract(event.getAdjustedMonthlyPayment());
        
        notificationService.sendNotification(
            loan.getStudentId(),
            "Income-Driven Repayment Approved",
            String.format("Your income-driven repayment plan has been approved based on your income of %s. " +
                "New monthly payment: %s (reduced from %s, saving %s/month). " +
                "Recertify your income annually.",
                event.getAnnualIncome(), event.getAdjustedMonthlyPayment(), oldPayment, savings),
            correlationId
        );
        
        metricsService.recordIncomeDrivenRepaymentApplied(event.getAnnualIncome(), event.getAdjustedMonthlyPayment());
    }
    
    private void processLoanForgivenessApplied(StudentLoanEvent event, String correlationId) {
        log.info("Loan forgiveness applied: loanId={}, forgivenessType={}, forgivenAmount={}", 
            event.getLoanId(), event.getForgivenessType(), event.getForgivenAmount());
        
        StudentLoan loan = studentLoanRepository.findById(event.getLoanId())
            .orElseThrow();
        
        loan.setForgivenAmount(event.getForgivenAmount());
        loan.setForgivenessType(event.getForgivenessType());
        loan.setForgivenessAppliedAt(LocalDateTime.now());
        loan.setRemainingBalance(
            loan.getRemainingBalance().subtract(event.getForgivenAmount()));
        
        if (loan.getRemainingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus("FORGIVEN");
            loan.setRemainingBalance(BigDecimal.ZERO);
        }
        
        studentLoanRepository.save(loan);
        
        String forgivenessReason = switch (event.getForgivenessType()) {
            case "PUBLIC_SERVICE" -> "your qualifying public service employment";
            case "TEACHER" -> "your service as a teacher in a low-income school";
            case "DISABILITY" -> "total and permanent disability";
            case "CLOSED_SCHOOL" -> "school closure";
            case "INCOME_DRIVEN_20_YEARS" -> "20 years of income-driven payments";
            default -> "qualifying circumstances";
        };
        
        notificationService.sendNotification(
            loan.getStudentId(),
            "Loan Forgiveness Granted",
            String.format("Congratulations! %s of your student loan has been forgiven due to %s. " +
                "Remaining balance: %s. This is a life-changing milestone!",
                event.getForgivenAmount(), forgivenessReason, loan.getRemainingBalance()),
            correlationId
        );
        
        metricsService.recordLoanForgivenessApplied(event.getForgivenessType(), event.getForgivenAmount());
    }
}