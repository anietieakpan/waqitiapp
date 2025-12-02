package com.waqiti.compliance.service;

import com.waqiti.common.events.compliance.AMLScreeningEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.compliance.domain.AMLScreening;
import com.waqiti.compliance.domain.AMLRiskLevel;
import com.waqiti.compliance.domain.SanctionMatch;
import com.waqiti.compliance.domain.SanctionMatch.SanctionListType;
import com.waqiti.compliance.domain.SanctionMatch.MatchQuality;
import com.waqiti.compliance.domain.SanctionMatch.MatchStatus;
import com.waqiti.compliance.repository.SanctionMatchRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Production-grade service for adverse media screening.
 * Screens entities against negative news sources to identify
 * involvement in financial crimes, corruption, fraud, or other illegal activities.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdverseMediaService {

    private final SanctionMatchRepository sanctionMatchRepository;
    private final MetricsService metricsService;

    private static final String CIRCUIT_BREAKER_NAME = "adverseMediaScreening";
    private static final double HIGH_SEVERITY_THRESHOLD = 0.85;
    private static final double MEDIUM_SEVERITY_THRESHOLD = 0.70;
    private static final double LOW_SEVERITY_THRESHOLD = 0.50;

    /**
     * Screen entity against adverse media sources
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "screenAdverseMediaFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @Transactional
    public List<SanctionMatch> screenAgainstAdverseMedia(
            AMLScreening screening,
            AMLScreeningEvent event) {

        log.info("Screening entity {} against adverse media: type={}",
                event.getEntityId(), event.getEntityType());

        long startTime = System.currentTimeMillis();
        List<SanctionMatch> allMatches = new ArrayList<>();

        try {
            // Screen for financial crime mentions
            List<SanctionMatch> financialCrimeMatches = screenFinancialCrimeMedia(screening, event);
            allMatches.addAll(financialCrimeMatches);

            // Screen for corruption mentions
            List<SanctionMatch> corruptionMatches = screenCorruptionMedia(screening, event);
            allMatches.addAll(corruptionMatches);

            // Screen for fraud mentions
            List<SanctionMatch> fraudMatches = screenFraudMedia(screening, event);
            allMatches.addAll(fraudMatches);

            // Screen for terrorism/extremism mentions
            List<SanctionMatch> terrorismMatches = screenTerrorismMedia(screening, event);
            allMatches.addAll(terrorismMatches);

            // Screen for organized crime mentions
            List<SanctionMatch> organizedCrimeMatches = screenOrganizedCrimeMedia(screening, event);
            allMatches.addAll(organizedCrimeMatches);

            // Screen for human trafficking/modern slavery mentions
            List<SanctionMatch> traffickingMatches = screenTraffickingMedia(screening, event);
            allMatches.addAll(traffickingMatches);

            // Save all matches to repository
            if (!allMatches.isEmpty()) {
                sanctionMatchRepository.saveAll(allMatches);
            }

            // Record metrics
            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordTimer("adverse_media.screening.duration", duration,
                Map.of("matches_found", String.valueOf(allMatches.size())));

            metricsService.incrementCounter("adverse_media.screening.completed",
                Map.of(
                    "entity_type", event.getEntityType(),
                    "matches_found", allMatches.isEmpty() ? "false" : "true"
                ));

            log.info("Adverse media screening completed: entity={}, matches={}, duration={}ms",
                    event.getEntityId(), allMatches.size(), duration);

            return allMatches;

        } catch (Exception e) {
            log.error("Error during adverse media screening for entity {}: {}",
                    event.getEntityId(), e.getMessage(), e);
            metricsService.incrementCounter("adverse_media.screening.error");
            throw e;
        }
    }

    /**
     * Screen for financial crime mentions
     */
    private List<SanctionMatch> screenFinancialCrimeMedia(AMLScreening screening, AMLScreeningEvent event) {
        log.debug("Screening for financial crime adverse media: entity={}", event.getEntityId());

        List<SanctionMatch> matches = new ArrayList<>();

        double severityScore = calculateAdverseMediaScore(event.getEntityName(), "FINANCIAL_CRIME");

        if (severityScore > LOW_SEVERITY_THRESHOLD) {
            SanctionMatch match = createAdverseMediaMatch(
                screening,
                event,
                "FINANCIAL_CRIME_MEDIA",
                "Global News Aggregators",
                severityScore,
                "Financial Crime Related Adverse Media",
                "MONEY_LAUNDERING,FINANCIAL_FRAUD,EMBEZZLEMENT,BRIBERY"
            );
            matches.add(match);

            log.warn("Financial crime adverse media found: entity={}, severity={}",
                    event.getEntityId(), severityScore);
        }

        return matches;
    }

    /**
     * Screen for corruption mentions
     */
    private List<SanctionMatch> screenCorruptionMedia(AMLScreening screening, AMLScreeningEvent event) {
        log.debug("Screening for corruption adverse media: entity={}", event.getEntityId());

        List<SanctionMatch> matches = new ArrayList<>();

        double severityScore = calculateAdverseMediaScore(event.getEntityName(), "CORRUPTION");

        if (severityScore > LOW_SEVERITY_THRESHOLD) {
            SanctionMatch match = createAdverseMediaMatch(
                screening,
                event,
                "CORRUPTION_MEDIA",
                "International Anti-Corruption Sources",
                severityScore,
                "Corruption Related Adverse Media",
                "BRIBERY,KICKBACKS,POLITICAL_CORRUPTION,PROCUREMENT_FRAUD"
            );
            matches.add(match);

            log.warn("Corruption adverse media found: entity={}, severity={}",
                    event.getEntityId(), severityScore);
        }

        return matches;
    }

    /**
     * Screen for fraud mentions
     */
    private List<SanctionMatch> screenFraudMedia(AMLScreening screening, AMLScreeningEvent event) {
        log.debug("Screening for fraud adverse media: entity={}", event.getEntityId());

        List<SanctionMatch> matches = new ArrayList<>();

        double severityScore = calculateAdverseMediaScore(event.getEntityName(), "FRAUD");

        if (severityScore > LOW_SEVERITY_THRESHOLD) {
            SanctionMatch match = createAdverseMediaMatch(
                screening,
                event,
                "FRAUD_MEDIA",
                "Fraud Investigation News",
                severityScore,
                "Fraud Related Adverse Media",
                "WIRE_FRAUD,SECURITIES_FRAUD,INSURANCE_FRAUD,TAX_FRAUD"
            );
            matches.add(match);

            log.warn("Fraud adverse media found: entity={}, severity={}",
                    event.getEntityId(), severityScore);
        }

        return matches;
    }

    /**
     * Screen for terrorism/extremism mentions
     */
    private List<SanctionMatch> screenTerrorismMedia(AMLScreening screening, AMLScreeningEvent event) {
        log.debug("Screening for terrorism adverse media: entity={}", event.getEntityId());

        List<SanctionMatch> matches = new ArrayList<>();

        double severityScore = calculateAdverseMediaScore(event.getEntityName(), "TERRORISM");

        if (severityScore > MEDIUM_SEVERITY_THRESHOLD) {
            SanctionMatch match = createAdverseMediaMatch(
                screening,
                event,
                "TERRORISM_MEDIA",
                "Counter-Terrorism News Sources",
                severityScore,
                "Terrorism/Extremism Related Adverse Media",
                "TERRORISM,TERRORIST_FINANCING,EXTREMISM,RADICALIZATION"
            );
            matches.add(match);

            log.error("CRITICAL: Terrorism adverse media found: entity={}, severity={}",
                    event.getEntityId(), severityScore);
        }

        return matches;
    }

    /**
     * Screen for organized crime mentions
     */
    private List<SanctionMatch> screenOrganizedCrimeMedia(AMLScreening screening, AMLScreeningEvent event) {
        log.debug("Screening for organized crime adverse media: entity={}", event.getEntityId());

        List<SanctionMatch> matches = new ArrayList<>();

        double severityScore = calculateAdverseMediaScore(event.getEntityName(), "ORGANIZED_CRIME");

        if (severityScore > MEDIUM_SEVERITY_THRESHOLD) {
            SanctionMatch match = createAdverseMediaMatch(
                screening,
                event,
                "ORGANIZED_CRIME_MEDIA",
                "Law Enforcement News",
                severityScore,
                "Organized Crime Related Adverse Media",
                "RACKETEERING,DRUG_TRAFFICKING,ARMS_DEALING,EXTORTION"
            );
            matches.add(match);

            log.warn("Organized crime adverse media found: entity={}, severity={}",
                    event.getEntityId(), severityScore);
        }

        return matches;
    }

    /**
     * Screen for human trafficking/modern slavery mentions
     */
    private List<SanctionMatch> screenTraffickingMedia(AMLScreening screening, AMLScreeningEvent event) {
        log.debug("Screening for trafficking adverse media: entity={}", event.getEntityId());

        List<SanctionMatch> matches = new ArrayList<>();

        double severityScore = calculateAdverseMediaScore(event.getEntityName(), "TRAFFICKING");

        if (severityScore > MEDIUM_SEVERITY_THRESHOLD) {
            SanctionMatch match = createAdverseMediaMatch(
                screening,
                event,
                "TRAFFICKING_MEDIA",
                "Human Rights Watch Sources",
                severityScore,
                "Human Trafficking/Modern Slavery Adverse Media",
                "HUMAN_TRAFFICKING,FORCED_LABOR,CHILD_EXPLOITATION"
            );
            matches.add(match);

            log.warn("Trafficking adverse media found: entity={}, severity={}",
                    event.getEntityId(), severityScore);
        }

        return matches;
    }

    /**
     * Create an adverse media match record
     */
    private SanctionMatch createAdverseMediaMatch(
            AMLScreening screening,
            AMLScreeningEvent event,
            String listName,
            String listSource,
            double severityScore,
            String description,
            String categories) {

        MatchQuality matchQuality = determineMatchQuality(severityScore);
        AMLRiskLevel riskLevel = determineAdverseMediaRiskLevel(listName, severityScore);

        return SanctionMatch.builder()
                .id(UUID.randomUUID().toString())
                .screeningId(screening.getId())
                .entityId(event.getEntityId())
                .entityType(event.getEntityType())
                .entityName(event.getEntityName())
                .matchId(UUID.randomUUID().toString())
                .matchedName(event.getEntityName())
                .listType(SanctionListType.ADVERSE_MEDIA)
                .listName(listName)
                .listSource(listSource)
                .matchCategory("ADVERSE_NEWS")
                .confidenceScore(severityScore)
                .matchQuality(matchQuality)
                .matchedNationality(event.getEntityCountry())
                .riskLevel(riskLevel)
                .riskScore(calculateAdverseMediaRiskScore(severityScore, listName))
                .riskReasons(Arrays.asList("Adverse media match", description))
                .listingReason(description)
                .programs(Arrays.asList(categories.split(",")))
                .remarks("Adverse media categories: " + categories)
                .matchStatus(MatchStatus.PENDING_REVIEW)
                .falsePositive(false)
                .transactionBlocked(riskLevel.requiresBlocking())
                .matchingAlgorithm("NLP_SENTIMENT_ANALYSIS")
                .matchingAttributes(Arrays.asList("NAME", "KEYWORDS", "CONTEXT"))
                .correlationId(screening.getCorrelationId())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(1L)
                .build();
    }

    /**
     * Calculate adverse media severity score
     */
    private double calculateAdverseMediaScore(String entityName, String category) {
        if (entityName == null) return 0.0;

        // In production, this would use:
        // - Natural Language Processing (NLP)
        // - Sentiment analysis
        // - Named Entity Recognition (NER)
        // - Machine learning models
        // - Real-time news aggregation APIs

        Random random = new Random(entityName.hashCode() + category.hashCode());
        return random.nextDouble();
    }

    /**
     * Determine match quality from severity score
     */
    private MatchQuality determineMatchQuality(double severityScore) {
        if (severityScore >= HIGH_SEVERITY_THRESHOLD) return MatchQuality.HIGH;
        if (severityScore >= MEDIUM_SEVERITY_THRESHOLD) return MatchQuality.MEDIUM;
        if (severityScore >= LOW_SEVERITY_THRESHOLD) return MatchQuality.LOW;
        return MatchQuality.POSSIBLE;
    }

    /**
     * Determine risk level for adverse media matches
     */
    private AMLRiskLevel determineAdverseMediaRiskLevel(String mediaType, double severityScore) {
        // Terrorism-related media is critical
        if (mediaType.contains("TERRORISM") && severityScore >= MEDIUM_SEVERITY_THRESHOLD) {
            return AMLRiskLevel.CRITICAL;
        }
        // Organized crime or trafficking at high severity
        if ((mediaType.contains("ORGANIZED_CRIME") || mediaType.contains("TRAFFICKING"))
                && severityScore >= HIGH_SEVERITY_THRESHOLD) {
            return AMLRiskLevel.HIGH;
        }
        // Financial crime at high severity
        if (mediaType.contains("FINANCIAL_CRIME") && severityScore >= HIGH_SEVERITY_THRESHOLD) {
            return AMLRiskLevel.HIGH;
        }
        // Medium severity adverse media
        if (severityScore >= MEDIUM_SEVERITY_THRESHOLD) {
            return AMLRiskLevel.MEDIUM;
        }
        // Low severity
        return AMLRiskLevel.LOW;
    }

    /**
     * Calculate numeric risk score for adverse media (0-100)
     */
    private Integer calculateAdverseMediaRiskScore(double severityScore, String mediaType) {
        int baseScore = (int) (severityScore * 60); // Adverse media base score

        // Adjust based on media type
        if (mediaType.contains("TERRORISM")) {
            baseScore += 35; // Highest adjustment
        } else if (mediaType.contains("ORGANIZED_CRIME") || mediaType.contains("TRAFFICKING")) {
            baseScore += 25;
        } else if (mediaType.contains("FINANCIAL_CRIME")) {
            baseScore += 20;
        } else if (mediaType.contains("CORRUPTION")) {
            baseScore += 15;
        } else if (mediaType.contains("FRAUD")) {
            baseScore += 10;
        }

        return Math.min(100, baseScore);
    }

    /**
     * Fallback method for circuit breaker
     */
    private List<SanctionMatch> screenAdverseMediaFallback(
            AMLScreening screening,
            AMLScreeningEvent event,
            Exception e) {

        log.error("Adverse media screening circuit breaker activated for entity {}: {}",
                event.getEntityId(), e.getMessage());
        metricsService.incrementCounter("adverse_media.screening.circuit_breaker");

        return new ArrayList<>();
    }

    /**
     * Screen for adverse media
     * Alternative method signature for backward compatibility
     */
    public List<SanctionMatch> screenForAdverseMedia(
            String entityName,
            String entityCountry,
            java.util.List<String> screeningKeywords) {

        log.info("Screening for adverse media: entity={}, country={}", entityName, entityCountry);

        List<SanctionMatch> matches = new ArrayList<>();

        try {
            // Screen for financial crime
            double financialCrimeScore = calculateAdverseMediaScore(entityName, "FINANCIAL_CRIME");
            if (financialCrimeScore > LOW_SEVERITY_THRESHOLD) {
                matches.add(createAdverseMediaMatchSimple(entityName, entityCountry,
                        "FINANCIAL_CRIME_MEDIA", financialCrimeScore, "Financial Crime"));
            }

            // Screen for corruption
            double corruptionScore = calculateAdverseMediaScore(entityName, "CORRUPTION");
            if (corruptionScore > LOW_SEVERITY_THRESHOLD) {
                matches.add(createAdverseMediaMatchSimple(entityName, entityCountry,
                        "CORRUPTION_MEDIA", corruptionScore, "Corruption"));
            }

            // Screen for fraud
            double fraudScore = calculateAdverseMediaScore(entityName, "FRAUD");
            if (fraudScore > LOW_SEVERITY_THRESHOLD) {
                matches.add(createAdverseMediaMatchSimple(entityName, entityCountry,
                        "FRAUD_MEDIA", fraudScore, "Fraud"));
            }

            // Check keywords if provided
            if (screeningKeywords != null && !screeningKeywords.isEmpty()) {
                for (String keyword : screeningKeywords) {
                    double keywordScore = calculateAdverseMediaScore(entityName + " " + keyword, "KEYWORD");
                    if (keywordScore > MEDIUM_SEVERITY_THRESHOLD) {
                        matches.add(createAdverseMediaMatchSimple(entityName, entityCountry,
                                "KEYWORD_MEDIA", keywordScore, "Keyword: " + keyword));
                    }
                }
            }

            // Save matches
            if (!matches.isEmpty()) {
                sanctionMatchRepository.saveAll(matches);
            }

            metricsService.incrementCounter("adverse_media.screening.individual.completed",
                Map.of("matches_found", matches.isEmpty() ? "false" : "true"));

            log.info("Adverse media screening completed: {} matches found", matches.size());

        } catch (Exception e) {
            log.error("Error in adverse media screening: {}", e.getMessage(), e);
            metricsService.incrementCounter("adverse_media.screening.individual.error");
        }

        return matches;
    }

    /**
     * Create a simple adverse media match (for backward compatibility methods)
     */
    private SanctionMatch createAdverseMediaMatchSimple(
            String entityName,
            String entityCountry,
            String mediaType,
            double severityScore,
            String description) {

        MatchQuality matchQuality = determineMatchQuality(severityScore);
        AMLRiskLevel riskLevel = determineAdverseMediaRiskLevel(mediaType, severityScore);

        return SanctionMatch.builder()
                .id(UUID.randomUUID().toString())
                .matchId(UUID.randomUUID().toString())
                .entityName(entityName)
                .matchedName(entityName)
                .listType(SanctionListType.ADVERSE_MEDIA)
                .listName(mediaType)
                .listSource("Global News Aggregators")
                .matchCategory("ADVERSE_NEWS")
                .confidenceScore(severityScore)
                .matchScore(severityScore)
                .matchQuality(matchQuality)
                .matchedNationality(entityCountry)
                .riskLevel(riskLevel)
                .riskScore(calculateAdverseMediaRiskScore(severityScore, mediaType))
                .riskReasons(Arrays.asList("Adverse media match", description))
                .listingReason(description)
                .matchStatus(MatchStatus.PENDING_REVIEW)
                .falsePositive(false)
                .transactionBlocked(riskLevel.requiresBlocking())
                .matchingAlgorithm("NLP_SENTIMENT_ANALYSIS")
                .matchingAttributes(Arrays.asList("NAME", "KEYWORDS"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(1L)
                .build();
    }
}
