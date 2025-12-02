package com.waqiti.gdpr.service;

import com.waqiti.gdpr.domain.ConsentPurpose;
import com.waqiti.gdpr.domain.ConsentRecord;
import com.waqiti.gdpr.domain.ConsentStatus;
import com.waqiti.gdpr.domain.LawfulBasis;
import com.waqiti.gdpr.dto.*;
import com.waqiti.gdpr.exception.GDPRException;
import com.waqiti.gdpr.repository.ConsentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ConsentManagementService {

    private final ConsentRecordRepository consentRepository;
    private final ConsentVersionService versionService;
    private final NotificationService notificationService;
    
    @Value("${gdpr.consent.default-retention-days:365}")
    private int defaultRetentionDays;
    
    @Value("${gdpr.consent.minor-age-threshold:16}")
    private int minorAgeThreshold;

    public ConsentRecordDTO grantConsent(GrantConsentDTO dto, String userId, HttpServletRequest request) {
        log.info("Granting consent for user: {} purpose: {}", userId, dto.getPurpose());

        // Validate consent request
        validateConsentRequest(dto, userId);

        // Check if user already has active consent for this purpose
        Optional<ConsentRecord> existingConsent = consentRepository
            .findByUserIdAndPurposeAndStatus(userId, dto.getPurpose(), ConsentStatus.GRANTED);

        if (existingConsent.isPresent()) {
            log.info("User already has active consent for purpose: {}", dto.getPurpose());
            return mapToDTO(existingConsent.get());
        }

        // Get current consent version
        ConsentVersion currentVersion = versionService.getCurrentVersion(dto.getPurpose());

        ConsentRecord consent = ConsentRecord.builder()
            .userId(userId)
            .purpose(dto.getPurpose())
            .status(ConsentStatus.GRANTED)
            .consentVersion(currentVersion.getVersion())
            .consentText(currentVersion.getConsentText())
            .ipAddress(getClientIpAddress(request))
            .userAgent(request.getHeader("User-Agent"))
            .collectionMethod(dto.getCollectionMethod())
            .lawfulBasis(determineLawfulBasis(dto.getPurpose()))
            .thirdParties(dto.getThirdParties())
            .dataRetentionDays(dto.getRetentionDays() != null ? dto.getRetentionDays() : defaultRetentionDays)
            .isMinor(dto.getIsMinor())
            .parentalConsentId(dto.getParentalConsentId())
            .build();

        // Set expiration if specified
        if (dto.getExpiresInDays() != null) {
            consent.setExpiresAt(LocalDateTime.now().plusDays(dto.getExpiresInDays()));
        }

        consent = consentRepository.save(consent);

        // Send confirmation
        notificationService.sendConsentConfirmation(userId, consent);

        // Log consent grant event
        logConsentEvent(consent, "CONSENT_GRANTED", "User granted consent");

        return mapToDTO(consent);
    }

    public ConsentRecordDTO withdrawConsent(String userId, ConsentPurpose purpose, String reason) {
        log.info("Withdrawing consent for user: {} purpose: {}", userId, purpose);

        ConsentRecord consent = consentRepository
            .findByUserIdAndPurposeAndStatus(userId, purpose, ConsentStatus.GRANTED)
            .orElseThrow(() -> new GDPRException("No active consent found for purpose: " + purpose));

        // Withdraw consent
        consent.withdraw();
        consent = consentRepository.save(consent);

        // Process withdrawal implications
        processConsentWithdrawal(userId, purpose);

        // Send confirmation
        notificationService.sendWithdrawalConfirmation(userId, consent);

        // Log withdrawal event
        logConsentEvent(consent, "CONSENT_WITHDRAWN", "User withdrew consent. Reason: " + reason);

        return mapToDTO(consent);
    }

    public List<ConsentRecordDTO> getUserConsents(String userId) {
        List<ConsentRecord> consents = consentRepository.findByUserId(userId);
        return consents.stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    public List<ConsentRecordDTO> getActiveUserConsents(String userId) {
        List<ConsentRecord> consents = consentRepository.findByUserIdAndStatus(userId, ConsentStatus.GRANTED);
        return consents.stream()
            .filter(ConsentRecord::isActive)
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    public ConsentStatusDTO getConsentStatus(String userId, ConsentPurpose purpose) {
        Optional<ConsentRecord> consent = consentRepository
            .findByUserIdAndPurposeAndStatus(userId, purpose, ConsentStatus.GRANTED);

        if (consent.isEmpty()) {
            return ConsentStatusDTO.builder()
                .userId(userId)
                .purpose(purpose)
                .hasConsent(false)
                .build();
        }

        ConsentRecord record = consent.get();
        return ConsentStatusDTO.builder()
            .userId(userId)
            .purpose(purpose)
            .hasConsent(record.isActive())
            .consentId(record.getId())
            .grantedAt(record.getGrantedAt())
            .expiresAt(record.getExpiresAt())
            .version(record.getConsentVersion())
            .build();
    }

    public Map<ConsentPurpose, Boolean> getUserConsentMap(String userId) {
        List<ConsentRecord> activeConsents = consentRepository
            .findByUserIdAndStatus(userId, ConsentStatus.GRANTED);

        Map<ConsentPurpose, Boolean> consentMap = new EnumMap<>(ConsentPurpose.class);
        
        // Initialize all purposes as false
        for (ConsentPurpose purpose : ConsentPurpose.values()) {
            consentMap.put(purpose, false);
        }

        // Set active consents to true
        activeConsents.stream()
            .filter(ConsentRecord::isActive)
            .forEach(consent -> consentMap.put(consent.getPurpose(), true));

        return consentMap;
    }

    public void updateConsentPreferences(String userId, UpdateConsentPreferencesDTO dto) {
        log.info("Updating consent preferences for user: {}", userId);

        for (Map.Entry<ConsentPurpose, Boolean> entry : dto.getPreferences().entrySet()) {
            ConsentPurpose purpose = entry.getKey();
            Boolean granted = entry.getValue();

            if (granted) {
                // Grant consent if not already granted
                Optional<ConsentRecord> existing = consentRepository
                    .findByUserIdAndPurposeAndStatus(userId, purpose, ConsentStatus.GRANTED);
                
                if (existing.isEmpty()) {
                    GrantConsentDTO grantDto = GrantConsentDTO.builder()
                        .purpose(purpose)
                        .collectionMethod(dto.getCollectionMethod())
                        .build();
                    grantConsent(grantDto, userId, dto.getRequest());
                }
            } else {
                // Withdraw consent if currently granted
                Optional<ConsentRecord> existing = consentRepository
                    .findByUserIdAndPurposeAndStatus(userId, purpose, ConsentStatus.GRANTED);
                
                if (existing.isPresent()) {
                    withdrawConsent(userId, purpose, "Bulk preference update");
                }
            }
        }

        // Send summary notification
        notificationService.sendPreferencesUpdateConfirmation(userId, dto.getPreferences());
    }

    public ConsentHistoryDTO getUserConsentHistory(String userId) {
        List<ConsentRecord> allConsents = consentRepository.findByUserId(userId);
        
        List<ConsentEventDTO> events = new ArrayList<>();
        
        for (ConsentRecord consent : allConsents) {
            // Grant event
            events.add(ConsentEventDTO.builder()
                .eventType("GRANTED")
                .purpose(consent.getPurpose())
                .timestamp(consent.getGrantedAt())
                .version(consent.getConsentVersion())
                .status(consent.getStatus())
                .build());
            
            // Withdrawal event if applicable
            if (consent.getStatus() == ConsentStatus.WITHDRAWN && consent.getWithdrawnAt() != null) {
                events.add(ConsentEventDTO.builder()
                    .eventType("WITHDRAWN")
                    .purpose(consent.getPurpose())
                    .timestamp(consent.getWithdrawnAt())
                    .version(consent.getConsentVersion())
                    .status(consent.getStatus())
                    .build());
            }
        }

        // Sort events by timestamp
        events.sort(Comparator.comparing(ConsentEventDTO::getTimestamp).reversed());

        return ConsentHistoryDTO.builder()
            .userId(userId)
            .events(events)
            .totalEvents(events.size())
            .build();
    }

    @Transactional(readOnly = true)
    public List<ConsentRecordDTO> getExpiredConsents() {
        List<ConsentRecord> expiredConsents = consentRepository
            .findByStatusAndExpiresAtBefore(ConsentStatus.GRANTED, LocalDateTime.now());
        
        return expiredConsents.stream()
            .map(this::mapToDTO)
            .collect(Collectors.toList());
    }

    public void processExpiredConsents() {
        log.info("Processing expired consents");
        
        List<ConsentRecord> expiredConsents = consentRepository
            .findByStatusAndExpiresAtBefore(ConsentStatus.GRANTED, LocalDateTime.now());
        
        for (ConsentRecord consent : expiredConsents) {
            consent.setStatus(ConsentStatus.EXPIRED);
            consentRepository.save(consent);
            
            // Process expiration implications
            processConsentExpiration(consent.getUserId(), consent.getPurpose());
            
            // Notify user
            notificationService.sendConsentExpirationNotification(consent.getUserId(), consent);
            
            // Log expiration event
            logConsentEvent(consent, "CONSENT_EXPIRED", "Consent expired automatically");
        }
        
        log.info("Processed {} expired consents", expiredConsents.size());
    }

    public ConsentFormDTO getConsentForm(ConsentPurpose purpose, String language) {
        ConsentVersion version = versionService.getCurrentVersion(purpose);
        
        return ConsentFormDTO.builder()
            .purpose(purpose)
            .version(version.getVersion())
            .title(version.getTitle())
            .consentText(version.getConsentText())
            .dataCategories(version.getDataCategories())
            .processingPurposes(version.getProcessingPurposes())
            .thirdParties(version.getThirdParties())
            .retentionPeriod(version.getRetentionPeriod())
            .userRights(version.getUserRights())
            .contactInfo(version.getContactInfo())
            .lastUpdated(version.getEffectiveDate())
            .isRequired(isConsentRequired(purpose))
            .lawfulBasis(determineLawfulBasis(purpose))
            .build();
    }

    // Helper methods

    private void validateConsentRequest(GrantConsentDTO dto, String userId) {
        // Validate purpose
        if (dto.getPurpose() == null) {
            throw new GDPRException("Consent purpose is required");
        }

        // Validate minor consent
        if (Boolean.TRUE.equals(dto.getIsMinor())) {
            if (dto.getParentalConsentId() == null) {
                throw new GDPRException("Parental consent is required for minors");
            }
            // Verify parental consent exists and is valid
            validateParentalConsent(dto.getParentalConsentId());
        }

        // Validate collection method
        if (dto.getCollectionMethod() == null) {
            throw new GDPRException("Collection method is required");
        }
    }

    private void validateParentalConsent(String parentalConsentId) {
        // Implementation to verify parental consent
        // This would check if the parental consent ID is valid and active
    }

    private LawfulBasis determineLawfulBasis(ConsentPurpose purpose) {
        // Determine the appropriate lawful basis for each purpose
        switch (purpose) {
            case ESSENTIAL_SERVICE:
                return LawfulBasis.CONTRACT;
            case MARKETING_EMAILS:
            case PROMOTIONAL_SMS:
            case PUSH_NOTIFICATIONS:
                return LawfulBasis.CONSENT;
            case ANALYTICS:
            case PERSONALIZATION:
                return LawfulBasis.LEGITIMATE_INTERESTS;
            default:
                return LawfulBasis.CONSENT;
        }
    }

    private boolean isConsentRequired(ConsentPurpose purpose) {
        // Some purposes might not require explicit consent
        return purpose != ConsentPurpose.ESSENTIAL_SERVICE;
    }

    private void processConsentWithdrawal(String userId, ConsentPurpose purpose) {
        // Process the implications of consent withdrawal
        log.info("Processing consent withdrawal implications for user: {} purpose: {}", userId, purpose);
        
        switch (purpose) {
            case MARKETING_EMAILS:
                // Unsubscribe from marketing emails
                notificationService.unsubscribeFromMarketing(userId, "email");
                break;
            case PROMOTIONAL_SMS:
                // Unsubscribe from SMS
                notificationService.unsubscribeFromMarketing(userId, "sms");
                break;
            case ANALYTICS:
                // Exclude from analytics
                // This would typically involve updating analytics systems
                break;
            case THIRD_PARTY_SHARING:
                // Notify third parties to stop processing
                notifyThirdPartiesOfWithdrawal(userId);
                break;
            default:
                // Handle other purposes
                break;
        }
    }

    private void processConsentExpiration(String userId, ConsentPurpose purpose) {
        // Similar to withdrawal but for expiration
        processConsentWithdrawal(userId, purpose);
    }

    private void notifyThirdPartiesOfWithdrawal(String userId) {
        // Implementation to notify third parties
        log.info("Notifying third parties of consent withdrawal for user: {}", userId);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private void logConsentEvent(ConsentRecord consent, String eventType, String details) {
        // Implementation to log consent events for audit trail
        log.info("Consent event - Type: {}, User: {}, Purpose: {}, Details: {}", 
            eventType, consent.getUserId(), consent.getPurpose(), details);
    }

    private ConsentRecordDTO mapToDTO(ConsentRecord consent) {
        return ConsentRecordDTO.builder()
            .id(consent.getId())
            .userId(consent.getUserId())
            .purpose(consent.getPurpose())
            .status(consent.getStatus())
            .consentVersion(consent.getConsentVersion())
            .grantedAt(consent.getGrantedAt())
            .withdrawnAt(consent.getWithdrawnAt())
            .expiresAt(consent.getExpiresAt())
            .collectionMethod(consent.getCollectionMethod())
            .consentText(consent.getConsentText())
            .lawfulBasis(consent.getLawfulBasis())
            .thirdParties(consent.getThirdParties())
            .dataRetentionDays(consent.getDataRetentionDays())
            .isActive(consent.isActive())
            .build();
    }
}