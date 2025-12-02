package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.GdprComplianceEventDto;
import com.waqiti.compliance.service.GdprComplianceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer for processing GDPR compliance events.
 * Handles EU GDPR-specific requirements including data subject rights enforcement,
 * lawful basis processing, DPO notifications, supervisory authority interactions,
 * and Article 30 record keeping with automated compliance monitoring.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class GdprComplianceEventsConsumer extends BaseConsumer<GdprComplianceEventDto> {

    private static final String TOPIC_NAME = "gdpr-compliance-events";
    private static final String CONSUMER_GROUP = "compliance-service-gdpr";
    private static final String DLQ_TOPIC = "gdpr-compliance-events-dlq";

    private final GdprComplianceService gdprService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter gdprViolationsCounter;
    private final Counter dataSubjectRightsCounter;
    private final Counter supervisoryAuthorityNotificationsCounter;
    private final Counter article30RecordsCounter;
    private final Timer processingTimer;
    private final Timer gdprAssessmentTimer;

    @Autowired
    public GdprComplianceEventsConsumer(
            GdprComplianceService gdprService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.gdprService = gdprService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("gdpr-compliance", 5, Duration.ofMinutes(2));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("gdpr_compliance_events_processed_total")
                .description("Total number of GDPR compliance events processed")
                .tag("service", "compliance")
                .tag("consumer", "gdpr-compliance")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("gdpr_compliance_events_failed_total")
                .description("Total number of failed GDPR compliance events")
                .tag("service", "compliance")
                .tag("consumer", "gdpr-compliance")
                .register(meterRegistry);

        this.gdprViolationsCounter = Counter.builder("gdpr_violations_total")
                .description("Total number of GDPR violations detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.dataSubjectRightsCounter = Counter.builder("gdpr_data_subject_rights_total")
                .description("Total number of GDPR data subject rights processed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.supervisoryAuthorityNotificationsCounter = Counter.builder("supervisory_authority_notifications_total")
                .description("Total number of supervisory authority notifications sent")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.article30RecordsCounter = Counter.builder("article30_records_total")
                .description("Total number of Article 30 records maintained")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("gdpr_compliance_processing_duration")
                .description("Time taken to process GDPR compliance events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.gdprAssessmentTimer = Timer.builder("gdpr_assessment_duration")
                .description("Time taken for GDPR assessment")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes GDPR compliance events with comprehensive EU regulation compliance.
     *
     * @param eventJson The JSON representation of the GDPR compliance event
     * @param key The message key
     * @param partition The partition number
     * @param offset The message offset
     * @param timestamp The message timestamp
     * @param acknowledgment The acknowledgment for manual commit
     */
    @KafkaListener(
        topics = TOPIC_NAME,
        groupId = CONSUMER_GROUP,
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @Retryable(
        value = {Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void processGdprComplianceEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing GDPR compliance event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            GdprComplianceEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processGdprEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Successfully processed GDPR compliance event - CorrelationId: {}, EventType: {}, Article: {}",
                    correlationId, event.getEventType(), event.getGdprArticle());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the GDPR compliance event with comprehensive EU regulation compliance.
     */
    private void processGdprEvent(GdprComplianceEventDto event, String correlationId) {
        Timer.Sample assessmentTimer = Timer.start();

        try {
            log.info("Processing GDPR compliance for - Type: {}, Article: {} - CorrelationId: {}",
                    event.getEventType(), event.getGdprArticle(), correlationId);

            // GDPR compliance assessment
            var gdprAssessment = gdprService.assessGdprCompliance(event, correlationId);

            // Process based on event type
            switch (event.getEventType()) {
                case "ARTICLE_15_ACCESS_REQUEST":
                    processArticle15AccessRequest(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_16_RECTIFICATION_REQUEST":
                    processArticle16RectificationRequest(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_17_ERASURE_REQUEST":
                    processArticle17ErasureRequest(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_18_RESTRICTION_REQUEST":
                    processArticle18RestrictionRequest(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_20_PORTABILITY_REQUEST":
                    processArticle20PortabilityRequest(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_21_OBJECTION_REQUEST":
                    processArticle21ObjectionRequest(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_22_AUTOMATED_DECISION_REVIEW":
                    processArticle22AutomatedDecisionReview(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_30_RECORD_KEEPING":
                    processArticle30RecordKeeping(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_33_BREACH_NOTIFICATION":
                    processArticle33BreachNotification(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_34_DATA_SUBJECT_NOTIFICATION":
                    processArticle34DataSubjectNotification(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_35_DPIA":
                    processArticle35DPIA(event, gdprAssessment, correlationId);
                    break;
                case "ARTICLE_37_DPO_DESIGNATION":
                    processArticle37DpoDesignation(event, gdprAssessment, correlationId);
                    break;
                case "LAWFUL_BASIS_ASSESSMENT":
                    processLawfulBasisAssessment(event, gdprAssessment, correlationId);
                    break;
                case "CONSENT_MANAGEMENT":
                    processConsentManagement(event, gdprAssessment, correlationId);
                    break;
                case "CROSS_BORDER_TRANSFER":
                    processCrossBorderTransfer(event, gdprAssessment, correlationId);
                    break;
                default:
                    processGenericGdprEvent(event, gdprAssessment, correlationId);
            }

            // Handle GDPR violations
            if (gdprAssessment.hasViolations()) {
                handleGdprViolations(event, gdprAssessment, correlationId);
            }

            // Update Article 30 records
            updateArticle30Records(event, gdprAssessment, correlationId);

            // Update GDPR tracking
            gdprService.updateGdprTracking(event, gdprAssessment, correlationId);

        } finally {
            assessmentTimer.stop(gdprAssessmentTimer);
        }
    }

    /**
     * Processes Article 15 - Right of access by the data subject.
     */
    private void processArticle15AccessRequest(GdprComplianceEventDto event,
                                             var gdprAssessment, String correlationId) {
        log.info("Processing Article 15 access request - CorrelationId: {}, Subject: {}",
                correlationId, event.getDataSubjectId());

        dataSubjectRightsCounter.increment();

        // Verify data subject identity according to GDPR requirements
        var identityVerification = gdprService.verifyDataSubjectIdentityGdpr(event, correlationId);

        if (!identityVerification.meetsGdprStandards()) {
            log.warn("GDPR identity verification failed - CorrelationId: {}, Reason: {}",
                    correlationId, identityVerification.getFailureReason());
            gdprService.rejectAccessRequestGdpr(event, identityVerification, correlationId);
            return;
        }

        // Comprehensive GDPR-compliant data discovery
        var gdprDataDiscovery = gdprService.discoverPersonalDataGdpr(event.getDataSubjectId(), correlationId);

        // GDPR Article 15 requires specific information
        var article15Response = gdprService.generateArticle15Response(gdprDataDiscovery, correlationId);

        // Include required GDPR information
        article15Response.includePurposesOfProcessing();
        article15Response.includeCategoriesOfPersonalData();
        article15Response.includeRecipientsOfData();
        article15Response.includeRetentionPeriods();
        article15Response.includeDataSubjectRights();
        article15Response.includeSourceOfData();
        article15Response.includeAutomatedDecisionMaking();
        article15Response.includeThirdCountryTransfers();

        // Deliver response within GDPR timeframe (1 month)
        var deliveryResult = gdprService.deliverArticle15Response(event, article15Response, correlationId);

        if (deliveryResult.isSuccessful()) {
            log.info("Article 15 access request fulfilled - CorrelationId: {}, ResponseId: {}",
                    correlationId, article15Response.getResponseId());

            gdprService.recordArticle15Fulfillment(event, deliveryResult, correlationId);
        } else {
            log.error("Failed to fulfill Article 15 request - CorrelationId: {}, Error: {}",
                    correlationId, deliveryResult.getError());
            gdprService.handleArticle15Failure(event, deliveryResult, correlationId);
        }
    }

    /**
     * Processes Article 17 - Right to erasure ('right to be forgotten').
     */
    private void processArticle17ErasureRequest(GdprComplianceEventDto event,
                                              var gdprAssessment, String correlationId) {
        log.info("Processing Article 17 erasure request - CorrelationId: {}, Subject: {}",
                correlationId, event.getDataSubjectId());

        dataSubjectRightsCounter.increment();

        // Assess Article 17 grounds for erasure
        var erasureGrounds = gdprService.assessArticle17ErasureGrounds(event, correlationId);

        if (!erasureGrounds.hasValidGrounds()) {
            log.info("Article 17 erasure request lacks valid grounds - CorrelationId: {}, Reason: {}",
                    correlationId, erasureGrounds.getRejectionReason());
            gdprService.rejectErasureRequestGdpr(event, erasureGrounds, correlationId);
            return;
        }

        // Check for Article 17(3) exceptions
        var erasureExceptions = gdprService.assessArticle17Exceptions(event, correlationId);

        if (erasureExceptions.hasBlockingExceptions()) {
            log.info("Article 17 erasure blocked by exceptions - CorrelationId: {}, Exceptions: {}",
                    correlationId, erasureExceptions.getBlockingExceptions());
            gdprService.partiallyRejectErasureRequest(event, erasureExceptions, correlationId);
            return;
        }

        // Discover data subject's personal data
        var personalDataInventory = gdprService.discoverPersonalDataForErasure(event.getDataSubjectId(), correlationId);

        // Check for third-party notifications required under Article 19
        var thirdPartyNotifications = gdprService.assessArticle19Notifications(personalDataInventory, correlationId);

        // Execute GDPR-compliant erasure
        var erasureResult = gdprService.executeGdprErasure(personalDataInventory, correlationId);

        if (erasureResult.isSuccessful()) {
            log.info("Article 17 erasure request fulfilled - CorrelationId: {}, ErasedRecords: {}",
                    correlationId, erasureResult.getErasedRecordCount());

            // Notify third parties as per Article 19
            if (thirdPartyNotifications.requiresNotifications()) {
                gdprService.notifyThirdPartiesOfErasure(thirdPartyNotifications, erasureResult, correlationId);
            }

            gdprService.recordArticle17Fulfillment(event, erasureResult, correlationId);
        } else {
            log.error("Failed to fulfill Article 17 request - CorrelationId: {}, Error: {}",
                    correlationId, erasureResult.getError());
            gdprService.handleArticle17Failure(event, erasureResult, correlationId);
        }
    }

    /**
     * Processes Article 18 - Right to restriction of processing.
     */
    private void processArticle18RestrictionRequest(GdprComplianceEventDto event,
                                                  var gdprAssessment, String correlationId) {
        log.info("Processing Article 18 restriction request - CorrelationId: {}, Subject: {}",
                correlationId, event.getDataSubjectId());

        dataSubjectRightsCounter.increment();

        // Assess Article 18 grounds for restriction
        var restrictionGrounds = gdprService.assessArticle18RestrictionGrounds(event, correlationId);

        if (!restrictionGrounds.hasValidGrounds()) {
            log.info("Article 18 restriction request lacks valid grounds - CorrelationId: {}", correlationId);
            gdprService.rejectRestrictionRequestGdpr(event, restrictionGrounds, correlationId);
            return;
        }

        // Identify data to be restricted
        var dataToRestrict = gdprService.identifyDataForRestriction(event, correlationId);

        // Implement processing restrictions
        var restrictionResult = gdprService.implementProcessingRestrictions(dataToRestrict, correlationId);

        if (restrictionResult.isSuccessful()) {
            log.info("Article 18 restriction implemented - CorrelationId: {}, RestrictedDatasets: {}",
                    correlationId, restrictionResult.getRestrictedDatasetCount());

            // Update processing systems to respect restrictions
            gdprService.updateSystemsForRestriction(restrictionResult, correlationId);

            // Notify third parties as per Article 19
            var thirdPartyNotifications = gdprService.assessArticle19RestrictionNotifications(dataToRestrict, correlationId);
            if (thirdPartyNotifications.requiresNotifications()) {
                gdprService.notifyThirdPartiesOfRestriction(thirdPartyNotifications, correlationId);
            }

            gdprService.recordArticle18Fulfillment(event, restrictionResult, correlationId);
        } else {
            log.error("Failed to implement Article 18 restriction - CorrelationId: {}, Error: {}",
                    correlationId, restrictionResult.getError());
            gdprService.handleArticle18Failure(event, restrictionResult, correlationId);
        }
    }

    /**
     * Processes Article 20 - Right to data portability.
     */
    private void processArticle20PortabilityRequest(GdprComplianceEventDto event,
                                                  var gdprAssessment, String correlationId) {
        log.info("Processing Article 20 portability request - CorrelationId: {}, Subject: {}",
                correlationId, event.getDataSubjectId());

        dataSubjectRightsCounter.increment();

        // Assess Article 20 eligibility criteria
        var portabilityEligibility = gdprService.assessArticle20Eligibility(event, correlationId);

        if (!portabilityEligibility.isEligible()) {
            log.info("Article 20 portability request not eligible - CorrelationId: {}, Reason: {}",
                    correlationId, portabilityEligibility.getRejectionReason());
            gdprService.rejectPortabilityRequestGdpr(event, portabilityEligibility, correlationId);
            return;
        }

        // Identify portable data under Article 20
        var portableData = gdprService.identifyArticle20PortableData(event.getDataSubjectId(), correlationId);

        // Create structured, machine-readable data export
        var portabilityPackage = gdprService.createGdprPortabilityPackage(portableData, correlationId);

        // Handle direct transmission to another controller if requested
        if (event.hasDirectTransmissionRequest()) {
            var transmissionResult = gdprService.executeDirectTransmission(portabilityPackage, event, correlationId);

            if (transmissionResult.isSuccessful()) {
                log.info("Article 20 direct transmission completed - CorrelationId: {}, TargetController: {}",
                        correlationId, event.getTargetController());
            } else {
                log.error("Direct transmission failed, providing data to subject - CorrelationId: {}", correlationId);
                gdprService.deliverPortabilityPackageToSubject(event, portabilityPackage, correlationId);
            }
        } else {
            // Provide data to the data subject
            var deliveryResult = gdprService.deliverPortabilityPackageToSubject(event, portabilityPackage, correlationId);

            if (deliveryResult.isSuccessful()) {
                log.info("Article 20 portability request fulfilled - CorrelationId: {}, PackageId: {}",
                        correlationId, portabilityPackage.getPackageId());
            }
        }

        gdprService.recordArticle20Fulfillment(event, portabilityPackage, correlationId);
    }

    /**
     * Processes Article 21 - Right to object.
     */
    private void processArticle21ObjectionRequest(GdprComplianceEventDto event,
                                                 var gdprAssessment, String correlationId) {
        log.info("Processing Article 21 objection request - CorrelationId: {}, Subject: {}",
                correlationId, event.getDataSubjectId());

        dataSubjectRightsCounter.increment();

        // Assess Article 21 objection grounds
        var objectionGrounds = gdprService.assessArticle21ObjectionGrounds(event, correlationId);

        // Check for compelling legitimate interests
        var legitimateInterestsAssessment = gdprService.assessCompellingLegitimateInterests(event, correlationId);

        if (legitimateInterestsAssessment.hasOverridingInterests()) {
            log.info("Article 21 objection overridden by compelling legitimate interests - CorrelationId: {}", correlationId);
            gdprService.rejectObjectionRequest(event, legitimateInterestsAssessment, correlationId);
            return;
        }

        // Identify processing activities to cease
        var processingToCease = gdprService.identifyProcessingToCease(event, objectionGrounds, correlationId);

        // Implement objection by ceasing processing
        var objectionResult = gdprService.implementObjection(processingToCease, correlationId);

        if (objectionResult.isSuccessful()) {
            log.info("Article 21 objection implemented - CorrelationId: {}, CeasedActivities: {}",
                    correlationId, objectionResult.getCeasedActivityCount());

            // Update processing systems
            gdprService.updateSystemsForObjection(objectionResult, correlationId);

            gdprService.recordArticle21Fulfillment(event, objectionResult, correlationId);
        } else {
            log.error("Failed to implement Article 21 objection - CorrelationId: {}, Error: {}",
                    correlationId, objectionResult.getError());
            gdprService.handleArticle21Failure(event, objectionResult, correlationId);
        }
    }

    /**
     * Processes Article 30 - Records of processing activities.
     */
    private void processArticle30RecordKeeping(GdprComplianceEventDto event,
                                             var gdprAssessment, String correlationId) {
        log.info("Processing Article 30 record keeping - CorrelationId: {}, Activity: {}",
                correlationId, event.getProcessingActivity());

        article30RecordsCounter.increment();

        // Update Article 30 records
        var recordUpdate = gdprService.updateArticle30Records(event, correlationId);

        // Ensure record completeness per Article 30 requirements
        var recordCompleteness = gdprService.validateArticle30Completeness(recordUpdate, correlationId);

        if (!recordCompleteness.isComplete()) {
            log.warn("Article 30 record incomplete - CorrelationId: {}, MissingElements: {}",
                    correlationId, recordCompleteness.getMissingElements());
            gdprService.flagIncompleteRecord(event, recordCompleteness, correlationId);
        }

        // Generate record entry
        var recordEntry = gdprService.generateArticle30RecordEntry(event, correlationId);

        // Include required elements:
        recordEntry.includeControllerDetails();
        recordEntry.includePurposesOfProcessing();
        recordEntry.includeDataSubjectCategories();
        recordEntry.includePersonalDataCategories();
        recordEntry.includeRecipientCategories();
        recordEntry.includeThirdCountryTransfers();
        recordEntry.includeRetentionPeriods();
        recordEntry.includeSecurityMeasures();

        // Store record entry
        gdprService.storeArticle30RecordEntry(recordEntry, correlationId);

        log.info("Article 30 record updated - CorrelationId: {}, RecordId: {}",
                correlationId, recordEntry.getRecordId());
    }

    /**
     * Processes Article 33 - Notification of a personal data breach to the supervisory authority.
     */
    private void processArticle33BreachNotification(GdprComplianceEventDto event,
                                                  var gdprAssessment, String correlationId) {
        log.error("Processing Article 33 breach notification - CorrelationId: {}, BreachId: {}",
                correlationId, event.getBreachId());

        supervisoryAuthorityNotificationsCounter.increment();

        // Assess breach risk to rights and freedoms
        var riskAssessment = gdprService.assessBreachRiskToRightsAndFreedoms(event, correlationId);

        if (!riskAssessment.requiresNotification()) {
            log.info("Article 33 breach notification not required - CorrelationId: {}, Reason: {}",
                    correlationId, riskAssessment.getExemptionReason());
            gdprService.documentBreachNotificationExemption(event, riskAssessment, correlationId);
            return;
        }

        // Prepare Article 33 notification
        var notificationContent = gdprService.prepareArticle33Notification(event, riskAssessment, correlationId);

        // Include required Article 33 information
        notificationContent.includeBreachNature();
        notificationContent.includeDataSubjectCategoriesAndNumbers();
        notificationContent.includePersonalDataCategoriesAndRecords();
        notificationContent.includeDpoContactDetails();
        notificationContent.includeLikelyConsequences();
        notificationContent.includeMeasuresTaken();

        // Submit to supervisory authority within 72 hours
        var notificationResult = gdprService.submitArticle33Notification(notificationContent, correlationId);

        if (notificationResult.isSuccessful()) {
            log.info("Article 33 notification submitted - CorrelationId: {}, NotificationId: {}",
                    correlationId, notificationResult.getNotificationId());

            gdprService.recordArticle33Submission(event, notificationResult, correlationId);
        } else {
            log.error("Failed to submit Article 33 notification - CorrelationId: {}, Error: {}",
                    correlationId, notificationResult.getError());
            gdprService.handleArticle33Failure(event, notificationResult, correlationId);
        }
    }

    /**
     * Processes Article 35 - Data protection impact assessment.
     */
    private void processArticle35DPIA(GdprComplianceEventDto event,
                                    var gdprAssessment, String correlationId) {
        log.info("Processing Article 35 DPIA - CorrelationId: {}, ProcessingType: {}",
                correlationId, event.getProcessingType());

        // Assess DPIA requirement under Article 35
        var dpiaRequirement = gdprService.assessArticle35Requirement(event, correlationId);

        if (!dpiaRequirement.isRequired()) {
            log.info("Article 35 DPIA not required - CorrelationId: {}, Reason: {}",
                    correlationId, dpiaRequirement.getExemptionReason());
            gdprService.documentDpiaExemption(event, dpiaRequirement, correlationId);
            return;
        }

        // Conduct comprehensive DPIA
        var dpiaResult = gdprService.conductArticle35DPIA(event, correlationId);

        // Assess high risk to rights and freedoms
        if (dpiaResult.indicatesHighRisk()) {
            log.warn("Article 35 DPIA indicates high risk - CorrelationId: {}, RiskFactors: {}",
                    correlationId, dpiaResult.getHighRiskFactors());

            // Consult supervisory authority as per Article 36
            var consultationResult = gdprService.consultSupervisoryAuthorityArticle36(event, dpiaResult, correlationId);
            supervisoryAuthorityNotificationsCounter.increment();

            if (!consultationResult.isApproved()) {
                log.error("Supervisory authority consultation negative - CorrelationId: {}", correlationId);
                gdprService.handleNegativeConsultation(event, consultationResult, correlationId);
                return;
            }
        }

        // Implement DPIA recommendations
        gdprService.implementDpiaRecommendations(event, dpiaResult, correlationId);

        // Schedule DPIA review
        gdprService.scheduleDpiaReview(event, dpiaResult, correlationId);

        log.info("Article 35 DPIA completed - CorrelationId: {}, DpiaId: {}",
                correlationId, dpiaResult.getDpiaId());
    }

    /**
     * Processes lawful basis assessments under GDPR.
     */
    private void processLawfulBasisAssessment(GdprComplianceEventDto event,
                                            var gdprAssessment, String correlationId) {
        log.info("Processing lawful basis assessment - CorrelationId: {}, ProcessingActivity: {}",
                correlationId, event.getProcessingActivity());

        // Assess lawful basis under Article 6
        var lawfulBasisAssessment = gdprService.assessLawfulBasisArticle6(event, correlationId);

        if (!lawfulBasisAssessment.hasValidLawfulBasis()) {
            log.error("No valid lawful basis found - CorrelationId: {}, ProcessingActivity: {}",
                    correlationId, event.getProcessingActivity());

            gdprViolationsCounter.increment();
            gdprService.flagLawfulBasisViolation(event, lawfulBasisAssessment, correlationId);
            return;
        }

        // Check for special categories under Article 9
        if (event.involvesSpecialCategories()) {
            var article9Assessment = gdprService.assessArticle9Conditions(event, correlationId);

            if (!article9Assessment.hasValidCondition()) {
                log.error("No valid Article 9 condition for special categories - CorrelationId: {}", correlationId);
                gdprViolationsCounter.increment();
                gdprService.flagArticle9Violation(event, article9Assessment, correlationId);
                return;
            }
        }

        // Document lawful basis determination
        gdprService.documentLawfulBasisDetermination(event, lawfulBasisAssessment, correlationId);

        log.info("Lawful basis assessment completed - CorrelationId: {}, LawfulBasis: {}",
                correlationId, lawfulBasisAssessment.getPrimaryLawfulBasis());
    }

    /**
     * Processes GDPR consent management events.
     */
    private void processConsentManagement(GdprComplianceEventDto event,
                                        var gdprAssessment, String correlationId) {
        log.info("Processing GDPR consent management - CorrelationId: {}, ConsentType: {}",
                correlationId, event.getConsentType());

        // Validate consent meets GDPR standards
        var consentValidation = gdprService.validateGdprConsent(event, correlationId);

        if (!consentValidation.meetsGdprStandards()) {
            log.error("Consent does not meet GDPR standards - CorrelationId: {}, Issues: {}",
                    correlationId, consentValidation.getComplianceIssues());

            gdprViolationsCounter.increment();
            gdprService.flagInvalidConsent(event, consentValidation, correlationId);
            return;
        }

        // Process consent action
        switch (event.getConsentAction()) {
            case "GRANTED":
                gdprService.processConsentGrant(event, correlationId);
                break;
            case "WITHDRAWN":
                gdprService.processConsentWithdrawal(event, correlationId);
                break;
            case "UPDATED":
                gdprService.processConsentUpdate(event, correlationId);
                break;
            default:
                log.warn("Unknown consent action - CorrelationId: {}, Action: {}",
                        correlationId, event.getConsentAction());
        }

        // Update consent records
        gdprService.updateGdprConsentRecords(event, correlationId);
    }

    /**
     * Processes cross-border data transfers under GDPR Chapter V.
     */
    private void processCrossBorderTransfer(GdprComplianceEventDto event,
                                          var gdprAssessment, String correlationId) {
        log.info("Processing GDPR cross-border transfer - CorrelationId: {}, Destination: {}",
                correlationId, event.getDestinationCountry());

        // Assess transfer mechanism under Chapter V
        var transferMechanism = gdprService.assessChapterVTransferMechanism(event, correlationId);

        if (!transferMechanism.isGdprCompliant()) {
            log.error("Cross-border transfer not GDPR compliant - CorrelationId: {}, Issues: {}",
                    correlationId, transferMechanism.getComplianceIssues());

            gdprViolationsCounter.increment();
            gdprService.blockNonCompliantTransfer(event, transferMechanism, correlationId);
            return;
        }

        // Apply appropriate safeguards
        var safeguards = gdprService.applyChapterVSafeguards(event, transferMechanism, correlationId);

        // Execute GDPR-compliant transfer
        var transferResult = gdprService.executeGdprCompliantTransfer(event, safeguards, correlationId);

        if (transferResult.isSuccessful()) {
            log.info("GDPR cross-border transfer completed - CorrelationId: {}, TransferId: {}",
                    correlationId, transferResult.getTransferId());

            gdprService.recordChapterVTransfer(event, transferResult, correlationId);
        } else {
            log.error("GDPR cross-border transfer failed - CorrelationId: {}, Error: {}",
                    correlationId, transferResult.getError());
            gdprService.handleGdprTransferFailure(event, transferResult, correlationId);
        }
    }

    /**
     * Processes generic GDPR events.
     */
    private void processGenericGdprEvent(GdprComplianceEventDto event,
                                       var gdprAssessment, String correlationId) {
        log.info("Processing generic GDPR event - CorrelationId: {}", correlationId);

        // Standard GDPR processing
        gdprService.processStandardGdprEvent(event, gdprAssessment, correlationId);
    }

    /**
     * Updates Article 30 records for all processing activities.
     */
    private void updateArticle30Records(GdprComplianceEventDto event,
                                       var gdprAssessment, String correlationId) {
        if (event.affectsProcessingRecords()) {
            var recordUpdate = gdprService.updateProcessingRecords(event, correlationId);
            article30RecordsCounter.increment();

            log.debug("Article 30 records updated - CorrelationId: {}, UpdateId: {}",
                    correlationId, recordUpdate.getUpdateId());
        }
    }

    /**
     * Handles GDPR violations with comprehensive remediation.
     */
    private void handleGdprViolations(GdprComplianceEventDto event,
                                    var gdprAssessment, String correlationId) {
        log.error("Handling GDPR violations - CorrelationId: {}, ViolationCount: {}",
                correlationId, gdprAssessment.getViolationCount());

        gdprViolationsCounter.increment();

        // Immediate containment
        gdprService.containGdprViolation(event, correlationId);

        // Impact assessment
        var impactAssessment = gdprService.assessGdprViolationImpact(event, gdprAssessment, correlationId);

        // Remediation planning
        var remediationPlan = gdprService.planGdprRemediation(event, gdprAssessment, correlationId);

        // Execute remediation
        gdprService.executeGdprRemediation(remediationPlan, correlationId);

        // DPO notification
        gdprService.notifyDpo(event, impactAssessment, correlationId);

        // Consider supervisory authority notification
        if (impactAssessment.requiresSupervisoryNotification()) {
            gdprService.notifySupervisoryAuthority(event, impactAssessment, correlationId);
            supervisoryAuthorityNotificationsCounter.increment();
        }
    }

    /**
     * Deserializes the event JSON into a GdprComplianceEventDto.
     */
    private GdprComplianceEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, GdprComplianceEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize GDPR compliance event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the GDPR compliance event.
     */
    private void validateEvent(GdprComplianceEventDto event, String correlationId) {
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }

        if (event.getGdprArticle() == null || event.getGdprArticle().trim().isEmpty()) {
            throw new IllegalArgumentException("GDPR article is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        // Validate specific fields based on event type
        if (event.getEventType().contains("REQUEST") &&
            (event.getDataSubjectId() == null || event.getDataSubjectId().trim().isEmpty())) {
            throw new IllegalArgumentException("Data subject ID is required for rights requests");
        }

        if (event.getEventType().contains("BREACH") &&
            (event.getBreachId() == null || event.getBreachId().trim().isEmpty())) {
            throw new IllegalArgumentException("Breach ID is required for breach events");
        }
    }

    /**
     * Handles processing errors with GDPR audit preservation.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("Failed to process GDPR compliance event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ with GDPR audit preservation
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "gdpr-compliance",
                "gdprAudit", true,
                "regulation", "GDPR"
            ));

            log.info("Sent failed GDPR compliance event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("Failed to send GDPR compliance event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "GdprComplianceEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}