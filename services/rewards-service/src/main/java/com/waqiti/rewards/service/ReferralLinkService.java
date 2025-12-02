package com.waqiti.rewards.service;

import com.waqiti.rewards.domain.ReferralLink;
import com.waqiti.rewards.domain.ReferralProgram;
import com.waqiti.rewards.exception.ReferralLinkNotFoundException;
import com.waqiti.rewards.exception.ReferralCodeAlreadyExistsException;
import com.waqiti.rewards.repository.ReferralLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing referral links
 *
 * Handles link generation, tracking, and analytics
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-08
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReferralLinkService {

    private final ReferralLinkRepository linkRepository;
    private final ReferralProgramService programService;
    private final com.waqiti.rewards.publisher.ReferralEventPublisher eventPublisher;

    @Value("${referral.base-url:https://example.com/r/}")
    private String baseUrl;

    @Value("${referral.code.length:8}")
    private int codeLength;

    private static final String CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Creates a new referral link for a user
     */
    @Transactional
    public ReferralLink createLink(UUID userId, String programId, String channel) {
        log.info("Creating referral link: userId={}, programId={}, channel={}",
                userId, programId, channel);

        // Validate program exists and is active
        ReferralProgram program = programService.getProgramByProgramId(programId);
        if (!program.isCurrentlyActive()) {
            throw new IllegalArgumentException("Program is not currently active: " + programId);
        }

        // Generate unique referral code
        String referralCode = generateUniqueReferralCode();

        // Generate link ID
        String linkId = "LNK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Create short URL
        String shortUrl = baseUrl + referralCode;

        // Create full URL with tracking parameters
        String fullUrl = buildFullUrl(referralCode, channel);

        ReferralLink link = ReferralLink.builder()
                .linkId(linkId)
                .userId(userId)
                .program(program)
                .referralCode(referralCode)
                .shortUrl(shortUrl)
                .fullUrl(fullUrl)
                .linkType("WEB")
                .channel(channel)
                .utmSource("referral")
                .utmMedium(channel.toLowerCase())
                .utmCampaign(programId)
                .build();

        ReferralLink savedLink = linkRepository.save(link);

        log.info("Created referral link: linkId={}, code={}", savedLink.getLinkId(), referralCode);

        // Publish link created event
        publishLinkCreatedEvent(savedLink);

        return savedLink;
    }

    /**
     * Publish link created event
     */
    private void publishLinkCreatedEvent(ReferralLink link) {
        try {
            var event = com.waqiti.rewards.event.ReferralLinkCreatedEvent.create(
                    link.getLinkId(),
                    link.getUserId(),
                    link.getProgram().getProgramId(),
                    link.getProgram().getProgramName(),
                    link.getReferralCode(),
                    link.getShortUrl(),
                    link.getChannel(),
                    org.slf4j.MDC.get("correlation_id")
            );

            eventPublisher.publishLinkCreated(event);
        } catch (Exception e) {
            // Don't fail transaction on event publish failure
            log.error("Failed to publish link created event: linkId={}", link.getLinkId(), e);
        }
    }

    /**
     * Gets a link by referral code
     */
    public ReferralLink getLinkByCode(String referralCode) {
        return linkRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new ReferralLinkNotFoundException("Link not found: " + referralCode));
    }

    /**
     * Gets a link by link ID
     */
    public ReferralLink getLinkById(String linkId) {
        return linkRepository.findByLinkId(linkId)
                .orElseThrow(() -> new ReferralLinkNotFoundException("Link not found: " + linkId));
    }

    /**
     * Gets all links for a user
     */
    public List<ReferralLink> getUserLinks(UUID userId) {
        return linkRepository.findByUserId(userId);
    }

    /**
     * Gets active links for a user
     */
    public List<ReferralLink> getActiveUserLinks(UUID userId) {
        return linkRepository.findByUserIdAndIsActiveTrue(userId);
    }

    /**
     * Gets links for a program
     */
    public List<ReferralLink> getProgramLinks(String programId) {
        return linkRepository.findByProgram_ProgramIdAndIsActiveTrue(programId);
    }

    /**
     * Records a click on a referral link
     */
    @Transactional
    public void recordClick(String referralCode, boolean isUniqueClick) {
        log.debug("Recording click: code={}, unique={}", referralCode, isUniqueClick);

        ReferralLink link = getLinkByCode(referralCode);

        if (!link.isValid()) {
            log.warn("Attempted click on invalid link: code={}", referralCode);
            throw new IllegalStateException("Link is not valid: " + referralCode);
        }

        // Update click metrics
        link.recordClick(isUniqueClick);
        linkRepository.save(link);

        log.debug("Recorded click: code={}, totalClicks={}", referralCode, link.getClickCount());
    }

    /**
     * Records a signup from a referral link
     */
    @Transactional
    public void recordSignup(String referralCode) {
        log.debug("Recording signup: code={}", referralCode);

        ReferralLink link = getLinkByCode(referralCode);
        link.recordSignup();
        linkRepository.save(link);

        log.info("Recorded signup: code={}, totalSignups={}", referralCode, link.getSignupCount());
    }

    /**
     * Records a conversion from a referral link
     */
    @Transactional
    public void recordConversion(String referralCode) {
        log.debug("Recording conversion: code={}", referralCode);

        ReferralLink link = getLinkByCode(referralCode);
        link.recordConversion();
        linkRepository.save(link);

        log.info("Recorded conversion: code={}, totalConversions={}", referralCode, link.getConversionCount());
    }

    /**
     * Deactivates a link
     */
    @Transactional
    public void deactivateLink(String linkId) {
        log.info("Deactivating link: {}", linkId);

        ReferralLink link = getLinkById(linkId);
        link.deactivate();
        linkRepository.save(link);

        log.info("Deactivated link: {}", linkId);
    }

    /**
     * Activates a link
     */
    @Transactional
    public void activateLink(String linkId) {
        log.info("Activating link: {}", linkId);

        ReferralLink link = getLinkById(linkId);
        link.activate();
        linkRepository.save(link);

        log.info("Activated link: {}", linkId);
    }

    /**
     * Deactivates expired links (scheduled job)
     */
    @Transactional
    public int deactivateExpiredLinks() {
        log.info("Deactivating expired links");

        List<ReferralLink> expiredLinks = linkRepository.findExpiredActiveLinks(LocalDateTime.now());
        int count = 0;

        for (ReferralLink link : expiredLinks) {
            link.deactivate();
            linkRepository.save(link);
            count++;
        }

        log.info("Deactivated {} expired links", count);
        return count;
    }

    /**
     * Gets total clicks for a user
     */
    public Long getTotalClicksForUser(UUID userId) {
        return linkRepository.getTotalClicksForUser(userId);
    }

    /**
     * Gets total conversions for a user
     */
    public Long getTotalConversionsForUser(UUID userId) {
        return linkRepository.getTotalConversionsForUser(userId);
    }

    /**
     * Gets conversion rate for a user
     */
    public double getConversionRateForUser(UUID userId) {
        Long clicks = getTotalClicksForUser(userId);
        Long conversions = getTotalConversionsForUser(userId);

        if (clicks == 0) {
            return 0.0;
        }

        return (double) conversions / clicks;
    }

    /**
     * Validates a referral code
     */
    public boolean isValidReferralCode(String code) {
        return linkRepository.findByReferralCode(code)
                .map(ReferralLink::isValid)
                .orElse(false);
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    /**
     * Generates a unique referral code
     */
    private String generateUniqueReferralCode() {
        String code;
        int attempts = 0;
        int maxAttempts = 10;

        do {
            code = generateRandomCode();
            attempts++;

            if (attempts >= maxAttempts) {
                throw new IllegalStateException("Failed to generate unique referral code after " + maxAttempts + " attempts");
            }
        } while (linkRepository.existsByReferralCode(code));

        return code;
    }

    /**
     * Generates a random code
     */
    private String generateRandomCode() {
        StringBuilder code = new StringBuilder(codeLength);

        for (int i = 0; i < codeLength; i++) {
            int index = RANDOM.nextInt(CODE_CHARACTERS.length());
            code.append(CODE_CHARACTERS.charAt(index));
        }

        return code.toString();
    }

    /**
     * Builds full URL with tracking parameters
     */
    private String buildFullUrl(String referralCode, String channel) {
        return String.format("%s?utm_source=referral&utm_medium=%s&utm_campaign=referral&ref=%s",
                baseUrl + referralCode,
                channel.toLowerCase(),
                referralCode);
    }
}
