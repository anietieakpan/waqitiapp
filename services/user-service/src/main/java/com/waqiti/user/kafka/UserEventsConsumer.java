package com.waqiti.user.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.EventValidator;
import com.waqiti.user.model.*;
import com.waqiti.user.repository.*;
import com.waqiti.user.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventsConsumer {

    private final ObjectMapper objectMapper;
    private final EventValidator eventValidator;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SecurityContext securityContext;
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final UserActivityRepository userActivityRepository;
    private final UserMetricsRepository userMetricsRepository;
    private final UserComplianceRepository userComplianceRepository;
    
    private final UserService userService;
    private final UserProfileService userProfileService;
    private final UserNotificationService userNotificationService;
    private final UserSecurityService userSecurityService;
    private final UserAnalyticsService userAnalyticsService;
    private final UserComplianceService userComplianceService;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, Long> processingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Integer> eventTypeCounts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "user-events", groupId = "user-service-group")
    @CircuitBreaker(name = "user-events-consumer", fallbackMethod = "fallbackProcessUserEvent")
    @Retry(name = "user-events-consumer")
    @Transactional
    public void processUserEvent(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        long startTime = System.currentTimeMillis();
        String eventId = null;
        String eventType = null;
        String userId = null;

        try {
            log.info("Processing user event from topic: {}, partition: {}, offset: {}", topic, partition, offset);

            JsonNode eventNode = objectMapper.readTree(eventPayload);
            eventId = eventNode.has("eventId") ? eventNode.get("eventId").asText() : UUID.randomUUID().toString();
            eventType = eventNode.has("eventType") ? eventNode.get("eventType").asText() : "UNKNOWN";
            userId = eventNode.has("userId") ? eventNode.get("userId").asText() : null;

            if (!eventValidator.validateEvent(eventNode, "USER_EVENT_SCHEMA")) {
                throw new IllegalArgumentException("Invalid user event structure");
            }

            UserEventContext context = buildEventContext(eventNode, eventId, eventType, userId);
            
            validateUserEvent(context);
            enrichUserEvent(context);
            
            UserEventProcessingResult result = processEventByType(context);
            
            executeAutomatedActions(context, result);
            updateUserMetrics(context, result);
            
            auditService.logUserEvent(eventId, eventType, userId, "SUCCESS", result.getProcessingDetails());
            
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordProcessingTime("user_events_consumer", processingTime);
            metricsService.incrementCounter("user_events_processed", "type", eventType);
            
            processingMetrics.put(eventType, processingTime);
            eventTypeCounts.merge(eventType, 1, Integer::sum);

            acknowledgment.acknowledge();
            log.info("Successfully processed user event: {} of type: {} in {}ms", eventId, eventType, processingTime);

        } catch (Exception e) {
            handleProcessingError(eventId, eventType, userId, eventPayload, e, acknowledgment);
        }
    }

    private UserEventContext buildEventContext(JsonNode eventNode, String eventId, String eventType, String userId) {
        return UserEventContext.builder()
                .eventId(eventId)
                .eventType(eventType)
                .userId(userId)
                .timestamp(eventNode.has("timestamp") ? 
                    Instant.ofEpochMilli(eventNode.get("timestamp").asLong()) : Instant.now())
                .eventData(eventNode)
                .sourceSystem(eventNode.has("sourceSystem") ? eventNode.get("sourceSystem").asText() : "UNKNOWN")
                .userAgent(eventNode.has("userAgent") ? eventNode.get("userAgent").asText() : null)
                .ipAddress(eventNode.has("ipAddress") ? eventNode.get("ipAddress").asText() : null)
                .sessionId(eventNode.has("sessionId") ? eventNode.get("sessionId").asText() : null)
                .deviceId(eventNode.has("deviceId") ? eventNode.get("deviceId").asText() : null)
                .build();
    }

    private void validateUserEvent(UserEventContext context) {
        if (context.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required for user events");
        }

        if (!userRepository.existsById(context.getUserId())) {
            throw new IllegalStateException("User not found: " + context.getUserId());
        }

        validateEventTimestamp(context.getTimestamp());
        validateSourceSystem(context.getSourceSystem());
        validateEventType(context.getEventType());
        
        if (context.getIpAddress() != null) {
            validateIpAddress(context.getIpAddress());
        }
    }

    private void validateEventTimestamp(Instant timestamp) {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minusSeconds(3600);
        Instant fiveMinutesFromNow = now.plusSeconds(300);

        if (timestamp.isBefore(oneHourAgo) || timestamp.isAfter(fiveMinutesFromNow)) {
            throw new IllegalArgumentException("Event timestamp is outside acceptable range: " + timestamp);
        }
    }

    private void validateSourceSystem(String sourceSystem) {
        Set<String> validSources = Set.of("WEB_APP", "MOBILE_APP", "API", "ADMIN_PORTAL", "SYSTEM", "THIRD_PARTY");
        if (!validSources.contains(sourceSystem)) {
            throw new IllegalArgumentException("Invalid source system: " + sourceSystem);
        }
    }

    private void validateEventType(String eventType) {
        Set<String> validTypes = Set.of(
            "USER_REGISTERED", "USER_LOGIN", "USER_LOGOUT", "USER_PROFILE_UPDATED",
            "USER_PREFERENCES_CHANGED", "USER_DEVICE_REGISTERED", "USER_DEVICE_REMOVED",
            "USER_PASSWORD_CHANGED", "USER_EMAIL_VERIFIED", "USER_PHONE_VERIFIED",
            "USER_KYC_INITIATED", "USER_KYC_COMPLETED", "USER_ACCOUNT_LOCKED",
            "USER_ACCOUNT_UNLOCKED", "USER_SUSPENDED", "USER_REACTIVATED",
            "USER_DELETED", "USER_ACTIVITY_DETECTED", "USER_SECURITY_EVENT",
            "USER_COMPLIANCE_CHECK", "USER_RISK_ASSESSMENT", "USER_NOTIFICATION_SENT"
        );
        
        if (!validTypes.contains(eventType)) {
            throw new IllegalArgumentException("Invalid event type: " + eventType);
        }
    }

    private void validateIpAddress(String ipAddress) {
        if (!isValidIpAddress(ipAddress)) {
            throw new IllegalArgumentException("Invalid IP address format: " + ipAddress);
        }
    }

    private boolean isValidIpAddress(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;
            
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void enrichUserEvent(UserEventContext context) {
        User user = userRepository.findById(context.getUserId()).orElse(null);
        if (user != null) {
            context.setUser(user);
            context.setUserTier(user.getTier());
            context.setUserStatus(user.getStatus());
            
            UserProfile profile = userProfileRepository.findByUserId(context.getUserId()).orElse(null);
            if (profile != null) {
                context.setUserProfile(profile);
                context.setCountryCode(profile.getCountryCode());
                context.setTimeZone(profile.getTimeZone());
            }
            
            UserPreferences preferences = userPreferencesRepository.findByUserId(context.getUserId()).orElse(null);
            if (preferences != null) {
                context.setUserPreferences(preferences);
            }
        }

        enrichWithSecurityContext(context);
        enrichWithGeolocation(context);
        enrichWithDeviceInfo(context);
        enrichWithSessionInfo(context);
        enrichWithRiskFactors(context);
    }

    private void enrichWithSecurityContext(UserEventContext context) {
        if (context.getSessionId() != null) {
            UserSession session = userService.getActiveSession(context.getSessionId());
            if (session != null) {
                context.setSessionData(session);
                context.setSessionStartTime(session.getStartTime());
                context.setLastActivityTime(session.getLastActivityTime());
            }
        }

        if (context.getDeviceId() != null) {
            UserDevice device = userService.getUserDevice(context.getDeviceId());
            if (device != null) {
                context.setDeviceInfo(device);
                context.setDeviceTrusted(device.isTrusted());
                context.setDeviceType(device.getDeviceType());
            }
        }
    }

    private void enrichWithGeolocation(UserEventContext context) {
        if (context.getIpAddress() != null) {
            GeolocationData geolocation = userSecurityService.getGeolocation(context.getIpAddress());
            if (geolocation != null) {
                context.setGeolocation(geolocation);
                context.setCountry(geolocation.getCountry());
                context.setCity(geolocation.getCity());
                context.setLatitude(geolocation.getLatitude());
                context.setLongitude(geolocation.getLongitude());
            }
        }
    }

    private void enrichWithDeviceInfo(UserEventContext context) {
        if (context.getUserAgent() != null) {
            DeviceFingerprint fingerprint = userSecurityService.parseUserAgent(context.getUserAgent());
            if (fingerprint != null) {
                context.setDeviceFingerprint(fingerprint);
                context.setBrowser(fingerprint.getBrowser());
                context.setOperatingSystem(fingerprint.getOperatingSystem());
                context.setDevicePlatform(fingerprint.getPlatform());
            }
        }
    }

    private void enrichWithSessionInfo(UserEventContext context) {
        if (context.getSessionId() != null) {
            SessionMetrics metrics = userAnalyticsService.getSessionMetrics(context.getSessionId());
            if (metrics != null) {
                context.setSessionMetrics(metrics);
                context.setSessionDuration(metrics.getDuration());
                context.setPageViews(metrics.getPageViews());
                context.setActionCount(metrics.getActionCount());
            }
        }
    }

    private void enrichWithRiskFactors(UserEventContext context) {
        UserRiskProfile riskProfile = userSecurityService.getUserRiskProfile(context.getUserId());
        if (riskProfile != null) {
            context.setRiskProfile(riskProfile);
            context.setRiskScore(riskProfile.getCurrentScore());
            context.setRiskLevel(riskProfile.getRiskLevel());
            context.setRiskFactors(riskProfile.getActiveRiskFactors());
        }

        if (context.getIpAddress() != null) {
            IpRiskAssessment ipRisk = userSecurityService.assessIpRisk(context.getIpAddress());
            context.setIpRiskAssessment(ipRisk);
        }
    }

    private UserEventProcessingResult processEventByType(UserEventContext context) {
        UserEventProcessingResult.Builder resultBuilder = UserEventProcessingResult.builder()
                .eventId(context.getEventId())
                .eventType(context.getEventType())
                .userId(context.getUserId())
                .processingStartTime(Instant.now());

        try {
            switch (context.getEventType()) {
                case "USER_REGISTERED":
                    return processUserRegistered(context, resultBuilder);
                case "USER_LOGIN":
                    return processUserLogin(context, resultBuilder);
                case "USER_LOGOUT":
                    return processUserLogout(context, resultBuilder);
                case "USER_PROFILE_UPDATED":
                    return processUserProfileUpdated(context, resultBuilder);
                case "USER_PREFERENCES_CHANGED":
                    return processUserPreferencesChanged(context, resultBuilder);
                case "USER_DEVICE_REGISTERED":
                    return processUserDeviceRegistered(context, resultBuilder);
                case "USER_DEVICE_REMOVED":
                    return processUserDeviceRemoved(context, resultBuilder);
                case "USER_PASSWORD_CHANGED":
                    return processUserPasswordChanged(context, resultBuilder);
                case "USER_EMAIL_VERIFIED":
                    return processUserEmailVerified(context, resultBuilder);
                case "USER_PHONE_VERIFIED":
                    return processUserPhoneVerified(context, resultBuilder);
                case "USER_KYC_INITIATED":
                    return processUserKycInitiated(context, resultBuilder);
                case "USER_KYC_COMPLETED":
                    return processUserKycCompleted(context, resultBuilder);
                case "USER_ACCOUNT_LOCKED":
                    return processUserAccountLocked(context, resultBuilder);
                case "USER_ACCOUNT_UNLOCKED":
                    return processUserAccountUnlocked(context, resultBuilder);
                case "USER_SUSPENDED":
                    return processUserSuspended(context, resultBuilder);
                case "USER_REACTIVATED":
                    return processUserReactivated(context, resultBuilder);
                case "USER_DELETED":
                    return processUserDeleted(context, resultBuilder);
                case "USER_ACTIVITY_DETECTED":
                    return processUserActivityDetected(context, resultBuilder);
                case "USER_SECURITY_EVENT":
                    return processUserSecurityEvent(context, resultBuilder);
                case "USER_COMPLIANCE_CHECK":
                    return processUserComplianceCheck(context, resultBuilder);
                case "USER_RISK_ASSESSMENT":
                    return processUserRiskAssessment(context, resultBuilder);
                case "USER_NOTIFICATION_SENT":
                    return processUserNotificationSent(context, resultBuilder);
                default:
                    throw new IllegalArgumentException("Unsupported event type: " + context.getEventType());
            }
        } finally {
            resultBuilder.processingEndTime(Instant.now());
        }
    }

    private UserEventProcessingResult processUserRegistered(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserRegistrationData registrationData = UserRegistrationData.builder()
                .email(eventData.get("email").asText())
                .phoneNumber(eventData.has("phoneNumber") ? eventData.get("phoneNumber").asText() : null)
                .firstName(eventData.has("firstName") ? eventData.get("firstName").asText() : null)
                .lastName(eventData.has("lastName") ? eventData.get("lastName").asText() : null)
                .dateOfBirth(eventData.has("dateOfBirth") ? 
                    LocalDateTime.parse(eventData.get("dateOfBirth").asText()).toLocalDate() : null)
                .countryCode(eventData.has("countryCode") ? eventData.get("countryCode").asText() : null)
                .referralCode(eventData.has("referralCode") ? eventData.get("referralCode").asText() : null)
                .marketingConsent(eventData.has("marketingConsent") ? eventData.get("marketingConsent").asBoolean() : false)
                .termsAccepted(eventData.has("termsAccepted") ? eventData.get("termsAccepted").asBoolean() : true)
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .registrationTimestamp(context.getTimestamp())
                .build();

        UserOnboardingResult onboardingResult = userService.processUserRegistration(registrationData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("REGISTRATION")
                .description("User completed registration process")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "registrationChannel", context.getSourceSystem(),
                    "hasReferral", registrationData.getReferralCode() != null,
                    "country", registrationData.getCountryCode() != null ? registrationData.getCountryCode() : "UNKNOWN"
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "onboardingStatus", onboardingResult.getStatus().toString(),
                    "kycRequired", onboardingResult.isKycRequired(),
                    "accountTier", onboardingResult.getInitialTier().toString(),
                    "welcomeEmailSent", onboardingResult.isWelcomeEmailSent(),
                    "referralProcessed", onboardingResult.isReferralProcessed()
                ))
                .build();
    }

    private UserEventProcessingResult processUserLogin(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserLoginData loginData = UserLoginData.builder()
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .deviceId(context.getDeviceId())
                .loginMethod(eventData.has("loginMethod") ? eventData.get("loginMethod").asText() : "PASSWORD")
                .twoFactorUsed(eventData.has("twoFactorUsed") ? eventData.get("twoFactorUsed").asBoolean() : false)
                .rememberedDevice(eventData.has("rememberedDevice") ? eventData.get("rememberedDevice").asBoolean() : false)
                .loginTimestamp(context.getTimestamp())
                .geolocation(context.getGeolocation())
                .deviceFingerprint(context.getDeviceFingerprint())
                .build();

        UserLoginResult loginResult = userSecurityService.processUserLogin(loginData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("LOGIN")
                .description("User logged in to the system")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "loginMethod", loginData.getLoginMethod(),
                    "twoFactorUsed", loginData.isTwoFactorUsed(),
                    "deviceTrusted", loginResult.isDeviceTrusted(),
                    "riskScore", loginResult.getRiskScore().toString(),
                    "country", context.getCountry() != null ? context.getCountry() : "UNKNOWN"
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "sessionCreated", loginResult.isSessionCreated(),
                    "deviceTrusted", loginResult.isDeviceTrusted(),
                    "riskScore", loginResult.getRiskScore().toString(),
                    "additionalAuthRequired", loginResult.isAdditionalAuthRequired(),
                    "securityAlertsTriggered", loginResult.getSecurityAlerts().size()
                ))
                .build();
    }

    private UserEventProcessingResult processUserLogout(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserLogoutData logoutData = UserLogoutData.builder()
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .logoutReason(eventData.has("logoutReason") ? eventData.get("logoutReason").asText() : "USER_INITIATED")
                .logoutTimestamp(context.getTimestamp())
                .sessionDuration(context.getSessionDuration())
                .ipAddress(context.getIpAddress())
                .build();

        UserLogoutResult logoutResult = userSecurityService.processUserLogout(logoutData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("LOGOUT")
                .description("User logged out of the system")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "logoutReason", logoutData.getLogoutReason(),
                    "sessionDuration", logoutData.getSessionDuration() != null ? logoutData.getSessionDuration().toString() : "UNKNOWN",
                    "gracefulLogout", logoutResult.isGracefulLogout()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "sessionTerminated", logoutResult.isSessionTerminated(),
                    "gracefulLogout", logoutResult.isGracefulLogout(),
                    "sessionMetricsRecorded", logoutResult.isSessionMetricsRecorded(),
                    "securityTokensInvalidated", logoutResult.isSecurityTokensInvalidated()
                ))
                .build();
    }

    private UserEventProcessingResult processUserProfileUpdated(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        Set<String> updatedFields = new HashSet<>();
        JsonNode changesNode = eventData.get("changes");
        if (changesNode != null && changesNode.isArray()) {
            changesNode.forEach(change -> updatedFields.add(change.asText()));
        }

        ProfileUpdateData updateData = ProfileUpdateData.builder()
                .userId(context.getUserId())
                .updatedFields(updatedFields)
                .updateTimestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .build();

        ProfileUpdateResult updateResult = userProfileService.processProfileUpdate(updateData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("PROFILE_UPDATE")
                .description("User updated profile information")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "updatedFields", String.join(",", updatedFields),
                    "requiresVerification", updateResult.isRequiresVerification(),
                    "kycImpacted", updateResult.isKycImpacted()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "fieldsUpdated", updatedFields.size(),
                    "requiresVerification", updateResult.isRequiresVerification(),
                    "kycImpacted", updateResult.isKycImpacted(),
                    "notificationsSent", updateResult.getNotificationsSent().size(),
                    "complianceCheckTriggered", updateResult.isComplianceCheckTriggered()
                ))
                .build();
    }

    private UserEventProcessingResult processUserPreferencesChanged(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        Map<String, Object> preferences = new HashMap<>();
        if (eventData.has("preferences")) {
            JsonNode prefsNode = eventData.get("preferences");
            prefsNode.fieldNames().forEachRemaining(fieldName -> {
                preferences.put(fieldName, prefsNode.get(fieldName).asText());
            });
        }

        PreferencesUpdateData updateData = PreferencesUpdateData.builder()
                .userId(context.getUserId())
                .preferences(preferences)
                .updateTimestamp(context.getTimestamp())
                .sessionId(context.getSessionId())
                .build();

        PreferencesUpdateResult updateResult = userService.updateUserPreferences(updateData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("PREFERENCES_CHANGE")
                .description("User updated account preferences")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "preferencesUpdated", preferences.keySet().size(),
                    "notificationSettingsChanged", updateResult.isNotificationSettingsChanged(),
                    "privacySettingsChanged", updateResult.isPrivacySettingsChanged()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "preferencesUpdated", preferences.keySet().size(),
                    "notificationSettingsChanged", updateResult.isNotificationSettingsChanged(),
                    "privacySettingsChanged", updateResult.isPrivacySettingsChanged(),
                    "marketingConsentChanged", updateResult.isMarketingConsentChanged()
                ))
                .build();
    }

    private UserEventProcessingResult processUserDeviceRegistered(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        DeviceRegistrationData deviceData = DeviceRegistrationData.builder()
                .userId(context.getUserId())
                .deviceId(context.getDeviceId())
                .deviceName(eventData.has("deviceName") ? eventData.get("deviceName").asText() : "Unknown Device")
                .deviceType(eventData.has("deviceType") ? eventData.get("deviceType").asText() : "UNKNOWN")
                .operatingSystem(context.getOperatingSystem())
                .browser(context.getBrowser())
                .userAgent(context.getUserAgent())
                .ipAddress(context.getIpAddress())
                .registrationTimestamp(context.getTimestamp())
                .deviceFingerprint(context.getDeviceFingerprint())
                .build();

        DeviceRegistrationResult registrationResult = userSecurityService.registerUserDevice(deviceData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("DEVICE_REGISTRATION")
                .description("User registered a new device")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "deviceType", deviceData.getDeviceType(),
                    "deviceTrusted", registrationResult.isDeviceTrusted(),
                    "requiresVerification", registrationResult.isRequiresVerification()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "deviceRegistered", registrationResult.isDeviceRegistered(),
                    "deviceTrusted", registrationResult.isDeviceTrusted(),
                    "requiresVerification", registrationResult.isRequiresVerification(),
                    "securityScore", registrationResult.getSecurityScore().toString(),
                    "verificationEmailSent", registrationResult.isVerificationEmailSent()
                ))
                .build();
    }

    private UserEventProcessingResult processUserDeviceRemoved(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        String removedDeviceId = eventData.has("removedDeviceId") ? eventData.get("removedDeviceId").asText() : context.getDeviceId();
        String removalReason = eventData.has("removalReason") ? eventData.get("removalReason").asText() : "USER_INITIATED";

        DeviceRemovalData removalData = DeviceRemovalData.builder()
                .userId(context.getUserId())
                .deviceId(removedDeviceId)
                .removalReason(removalReason)
                .removalTimestamp(context.getTimestamp())
                .sessionId(context.getSessionId())
                .ipAddress(context.getIpAddress())
                .build();

        DeviceRemovalResult removalResult = userSecurityService.removeUserDevice(removalData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("DEVICE_REMOVAL")
                .description("User removed a device from their account")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "removedDeviceId", removedDeviceId,
                    "removalReason", removalReason,
                    "sessionsTerminated", removalResult.getSessionsTerminated()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "deviceRemoved", removalResult.isDeviceRemoved(),
                    "sessionsTerminated", removalResult.getSessionsTerminated(),
                    "securityTokensInvalidated", removalResult.isSecurityTokensInvalidated(),
                    "notificationSent", removalResult.isNotificationSent()
                ))
                .build();
    }

    private UserEventProcessingResult processUserPasswordChanged(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        PasswordChangeData changeData = PasswordChangeData.builder()
                .userId(context.getUserId())
                .changeReason(eventData.has("changeReason") ? eventData.get("changeReason").asText() : "USER_INITIATED")
                .changeTimestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .passwordStrengthScore(eventData.has("passwordStrengthScore") ? eventData.get("passwordStrengthScore").asInt() : null)
                .build();

        PasswordChangeResult changeResult = userSecurityService.processPasswordChange(changeData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("PASSWORD_CHANGE")
                .description("User changed their password")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "changeReason", changeData.getChangeReason(),
                    "strengthScore", changeData.getPasswordStrengthScore() != null ? changeData.getPasswordStrengthScore().toString() : "UNKNOWN",
                    "sessionsTerminated", changeResult.getOtherSessionsTerminated()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "passwordChanged", changeResult.isPasswordChanged(),
                    "otherSessionsTerminated", changeResult.getOtherSessionsTerminated(),
                    "securityNotificationSent", changeResult.isSecurityNotificationSent(),
                    "strengthImproved", changeResult.isStrengthImproved(),
                    "complianceUpdated", changeResult.isComplianceUpdated()
                ))
                .build();
    }

    private UserEventProcessingResult processUserEmailVerified(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        EmailVerificationData verificationData = EmailVerificationData.builder()
                .userId(context.getUserId())
                .email(eventData.has("email") ? eventData.get("email").asText() : null)
                .verificationToken(eventData.has("verificationToken") ? eventData.get("verificationToken").asText() : null)
                .verificationTimestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .build();

        EmailVerificationResult verificationResult = userService.processEmailVerification(verificationData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("EMAIL_VERIFICATION")
                .description("User verified their email address")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .metadata(Map.of(
                    "email", verificationData.getEmail() != null ? verificationData.getEmail() : "UNKNOWN",
                    "accountUpgraded", verificationResult.isAccountUpgraded(),
                    "kycProgressUpdated", verificationResult.isKycProgressUpdated()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "emailVerified", verificationResult.isEmailVerified(),
                    "accountUpgraded", verificationResult.isAccountUpgraded(),
                    "kycProgressUpdated", verificationResult.isKycProgressUpdated(),
                    "welcomeEmailSent", verificationResult.isWelcomeEmailSent(),
                    "featuresUnlocked", verificationResult.getFeaturesUnlocked().size()
                ))
                .build();
    }

    private UserEventProcessingResult processUserPhoneVerified(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        PhoneVerificationData verificationData = PhoneVerificationData.builder()
                .userId(context.getUserId())
                .phoneNumber(eventData.has("phoneNumber") ? eventData.get("phoneNumber").asText() : null)
                .verificationCode(eventData.has("verificationCode") ? eventData.get("verificationCode").asText() : null)
                .verificationTimestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .build();

        PhoneVerificationResult verificationResult = userService.processPhoneVerification(verificationData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("PHONE_VERIFICATION")
                .description("User verified their phone number")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .metadata(Map.of(
                    "phoneNumber", verificationData.getPhoneNumber() != null ? 
                        maskPhoneNumber(verificationData.getPhoneNumber()) : "UNKNOWN",
                    "accountUpgraded", verificationResult.isAccountUpgraded(),
                    "twoFactorEnabled", verificationResult.isTwoFactorEnabled()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "phoneVerified", verificationResult.isPhoneVerified(),
                    "accountUpgraded", verificationResult.isAccountUpgraded(),
                    "twoFactorEnabled", verificationResult.isTwoFactorEnabled(),
                    "kycProgressUpdated", verificationResult.isKycProgressUpdated(),
                    "securityScoreImproved", verificationResult.isSecurityScoreImproved()
                ))
                .build();
    }

    private UserEventProcessingResult processUserKycInitiated(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        KycInitiationData kycData = KycInitiationData.builder()
                .userId(context.getUserId())
                .kycLevel(eventData.has("kycLevel") ? eventData.get("kycLevel").asText() : "LEVEL_1")
                .initiationTimestamp(context.getTimestamp())
                .documentTypes(eventData.has("documentTypes") ? 
                    parseStringArray(eventData.get("documentTypes")) : List.of())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .build();

        KycInitiationResult kycResult = userComplianceService.initiateKyc(kycData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("KYC_INITIATION")
                .description("User initiated KYC verification process")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "kycLevel", kycData.getKycLevel(),
                    "documentTypesRequired", kycData.getDocumentTypes().size(),
                    "kycProvider", kycResult.getKycProvider()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "kycInitiated", kycResult.isKycInitiated(),
                    "kycProvider", kycResult.getKycProvider(),
                    "documentsRequired", kycResult.getDocumentsRequired().size(),
                    "estimatedCompletionTime", kycResult.getEstimatedCompletionTime().toString(),
                    "complianceRecordCreated", kycResult.isComplianceRecordCreated()
                ))
                .build();
    }

    private UserEventProcessingResult processUserKycCompleted(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        KycCompletionData kycData = KycCompletionData.builder()
                .userId(context.getUserId())
                .kycLevel(eventData.has("kycLevel") ? eventData.get("kycLevel").asText() : "LEVEL_1")
                .kycStatus(eventData.has("kycStatus") ? eventData.get("kycStatus").asText() : "COMPLETED")
                .kycProvider(eventData.has("kycProvider") ? eventData.get("kycProvider").asText() : "INTERNAL")
                .completionTimestamp(context.getTimestamp())
                .verificationScore(eventData.has("verificationScore") ? eventData.get("verificationScore").asDouble() : null)
                .documentsVerified(eventData.has("documentsVerified") ? 
                    parseStringArray(eventData.get("documentsVerified")) : List.of())
                .build();

        KycCompletionResult kycResult = userComplianceService.completeKyc(kycData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("KYC_COMPLETION")
                .description("User completed KYC verification process")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .metadata(Map.of(
                    "kycLevel", kycData.getKycLevel(),
                    "kycStatus", kycData.getKycStatus(),
                    "verificationScore", kycData.getVerificationScore() != null ? kycData.getVerificationScore().toString() : "UNKNOWN",
                    "accountUpgraded", kycResult.isAccountUpgraded()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "kycCompleted", kycResult.isKycCompleted(),
                    "accountUpgraded", kycResult.isAccountUpgraded(),
                    "newTier", kycResult.getNewTier().toString(),
                    "limitsIncreased", kycResult.isLimitsIncreased(),
                    "featuresUnlocked", kycResult.getFeaturesUnlocked().size(),
                    "complianceScoreUpdated", kycResult.isComplianceScoreUpdated()
                ))
                .build();
    }

    private UserEventProcessingResult processUserAccountLocked(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        AccountLockData lockData = AccountLockData.builder()
                .userId(context.getUserId())
                .lockReason(eventData.has("lockReason") ? eventData.get("lockReason").asText() : "SECURITY_VIOLATION")
                .lockDuration(eventData.has("lockDurationMinutes") ? eventData.get("lockDurationMinutes").asLong() : null)
                .lockTimestamp(context.getTimestamp())
                .triggeredBy(eventData.has("triggeredBy") ? eventData.get("triggeredBy").asText() : "SYSTEM")
                .securityIncidentId(eventData.has("securityIncidentId") ? eventData.get("securityIncidentId").asText() : null)
                .build();

        AccountLockResult lockResult = userSecurityService.lockUserAccount(lockData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("ACCOUNT_LOCK")
                .description("User account was locked")
                .timestamp(context.getTimestamp())
                .metadata(Map.of(
                    "lockReason", lockData.getLockReason(),
                    "triggeredBy", lockData.getTriggeredBy(),
                    "lockDuration", lockData.getLockDuration() != null ? lockData.getLockDuration().toString() : "INDEFINITE",
                    "sessionsTerminated", lockResult.getSessionsTerminated()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "accountLocked", lockResult.isAccountLocked(),
                    "sessionsTerminated", lockResult.getSessionsTerminated(),
                    "securityNotificationSent", lockResult.isSecurityNotificationSent(),
                    "complianceRecordUpdated", lockResult.isComplianceRecordUpdated(),
                    "unlockInstructions", lockResult.getUnlockInstructions()
                ))
                .build();
    }

    private UserEventProcessingResult processUserAccountUnlocked(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        AccountUnlockData unlockData = AccountUnlockData.builder()
                .userId(context.getUserId())
                .unlockReason(eventData.has("unlockReason") ? eventData.get("unlockReason").asText() : "ADMIN_APPROVAL")
                .unlockTimestamp(context.getTimestamp())
                .unlockedBy(eventData.has("unlockedBy") ? eventData.get("unlockedBy").asText() : "SYSTEM")
                .verificationMethod(eventData.has("verificationMethod") ? eventData.get("verificationMethod").asText() : "MANUAL")
                .build();

        AccountUnlockResult unlockResult = userSecurityService.unlockUserAccount(unlockData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("ACCOUNT_UNLOCK")
                .description("User account was unlocked")
                .timestamp(context.getTimestamp())
                .metadata(Map.of(
                    "unlockReason", unlockData.getUnlockReason(),
                    "unlockedBy", unlockData.getUnlockedBy(),
                    "verificationMethod", unlockData.getVerificationMethod(),
                    "accountRestored", unlockResult.isAccountRestored()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "accountUnlocked", unlockResult.isAccountUnlocked(),
                    "accountRestored", unlockResult.isAccountRestored(),
                    "securityScoreReset", unlockResult.isSecurityScoreReset(),
                    "notificationSent", unlockResult.isNotificationSent(),
                    "accessRestored", unlockResult.isAccessRestored()
                ))
                .build();
    }

    private UserEventProcessingResult processUserSuspended(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserSuspensionData suspensionData = UserSuspensionData.builder()
                .userId(context.getUserId())
                .suspensionReason(eventData.has("suspensionReason") ? eventData.get("suspensionReason").asText() : "COMPLIANCE_VIOLATION")
                .suspensionType(eventData.has("suspensionType") ? eventData.get("suspensionType").asText() : "TEMPORARY")
                .suspensionDuration(eventData.has("suspensionDurationDays") ? eventData.get("suspensionDurationDays").asLong() : null)
                .suspensionTimestamp(context.getTimestamp())
                .suspendedBy(eventData.has("suspendedBy") ? eventData.get("suspendedBy").asText() : "SYSTEM")
                .complianceIncidentId(eventData.has("complianceIncidentId") ? eventData.get("complianceIncidentId").asText() : null)
                .build();

        UserSuspensionResult suspensionResult = userComplianceService.suspendUser(suspensionData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("USER_SUSPENSION")
                .description("User account was suspended")
                .timestamp(context.getTimestamp())
                .metadata(Map.of(
                    "suspensionReason", suspensionData.getSuspensionReason(),
                    "suspensionType", suspensionData.getSuspensionType(),
                    "suspendedBy", suspensionData.getSuspendedBy(),
                    "duration", suspensionData.getSuspensionDuration() != null ? suspensionData.getSuspensionDuration().toString() : "INDEFINITE"
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "userSuspended", suspensionResult.isUserSuspended(),
                    "transactionsFrozen", suspensionResult.isTransactionsFrozen(),
                    "fundsFrozen", suspensionResult.isFundsFrozen(),
                    "sessionsTerminated", suspensionResult.getSessionsTerminated(),
                    "legalNotificationSent", suspensionResult.isLegalNotificationSent(),
                    "complianceRecordUpdated", suspensionResult.isComplianceRecordUpdated()
                ))
                .build();
    }

    private UserEventProcessingResult processUserReactivated(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserReactivationData reactivationData = UserReactivationData.builder()
                .userId(context.getUserId())
                .reactivationReason(eventData.has("reactivationReason") ? eventData.get("reactivationReason").asText() : "COMPLIANCE_CLEARED")
                .reactivationTimestamp(context.getTimestamp())
                .reactivatedBy(eventData.has("reactivatedBy") ? eventData.get("reactivatedBy").asText() : "SYSTEM")
                .verificationMethod(eventData.has("verificationMethod") ? eventData.get("verificationMethod").asText() : "MANUAL_REVIEW")
                .complianceClearance(eventData.has("complianceClearance") ? eventData.get("complianceClearance").asBoolean() : true)
                .build();

        UserReactivationResult reactivationResult = userComplianceService.reactivateUser(reactivationData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("USER_REACTIVATION")
                .description("User account was reactivated")
                .timestamp(context.getTimestamp())
                .metadata(Map.of(
                    "reactivationReason", reactivationData.getReactivationReason(),
                    "reactivatedBy", reactivationData.getReactivatedBy(),
                    "verificationMethod", reactivationData.getVerificationMethod(),
                    "accessRestored", reactivationResult.isAccessRestored()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "userReactivated", reactivationResult.isUserReactivated(),
                    "accessRestored", reactivationResult.isAccessRestored(),
                    "transactionsUnfrozen", reactivationResult.isTransactionsUnfrozen(),
                    "fundsUnfrozen", reactivationResult.isFundsUnfrozen(),
                    "welcomeBackNotificationSent", reactivationResult.isWelcomeBackNotificationSent(),
                    "securityReview", reactivationResult.isSecurityReviewRequired()
                ))
                .build();
    }

    private UserEventProcessingResult processUserDeleted(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserDeletionData deletionData = UserDeletionData.builder()
                .userId(context.getUserId())
                .deletionReason(eventData.has("deletionReason") ? eventData.get("deletionReason").asText() : "USER_REQUEST")
                .deletionType(eventData.has("deletionType") ? eventData.get("deletionType").asText() : "SOFT_DELETE")
                .deletionTimestamp(context.getTimestamp())
                .deletedBy(eventData.has("deletedBy") ? eventData.get("deletedBy").asText() : "USER")
                .dataRetentionPeriod(eventData.has("dataRetentionDays") ? eventData.get("dataRetentionDays").asLong() : 365L)
                .gdprRequest(eventData.has("gdprRequest") ? eventData.get("gdprRequest").asBoolean() : false)
                .build();

        UserDeletionResult deletionResult = userService.deleteUser(deletionData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("USER_DELETION")
                .description("User account was deleted")
                .timestamp(context.getTimestamp())
                .metadata(Map.of(
                    "deletionReason", deletionData.getDeletionReason(),
                    "deletionType", deletionData.getDeletionType(),
                    "deletedBy", deletionData.getDeletedBy(),
                    "gdprRequest", deletionData.isGdprRequest()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "userDeleted", deletionResult.isUserDeleted(),
                    "dataAnonymized", deletionResult.isDataAnonymized(),
                    "sessionsTerminated", deletionResult.getSessionsTerminated(),
                    "subscriptionsCancelled", deletionResult.getSubscriptionsCancelled(),
                    "complianceRecordRetained", deletionResult.isComplianceRecordRetained(),
                    "backupScheduled", deletionResult.isBackupScheduled()
                ))
                .build();
    }

    private UserEventProcessingResult processUserActivityDetected(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserActivityData activityData = UserActivityData.builder()
                .userId(context.getUserId())
                .activityType(eventData.has("activityType") ? eventData.get("activityType").asText() : "GENERAL")
                .activityDescription(eventData.has("activityDescription") ? eventData.get("activityDescription").asText() : "User activity detected")
                .activityTimestamp(context.getTimestamp())
                .sessionId(context.getSessionId())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .activityMetadata(parseMetadata(eventData.get("metadata")))
                .build();

        UserActivityResult activityResult = userAnalyticsService.recordUserActivity(activityData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("ACTIVITY_DETECTED")
                .description(activityData.getActivityDescription())
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(activityData.getActivityMetadata())
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "activityRecorded", activityResult.isActivityRecorded(),
                    "patternsUpdated", activityResult.isPatternsUpdated(),
                    "anomaliesDetected", activityResult.getAnomaliesDetected().size(),
                    "behaviorScoreUpdated", activityResult.isBehaviorScoreUpdated(),
                    "sessionMetricsUpdated", activityResult.isSessionMetricsUpdated()
                ))
                .build();
    }

    private UserEventProcessingResult processUserSecurityEvent(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserSecurityEventData securityData = UserSecurityEventData.builder()
                .userId(context.getUserId())
                .securityEventType(eventData.has("securityEventType") ? eventData.get("securityEventType").asText() : "UNKNOWN")
                .severity(eventData.has("severity") ? eventData.get("severity").asText() : "MEDIUM")
                .threatLevel(eventData.has("threatLevel") ? eventData.get("threatLevel").asText() : "UNKNOWN")
                .eventTimestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .eventDetails(parseMetadata(eventData.get("eventDetails")))
                .build();

        UserSecurityEventResult securityResult = userSecurityService.processSecurityEvent(securityData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("SECURITY_EVENT")
                .description("Security event detected for user")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .userAgent(context.getUserAgent())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "securityEventType", securityData.getSecurityEventType(),
                    "severity", securityData.getSeverity(),
                    "threatLevel", securityData.getThreatLevel(),
                    "actionTaken", securityResult.getActionTaken()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "securityEventProcessed", securityResult.isSecurityEventProcessed(),
                    "actionTaken", securityResult.getActionTaken(),
                    "riskScoreUpdated", securityResult.isRiskScoreUpdated(),
                    "alertsSent", securityResult.getAlertsSent().size(),
                    "investigationTriggered", securityResult.isInvestigationTriggered(),
                    "accountActionRequired", securityResult.isAccountActionRequired()
                ))
                .build();
    }

    private UserEventProcessingResult processUserComplianceCheck(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserComplianceCheckData complianceData = UserComplianceCheckData.builder()
                .userId(context.getUserId())
                .checkType(eventData.has("checkType") ? eventData.get("checkType").asText() : "ROUTINE")
                .checkReason(eventData.has("checkReason") ? eventData.get("checkReason").asText() : "PERIODIC_REVIEW")
                .checkTimestamp(context.getTimestamp())
                .triggeredBy(eventData.has("triggeredBy") ? eventData.get("triggeredBy").asText() : "SYSTEM")
                .complianceFlags(eventData.has("complianceFlags") ? 
                    parseStringArray(eventData.get("complianceFlags")) : List.of())
                .build();

        UserComplianceCheckResult complianceResult = userComplianceService.performComplianceCheck(complianceData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("COMPLIANCE_CHECK")
                .description("Compliance check performed for user")
                .timestamp(context.getTimestamp())
                .metadata(Map.of(
                    "checkType", complianceData.getCheckType(),
                    "checkReason", complianceData.getCheckReason(),
                    "triggeredBy", complianceData.getTriggeredBy(),
                    "complianceScore", complianceResult.getComplianceScore().toString(),
                    "flagsRaised", complianceResult.getFlagsRaised().size()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "complianceCheckCompleted", complianceResult.isComplianceCheckCompleted(),
                    "complianceScore", complianceResult.getComplianceScore().toString(),
                    "flagsRaised", complianceResult.getFlagsRaised().size(),
                    "actionRequired", complianceResult.isActionRequired(),
                    "reportGenerated", complianceResult.isReportGenerated(),
                    "regulatoryNotificationSent", complianceResult.isRegulatoryNotificationSent()
                ))
                .build();
    }

    private UserEventProcessingResult processUserRiskAssessment(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserRiskAssessmentData riskData = UserRiskAssessmentData.builder()
                .userId(context.getUserId())
                .assessmentType(eventData.has("assessmentType") ? eventData.get("assessmentType").asText() : "COMPREHENSIVE")
                .assessmentReason(eventData.has("assessmentReason") ? eventData.get("assessmentReason").asText() : "PERIODIC_REVIEW")
                .assessmentTimestamp(context.getTimestamp())
                .triggeredBy(eventData.has("triggeredBy") ? eventData.get("triggeredBy").asText() : "SYSTEM")
                .riskFactors(eventData.has("riskFactors") ? 
                    parseStringArray(eventData.get("riskFactors")) : List.of())
                .ipAddress(context.getIpAddress())
                .sessionId(context.getSessionId())
                .build();

        UserRiskAssessmentResult riskResult = userSecurityService.performRiskAssessment(riskData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("RISK_ASSESSMENT")
                .description("Risk assessment performed for user")
                .timestamp(context.getTimestamp())
                .ipAddress(context.getIpAddress())
                .sessionId(context.getSessionId())
                .metadata(Map.of(
                    "assessmentType", riskData.getAssessmentType(),
                    "assessmentReason", riskData.getAssessmentReason(),
                    "triggeredBy", riskData.getTriggeredBy(),
                    "riskScore", riskResult.getRiskScore().toString(),
                    "riskLevel", riskResult.getRiskLevel()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "riskAssessmentCompleted", riskResult.isRiskAssessmentCompleted(),
                    "riskScore", riskResult.getRiskScore().toString(),
                    "riskLevel", riskResult.getRiskLevel(),
                    "mitigationActions", riskResult.getMitigationActions().size(),
                    "monitoringEnhanced", riskResult.isMonitoringEnhanced(),
                    "securityMeasuresUpdated", riskResult.isSecurityMeasuresUpdated()
                ))
                .build();
    }

    private UserEventProcessingResult processUserNotificationSent(UserEventContext context, UserEventProcessingResult.Builder resultBuilder) {
        JsonNode eventData = context.getEventData();
        
        UserNotificationData notificationData = UserNotificationData.builder()
                .userId(context.getUserId())
                .notificationType(eventData.has("notificationType") ? eventData.get("notificationType").asText() : "GENERAL")
                .notificationChannel(eventData.has("notificationChannel") ? eventData.get("notificationChannel").asText() : "EMAIL")
                .notificationTimestamp(context.getTimestamp())
                .messageId(eventData.has("messageId") ? eventData.get("messageId").asText() : UUID.randomUUID().toString())
                .priority(eventData.has("priority") ? eventData.get("priority").asText() : "NORMAL")
                .deliveryStatus(eventData.has("deliveryStatus") ? eventData.get("deliveryStatus").asText() : "SENT")
                .build();

        UserNotificationResult notificationResult = userNotificationService.processNotification(notificationData);
        
        UserActivity activity = UserActivity.builder()
                .userId(context.getUserId())
                .activityType("NOTIFICATION_SENT")
                .description("Notification sent to user")
                .timestamp(context.getTimestamp())
                .metadata(Map.of(
                    "notificationType", notificationData.getNotificationType(),
                    "notificationChannel", notificationData.getNotificationChannel(),
                    "messageId", notificationData.getMessageId(),
                    "priority", notificationData.getPriority(),
                    "deliveryStatus", notificationData.getDeliveryStatus()
                ))
                .build();
        
        userActivityRepository.save(activity);

        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "notificationProcessed", notificationResult.isNotificationProcessed(),
                    "deliveryConfirmed", notificationResult.isDeliveryConfirmed(),
                    "readReceiptEnabled", notificationResult.isReadReceiptEnabled(),
                    "preferencesRespected", notificationResult.isPreferencesRespected(),
                    "deliveryAttempts", notificationResult.getDeliveryAttempts()
                ))
                .build();
    }

    private void executeAutomatedActions(UserEventContext context, UserEventProcessingResult result) {
        try {
            if (result.isSuccess()) {
                executeSuccessActions(context, result);
            } else {
                executeFailureActions(context, result);
            }
            
            executeUniversalActions(context, result);
            
        } catch (Exception e) {
            log.error("Error executing automated actions for event: {}", context.getEventId(), e);
            metricsService.incrementCounter("user_events_action_errors", "event_type", context.getEventType());
        }
    }

    private void executeSuccessActions(UserEventContext context, UserEventProcessingResult result) {
        switch (context.getEventType()) {
            case "USER_REGISTERED":
                sendWelcomeNotifications(context);
                scheduleOnboardingTasks(context);
                break;
            case "USER_LOGIN":
                updateUserActivity(context);
                checkSecurityThresholds(context);
                break;
            case "USER_KYC_COMPLETED":
                upgradeAccountTier(context);
                unlockFeatures(context);
                break;
            case "USER_SECURITY_EVENT":
                escalateSecurityIncident(context, result);
                break;
            case "USER_SUSPENDED":
                freezeUserAssets(context);
                notifyComplianceTeam(context);
                break;
        }
    }

    private void executeFailureActions(UserEventContext context, UserEventProcessingResult result) {
        sendAdminAlert(context, result);
        logFailureMetrics(context, result);
    }

    private void executeUniversalActions(UserEventContext context, UserEventProcessingResult result) {
        updateUserTimestamps(context);
        recordEventMetrics(context, result);
        
        if (isHighRiskEvent(context)) {
            triggerSecurityReview(context);
        }
        
        if (isComplianceRelevantEvent(context)) {
            updateComplianceRecord(context);
        }
    }

    private void updateUserMetrics(UserEventContext context, UserEventProcessingResult result) {
        UserMetrics metrics = userMetricsRepository.findByUserIdAndDate(
            context.getUserId(), 
            context.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate()
        ).orElse(new UserMetrics());
        
        metrics.setUserId(context.getUserId());
        metrics.setDate(context.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate());
        metrics.incrementEventCount(context.getEventType());
        metrics.setLastEventTimestamp(context.getTimestamp());
        
        if (context.getSessionId() != null) {
            metrics.addUniqueSession(context.getSessionId());
        }
        
        if (context.getIpAddress() != null) {
            metrics.addUniqueIpAddress(context.getIpAddress());
        }
        
        if (context.getDeviceId() != null) {
            metrics.addUniqueDevice(context.getDeviceId());
        }
        
        userMetricsRepository.save(metrics);
    }

    private void sendWelcomeNotifications(UserEventContext context) {
        userNotificationService.sendWelcomeEmail(context.getUserId());
        userNotificationService.sendOnboardingNotifications(context.getUserId());
    }

    private void scheduleOnboardingTasks(UserEventContext context) {
        userService.scheduleOnboardingTasks(context.getUserId());
    }

    private void updateUserActivity(UserEventContext context) {
        userService.updateLastActivity(context.getUserId(), context.getTimestamp());
    }

    private void checkSecurityThresholds(UserEventContext context) {
        userSecurityService.checkSecurityThresholds(context.getUserId(), context.getIpAddress());
    }

    private void upgradeAccountTier(UserEventContext context) {
        userService.upgradeAccountTier(context.getUserId());
    }

    private void unlockFeatures(UserEventContext context) {
        userService.unlockFeatures(context.getUserId());
    }

    private void escalateSecurityIncident(UserEventContext context, UserEventProcessingResult result) {
        userSecurityService.escalateSecurityIncident(context.getUserId(), context.getEventId());
    }

    private void freezeUserAssets(UserEventContext context) {
        kafkaTemplate.send("asset-freeze-events", context.getUserId(), 
            Map.of("userId", context.getUserId(), "reason", "USER_SUSPENDED", "timestamp", context.getTimestamp()));
    }

    private void notifyComplianceTeam(UserEventContext context) {
        userNotificationService.notifyComplianceTeam(context.getUserId(), "USER_SUSPENDED");
    }

    private void sendAdminAlert(UserEventContext context, UserEventProcessingResult result) {
        userNotificationService.sendAdminAlert(context.getUserId(), context.getEventType(), result.getErrorMessage());
    }

    private void logFailureMetrics(UserEventContext context, UserEventProcessingResult result) {
        metricsService.incrementCounter("user_events_failures", 
            "event_type", context.getEventType(),
            "error_type", result.getErrorMessage() != null ? "PROCESSING_ERROR" : "UNKNOWN");
    }

    private void updateUserTimestamps(UserEventContext context) {
        userService.updateUserTimestamps(context.getUserId(), context.getTimestamp());
    }

    private void recordEventMetrics(UserEventContext context, UserEventProcessingResult result) {
        metricsService.recordEventProcessing(context.getEventType(), result.isSuccess());
    }

    private boolean isHighRiskEvent(UserEventContext context) {
        return Set.of("USER_SECURITY_EVENT", "USER_SUSPENDED", "USER_ACCOUNT_LOCKED", "USER_DELETED").contains(context.getEventType());
    }

    private boolean isComplianceRelevantEvent(UserEventContext context) {
        return Set.of("USER_KYC_INITIATED", "USER_KYC_COMPLETED", "USER_SUSPENDED", 
                     "USER_COMPLIANCE_CHECK", "USER_DELETED").contains(context.getEventType());
    }

    private void triggerSecurityReview(UserEventContext context) {
        userSecurityService.triggerSecurityReview(context.getUserId(), context.getEventType());
    }

    private void updateComplianceRecord(UserEventContext context) {
        userComplianceService.updateComplianceRecord(context.getUserId(), context.getEventType(), context.getTimestamp());
    }

    private List<String> parseStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> result.add(node.asText()));
        }
        return result;
    }

    private Map<String, Object> parseMetadata(JsonNode metadataNode) {
        Map<String, Object> metadata = new HashMap<>();
        if (metadataNode != null && metadataNode.isObject()) {
            metadataNode.fieldNames().forEachRemaining(fieldName -> {
                JsonNode fieldValue = metadataNode.get(fieldName);
                if (fieldValue.isTextual()) {
                    metadata.put(fieldName, fieldValue.asText());
                } else if (fieldValue.isNumber()) {
                    metadata.put(fieldName, fieldValue.asDouble());
                } else if (fieldValue.isBoolean()) {
                    metadata.put(fieldName, fieldValue.asBoolean());
                } else {
                    metadata.put(fieldName, fieldValue.toString());
                }
            });
        }
        return metadata;
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 2) + "****" + phoneNumber.substring(phoneNumber.length() - 2);
    }

    private void handleProcessingError(String eventId, String eventType, String userId, String eventPayload, 
                                     Exception e, Acknowledgment acknowledgment) {
        log.error("Error processing user event: {} of type: {} for user: {}", eventId, eventType, userId, e);
        
        try {
            auditService.logUserEvent(eventId, eventType, userId, "ERROR", Map.of("error", e.getMessage()));
            
            metricsService.incrementCounter("user_events_errors", 
                "event_type", eventType != null ? eventType : "UNKNOWN",
                "error_type", e.getClass().getSimpleName());
            
            if (isRetryableError(e)) {
                sendToDlq(eventPayload, "user-events-dlq", "RETRYABLE_ERROR", e.getMessage());
            } else {
                sendToDlq(eventPayload, "user-events-dlq", "NON_RETRYABLE_ERROR", e.getMessage());
            }
            
        } catch (Exception dlqError) {
            log.error("Failed to send message to DLQ", dlqError);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private boolean isRetryableError(Exception e) {
        return e instanceof org.springframework.dao.TransientDataAccessException ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof org.springframework.web.client.ResourceAccessException;
    }

    private void sendToDlq(String originalMessage, String dlqTopic, String errorType, String errorMessage) {
        Map<String, Object> dlqMessage = Map.of(
            "originalMessage", originalMessage,
            "errorType", errorType,
            "errorMessage", errorMessage,
            "timestamp", Instant.now().toString(),
            "service", "user-service"
        );
        
        kafkaTemplate.send(dlqTopic, dlqMessage);
    }

    public void fallbackProcessUserEvent(String eventPayload, String topic, int partition, long offset, 
                                       Long timestamp, Acknowledgment acknowledgment, Exception ex) {
        log.error("Circuit breaker fallback triggered for user event processing", ex);
        
        metricsService.incrementCounter("user_events_circuit_breaker_fallback");
        
        sendToDlq(eventPayload, "user-events-dlq", "CIRCUIT_BREAKER_OPEN", ex.getMessage());
        acknowledgment.acknowledge();
    }
}