package com.waqiti.compliance.ofac;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.exception.ComplianceException;
import com.waqiti.compliance.ofac.dto.*;
import com.waqiti.compliance.ofac.repository.OFACScreeningRepository;
import com.waqiti.payment.domain.Payment;
import com.waqiti.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Synchronous OFAC Screening Service
 *
 * Implements real-time sanctions screening BEFORE payment execution:
 * - OFAC SDN (Specially Designated Nationals) List
 * - OFAC Consolidated Sanctions List
 * - EU Sanctions List
 * - UN Sanctions List
 * - Country-based sanctions (embargoed countries)
 *
 * Legal Requirements:
 * - Office of Foreign Assets Control (OFAC) regulations
 * - Executive Orders on sanctions
 * - International Emergency Economic Powers Act (IEEPA)
 * - Trading with the Enemy Act
 *
 * Penalties for Violations:
 * - Civil: Up to $307,922 per violation or 2x transaction value
 * - Criminal: Up to $1,000,000 and/or 20 years imprisonment
 * - Blocked assets subject to seizure
 *
 * Screening Must Occur:
 * - BEFORE payment execution (blocking)
 * - On customer onboarding
 * - On beneficial owner changes
 * - On periodic re-screening (quarterly)
 * - On sanctions list updates (real-time)
 *
 * @author Waqiti Platform Engineering
 * @version 2.0.0
 * @since 2025-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SynchronousOFACScreeningService {

    private final OFACScreeningRepository ofacRepository;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final OFACSDNListService sdnListService;
    private final FuzzyNameMatchingService fuzzyMatchingService;
    private final CountrySanctionsService countrySanctionsService;

    // Matching thresholds
    private static final int EXACT_MATCH_THRESHOLD = 100;
    private static final int HIGH_CONFIDENCE_THRESHOLD = 85;
    private static final int MEDIUM_CONFIDENCE_THRESHOLD = 70;
    private static final int LOW_CONFIDENCE_THRESHOLD = 50;

    /**
     * Perform synchronous OFAC screening on payment BEFORE execution
     * BLOCKS payment if sanctions hit detected
     *
     * @param payment Payment to screen
     * @return Screening result with block decision
     * @throws ComplianceException if payment is blocked by sanctions
     */
    @Timed(value = "ofac.screening.payment", description = "Time for synchronous payment OFAC screening")
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public OFACScreeningResult screenPaymentSynchronously(Payment payment) {
        log.info("Performing synchronous OFAC screening: paymentId={}, amount=${}, recipient={}",
            payment.getId(), payment.getAmount(), payment.getRecipientId());

        long startTime = System.currentTimeMillis();

        try {
            // Screen sender
            OFACScreeningResult senderResult = screenPerson(
                payment.getSenderId(),
                payment.getSenderName(),
                payment.getSenderCountry(),
                "PAYMENT_SENDER",
                payment.getId()
            );

            // Screen recipient
            OFACScreeningResult recipientResult = screenPerson(
                payment.getRecipientId(),
                payment.getRecipientName(),
                payment.getRecipientCountry(),
                "PAYMENT_RECIPIENT",
                payment.getId()
            );

            // Screen destination country
            OFACScreeningResult countryResult = screenCountry(
                payment.getRecipientCountry(),
                payment.getAmount(),
                payment.getId()
            );

            // Aggregate results
            OFACScreeningResult aggregatedResult = aggregateScreeningResults(
                Arrays.asList(senderResult, recipientResult, countryResult),
                payment.getId()
            );

            // Persist screening record
            ofacRepository.saveScreeningResult(aggregatedResult);

            // Audit trail
            auditService.logSecurityEvent(
                "OFAC_SCREENING_COMPLETED",
                Map.of(
                    "paymentId", payment.getId(),
                    "screeningId", aggregatedResult.getScreeningId(),
                    "blocked", aggregatedResult.isBlocked(),
                    "matchLevel", aggregatedResult.getHighestMatchLevel(),
                    "duration", System.currentTimeMillis() - startTime
                ),
                payment.getSenderId(),
                "OFAC_COMPLIANCE"
            );

            // Metrics
            meterRegistry.counter("ofac.screenings.completed").increment();
            meterRegistry.timer("ofac.screening.duration")
                .record(System.currentTimeMillis() - startTime, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (aggregatedResult.isBlocked()) {
                meterRegistry.counter("ofac.payments.blocked").increment();

                // CRITICAL ALERT
                alertComplianceTeamCritical("OFAC SANCTIONS HIT - PAYMENT BLOCKED",
                    String.format("Payment ID: %s, Amount: $%s, Sender: %s, Recipient: %s, Match: %s",
                        payment.getId(), payment.getAmount(), payment.getSenderName(),
                        payment.getRecipientName(), aggregatedResult.getMatchReason()));

                log.error("PAYMENT BLOCKED BY OFAC SANCTIONS: paymentId={}, reason={}",
                    payment.getId(), aggregatedResult.getMatchReason());

                // THROW EXCEPTION TO BLOCK PAYMENT
                throw new ComplianceException(
                    "OFAC_SANCTIONS_BLOCK",
                    "Payment blocked due to OFAC sanctions screening hit: " + aggregatedResult.getMatchReason()
                );
            }

            if (aggregatedResult.requiresManualReview()) {
                // Queue for manual review but don't block
                queueForManualReview(aggregatedResult);
                meterRegistry.counter("ofac.manual.review.queued").increment();

                log.warn("OFAC screening requires manual review: paymentId={}, reason={}",
                    payment.getId(), aggregatedResult.getMatchReason());
            }

            log.info("OFAC screening passed: paymentId={}, duration={}ms",
                payment.getId(), System.currentTimeMillis() - startTime);

            return aggregatedResult;

        } catch (ComplianceException e) {
            // Re-throw compliance exceptions (these block payments)
            throw e;
        } catch (Exception e) {
            log.error("Error during OFAC screening: paymentId={}", payment.getId(), e);
            meterRegistry.counter("ofac.screening.errors").increment();

            // FAIL CLOSED: Block payment on screening error
            throw new ComplianceException(
                "OFAC_SCREENING_ERROR",
                "Payment blocked due to OFAC screening system error", e
            );
        }
    }

    /**
     * Screen individual person against OFAC SDN list
     *
     * @param personId Person ID
     * @param personName Person full name
     * @param country Person country
     * @param role Role in transaction (SENDER/RECIPIENT/BENEFICIARY)
     * @param transactionId Associated transaction ID
     * @return Screening result
     */
    @Timed(value = "ofac.screening.person")
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public OFACScreeningResult screenPerson(String personId, String personName,
                                            String country, String role, String transactionId) {
        log.debug("Screening person: personId={}, name={}, country={}, role={}",
            personId, personName, country, role);

        try {
            // Check cache first
            Optional<OFACScreeningResult> cachedResult = getCachedScreeningResult(personId);
            if (cachedResult.isPresent() && !isStale(cachedResult.get())) {
                log.debug("Using cached OFAC screening result: personId={}", personId);
                meterRegistry.counter("ofac.cache.hits").increment();
                return cachedResult.get();
            }

            // Get latest SDN list
            List<SDNEntry> sdnList = sdnListService.getLatestSDNList();

            // Perform fuzzy name matching
            List<SDNMatch> matches = fuzzyMatchingService.findMatches(personName, sdnList);

            // Filter and score matches
            List<SDNMatch> significantMatches = matches.stream()
                .filter(m -> m.getConfidenceScore() >= LOW_CONFIDENCE_THRESHOLD)
                .sorted(Comparator.comparingInt(SDNMatch::getConfidenceScore).reversed())
                .collect(Collectors.toList());

            // Determine screening decision
            OFACScreeningDecision decision = determineDecision(significantMatches, country);

            // Build result
            OFACScreeningResult result = OFACScreeningResult.builder()
                .screeningId(generateScreeningId())
                .personId(personId)
                .personName(personName)
                .country(country)
                .role(role)
                .transactionId(transactionId)
                .screeningDate(LocalDateTime.now())
                .sdnMatches(significantMatches)
                .highestMatchScore(significantMatches.isEmpty() ? 0 :
                    significantMatches.get(0).getConfidenceScore())
                .highestMatchLevel(decision.getMatchLevel())
                .blocked(decision.isBlocked())
                .manualReviewRequired(decision.isManualReviewRequired())
                .matchReason(decision.getReason())
                .sdnListVersion(sdnListService.getCurrentVersion())
                .build();

            // Cache result
            cacheScreeningResult(personId, result);

            log.debug("Person screening complete: personId={}, matches={}, blocked={}",
                personId, significantMatches.size(), result.isBlocked());

            return result;

        } catch (Exception e) {
            log.error("Error screening person: personId={}", personId, e);
            meterRegistry.counter("ofac.person.screening.errors").increment();

            // FAIL CLOSED: Return blocked result on error
            return OFACScreeningResult.builder()
                .screeningId(generateScreeningId())
                .personId(personId)
                .personName(personName)
                .blocked(true)
                .matchReason("SCREENING_ERROR: " + e.getMessage())
                .build();
        }
    }

    /**
     * Screen destination country for sanctions/embargoes
     *
     * @param country Destination country code
     * @param amount Transaction amount
     * @param transactionId Transaction ID
     * @return Screening result
     */
    @Timed(value = "ofac.screening.country")
    @Cacheable(value = "ofac-country-screening", key = "#country")
    public OFACScreeningResult screenCountry(String country, BigDecimal amount, String transactionId) {
        log.debug("Screening country: country={}, amount=${}", country, amount);

        try {
            CountrySanctionsStatus sanctions = countrySanctionsService.getSanctionsStatus(country);

            boolean blocked = false;
            String reason = null;

            if (sanctions.isCompleteEmbargo()) {
                blocked = true;
                reason = String.format("Complete embargo on %s - all transactions prohibited", country);
            } else if (sanctions.hasSelectiveSanctions() && amount.compareTo(sanctions.getThreshold()) >= 0) {
                blocked = true;
                reason = String.format("Selective sanctions on %s - transactions >$%s prohibited",
                    country, sanctions.getThreshold());
            } else if (sanctions.hasSectoralSanctions()) {
                // Sectoral sanctions may require manual review
                reason = String.format("Sectoral sanctions on %s - manual review required", country);
            }

            OFACScreeningResult result = OFACScreeningResult.builder()
                .screeningId(generateScreeningId())
                .country(country)
                .transactionId(transactionId)
                .screeningDate(LocalDateTime.now())
                .blocked(blocked)
                .manualReviewRequired(sanctions.hasSectoralSanctions())
                .matchReason(reason)
                .sanctionsPrograms(sanctions.getPrograms())
                .build();

            if (blocked) {
                log.warn("Country blocked by sanctions: country={}, reason={}", country, reason);
                meterRegistry.counter("ofac.country.blocked").increment();
            }

            return result;

        } catch (Exception e) {
            log.error("Error screening country: country={}", country, e);
            meterRegistry.counter("ofac.country.screening.errors").increment();

            // FAIL CLOSED
            return OFACScreeningResult.builder()
                .screeningId(generateScreeningId())
                .country(country)
                .blocked(true)
                .matchReason("COUNTRY_SCREENING_ERROR: " + e.getMessage())
                .build();
        }
    }

    /**
     * Screen user on account creation/update
     *
     * @param user User to screen
     * @return Screening result
     */
    @Timed(value = "ofac.screening.user.onboarding")
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public OFACScreeningResult screenUserOnboarding(User user) {
        log.info("Performing OFAC screening for user onboarding: userId={}, name={}",
            user.getId(), user.getFullName());

        OFACScreeningResult result = screenPerson(
            user.getId(),
            user.getFullName(),
            user.getCountry(),
            "USER_ONBOARDING",
            null
        );

        // Store screening result
        ofacRepository.saveUserScreeningResult(user.getId(), result);

        // Audit trail
        auditService.logSecurityEvent(
            "OFAC_USER_SCREENING_COMPLETED",
            Map.of(
                "userId", user.getId(),
                "screeningId", result.getScreeningId(),
                "blocked", result.isBlocked(),
                "matchLevel", result.getHighestMatchLevel()
            ),
            user.getId(),
            "OFAC_COMPLIANCE"
        );

        if (result.isBlocked()) {
            log.error("USER ONBOARDING BLOCKED BY OFAC: userId={}, name={}, reason={}",
                user.getId(), user.getFullName(), result.getMatchReason());

            meterRegistry.counter("ofac.user.onboarding.blocked").increment();

            alertComplianceTeamCritical("OFAC HIT - USER ONBOARDING BLOCKED",
                String.format("User ID: %s, Name: %s, Country: %s, Reason: %s",
                    user.getId(), user.getFullName(), user.getCountry(), result.getMatchReason()));

            throw new ComplianceException(
                "OFAC_USER_BLOCKED",
                "User blocked due to OFAC sanctions screening: " + result.getMatchReason()
            );
        }

        return result;
    }

    // Helper methods

    private OFACScreeningResult aggregateScreeningResults(List<OFACScreeningResult> results,
                                                          String paymentId) {
        // Find highest risk result
        OFACScreeningResult highestRisk = results.stream()
            .max(Comparator.comparingInt(r -> r.isBlocked() ? 100 : r.getHighestMatchScore()))
            .orElseThrow(() -> new IllegalStateException("No screening results"));

        // Aggregate all matches
        List<SDNMatch> allMatches = results.stream()
            .flatMap(r -> r.getSdnMatches().stream())
            .collect(Collectors.toList());

        // Determine if any result blocks
        boolean blocked = results.stream().anyMatch(OFACScreeningResult::isBlocked);

        // Determine if manual review required
        boolean manualReview = results.stream().anyMatch(OFACScreeningResult::requiresManualReview);

        // Aggregate reasons
        String aggregatedReason = results.stream()
            .filter(r -> r.getMatchReason() != null)
            .map(OFACScreeningResult::getMatchReason)
            .collect(Collectors.joining("; "));

        return OFACScreeningResult.builder()
            .screeningId(generateScreeningId())
            .transactionId(paymentId)
            .screeningDate(LocalDateTime.now())
            .sdnMatches(allMatches)
            .highestMatchScore(highestRisk.getHighestMatchScore())
            .highestMatchLevel(highestRisk.getHighestMatchLevel())
            .blocked(blocked)
            .manualReviewRequired(manualReview)
            .matchReason(aggregatedReason)
            .build();
    }

    private OFACScreeningDecision determineDecision(List<SDNMatch> matches, String country) {
        if (matches.isEmpty()) {
            return OFACScreeningDecision.builder()
                .blocked(false)
                .manualReviewRequired(false)
                .matchLevel("CLEAR")
                .reason("No SDN matches found")
                .build();
        }

        SDNMatch highestMatch = matches.get(0);
        int score = highestMatch.getConfidenceScore();

        if (score >= EXACT_MATCH_THRESHOLD) {
            return OFACScreeningDecision.builder()
                .blocked(true)
                .manualReviewRequired(false)
                .matchLevel("EXACT_MATCH")
                .reason(String.format("Exact match to SDN entry: %s (Score: %d)",
                    highestMatch.getSdnName(), score))
                .build();
        }

        if (score >= HIGH_CONFIDENCE_THRESHOLD) {
            return OFACScreeningDecision.builder()
                .blocked(true)
                .manualReviewRequired(false)
                .matchLevel("HIGH_CONFIDENCE")
                .reason(String.format("High confidence match to SDN entry: %s (Score: %d)",
                    highestMatch.getSdnName(), score))
                .build();
        }

        if (score >= MEDIUM_CONFIDENCE_THRESHOLD) {
            return OFACScreeningDecision.builder()
                .blocked(false)
                .manualReviewRequired(true)
                .matchLevel("MEDIUM_CONFIDENCE")
                .reason(String.format("Medium confidence match requires review: %s (Score: %d)",
                    highestMatch.getSdnName(), score))
                .build();
        }

        // LOW_CONFIDENCE_THRESHOLD
        return OFACScreeningDecision.builder()
            .blocked(false)
            .manualReviewRequired(true)
            .matchLevel("LOW_CONFIDENCE")
            .reason(String.format("Low confidence match requires review: %s (Score: %d)",
                highestMatch.getSdnName(), score))
            .build();
    }

    private void queueForManualReview(OFACScreeningResult result) {
        log.info("Queuing for manual review: screeningId={}", result.getScreeningId());

        ManualReviewCase reviewCase = ManualReviewCase.builder()
            .caseId(UUID.randomUUID().toString())
            .screeningId(result.getScreeningId())
            .transactionId(result.getTransactionId())
            .personId(result.getPersonId())
            .personName(result.getPersonName())
            .matchLevel(result.getHighestMatchLevel())
            .matchScore(result.getHighestMatchScore())
            .matchReason(result.getMatchReason())
            .sdnMatches(result.getSdnMatches())
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .priority("HIGH")
            .build();

        ofacRepository.saveManualReviewCase(reviewCase);

        // Alert compliance team
        alertComplianceTeam("OFAC Manual Review Required",
            String.format("Case ID: %s, Person: %s, Match Score: %d, Reason: %s",
                reviewCase.getCaseId(), result.getPersonName(),
                result.getHighestMatchScore(), result.getMatchReason()));
    }

    private Optional<OFACScreeningResult> getCachedScreeningResult(String personId) {
        return ofacRepository.findLatestScreeningResult(personId);
    }

    private void cacheScreeningResult(String personId, OFACScreeningResult result) {
        // Cache valid for 24 hours
        ofacRepository.cacheScreeningResult(personId, result, 24);
    }

    private boolean isStale(OFACScreeningResult result) {
        // Screening result is stale if:
        // 1. Older than 24 hours
        // 2. SDN list version changed
        long hoursSinceScreening = java.time.Duration.between(
            result.getScreeningDate(), LocalDateTime.now()).toHours();

        return hoursSinceScreening > 24 ||
               !sdnListService.getCurrentVersion().equals(result.getSdnListVersion());
    }

    private String generateScreeningId() {
        return "OFAC-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
               "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void alertComplianceTeam(String subject, String message) {
        log.warn("OFAC COMPLIANCE ALERT: {} - {}", subject, message);
        // TODO: Integration with notification service
    }

    private void alertComplianceTeamCritical(String subject, String message) {
        log.error("CRITICAL OFAC ALERT: {} - {}", subject, message);
        // TODO: Integration with PagerDuty/critical alert system
    }
}
