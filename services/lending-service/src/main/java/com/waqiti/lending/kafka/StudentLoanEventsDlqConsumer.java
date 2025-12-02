package com.waqiti.lending.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.lending.service.StudentLoanService;
import com.waqiti.lending.service.LoanService;
import com.waqiti.lending.repository.StudentLoanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for student loan events that failed to process.
 * Handles critical student loan lifecycle failures affecting educational financing.
 */
@Component
@Slf4j
public class StudentLoanEventsDlqConsumer extends BaseDlqConsumer {

    private final StudentLoanService studentLoanService;
    private final LoanService loanService;
    private final StudentLoanRepository studentLoanRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StudentLoanEventsDlqConsumer(DlqHandler dlqHandler,
                                       AuditService auditService,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry,
                                       StudentLoanService studentLoanService,
                                       LoanService loanService,
                                       StudentLoanRepository studentLoanRepository,
                                       KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.studentLoanService = studentLoanService;
        this.loanService = loanService;
        this.studentLoanRepository = studentLoanRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"student-loan-events-dlq"},
        groupId = "student-loan-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "student-loan-dlq", fallbackMethod = "handleStudentLoanDlqFallback")
    public void handleStudentLoanDlq(@Payload Object originalMessage,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    Acknowledgment acknowledgment,
                                    @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing student loan DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String loanId = extractLoanId(originalMessage);
            String studentId = extractStudentId(originalMessage);
            String schoolId = extractSchoolId(originalMessage);
            String eventType = extractEventType(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing student loan DLQ: loanId={}, studentId={}, schoolId={}, eventType={}, messageId={}",
                loanId, studentId, schoolId, eventType, messageId);

            // Validate student loan and enrollment status
            if (loanId != null) {
                validateStudentLoanStatus(loanId, messageId);
                assessEducationalImpact(loanId, schoolId, originalMessage, messageId);
                handleStudentFinancialAid(loanId, originalMessage, exceptionMessage, messageId);
            }

            // Generate domain-specific alerts
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle educational institution implications
            if (schoolId != null) {
                handleInstitutionalImpact(schoolId, eventType, originalMessage, messageId);
            }

            // Check for regulatory and compliance implications
            assessStudentLoanCompliance(loanId, eventType, originalMessage, messageId);

            // Handle specific student loan event failures
            handleSpecificStudentLoanEventFailure(eventType, loanId, originalMessage, messageId);

        } catch (Exception e) {
            log.error("Error in student loan DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "student-loan-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FINANCIAL_STUDENT_LOANS";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        Double amount = extractAmount(originalMessage);
        String loanStatus = extractLoanStatus(originalMessage);

        // Critical events affecting student education
        if ("ENROLLMENT_VERIFICATION_FAILED".equals(eventType) || "DISBURSEMENT_FAILED".equals(eventType) ||
            "GRADUATION_PROCESSING_FAILED".equals(eventType) || "DEFERMENT_EXPIRY".equals(eventType) ||
            "GRACE_PERIOD_END".equals(eventType)) {
            return true;
        }

        // Large student loan amounts are always critical
        return amount != null && amount > 50000;
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String loanId = extractLoanId(originalMessage);
        String studentId = extractStudentId(originalMessage);
        String schoolId = extractSchoolId(originalMessage);
        String eventType = extractEventType(originalMessage);
        Double amount = extractAmount(originalMessage);

        try {
            // Send immediate alert to student loan operations team
            String alertTitle = String.format("CRITICAL: Student Loan Event Failed - %s", eventType);
            String alertMessage = String.format(
                "Student loan event processing failed:\n" +
                "Loan ID: %s\n" +
                "Student ID: %s\n" +
                "School ID: %s\n" +
                "Event Type: %s\n" +
                "Amount: %s\n" +
                "Error: %s\n" +
                "IMMEDIATE ACTION REQUIRED - Student education may be affected.",
                loanId != null ? loanId : "unknown",
                studentId != null ? studentId : "unknown",
                schoolId != null ? schoolId : "unknown",
                eventType != null ? eventType : "unknown",
                amount != null ? String.format("$%.2f", amount) : "unknown",
                exceptionMessage
            );

            notificationService.sendCriticalAlert(alertTitle, alertMessage,
                Map.of("loanId", loanId != null ? loanId : "unknown",
                       "studentId", studentId != null ? studentId : "unknown",
                       "schoolId", schoolId != null ? schoolId : "unknown",
                       "eventType", eventType != null ? eventType : "unknown",
                       "messageId", messageId,
                       "businessImpact", "CRITICAL_EDUCATIONAL_RISK"));

            // Send student notification for education-impacting events
            if (isStudentImpactingEvent(eventType) && studentId != null) {
                String studentMessage = getStudentNotificationMessage(eventType);
                notificationService.sendNotification(studentId,
                    "Student Loan Update",
                    studentMessage,
                    messageId);
            }

            // Alert financial aid office for disbursement issues
            if (isDisbursementEvent(eventType)) {
                notificationService.sendFinancialAidAlert(
                    "Student Loan Disbursement Failed",
                    String.format("Student loan disbursement event %s failed for loan %s (school: %s). " +
                        "Review disbursement schedules and student account funding.", eventType, loanId, schoolId),
                    "HIGH"
                );
            }

            // Alert compliance team for regulatory events
            if (isRegulatoryEvent(eventType)) {
                notificationService.sendComplianceAlert(
                    "Student Loan Regulatory Event Failed",
                    String.format("Regulatory event %s failed for student loan %s. " +
                        "Review compliance with federal student aid regulations.", eventType, loanId),
                    "URGENT"
                );
            }

        } catch (Exception e) {
            log.error("Failed to send student loan DLQ alerts: {}", e.getMessage());
        }
    }

    private void validateStudentLoanStatus(String loanId, String messageId) {
        try {
            var studentLoan = studentLoanRepository.findById(loanId);
            if (studentLoan.isPresent()) {
                String status = studentLoan.get().getStatus();
                String enrollmentStatus = studentLoan.get().getEnrollmentStatus();
                String repaymentStatus = studentLoan.get().getRepaymentStatus();

                log.info("Student loan status validation for DLQ: loanId={}, status={}, enrollment={}, repayment={}, messageId={}",
                    loanId, status, enrollmentStatus, repaymentStatus, messageId);

                // Check for enrollment-related issues
                if ("ENROLLMENT_VERIFICATION_FAILED".equals(enrollmentStatus)) {
                    log.warn("Student loan DLQ for loan with enrollment issues: loanId={}, enrollmentStatus={}",
                        loanId, enrollmentStatus);

                    notificationService.sendCriticalAlert(
                        "High-Risk Student Loan DLQ",
                        String.format("Student loan %s with enrollment status %s has failed event processing. " +
                            "Student education continuity may be at risk.", loanId, enrollmentStatus),
                        Map.of("loanId", loanId, "enrollmentStatus", enrollmentStatus, "riskLevel", "HIGH")
                    );
                }

                // Check grace period and deferment status
                if ("GRACE_PERIOD".equals(repaymentStatus) || "DEFERMENT".equals(repaymentStatus)) {
                    notificationService.sendOperationalAlert(
                        "Grace/Deferment Period Student Loan DLQ",
                        String.format("Student loan %s in %s has failed event processing. " +
                            "Review grace period or deferment arrangements.", loanId, repaymentStatus),
                        "HIGH"
                    );
                }
            } else {
                log.error("Student loan not found for DLQ: loanId={}, messageId={}",
                    loanId, messageId);
            }
        } catch (Exception e) {
            log.error("Error validating student loan status for DLQ: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void assessEducationalImpact(String loanId, String schoolId,
                                       Object originalMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);
            String academicYear = extractAcademicYear(originalMessage);
            String semester = extractSemester(originalMessage);

            // Assess impact on student's education
            if (isDisbursementEvent(eventType)) {
                log.info("Assessing educational impact for disbursement DLQ: loanId={}, school={}, academicYear={}, semester={}",
                    loanId, schoolId, academicYear, semester);

                // Check if this affects current semester funding
                if (isCurrentSemester(semester)) {
                    notificationService.sendCriticalAlert(
                        "Current Semester Funding Failed",
                        String.format("Student loan %s disbursement failed for current semester (%s). " +
                            "Student may be unable to continue enrollment.", loanId, semester),
                        Map.of("loanId", loanId, "semester", semester, "urgency", "IMMEDIATE")
                    );
                }
            }

            // Check for graduation impact
            if ("GRADUATION_PROCESSING".equals(eventType)) {
                notificationService.sendOperationalAlert(
                    "Graduation Processing Failed",
                    String.format("Graduation processing failed for student loan %s. " +
                        "Review loan closure procedures and final disbursements.", loanId),
                    "HIGH"
                );
            }

        } catch (Exception e) {
            log.error("Error assessing educational impact: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleStudentFinancialAid(String loanId, Object originalMessage,
                                         String exceptionMessage, String messageId) {
        try {
            String eventType = extractEventType(originalMessage);

            if (isFinancialAidEvent(eventType)) {
                // Record financial aid failure for student services
                studentLoanService.recordFinancialAidFailure(loanId, Map.of(
                    "failureType", "STUDENT_LOAN_DLQ",
                    "eventType", eventType,
                    "errorMessage", exceptionMessage,
                    "messageId", messageId,
                    "timestamp", Instant.now(),
                    "requiresFinancialAidReview", true
                ));

                // Check for FAFSA and financial aid eligibility impacts
                String aidStatus = studentLoanService.getFinancialAidStatus(loanId);
                if ("ELIGIBILITY_REVIEW_REQUIRED".equals(aidStatus)) {
                    log.warn("Student loan DLQ requiring financial aid eligibility review: loanId={}", loanId);

                    // Trigger financial aid review
                    kafkaTemplate.send("student-aid-review-queue", Map.of(
                        "loanId", loanId,
                        "aidStatus", aidStatus,
                        "triggerReason", "STUDENT_LOAN_DLQ",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error handling student financial aid: loanId={}, error={}", loanId, e.getMessage());
        }
    }

    private void handleInstitutionalImpact(String schoolId, String eventType,
                                         Object originalMessage, String messageId) {
        try {
            // Handle school-specific events
            if ("ENROLLMENT_VERIFICATION".equals(eventType)) {
                notificationService.sendEducationalInstitutionAlert(
                    "Enrollment Verification Failed",
                    String.format("Enrollment verification processing failed for school %s. " +
                        "Review student enrollment status and certification procedures.", schoolId),
                    "HIGH"
                );
            } else if ("DISBURSEMENT_TO_SCHOOL".equals(eventType)) {
                notificationService.sendFinancialAidAlert(
                    "School Disbursement Failed",
                    String.format("Disbursement to school %s failed. " +
                        "Review school payment processing and student account credits.", schoolId),
                    "URGENT"
                );
            }

            // Update school relationship status for failed events
            studentLoanService.recordSchoolEventFailure(schoolId, eventType, messageId);

        } catch (Exception e) {
            log.error("Error handling institutional impact: schoolId={}, error={}", schoolId, e.getMessage());
        }
    }

    private void assessStudentLoanCompliance(String loanId, String eventType,
                                           Object originalMessage, String messageId) {
        try {
            if (isRegulatoryEvent(eventType)) {
                // Student loans have specific federal compliance requirements
                notificationService.sendComplianceAlert(
                    "Student Loan Regulatory Event Failed",
                    String.format("Regulatory event %s failed for student loan %s. " +
                        "Review compliance with federal student aid regulations and Department of Education requirements.",
                        eventType, loanId),
                    "HIGH"
                );

                // Trigger regulatory compliance review for certain events
                if ("NSLDS_REPORTING".equals(eventType) || "DEFAULT_AVERSION".equals(eventType)) {
                    kafkaTemplate.send("student-loan-compliance-review-queue", Map.of(
                        "loanId", loanId,
                        "eventType", eventType,
                        "reviewReason", "REGULATORY_DLQ",
                        "messageId", messageId,
                        "timestamp", Instant.now()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Error assessing student loan compliance: loanId={}, error={}",
                loanId, e.getMessage());
        }
    }

    private void handleSpecificStudentLoanEventFailure(String eventType, String loanId,
                                                      Object originalMessage, String messageId) {
        try {
            switch (eventType) {
                case "LOAN_ORIGINATION":
                    handleOriginationFailure(loanId, originalMessage, messageId);
                    break;
                case "DISBURSEMENT":
                    handleDisbursementFailure(loanId, originalMessage, messageId);
                    break;
                case "ENROLLMENT_VERIFICATION":
                    handleEnrollmentVerificationFailure(loanId, originalMessage, messageId);
                    break;
                case "GRACE_PERIOD_START":
                    handleGracePeriodFailure(loanId, originalMessage, messageId);
                    break;
                case "REPAYMENT_START":
                    handleRepaymentStartFailure(loanId, originalMessage, messageId);
                    break;
                default:
                    log.info("No specific handling for student loan event type: {}", eventType);
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling specific student loan event failure: eventType={}, loanId={}, error={}",
                eventType, loanId, e.getMessage());
        }
    }

    private void handleOriginationFailure(String loanId, Object originalMessage, String messageId) {
        String studentId = extractStudentId(originalMessage);
        notificationService.sendCriticalAlert(
            "Student Loan Origination Failed",
            String.format("Student loan %s origination failed. Student %s may be waiting for loan approval for education.",
                loanId, studentId),
            Map.of("loanId", loanId, "studentId", studentId, "urgency", "HIGH", "educationalImpact", "HIGH")
        );
    }

    private void handleDisbursementFailure(String loanId, Object originalMessage, String messageId) {
        Double amount = extractAmount(originalMessage);
        String schoolId = extractSchoolId(originalMessage);
        notificationService.sendCriticalAlert(
            "Student Loan Disbursement Failed",
            String.format("Disbursement of $%.2f failed for student loan %s to school %s. " +
                "Student education funding may be delayed.", amount != null ? amount : 0.0, loanId, schoolId),
            Map.of("loanId", loanId, "amount", amount, "schoolId", schoolId, "urgency", "IMMEDIATE")
        );
    }

    private void handleEnrollmentVerificationFailure(String loanId, Object originalMessage, String messageId) {
        String studentId = extractStudentId(originalMessage);
        notificationService.sendOperationalAlert(
            "Enrollment Verification Failed",
            String.format("Enrollment verification failed for student loan %s (student: %s). " +
                "Review student enrollment status and certification requirements.", loanId, studentId),
            "HIGH"
        );
    }

    private void handleGracePeriodFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendOperationalAlert(
            "Grace Period Start Failed",
            String.format("Grace period initiation failed for student loan %s. " +
                "Student repayment schedule may be affected.", loanId),
            "HIGH"
        );
    }

    private void handleRepaymentStartFailure(String loanId, Object originalMessage, String messageId) {
        notificationService.sendCriticalAlert(
            "Student Loan Repayment Start Failed",
            String.format("Repayment initiation failed for student loan %s. " +
                "Student may not receive proper repayment notifications.", loanId),
            Map.of("loanId", loanId, "urgency", "HIGH", "repaymentImpact", "DELAYED")
        );
    }

    // Circuit breaker fallback method
    public void handleStudentLoanDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                            int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String loanId = extractLoanId(originalMessage);
        String studentId = extractStudentId(originalMessage);

        if (loanId != null) {
            try {
                // Mark student loan for urgent review
                studentLoanService.markForUrgentReview(loanId, "STUDENT_LOAN_DLQ_CIRCUIT_BREAKER");

                // Send executive notification with educational impact
                notificationService.sendExecutiveAlert(
                    "Critical: Student Loan DLQ Circuit Breaker Triggered",
                    String.format("Circuit breaker triggered for student loan DLQ processing on loan %s " +
                        "(student: %s). This indicates a systemic issue affecting educational financing.",
                        loanId, studentId)
                );
            } catch (Exception e) {
                log.error("Error in student loan DLQ fallback: {}", e.getMessage());
            }
        }
    }

    // Helper methods for event classification
    private boolean isStudentImpactingEvent(String eventType) {
        return eventType != null && (eventType.contains("DISBURSEMENT") || eventType.contains("ENROLLMENT") ||
                                   eventType.contains("GRACE") || eventType.contains("REPAYMENT"));
    }

    private boolean isDisbursementEvent(String eventType) {
        return eventType != null && eventType.contains("DISBURSEMENT");
    }

    private boolean isRegulatoryEvent(String eventType) {
        return eventType != null && (eventType.contains("NSLDS") || eventType.contains("REGULATORY") ||
                                   eventType.contains("COMPLIANCE") || eventType.contains("DEFAULT_AVERSION"));
    }

    private boolean isFinancialAidEvent(String eventType) {
        return eventType != null && (eventType.contains("AID") || eventType.contains("FAFSA") ||
                                   eventType.contains("ELIGIBILITY"));
    }

    private boolean isCurrentSemester(String semester) {
        // Implementation would check if the semester is current
        return semester != null && semester.contains("CURRENT");
    }

    private String getStudentNotificationMessage(String eventType) {
        switch (eventType) {
            case "DISBURSEMENT":
                return "We're processing your student loan disbursement. " +
                       "Your funds will be available once processing is complete.";
            case "ENROLLMENT_VERIFICATION":
                return "We're verifying your enrollment status for your student loan. " +
                       "Please ensure your enrollment information is current with your school.";
            case "GRACE_PERIOD_START":
                return "We're setting up your student loan grace period. " +
                       "You'll receive information about your repayment schedule soon.";
            default:
                return "We're processing an update to your student loan. " +
                       "You'll receive confirmation once processing is complete.";
        }
    }

    // Data extraction helper methods
    private String extractLoanId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object loanId = messageMap.get("loanId");
                if (loanId == null) loanId = messageMap.get("studentLoanId");
                return loanId != null ? loanId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract loanId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractStudentId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object studentId = messageMap.get("studentId");
                if (studentId == null) studentId = messageMap.get("userId");
                return studentId != null ? studentId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract studentId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractSchoolId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object schoolId = messageMap.get("schoolId");
                return schoolId != null ? schoolId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract schoolId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractEventType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object eventType = messageMap.get("eventType");
                return eventType != null ? eventType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract eventType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractLoanStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("loanStatus");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract loanStatus from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAcademicYear(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object year = messageMap.get("academicYear");
                return year != null ? year.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract academicYear from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractSemester(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object semester = messageMap.get("semester");
                return semester != null ? semester.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract semester from message: {}", e.getMessage());
        }
        return null;
    }

    private Double extractAmount(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object amount = messageMap.get("amount");
                if (amount == null) amount = messageMap.get("disbursementAmount");
                if (amount instanceof Number) {
                    return ((Number) amount).doubleValue();
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract amount from message: {}", e.getMessage());
        }
        return null;
    }
}