package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseConsumer;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.common.monitoring.CircuitBreaker;
import com.waqiti.compliance.dto.CcpaComplianceEventDto;
import com.waqiti.compliance.service.CcpaComplianceService;
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
 * Consumer for processing CCPA compliance events.
 * Handles California Consumer Privacy Act requirements including consumer rights,
 * opt-out mechanisms, non-discrimination provisions, and service provider compliance
 * with automated California privacy protection.
 *
 * @author Waqiti Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@Slf4j
@Component
public class CcpaComplianceEventsConsumer extends BaseConsumer<CcpaComplianceEventDto> {

    private static final String TOPIC_NAME = "ccpa-compliance-events";
    private static final String CONSUMER_GROUP = "compliance-service-ccpa";
    private static final String DLQ_TOPIC = "ccpa-compliance-events-dlq";

    private final CcpaComplianceService ccpaService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;
    private final CircuitBreaker circuitBreaker;

    // Metrics
    private final Counter eventsProcessedCounter;
    private final Counter eventsFailedCounter;
    private final Counter ccpaViolationsCounter;
    private final Counter consumerRightsRequestsCounter;
    private final Counter optOutRequestsCounter;
    private final Counter disclosureRequestsCounter;
    private final Timer processingTimer;
    private final Timer ccpaAssessmentTimer;

    @Autowired
    public CcpaComplianceEventsConsumer(
            CcpaComplianceService ccpaService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector,
            MeterRegistry meterRegistry) {

        this.ccpaService = ccpaService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
        this.circuitBreaker = new CircuitBreaker("ccpa-compliance", 5, Duration.ofMinutes(2));

        // Initialize metrics
        this.eventsProcessedCounter = Counter.builder("ccpa_compliance_events_processed_total")
                .description("Total number of CCPA compliance events processed")
                .tag("service", "compliance")
                .tag("consumer", "ccpa-compliance")
                .register(meterRegistry);

        this.eventsFailedCounter = Counter.builder("ccpa_compliance_events_failed_total")
                .description("Total number of failed CCPA compliance events")
                .tag("service", "compliance")
                .tag("consumer", "ccpa-compliance")
                .register(meterRegistry);

        this.ccpaViolationsCounter = Counter.builder("ccpa_violations_total")
                .description("Total number of CCPA violations detected")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.consumerRightsRequestsCounter = Counter.builder("ccpa_consumer_rights_requests_total")
                .description("Total number of CCPA consumer rights requests processed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.optOutRequestsCounter = Counter.builder("ccpa_opt_out_requests_total")
                .description("Total number of CCPA opt-out requests processed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.disclosureRequestsCounter = Counter.builder("ccpa_disclosure_requests_total")
                .description("Total number of CCPA disclosure requests processed")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("ccpa_compliance_processing_duration")
                .description("Time taken to process CCPA compliance events")
                .tag("service", "compliance")
                .register(meterRegistry);

        this.ccpaAssessmentTimer = Timer.builder("ccpa_assessment_duration")
                .description("Time taken for CCPA assessment")
                .tag("service", "compliance")
                .register(meterRegistry);
    }

    /**
     * Processes CCPA compliance events with comprehensive California privacy protection.
     *
     * @param eventJson The JSON representation of the CCPA compliance event
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
    public void processCcpaComplianceEvent(
            String eventJson,
            @Header(KafkaHeaders.RECEIVED_MESSAGE_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start();
        String correlationId = UUID.randomUUID().toString();

        try {
            log.info("Processing CCPA compliance event - CorrelationId: {}, Key: {}, Partition: {}, Offset: {}",
                    correlationId, key, partition, timestamp);

            // Deserialize event
            CcpaComplianceEventDto event = deserializeEvent(eventJson, correlationId);
            if (event == null) {
                return;
            }

            // Validate event
            validateEvent(event, correlationId);

            // Process with circuit breaker
            circuitBreaker.executeSupplier(() -> {
                processCcpaEvent(event, correlationId);
                return null;
            });

            // Track metrics
            eventsProcessedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "success");

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Successfully processed CCPA compliance event - CorrelationId: {}, EventType: {}, Consumer: {}",
                    correlationId, event.getEventType(), event.getConsumerId());

        } catch (Exception e) {
            handleProcessingError(eventJson, key, correlationId, e, acknowledgment);
        } finally {
            sample.stop(processingTimer);
        }
    }

    /**
     * Processes the CCPA compliance event with comprehensive California privacy protection.
     */
    private void processCcpaEvent(CcpaComplianceEventDto event, String correlationId) {
        Timer.Sample assessmentTimer = Timer.start();

        try {
            log.info("Processing CCPA compliance for - Type: {}, Consumer: {} - CorrelationId: {}",
                    event.getEventType(), event.getConsumerId(), correlationId);

            // CCPA compliance assessment
            var ccpaAssessment = ccpaService.assessCcpaCompliance(event, correlationId);

            // Process based on event type
            switch (event.getEventType()) {
                case "RIGHT_TO_KNOW_REQUEST":
                    processRightToKnowRequest(event, ccpaAssessment, correlationId);
                    break;
                case "RIGHT_TO_DELETE_REQUEST":
                    processRightToDeleteRequest(event, ccpaAssessment, correlationId);
                    break;
                case "RIGHT_TO_OPT_OUT_REQUEST":
                    processRightToOptOutRequest(event, ccpaAssessment, correlationId);
                    break;
                case "RIGHT_TO_NON_DISCRIMINATION":
                    processRightToNonDiscrimination(event, ccpaAssessment, correlationId);
                    break;
                case "CONSUMER_REQUEST_VERIFICATION":
                    processConsumerRequestVerification(event, ccpaAssessment, correlationId);
                    break;
                case "THIRD_PARTY_SALE_DISCLOSURE":
                    processThirdPartySaleDisclosure(event, ccpaAssessment, correlationId);
                    break;
                case "SERVICE_PROVIDER_COMPLIANCE":
                    processServiceProviderCompliance(event, ccpaAssessment, correlationId);
                    break;
                case "BUSINESS_PURPOSE_DISCLOSURE":
                    processBusinessPurposeDisclosure(event, ccpaAssessment, correlationId);
                    break;
                case "CCPA_PRIVACY_NOTICE_UPDATE":
                    processCcpaPrivacyNoticeUpdate(event, ccpaAssessment, correlationId);
                    break;
                case "PERSONAL_INFORMATION_SALE_TRACKING":
                    processPersonalInformationSaleTracking(event, ccpaAssessment, correlationId);
                    break;
                case "MINOR_CONSENT_VERIFICATION":
                    processMinorConsentVerification(event, ccpaAssessment, correlationId);
                    break;
                default:
                    processGenericCcpaEvent(event, ccpaAssessment, correlationId);
            }

            // Handle CCPA violations
            if (ccpaAssessment.hasViolations()) {
                handleCcpaViolations(event, ccpaAssessment, correlationId);
            }

            // Update CCPA tracking and records
            ccpaService.updateCcpaTracking(event, ccpaAssessment, correlationId);

        } finally {
            assessmentTimer.stop(ccpaAssessmentTimer);
        }
    }

    /**
     * Processes Right to Know requests under CCPA Section 1798.110.
     */
    private void processRightToKnowRequest(CcpaComplianceEventDto event,
                                         var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA Right to Know request - CorrelationId: {}, Consumer: {}",
                correlationId, event.getConsumerId());

        consumerRightsRequestsCounter.increment();
        disclosureRequestsCounter.increment();

        // Verify consumer identity per CCPA requirements
        var identityVerification = ccpaService.verifyCcpaConsumerIdentity(event, correlationId);

        if (!identityVerification.meetsCcpaStandards()) {
            log.warn("CCPA consumer identity verification failed - CorrelationId: {}, Reason: {}",
                    correlationId, identityVerification.getFailureReason());
            ccpaService.rejectRightToKnowRequest(event, identityVerification, correlationId);
            return;
        }

        // Determine request scope (categories or specific pieces)
        var requestScope = ccpaService.determineRightToKnowScope(event, correlationId);

        // Discover personal information per CCPA definition
        var personalInfoDiscovery = ccpaService.discoverCcpaPersonalInformation(event.getConsumerId(), correlationId);

        // Generate CCPA-compliant disclosure
        var disclosureResponse = ccpaService.generateRightToKnowResponse(personalInfoDiscovery, requestScope, correlationId);

        // Include required CCPA information
        if (requestScope.includesCategoriesOfInformation()) {
            disclosureResponse.includeCategoriesOfPersonalInformation();
            disclosureResponse.includeCategoriesOfSources();
            disclosureResponse.includeBusinessPurposes();
            disclosureResponse.includeCategoriesOfThirdParties();
        }

        if (requestScope.includesSpecificPieces()) {
            disclosureResponse.includeSpecificPiecesOfPersonalInformation();
        }

        // Deliver response within CCPA timeframe (45 days, extendable to 90)
        var deliveryResult = ccpaService.deliverRightToKnowResponse(event, disclosureResponse, correlationId);

        if (deliveryResult.isSuccessful()) {
            log.info("CCPA Right to Know request fulfilled - CorrelationId: {}, ResponseId: {}",
                    correlationId, disclosureResponse.getResponseId());

            ccpaService.recordRightToKnowFulfillment(event, deliveryResult, correlationId);
        } else {
            log.error("Failed to fulfill CCPA Right to Know request - CorrelationId: {}, Error: {}",
                    correlationId, deliveryResult.getError());
            ccpaService.handleRightToKnowFailure(event, deliveryResult, correlationId);
        }
    }

    /**
     * Processes Right to Delete requests under CCPA Section 1798.105.
     */
    private void processRightToDeleteRequest(CcpaComplianceEventDto event,
                                           var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA Right to Delete request - CorrelationId: {}, Consumer: {}",
                correlationId, event.getConsumerId());

        consumerRightsRequestsCounter.increment();

        // Verify consumer identity with higher standard for deletion
        var identityVerification = ccpaService.verifyCcpaConsumerIdentityForDeletion(event, correlationId);

        if (!identityVerification.meetsCcpaStandards()) {
            log.warn("CCPA consumer identity verification for deletion failed - CorrelationId: {}", correlationId);
            ccpaService.rejectRightToDeleteRequest(event, identityVerification, correlationId);
            return;
        }

        // Assess CCPA deletion exceptions under Section 1798.105(d)
        var deletionExceptions = ccpaService.assessCcpaDeletionExceptions(event, correlationId);

        if (deletionExceptions.hasBlockingExceptions()) {
            log.info("CCPA deletion blocked by exceptions - CorrelationId: {}, Exceptions: {}",
                    correlationId, deletionExceptions.getBlockingExceptions());
            ccpaService.partiallyRejectDeletionRequest(event, deletionExceptions, correlationId);
            return;
        }

        // Discover consumer's personal information
        var personalInfoInventory = ccpaService.discoverPersonalInfoForDeletion(event.getConsumerId(), correlationId);

        // Check for service provider notification requirements
        var serviceProviderNotifications = ccpaService.assessServiceProviderNotifications(personalInfoInventory, correlationId);

        // Execute CCPA-compliant deletion
        var deletionResult = ccpaService.executeCcpaDeletion(personalInfoInventory, correlationId);

        if (deletionResult.isSuccessful()) {
            log.info("CCPA Right to Delete request fulfilled - CorrelationId: {}, DeletedRecords: {}",
                    correlationId, deletionResult.getDeletedRecordCount());

            // Notify service providers if required
            if (serviceProviderNotifications.requiresNotifications()) {
                ccpaService.notifyServiceProvidersOfDeletion(serviceProviderNotifications, deletionResult, correlationId);
            }

            ccpaService.recordRightToDeleteFulfillment(event, deletionResult, correlationId);
        } else {
            log.error("Failed to fulfill CCPA Right to Delete request - CorrelationId: {}, Error: {}",
                    correlationId, deletionResult.getError());
            ccpaService.handleRightToDeleteFailure(event, deletionResult, correlationId);
        }
    }

    /**
     * Processes Right to Opt-Out requests under CCPA Section 1798.120.
     */
    private void processRightToOptOutRequest(CcpaComplianceEventDto event,
                                           var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA Right to Opt-Out request - CorrelationId: {}, Consumer: {}",
                correlationId, event.getConsumerId());

        consumerRightsRequestsCounter.increment();
        optOutRequestsCounter.increment();

        // Verify opt-out request validity (no identity verification required for opt-out)
        var optOutValidation = ccpaService.validateOptOutRequest(event, correlationId);

        if (!optOutValidation.isValid()) {
            log.warn("Invalid CCPA opt-out request - CorrelationId: {}, Errors: {}",
                    correlationId, optOutValidation.getErrors());
            ccpaService.rejectOptOutRequest(event, optOutValidation, correlationId);
            return;
        }

        // Identify personal information sales to opt-out from
        var salesInventory = ccpaService.identifyPersonalInformationSales(event.getConsumerId(), correlationId);

        // Implement opt-out across all sales activities
        var optOutResult = ccpaService.implementOptOut(salesInventory, correlationId);

        if (optOutResult.isSuccessful()) {
            log.info("CCPA Right to Opt-Out implemented - CorrelationId: {}, OptedOutSales: {}",
                    correlationId, optOutResult.getOptedOutSalesCount());

            // Update all systems to respect opt-out
            ccpaService.updateSystemsForOptOut(optOutResult, correlationId);

            // Notify third parties receiving personal information
            ccpaService.notifyThirdPartiesOfOptOut(optOutResult, correlationId);

            // Add consumer to global opt-out list
            ccpaService.addToGlobalOptOutList(event.getConsumerId(), correlationId);

            ccpaService.recordOptOutFulfillment(event, optOutResult, correlationId);
        } else {
            log.error("Failed to implement CCPA opt-out - CorrelationId: {}, Error: {}",
                    correlationId, optOutResult.getError());
            ccpaService.handleOptOutFailure(event, optOutResult, correlationId);
        }
    }

    /**
     * Processes Right to Non-Discrimination under CCPA Section 1798.125.
     */
    private void processRightToNonDiscrimination(CcpaComplianceEventDto event,
                                               var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA Right to Non-Discrimination - CorrelationId: {}, Consumer: {}",
                correlationId, event.getConsumerId());

        consumerRightsRequestsCounter.increment();

        // Assess for discriminatory practices
        var discriminationAssessment = ccpaService.assessDiscriminatoryPractices(event, correlationId);

        if (discriminationAssessment.hasDiscrimination()) {
            log.error("CCPA discrimination detected - CorrelationId: {}, DiscriminationType: {}",
                    correlationId, discriminationAssessment.getDiscriminationType());

            ccpaViolationsCounter.increment();

            // Immediate remediation of discriminatory practices
            ccpaService.remediateDiscrimination(event, discriminationAssessment, correlationId);

            // Investigate systemic discrimination
            ccpaService.investigateSystemicDiscrimination(event, discriminationAssessment, correlationId);

            // Notify consumer of remediation
            ccpaService.notifyConsumerOfDiscriminationRemediation(event, correlationId);
        }

        // Validate financial incentive programs
        if (event.hasFinancialIncentiveProgram()) {
            var incentiveValidation = ccpaService.validateFinancialIncentiveProgram(event, correlationId);

            if (!incentiveValidation.isCcpaCompliant()) {
                log.warn("Financial incentive program not CCPA compliant - CorrelationId: {}", correlationId);
                ccpaService.remedyFinancialIncentiveProgram(event, incentiveValidation, correlationId);
            }
        }

        // Document non-discrimination compliance
        ccpaService.documentNonDiscriminationCompliance(event, discriminationAssessment, correlationId);
    }

    /**
     * Processes consumer request verification procedures.
     */
    private void processConsumerRequestVerification(CcpaComplianceEventDto event,
                                                  var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA consumer request verification - CorrelationId: {}, RequestType: {}",
                correlationId, event.getRequestType());

        // Implement CCPA verification procedures
        var verificationProcedure = ccpaService.implementCcpaVerificationProcedure(event, correlationId);

        switch (event.getRequestType()) {
            case "RIGHT_TO_KNOW_CATEGORIES":
                // Lower verification standard for categories
                verificationProcedure.applyLowVerificationStandard();
                break;
            case "RIGHT_TO_KNOW_SPECIFIC_PIECES":
            case "RIGHT_TO_DELETE":
                // Higher verification standard for specific pieces and deletion
                verificationProcedure.applyHighVerificationStandard();
                break;
            case "RIGHT_TO_OPT_OUT":
                // No verification required for opt-out
                verificationProcedure.noVerificationRequired();
                break;
            default:
                verificationProcedure.applyStandardVerification();
        }

        // Execute verification
        var verificationResult = ccpaService.executeVerification(verificationProcedure, correlationId);

        if (verificationResult.isSuccessful()) {
            log.info("CCPA consumer verification successful - CorrelationId: {}, VerificationLevel: {}",
                    correlationId, verificationResult.getVerificationLevel());

            ccpaService.recordSuccessfulVerification(event, verificationResult, correlationId);
        } else {
            log.warn("CCPA consumer verification failed - CorrelationId: {}, Reason: {}",
                    correlationId, verificationResult.getFailureReason());

            ccpaService.handleVerificationFailure(event, verificationResult, correlationId);
        }
    }

    /**
     * Processes third-party sale disclosures.
     */
    private void processThirdPartySaleDisclosure(CcpaComplianceEventDto event,
                                               var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA third-party sale disclosure - CorrelationId: {}, ThirdParty: {}",
                correlationId, event.getThirdPartyId());

        disclosureRequestsCounter.increment();

        // Assess whether transaction constitutes a "sale" under CCPA
        var saleAssessment = ccpaService.assessCcpaSaleDefinition(event, correlationId);

        if (saleAssessment.constitutesSharing()) {
            log.info("Transaction constitutes sharing under CCPA - CorrelationId: {}", correlationId);

            // Check consumer opt-out status
            var optOutStatus = ccpaService.checkConsumerOptOutStatus(event.getConsumerId(), correlationId);

            if (optOutStatus.hasOptedOut()) {
                log.warn("Consumer has opted out, blocking sale - CorrelationId: {}", correlationId);
                ccpaService.blockSaleForOptedOutConsumer(event, correlationId);
                return;
            }

            // Record sale for disclosure purposes
            ccpaService.recordPersonalInformationSale(event, saleAssessment, correlationId);

            // Update privacy notice if new category of sharing
            if (saleAssessment.isNewSharingCategory()) {
                ccpaService.updatePrivacyNoticeForNewSharing(event, saleAssessment, correlationId);
            }
        }

        // Document sharing/sale analysis
        ccpaService.documentSaleAssessment(event, saleAssessment, correlationId);
    }

    /**
     * Processes service provider compliance monitoring.
     */
    private void processServiceProviderCompliance(CcpaComplianceEventDto event,
                                                var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA service provider compliance - CorrelationId: {}, ServiceProvider: {}",
                correlationId, event.getServiceProviderId());

        // Validate service provider agreement
        var agreementValidation = ccpaService.validateServiceProviderAgreement(event, correlationId);

        if (!agreementValidation.isCcpaCompliant()) {
            log.error("Service provider agreement not CCPA compliant - CorrelationId: {}, Issues: {}",
                    correlationId, agreementValidation.getComplianceIssues());

            ccpaViolationsCounter.increment();
            ccpaService.flagNonCompliantServiceProvider(event, agreementValidation, correlationId);
            return;
        }

        // Monitor service provider activities
        var activityMonitoring = ccpaService.monitorServiceProviderActivities(event, correlationId);

        if (activityMonitoring.hasViolations()) {
            log.warn("Service provider violations detected - CorrelationId: {}, Violations: {}",
                    correlationId, activityMonitoring.getViolations());

            // Initiate corrective actions
            ccpaService.initiateServiceProviderCorrectiveActions(event, activityMonitoring, correlationId);
        }

        // Verify purpose limitation compliance
        var purposeLimitationCompliance = ccpaService.verifyPurposeLimitationCompliance(event, correlationId);

        if (!purposeLimitationCompliance.isCompliant()) {
            ccpaViolationsCounter.increment();
            ccpaService.addressPurposeLimitationViolation(event, purposeLimitationCompliance, correlationId);
        }

        // Document service provider compliance
        ccpaService.documentServiceProviderCompliance(event, correlationId);
    }

    /**
     * Processes business purpose disclosures.
     */
    private void processBusinessPurposeDisclosure(CcpaComplianceEventDto event,
                                                var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA business purpose disclosure - CorrelationId: {}, Purpose: {}",
                correlationId, event.getBusinessPurpose());

        disclosureRequestsCounter.increment();

        // Validate business purpose legitimacy
        var purposeValidation = ccpaService.validateBusinessPurpose(event, correlationId);

        if (!purposeValidation.isValidCcpaPurpose()) {
            log.warn("Invalid CCPA business purpose - CorrelationId: {}, Issues: {}",
                    correlationId, purposeValidation.getIssues());

            ccpaViolationsCounter.increment();
            ccpaService.flagInvalidBusinessPurpose(event, purposeValidation, correlationId);
            return;
        }

        // Record business purpose disclosure
        ccpaService.recordBusinessPurposeDisclosure(event, correlationId);

        // Update privacy notice if new business purpose
        if (purposeValidation.isNewBusinessPurpose()) {
            ccpaService.updatePrivacyNoticeForNewPurpose(event, correlationId);
        }

        // Verify purpose limitation
        ccpaService.verifyBusinessPurposeLimitation(event, correlationId);
    }

    /**
     * Processes CCPA privacy notice updates.
     */
    private void processCcpaPrivacyNoticeUpdate(CcpaComplianceEventDto event,
                                              var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA privacy notice update - CorrelationId: {}, NoticeType: {}",
                correlationId, event.getNoticeType());

        // Validate CCPA privacy notice requirements
        var noticeValidation = ccpaService.validateCcpaPrivacyNotice(event, correlationId);

        if (!noticeValidation.meetsCcpaRequirements()) {
            log.error("Privacy notice does not meet CCPA requirements - CorrelationId: {}, MissingElements: {}",
                    correlationId, noticeValidation.getMissingElements());

            ccpaViolationsCounter.increment();
            ccpaService.flagDeficientPrivacyNotice(event, noticeValidation, correlationId);
            return;
        }

        // Update privacy notice
        var updateResult = ccpaService.updateCcpaPrivacyNotice(event, correlationId);

        if (updateResult.isSuccessful()) {
            log.info("CCPA privacy notice updated successfully - CorrelationId: {}, NoticeId: {}",
                    correlationId, updateResult.getNoticeId());

            // Notify consumers if material changes
            if (updateResult.hasMaterialChanges()) {
                ccpaService.notifyConsumersOfMaterialChanges(updateResult, correlationId);
            }

            ccpaService.recordPrivacyNoticeUpdate(event, updateResult, correlationId);
        } else {
            log.error("Failed to update CCPA privacy notice - CorrelationId: {}, Error: {}",
                    correlationId, updateResult.getError());
            ccpaService.handlePrivacyNoticeUpdateFailure(event, updateResult, correlationId);
        }
    }

    /**
     * Processes personal information sale tracking.
     */
    private void processPersonalInformationSaleTracking(CcpaComplianceEventDto event,
                                                      var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA personal information sale tracking - CorrelationId: {}, SaleId: {}",
                correlationId, event.getSaleId());

        // Track personal information sale
        var saleTracking = ccpaService.trackPersonalInformationSale(event, correlationId);

        // Verify opt-out compliance
        var optOutCompliance = ccpaService.verifyOptOutCompliance(event, correlationId);

        if (!optOutCompliance.isCompliant()) {
            log.error("Sale violates consumer opt-out preference - CorrelationId: {}, Consumer: {}",
                    correlationId, event.getConsumerId());

            ccpaViolationsCounter.increment();

            // Reverse the sale
            ccpaService.reverseSaleForOptOutViolation(event, correlationId);

            // Apply corrective measures
            ccpaService.applyOptOutViolationCorrectiveMeasures(event, optOutCompliance, correlationId);
        }

        // Update sale records and analytics
        ccpaService.updateSaleRecordsAndAnalytics(saleTracking, correlationId);

        // Generate periodic sale disclosures
        if (saleTracking.triggersPeriodicDisclosure()) {
            ccpaService.generatePeriodicSaleDisclosure(saleTracking, correlationId);
        }
    }

    /**
     * Processes minor consent verification under CCPA.
     */
    private void processMinorConsentVerification(CcpaComplianceEventDto event,
                                               var ccpaAssessment, String correlationId) {
        log.info("Processing CCPA minor consent verification - CorrelationId: {}, MinorAge: {}",
                correlationId, event.getMinorAge());

        // Assess minor consent requirements
        var minorConsentRequirements = ccpaService.assessMinorConsentRequirements(event, correlationId);

        if (minorConsentRequirements.requiresConsentIn()) {
            // Under 13: Affirmative authorization from parent required
            if (event.getMinorAge() < 13) {
                var parentalConsent = ccpaService.verifyParentalConsent(event, correlationId);

                if (!parentalConsent.isVerified()) {
                    log.warn("Parental consent not verified for minor under 13 - CorrelationId: {}", correlationId);
                    ccpaViolationsCounter.increment();
                    ccpaService.blockProcessingForUnconsentedMinor(event, correlationId);
                    return;
                }
            }
            // 13-16: Affirmative authorization from minor required
            else if (event.getMinorAge() >= 13 && event.getMinorAge() < 16) {
                var minorConsent = ccpaService.verifyMinorConsent(event, correlationId);

                if (!minorConsent.isVerified()) {
                    log.warn("Minor consent not verified for 13-15 year old - CorrelationId: {}", correlationId);
                    ccpaViolationsCounter.increment();
                    ccpaService.blockProcessingForUnconsentedMinor(event, correlationId);
                    return;
                }
            }
        }

        // Document minor consent compliance
        ccpaService.documentMinorConsentCompliance(event, minorConsentRequirements, correlationId);

        log.info("CCPA minor consent verification completed - CorrelationId: {}", correlationId);
    }

    /**
     * Processes generic CCPA events.
     */
    private void processGenericCcpaEvent(CcpaComplianceEventDto event,
                                       var ccpaAssessment, String correlationId) {
        log.info("Processing generic CCPA event - CorrelationId: {}", correlationId);

        // Standard CCPA processing
        ccpaService.processStandardCcpaEvent(event, ccpaAssessment, correlationId);
    }

    /**
     * Handles CCPA violations with comprehensive remediation.
     */
    private void handleCcpaViolations(CcpaComplianceEventDto event,
                                    var ccpaAssessment, String correlationId) {
        log.error("Handling CCPA violations - CorrelationId: {}, ViolationCount: {}",
                correlationId, ccpaAssessment.getViolationCount());

        ccpaViolationsCounter.increment();

        // Immediate containment
        ccpaService.containCcpaViolation(event, correlationId);

        // Impact assessment
        var impactAssessment = ccpaService.assessCcpaViolationImpact(event, ccpaAssessment, correlationId);

        // Remediation planning
        var remediationPlan = ccpaService.planCcpaRemediation(event, ccpaAssessment, correlationId);

        // Execute remediation
        ccpaService.executeCcpaRemediation(remediationPlan, correlationId);

        // Consumer notification if required
        if (impactAssessment.requiresConsumerNotification()) {
            ccpaService.notifyAffectedConsumers(event, impactAssessment, correlationId);
        }

        // Attorney General notification for significant violations
        if (impactAssessment.requiresAttorneyGeneralNotification()) {
            ccpaService.notifyCaliforniaAttorneyGeneral(event, impactAssessment, correlationId);
        }

        // Stakeholder notification
        ccpaService.notifyCcpaViolationStakeholders(event, impactAssessment, correlationId);
    }

    /**
     * Deserializes the event JSON into a CcpaComplianceEventDto.
     */
    private CcpaComplianceEventDto deserializeEvent(String eventJson, String correlationId) {
        try {
            return objectMapper.readValue(eventJson, CcpaComplianceEventDto.class);
        } catch (Exception e) {
            log.error("Failed to deserialize CCPA compliance event - CorrelationId: {}, Error: {}",
                     correlationId, e.getMessage(), e);
            eventsFailedCounter.increment();
            metricsCollector.recordEventProcessed(TOPIC_NAME, "deserialization_error");
            return null;
        }
    }

    /**
     * Validates the CCPA compliance event.
     */
    private void validateEvent(CcpaComplianceEventDto event, String correlationId) {
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }

        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }

        // Validate specific fields based on event type
        if (event.getEventType().contains("REQUEST") &&
            (event.getConsumerId() == null || event.getConsumerId().trim().isEmpty())) {
            throw new IllegalArgumentException("Consumer ID is required for rights requests");
        }

        if (event.getEventType().contains("SALE") &&
            (event.getSaleId() == null || event.getSaleId().trim().isEmpty())) {
            throw new IllegalArgumentException("Sale ID is required for sale events");
        }

        if (event.getEventType().contains("SERVICE_PROVIDER") &&
            (event.getServiceProviderId() == null || event.getServiceProviderId().trim().isEmpty())) {
            throw new IllegalArgumentException("Service Provider ID is required for service provider events");
        }
    }

    /**
     * Handles processing errors with CCPA audit preservation.
     */
    private void handleProcessingError(String eventJson, String key, String correlationId,
                                     Exception error, Acknowledgment acknowledgment) {
        log.error("Failed to process CCPA compliance event - CorrelationId: {}, Key: {}, Error: {}",
                 correlationId, key, error.getMessage(), error);

        eventsFailedCounter.increment();
        metricsCollector.recordEventProcessed(TOPIC_NAME, "error");

        try {
            // Send to DLQ with CCPA audit preservation
            kafkaTemplate.send(DLQ_TOPIC, key, Map.of(
                "originalEvent", eventJson,
                "error", error.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now().toString(),
                "service", "compliance-service",
                "consumer", "ccpa-compliance",
                "ccpaAudit", true,
                "regulation", "CCPA"
            ));

            log.info("Sent failed CCPA compliance event to DLQ - CorrelationId: {}", correlationId);

        } catch (Exception dlqError) {
            log.error("Failed to send CCPA compliance event to DLQ - CorrelationId: {}, Error: {}",
                     correlationId, dlqError.getMessage(), dlqError);
        }

        // Acknowledge to prevent reprocessing
        acknowledgment.acknowledge();
    }

    @Override
    protected String getConsumerName() {
        return "CcpaComplianceEventsConsumer";
    }

    @Override
    protected String getTopicName() {
        return TOPIC_NAME;
    }
}