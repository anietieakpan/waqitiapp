package com.waqiti.investment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.SocialImpactBondService;
import com.waqiti.investment.service.ImpactMeasurementService;
import com.waqiti.investment.service.InvestmentComplianceService;
import com.waqiti.investment.entity.SocialImpactBond;
import com.waqiti.investment.entity.ImpactMetric;
import com.waqiti.investment.entity.SIBInvestment;
import com.waqiti.investment.entity.SIBStatus;
import com.waqiti.investment.entity.SIBInvestmentType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #70: Social Impact Bond (SIB) Event Consumer
 * Processes social impact bond transactions and impact measurement with full ESG compliance
 * Implements 12-step zero-tolerance processing for impact investing and outcome-based payments
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialImpactBondEventConsumer extends BaseKafkaConsumer {

    private final SocialImpactBondService socialImpactBondService;
    private final ImpactMeasurementService impactMeasurementService;
    private final InvestmentComplianceService investmentComplianceService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "social-impact-bond-events", groupId = "social-impact-bond-group")
    @CircuitBreaker(name = "social-impact-bond-consumer")
    @Retry(name = "social-impact-bond-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSocialImpactBondEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "social-impact-bond-event");
        MDC.put("partition", String.valueOf(record.partition()));
        MDC.put("offset", String.valueOf(record.offset()));
        
        try {
            log.info("Step 1: Processing social impact bond event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            // Step 2: Parse and validate event structure
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            // Step 3: Extract and validate event details
            String eventId = eventData.path("eventId").asText();
            String eventType = eventData.path("eventType").asText();
            String sibId = eventData.path("sibId").asText();
            String investorId = eventData.path("investorId").asText();
            String serviceProviderId = eventData.path("serviceProviderId").asText();
            String outcomePayerId = eventData.path("outcomePayerId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String socialOutcome = eventData.path("socialOutcome").asText();
            String targetPopulation = eventData.path("targetPopulation").asText();
            LocalDateTime contractPeriodStart = LocalDateTime.parse(eventData.path("contractPeriodStart").asText());
            LocalDateTime contractPeriodEnd = LocalDateTime.parse(eventData.path("contractPeriodEnd").asText());
            String impactMetrics = eventData.path("impactMetrics").asText();
            String paymentStructure = eventData.path("paymentStructure").asText();
            BigDecimal targetImpact = new BigDecimal(eventData.path("targetImpact").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            // CRITICAL SECURITY: Idempotency check
            String idempotencyKey = "social-impact-bond:" + sibId + ":" + investorId + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate SIB event ignored: sibId={}, investorId={}, eventId={}", sibId, investorId, eventId);
                ack.acknowledge();
                return;
            }

            MDC.put("eventId", eventId);
            MDC.put("sibId", sibId);
            MDC.put("eventType", eventType);
            MDC.put("socialOutcome", socialOutcome);
            MDC.put("investorId", investorId);
            
            log.info("Step 2: Extracted SIB event details: eventId={}, type={}, sibId={}, outcome={}, amount={}", 
                    eventId, eventType, sibId, socialOutcome, amount);
            
            // Step 4: Validate social impact compliance and ESG requirements
            validateSocialImpactCompliance(eventId, sibId, socialOutcome, targetPopulation, investorId, serviceProviderId);
            
            // Step 5: Validate impact measurement framework
            validateImpactMeasurementFramework(eventId, impactMetrics, targetImpact, socialOutcome);
            
            // Step 6: Check idempotency to prevent duplicate processing
            if (socialImpactBondService.isSIBEventProcessed(eventId, sibId)) {
                log.warn("Step 6: SIB event already processed: eventId={}, sibId={}", eventId, sibId);
                ack.acknowledge();
                return;
            }
            
            // Step 7: Validate stakeholder authorization and accreditation
            validateStakeholderAuthorization(investorId, serviceProviderId, outcomePayerId, amount);
            
            // Step 8: Process social impact bond transaction based on type
            processSocialImpactBondTransaction(eventId, eventType, sibId, investorId, serviceProviderId, 
                    outcomePayerId, amount, currency, socialOutcome, targetPopulation, contractPeriodStart, 
                    contractPeriodEnd, impactMetrics, paymentStructure, targetImpact, timestamp);
            
            // Step 9: Update impact measurement and tracking
            updateImpactMeasurementTracking(eventId, sibId, impactMetrics, targetImpact, socialOutcome, timestamp);
            
            // Step 10: Record ESG and sustainability compliance
            recordESGCompliance(eventId, sibId, socialOutcome, targetPopulation, investorId, amount, timestamp);
            
            // Step 11: Publish impact reporting events
            publishImpactReportingEvents(eventId, sibId, socialOutcome, impactMetrics, targetImpact, timestamp);
            
            // Step 12: Complete processing and acknowledge
            socialImpactBondService.markSIBEventProcessed(eventId, sibId);

            // CRITICAL SECURITY: Mark operation as completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("sibId", sibId, "investorId", investorId, "amount", amount.toString(),
                       "socialOutcome", socialOutcome, "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();

            log.info("Step 12: Successfully processed SIB event: eventId={}, sibId={}, outcome={}",
                    eventId, sibId, socialOutcome);

        } catch (JsonProcessingException e) {
            log.error("SECURITY: Failed to parse social impact bond event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("SECURITY: Error processing social impact bond event: {}", e.getMessage(), e);
            String idempotencyKey = "social-impact-bond:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        log.info("Step 2a: Validating social impact bond event structure");
        
        if (!eventData.has("eventId") || eventData.path("eventId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventId in SIB event");
        }
        
        if (!eventData.has("eventType") || eventData.path("eventType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventType in SIB event");
        }
        
        if (!eventData.has("sibId") || eventData.path("sibId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty sibId in SIB event");
        }
        
        if (!eventData.has("socialOutcome") || eventData.path("socialOutcome").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty socialOutcome in SIB event");
        }
        
        if (!eventData.has("amount") || eventData.path("amount").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty amount in SIB event");
        }
        
        if (!eventData.has("impactMetrics") || eventData.path("impactMetrics").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty impactMetrics in SIB event");
        }
        
        if (!eventData.has("targetImpact") || eventData.path("targetImpact").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty targetImpact in SIB event");
        }
        
        log.info("Step 2b: SIB event structure validation successful");
    }

    private void validateSocialImpactCompliance(String eventId, String sibId, String socialOutcome, 
                                              String targetPopulation, String investorId, String serviceProviderId) {
        log.info("Step 4: Validating social impact compliance for eventId={}", eventId);
        
        try {
            // Validate social outcome eligibility
            if (!investmentComplianceService.isEligibleSocialOutcome(socialOutcome)) {
                throw new IllegalArgumentException("Ineligible social outcome for SIB: " + socialOutcome);
            }
            
            // Validate target population compliance
            if (!investmentComplianceService.validateTargetPopulation(targetPopulation, socialOutcome)) {
                throw new IllegalArgumentException("Invalid target population for social outcome: " + targetPopulation);
            }
            
            // Check investor ESG accreditation
            if (!investmentComplianceService.hasESGAccreditation(investorId)) {
                throw new SecurityException("Investor lacks ESG accreditation for SIB investment: " + investorId);
            }
            
            // Validate service provider social credentials
            if (!investmentComplianceService.validateServiceProviderCredentials(serviceProviderId, socialOutcome)) {
                throw new SecurityException("Service provider lacks credentials for social outcome: " + serviceProviderId);
            }
            
            // Check sanctions and ethical compliance
            if (!investmentComplianceService.checkEthicalCompliance(investorId, serviceProviderId)) {
                throw new SecurityException("Ethical compliance check failed for SIB stakeholders");
            }
            
            log.info("Step 4: Social impact compliance validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 4: Social impact compliance validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("Social impact compliance validation failed: " + e.getMessage(), e);
        }
    }

    private void validateImpactMeasurementFramework(String eventId, String impactMetrics, BigDecimal targetImpact, 
                                                  String socialOutcome) {
        log.info("Step 5: Validating impact measurement framework for eventId={}", eventId);
        
        try {
            // Validate impact metrics are measurable and credible
            if (!impactMeasurementService.areMetricsMeasurable(impactMetrics, socialOutcome)) {
                throw new IllegalArgumentException("Impact metrics not measurable for social outcome: " + impactMetrics);
            }
            
            // Validate target impact is realistic and achievable
            if (!impactMeasurementService.isTargetImpactRealistic(targetImpact, impactMetrics, socialOutcome)) {
                throw new IllegalArgumentException("Target impact not realistic for metrics: " + targetImpact);
            }
            
            // Check measurement methodology compliance
            if (!impactMeasurementService.isMethodologyCompliant(impactMetrics, socialOutcome)) {
                throw new IllegalArgumentException("Impact measurement methodology not compliant: " + impactMetrics);
            }
            
            // Validate third-party verification requirements
            if (!impactMeasurementService.hasThirdPartyVerification(impactMetrics, socialOutcome)) {
                throw new IllegalArgumentException("Third-party verification required for impact metrics: " + impactMetrics);
            }
            
            log.info("Step 5: Impact measurement framework validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 5: Impact measurement framework validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new IllegalArgumentException("Impact measurement framework validation failed: " + e.getMessage(), e);
        }
    }

    private void validateStakeholderAuthorization(String investorId, String serviceProviderId, 
                                                String outcomePayerId, BigDecimal amount) {
        log.info("Step 7: Validating stakeholder authorization for amount={}", amount);
        
        try {
            // Validate investor accreditation and investment limits
            if (!investmentComplianceService.isAccreditedImpactInvestor(investorId)) {
                throw new SecurityException("Investor not accredited for impact investing: " + investorId);
            }
            
            if (!investmentComplianceService.validateInvestmentLimits(investorId, amount, "SIB")) {
                throw new IllegalArgumentException("Investment amount exceeds limits for investor: " + investorId);
            }
            
            // Validate service provider authorization
            if (!investmentComplianceService.isAuthorizedServiceProvider(serviceProviderId)) {
                throw new SecurityException("Unauthorized service provider: " + serviceProviderId);
            }
            
            // Validate outcome payer authorization and capacity
            if (!investmentComplianceService.isAuthorizedOutcomePayer(outcomePayerId)) {
                throw new SecurityException("Unauthorized outcome payer: " + outcomePayerId);
            }
            
            if (!investmentComplianceService.validatePaymentCapacity(outcomePayerId, amount)) {
                throw new IllegalArgumentException("Outcome payer lacks payment capacity: " + outcomePayerId);
            }
            
            // Check for conflicts of interest
            if (investmentComplianceService.hasConflictOfInterest(investorId, serviceProviderId, outcomePayerId)) {
                throw new SecurityException("Conflict of interest detected among SIB stakeholders");
            }
            
            log.info("Step 7: Stakeholder authorization validation successful");
            
        } catch (Exception e) {
            log.error("Step 7: Stakeholder authorization validation failed: {}", e.getMessage(), e);
            throw new SecurityException("Stakeholder authorization validation failed: " + e.getMessage(), e);
        }
    }

    private void processSocialImpactBondTransaction(String eventId, String eventType, String sibId, String investorId,
                                                  String serviceProviderId, String outcomePayerId, BigDecimal amount,
                                                  String currency, String socialOutcome, String targetPopulation,
                                                  LocalDateTime contractPeriodStart, LocalDateTime contractPeriodEnd,
                                                  String impactMetrics, String paymentStructure, BigDecimal targetImpact,
                                                  LocalDateTime timestamp) {
        log.info("Step 8: Processing SIB transaction: eventId={}, type={}", eventId, eventType);
        
        try {
            SIBInvestment sibInvestment = SIBInvestment.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .sibId(sibId)
                    .investorId(investorId)
                    .serviceProviderId(serviceProviderId)
                    .outcomePayerId(outcomePayerId)
                    .type(SIBInvestmentType.valueOf(eventType.toUpperCase()))
                    .amount(amount)
                    .currency(currency)
                    .socialOutcome(socialOutcome)
                    .targetPopulation(targetPopulation)
                    .contractPeriodStart(contractPeriodStart)
                    .contractPeriodEnd(contractPeriodEnd)
                    .impactMetrics(impactMetrics)
                    .paymentStructure(paymentStructure)
                    .targetImpact(targetImpact)
                    .status(SIBStatus.ACTIVE)
                    .timestamp(timestamp)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            // Process based on transaction type
            switch (eventType.toUpperCase()) {
                case "INVESTMENT":
                    socialImpactBondService.processSIBInvestment(sibInvestment);
                    break;
                case "OUTCOME_PAYMENT":
                    socialImpactBondService.processOutcomePayment(sibInvestment);
                    break;
                case "IMPACT_MEASUREMENT":
                    socialImpactBondService.processImpactMeasurement(sibInvestment);
                    break;
                case "CONTRACT_MODIFICATION":
                    socialImpactBondService.processContractModification(sibInvestment);
                    break;
                case "EARLY_TERMINATION":
                    socialImpactBondService.processEarlyTermination(sibInvestment);
                    break;
                case "IMPACT_VERIFICATION":
                    socialImpactBondService.processImpactVerification(sibInvestment);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported SIB transaction type: " + eventType);
            }
            
            // Save SIB investment record
            socialImpactBondService.saveSIBInvestment(sibInvestment);
            
            log.info("Step 8: SIB transaction processed successfully: eventId={}, sibId={}", eventId, sibId);
            
        } catch (Exception e) {
            log.error("Step 8: SIB transaction processing failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("SIB transaction processing failed: " + e.getMessage(), e);
        }
    }

    private void updateImpactMeasurementTracking(String eventId, String sibId, String impactMetrics, 
                                               BigDecimal targetImpact, String socialOutcome, LocalDateTime timestamp) {
        log.info("Step 9: Updating impact measurement tracking for eventId={}", eventId);
        
        try {
            // Create impact metric tracking entries
            List<ImpactMetric> metrics = impactMeasurementService.parseImpactMetrics(impactMetrics);
            for (ImpactMetric metric : metrics) {
                impactMeasurementService.createImpactTracking(
                        sibId, metric, targetImpact, socialOutcome, timestamp
                );
            }
            
            // Establish baseline measurements
            impactMeasurementService.establishBaselineMeasurements(
                    sibId, impactMetrics, socialOutcome, timestamp
            );
            
            // Set up measurement milestones
            impactMeasurementService.setupMeasurementMilestones(
                    sibId, impactMetrics, targetImpact, timestamp
            );
            
            // Initialize third-party verification framework
            impactMeasurementService.initializeVerificationFramework(
                    sibId, impactMetrics, socialOutcome
            );
            
            log.info("Step 9: Impact measurement tracking updated successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 9: Impact measurement tracking update failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for tracking issues, but log for manual review
        }
    }

    private void recordESGCompliance(String eventId, String sibId, String socialOutcome, String targetPopulation,
                                   String investorId, BigDecimal amount, LocalDateTime timestamp) {
        log.info("Step 10: Recording ESG compliance for eventId={}", eventId);
        
        try {
            // Record ESG investment classification
            investmentComplianceService.recordESGInvestment(
                    eventId, sibId, "SOCIAL_IMPACT_BOND", socialOutcome, targetPopulation, amount, timestamp
            );
            
            // Update ESG metrics and scoring
            investmentComplianceService.updateESGMetrics(
                    investorId, "SOCIAL", socialOutcome, amount, timestamp
            );
            
            // Record sustainability reporting data
            investmentComplianceService.recordSustainabilityData(
                    sibId, socialOutcome, targetPopulation, amount, timestamp
            );
            
            // Generate ESG compliance certificate
            investmentComplianceService.generateESGComplianceCertificate(
                    eventId, sibId, investorId, socialOutcome, timestamp
            );
            
            log.info("Step 10: ESG compliance recorded successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 10: ESG compliance recording failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for compliance recording issues, but log for manual review
        }
    }

    private void publishImpactReportingEvents(String eventId, String sibId, String socialOutcome, 
                                            String impactMetrics, BigDecimal targetImpact, LocalDateTime timestamp) {
        log.info("Step 11: Publishing impact reporting events for eventId={}", eventId);
        
        try {
            // Publish impact tracking event
            socialImpactBondService.publishImpactTrackingEvent(
                    eventId, sibId, socialOutcome, impactMetrics, targetImpact, timestamp
            );
            
            // Publish ESG reporting event
            socialImpactBondService.publishESGReportingEvent(
                    eventId, sibId, socialOutcome, timestamp
            );
            
            // Publish to impact measurement platforms
            socialImpactBondService.publishToImpactPlatforms(
                    sibId, socialOutcome, impactMetrics, targetImpact, timestamp
            );
            
            // Publish stakeholder notification
            socialImpactBondService.publishStakeholderNotification(
                    eventId, sibId, socialOutcome, "SIB_EVENT_PROCESSED", timestamp
            );
            
            log.info("Step 11: Impact reporting events published successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 11: Impact reporting event publishing failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for event publishing issues, but log for manual review
        }
    }
}