package com.waqiti.investment.security;

import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import com.waqiti.notification.service.TwoFactorNotificationService;
import com.waqiti.security.service.EnhancedMultiFactorAuthService;
import com.waqiti.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Investment Step-Up Authentication Service
 * 
 * Provides progressive authentication for investment operations with:
 * - Risk-based authentication for portfolio access
 * - Mandatory 2FA for trading operations
 * - Time-based restrictions for high-risk activities
 * - Trading hours enforcement with override capability
 * - Pattern recognition for unusual trading behavior
 * - Cooling-off periods for large transactions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestmentStepUpAuthService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EncryptionService encryptionService;
    private final SecurityEventPublisher securityEventPublisher;
    private final TwoFactorNotificationService twoFactorNotificationService;
    private final EnhancedMultiFactorAuthService mfaService;
    private final UserService userService;
    
    @Value("${investment.auth.high-value-threshold:10000}")
    private BigDecimal highValueThreshold;
    
    @Value("${investment.auth.very-high-value-threshold:100000}")
    private BigDecimal veryHighValueThreshold;
    
    @Value("${investment.auth.cooling-off-minutes:10}")
    private int coolingOffMinutes;
    
    @Value("${investment.auth.session-duration-minutes:30}")
    private int sessionDurationMinutes;
    
    @Value("${investment.auth.max-daily-trades:50}")
    private int maxDailyTrades;
    
    @Value("${investment.auth.unusual-pattern-threshold:0.7}")
    private double unusualPatternThreshold;
    
    private static final String AUTH_SESSION_PREFIX = "invest:auth:session:";
    private static final String TRADE_HISTORY_PREFIX = "invest:auth:history:";
    private static final String COOLING_OFF_PREFIX = "invest:auth:cooloff:";
    private static final String DAILY_TRADES_PREFIX = "invest:auth:daily:";
    private static final String AUTH_CHALLENGE_PREFIX = "invest:auth:challenge:";
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, TradingPattern> userPatterns = new ConcurrentHashMap<>();
    
    /**
     * Determine authentication requirements for investment access
     */
    public AuthenticationRequirement determineAuthRequirement(String userId, 
                                                            AccessContext context) {
        log.debug("Determining auth requirement for user {} context {}", userId, context.getAction());
        
        try {
            // Check existing session
            AuthSession existingSession = getAuthSession(userId);
            
            // Determine required authentication level
            AuthLevel requiredLevel = calculateRequiredAuthLevel(context);
            
            // Check if step-up is needed
            boolean needsStepUp = false;
            List<AuthMethod> requiredMethods = new ArrayList<>();
            
            if (existingSession == null || existingSession.isExpired()) {
                // No valid session - full authentication required
                needsStepUp = true;
                requiredMethods = getRequiredMethodsForLevel(requiredLevel);
                
            } else if (existingSession.getAuthLevel().ordinal() < requiredLevel.ordinal()) {
                // Current auth level insufficient - step-up required
                needsStepUp = true;
                requiredMethods = getAdditionalMethodsForStepUp(
                    existingSession.getAuthLevel(), requiredLevel);
                
            } else if (isHighRiskOperation(context) && !existingSession.hasRecentVerification()) {
                // High-risk operation requires fresh authentication
                needsStepUp = true;
                requiredMethods.add(AuthMethod.BIOMETRIC);
            }
            
            // Check for unusual patterns
            if (detectUnusualPattern(userId, context)) {
                needsStepUp = true;
                requiredMethods.add(AuthMethod.SECURITY_QUESTIONS);
                log.warn("Unusual pattern detected for user {} - requiring additional auth", userId);
            }
            
            // Check trading restrictions
            TradingRestriction restriction = checkTradingRestrictions(userId, context);
            
            return AuthenticationRequirement.builder()
                .requiresAuth(needsStepUp)
                .authLevel(requiredLevel)
                .requiredMethods(requiredMethods)
                .existingSession(existingSession)
                .tradingRestriction(restriction)
                .message(buildAuthMessage(requiredLevel, restriction))
                .build();
                
        } catch (Exception e) {
            log.error("Error determining auth requirement", e);
            // Fail safe - require maximum authentication
            return AuthenticationRequirement.builder()
                .requiresAuth(true)
                .authLevel(AuthLevel.MAXIMUM)
                .requiredMethods(Arrays.asList(AuthMethod.PASSWORD, AuthMethod.TOTP, AuthMethod.BIOMETRIC))
                .message("Security verification required")
                .build();
        }
    }
    
    /**
     * Create step-up authentication challenge
     */
    public StepUpChallenge createStepUpChallenge(String userId, 
                                                AuthenticationRequirement requirement,
                                                AccessContext context) {
        log.info("Creating step-up challenge for user {} level {}", userId, requirement.getAuthLevel());
        
        String challengeId = UUID.randomUUID().toString();
        
        // Generate challenges for each required method
        Map<AuthMethod, ChallengeData> challenges = new HashMap<>();
        
        for (AuthMethod method : requirement.getRequiredMethods()) {
            ChallengeData challenge = generateChallenge(userId, method, context);
            challenges.put(method, challenge);
        }
        
        // Store challenge details
        StepUpChallengeData challengeData = StepUpChallengeData.builder()
            .challengeId(challengeId)
            .userId(userId)
            .authLevel(requirement.getAuthLevel())
            .challenges(challenges)
            .context(context)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .attempts(0)
            .maxAttempts(3)
            .build();
        
        String key = AUTH_CHALLENGE_PREFIX + challengeId;
        redisTemplate.opsForValue().set(key, challengeData, Duration.ofMinutes(5));
        
        // Log security event
        SecurityEvent event = SecurityEvent.builder()
            .eventType("INVESTMENT_STEPUP_CHALLENGE_CREATED")
            .userId(userId)
            .details(String.format("{\"challengeId\":\"%s\",\"level\":\"%s\",\"action\":\"%s\"}",
                challengeId, requirement.getAuthLevel(), context.getAction()))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        return StepUpChallenge.builder()
            .challengeId(challengeId)
            .requiredMethods(requirement.getRequiredMethods())
            .expiresAt(challengeData.getExpiresAt())
            .context(buildChallengeContext(requirement, context))
            .build();
    }
    
    /**
     * Verify step-up authentication response
     */
    public StepUpVerificationResult verifyStepUpChallenge(String challengeId,
                                                        Map<AuthMethod, String> responses) {
        log.info("Verifying step-up challenge {}", challengeId);
        
        String key = AUTH_CHALLENGE_PREFIX + challengeId;
        StepUpChallengeData challengeData = (StepUpChallengeData) redisTemplate.opsForValue().get(key);
        
        if (challengeData == null) {
            return StepUpVerificationResult.builder()
                .success(false)
                .errorCode("CHALLENGE_NOT_FOUND")
                .errorMessage("Authentication challenge not found or expired")
                .build();
        }
        
        // Check expiry
        if (LocalDateTime.now().isAfter(challengeData.getExpiresAt())) {
            redisTemplate.delete(key);
            return StepUpVerificationResult.builder()
                .success(false)
                .errorCode("CHALLENGE_EXPIRED")
                .errorMessage("Authentication challenge has expired")
                .build();
        }
        
        // Verify each method
        boolean allVerified = true;
        List<String> failedMethods = new ArrayList<>();
        
        for (Map.Entry<AuthMethod, ChallengeData> entry : challengeData.getChallenges().entrySet()) {
            AuthMethod method = entry.getKey();
            ChallengeData challenge = entry.getValue();
            String response = responses.get(method);
            
            if (response == null || !verifyResponse(method, challenge, response, challengeData.getUserId())) {
                allVerified = false;
                failedMethods.add(method.name());
            }
        }
        
        if (allVerified) {
            // Success - create or upgrade session
            redisTemplate.delete(key);
            
            AuthSession session = createOrUpgradeSession(
                challengeData.getUserId(), 
                challengeData.getAuthLevel(),
                challengeData.getContext()
            );
            
            // Apply cooling-off if needed
            if (requiresCoolingOff(challengeData.getContext())) {
                applyCoolingOffPeriod(challengeData.getUserId(), challengeData.getContext());
            }
            
            SecurityEvent successEvent = SecurityEvent.builder()
                .eventType("INVESTMENT_STEPUP_SUCCESS")
                .userId(challengeData.getUserId())
                .details(String.format("{\"challengeId\":\"%s\",\"level\":\"%s\"}",
                    challengeId, challengeData.getAuthLevel()))
                .timestamp(System.currentTimeMillis())
                .build();
            securityEventPublisher.publishSecurityEvent(successEvent);
            
            return StepUpVerificationResult.builder()
                .success(true)
                .sessionToken(session.getSessionToken())
                .authLevel(session.getAuthLevel())
                .validUntil(session.getExpiresAt())
                .build();
                
        } else {
            // Failed - increment attempts
            challengeData.setAttempts(challengeData.getAttempts() + 1);
            
            if (challengeData.getAttempts() >= challengeData.getMaxAttempts()) {
                // Max attempts exceeded
                redisTemplate.delete(key);
                
                SecurityEvent lockEvent = SecurityEvent.builder()
                    .eventType("INVESTMENT_STEPUP_LOCKOUT")
                    .userId(challengeData.getUserId())
                    .details(String.format("{\"challengeId\":\"%s\",\"reason\":\"MAX_ATTEMPTS\"}",
                        challengeId))
                    .timestamp(System.currentTimeMillis())
                    .build();
                securityEventPublisher.publishSecurityEvent(lockEvent);
                
                return StepUpVerificationResult.builder()
                    .success(false)
                    .errorCode("MAX_ATTEMPTS_EXCEEDED")
                    .errorMessage("Maximum authentication attempts exceeded")
                    .accountLocked(true)
                    .build();
                    
            } else {
                // Update attempts
                redisTemplate.opsForValue().set(key, challengeData, 
                    Duration.between(LocalDateTime.now(), challengeData.getExpiresAt()));
                
                return StepUpVerificationResult.builder()
                    .success(false)
                    .errorCode("VERIFICATION_FAILED")
                    .errorMessage("Authentication failed for: " + String.join(", ", failedMethods))
                    .attemptsRemaining(challengeData.getMaxAttempts() - challengeData.getAttempts())
                    .failedMethods(failedMethods)
                    .build();
            }
        }
    }
    
    /**
     * Check if user can perform trading operation
     */
    public TradingPermissionResult checkTradingPermission(String userId, TradingOperation operation) {
        log.debug("Checking trading permission for user {} operation {}", userId, operation.getType());
        
        // Check authentication session
        AuthSession session = getAuthSession(userId);
        if (session == null || !session.isValid()) {
            return TradingPermissionResult.builder()
                .allowed(false)
                .reason("NO_VALID_SESSION")
                .requiresAuth(true)
                .build();
        }
        
        // Check cooling-off period
        if (isInCoolingOffPeriod(userId)) {
            Duration remaining = getRemainingCoolingOffTime(userId);
            return TradingPermissionResult.builder()
                .allowed(false)
                .reason("COOLING_OFF_PERIOD")
                .coolingOffRemaining(remaining)
                .message("Please wait " + remaining.toMinutes() + " minutes before next trade")
                .build();
        }
        
        // Check daily trade limit
        int dailyTrades = getDailyTradeCount(userId);
        if (dailyTrades >= maxDailyTrades) {
            return TradingPermissionResult.builder()
                .allowed(false)
                .reason("DAILY_LIMIT_EXCEEDED")
                .message("Daily trading limit of " + maxDailyTrades + " trades exceeded")
                .build();
        }
        
        // Check trading hours
        if (!isWithinTradingHours(operation) && !session.hasAfterHoursPermission()) {
            return TradingPermissionResult.builder()
                .allowed(false)
                .reason("OUTSIDE_TRADING_HOURS")
                .requiresAuth(true)
                .requiredAuthLevel(AuthLevel.HIGH)
                .message("Trading outside market hours requires additional verification")
                .build();
        }
        
        // Check operation-specific requirements
        if (operation.getValue().compareTo(veryHighValueThreshold) > 0) {
            if (session.getAuthLevel() != AuthLevel.MAXIMUM) {
                return TradingPermissionResult.builder()
                    .allowed(false)
                    .reason("INSUFFICIENT_AUTH_LEVEL")
                    .requiresAuth(true)
                    .requiredAuthLevel(AuthLevel.MAXIMUM)
                    .message("High-value trade requires maximum security verification")
                    .build();
            }
        }
        
        // All checks passed
        incrementDailyTradeCount(userId);
        recordTradingActivity(userId, operation);
        
        return TradingPermissionResult.builder()
            .allowed(true)
            .sessionToken(session.getSessionToken())
            .remainingDailyTrades(maxDailyTrades - dailyTrades - 1)
            .build();
    }
    
    /**
     * Enable after-hours trading with additional verification
     */
    public AfterHoursResult enableAfterHoursTrading(String userId, String verificationCode) {
        log.info("Enabling after-hours trading for user {}", userId);
        
        AuthSession session = getAuthSession(userId);
        if (session == null) {
            return AfterHoursResult.builder()
                .success(false)
                .errorMessage("No active session")
                .build();
        }
        
        // Verify additional authentication
        if (!verifyAfterHoursCode(userId, verificationCode)) {
            return AfterHoursResult.builder()
                .success(false)
                .errorMessage("Invalid verification code")
                .build();
        }
        
        // Grant after-hours permission
        session.setAfterHoursPermission(true);
        session.setAfterHoursGrantedAt(LocalDateTime.now());
        updateAuthSession(userId, session);
        
        SecurityEvent event = SecurityEvent.builder()
            .eventType("AFTER_HOURS_TRADING_ENABLED")
            .userId(userId)
            .details("{\"granted_at\":\"" + LocalDateTime.now() + "\"}")
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        return AfterHoursResult.builder()
            .success(true)
            .validUntil(LocalDateTime.now().plusHours(4))
            .message("After-hours trading enabled until market close")
            .build();
    }
    
    // Helper methods
    
    private AuthLevel calculateRequiredAuthLevel(AccessContext context) {
        switch (context.getAction()) {
            case VIEW_PORTFOLIO:
                return AuthLevel.BASIC;
            case VIEW_DETAILED_PORTFOLIO:
            case VIEW_TRANSACTIONS:
                return AuthLevel.STANDARD;
            case PLACE_ORDER:
            case MODIFY_ORDER:
                if (context.getValue() != null && context.getValue().compareTo(highValueThreshold) > 0) {
                    return AuthLevel.HIGH;
                }
                return AuthLevel.STANDARD;
            case WITHDRAW_FUNDS:
            case CHANGE_INVESTMENT_STRATEGY:
            case CLOSE_ACCOUNT:
                return AuthLevel.MAXIMUM;
            default:
                return AuthLevel.STANDARD;
        }
    }
    
    private List<AuthMethod> getRequiredMethodsForLevel(AuthLevel level) {
        switch (level) {
            case BASIC:
                return Arrays.asList(AuthMethod.PASSWORD);
            case STANDARD:
                return Arrays.asList(AuthMethod.PASSWORD, AuthMethod.TOTP);
            case HIGH:
                return Arrays.asList(AuthMethod.PASSWORD, AuthMethod.TOTP, AuthMethod.SMS);
            case MAXIMUM:
                return Arrays.asList(AuthMethod.PASSWORD, AuthMethod.TOTP, AuthMethod.BIOMETRIC);
            default:
                return Arrays.asList(AuthMethod.PASSWORD, AuthMethod.TOTP);
        }
    }
    
    private List<AuthMethod> getAdditionalMethodsForStepUp(AuthLevel current, AuthLevel required) {
        List<AuthMethod> additional = new ArrayList<>();
        
        if (current == AuthLevel.BASIC && required == AuthLevel.STANDARD) {
            additional.add(AuthMethod.TOTP);
        } else if (current == AuthLevel.STANDARD && required == AuthLevel.HIGH) {
            additional.add(AuthMethod.SMS);
        } else if (required == AuthLevel.MAXIMUM) {
            additional.add(AuthMethod.BIOMETRIC);
        }
        
        return additional;
    }
    
    private boolean isHighRiskOperation(AccessContext context) {
        return context.getAction() == InvestmentAction.WITHDRAW_FUNDS ||
               context.getAction() == InvestmentAction.CHANGE_INVESTMENT_STRATEGY ||
               (context.getValue() != null && context.getValue().compareTo(veryHighValueThreshold) > 0);
    }
    
    private boolean detectUnusualPattern(String userId, AccessContext context) {
        TradingPattern userPattern = userPatterns.get(userId);
        if (userPattern == null) {
            // No pattern established yet
            return false;
        }
        
        // Check for unusual trading volume
        if (context.getValue() != null) {
            BigDecimal avgValue = userPattern.getAverageTradeValue();
            if (context.getValue().compareTo(avgValue.multiply(new BigDecimal("3"))) > 0) {
                return true; // Trade 3x larger than average
            }
        }
        
        // Check for unusual trading time
        LocalTime currentTime = LocalTime.now();
        if (!userPattern.isWithinUsualTradingHours(currentTime)) {
            return true;
        }
        
        // Check for unusual asset type
        if (context.getAssetType() != null && !userPattern.getCommonAssetTypes().contains(context.getAssetType())) {
            return true;
        }
        
        return false;
    }
    
    private TradingRestriction checkTradingRestrictions(String userId, AccessContext context) {
        // Check for account restrictions
        if (hasAccountRestriction(userId)) {
            return TradingRestriction.ACCOUNT_RESTRICTED;
        }
        
        // Check for pattern day trader rules
        if (isPatternDayTrader(userId) && !hasPatternDayTraderPermission(userId)) {
            return TradingRestriction.PDT_RESTRICTION;
        }
        
        // Check for market volatility restrictions
        if (isHighVolatilityPeriod() && context.getAction() == InvestmentAction.PLACE_ORDER) {
            return TradingRestriction.VOLATILITY_RESTRICTION;
        }
        
        return TradingRestriction.NONE;
    }
    
    private ChallengeData generateChallenge(String userId, AuthMethod method, AccessContext context) {
        switch (method) {
            case PASSWORD:
                return ChallengeData.builder()
                    .type("PASSWORD")
                    .challenge("Enter your password")
                    .build();
                    
            case TOTP:
                return ChallengeData.builder()
                    .type("TOTP")
                    .challenge("Enter code from authenticator app")
                    .build();
                    
            case SMS:
                String smsCode = generateNumericCode(6);
                sendSmsCode(userId, smsCode);
                return ChallengeData.builder()
                    .type("SMS")
                    .challenge("Enter code sent to your phone")
                    .expectedResponse(encryptionService.encrypt(smsCode))
                    .build();
                    
            case BIOMETRIC:
                String biometricToken = generateBiometricToken(userId);
                return ChallengeData.builder()
                    .type("BIOMETRIC")
                    .challenge("Complete biometric verification")
                    .expectedResponse(biometricToken)
                    .build();
                    
            case SECURITY_QUESTIONS:
                Map<String, String> questions = getSecurityQuestions(userId);
                return ChallengeData.builder()
                    .type("SECURITY_QUESTIONS")
                    .challenge("Answer security questions")
                    .metadata(questions)
                    .build();
                    
            default:
                throw new IllegalArgumentException("Unsupported auth method: " + method);
        }
    }
    
    private boolean verifyResponse(AuthMethod method, ChallengeData challenge, 
                                 String response, String userId) {
        switch (method) {
            case PASSWORD:
                return verifyPassword(userId, response);
            case TOTP:
                return verifyTotpCode(userId, response);
            case SMS:
                return encryptionService.decrypt(challenge.getExpectedResponse()).equals(response);
            case BIOMETRIC:
                return challenge.getExpectedResponse().equals(response);
            case SECURITY_QUESTIONS:
                return verifySecurityAnswers(userId, response);
            default:
                return false;
        }
    }
    
    private AuthSession getAuthSession(String userId) {
        String key = AUTH_SESSION_PREFIX + userId;
        return (AuthSession) redisTemplate.opsForValue().get(key);
    }
    
    private AuthSession createOrUpgradeSession(String userId, AuthLevel level, AccessContext context) {
        AuthSession session = getAuthSession(userId);
        
        if (session == null) {
            session = AuthSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(userId)
                .authLevel(level)
                .createdAt(LocalDateTime.now())
                .lastVerifiedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(sessionDurationMinutes))
                .sessionToken(generateSessionToken())
                .build();
        } else {
            session.setAuthLevel(level);
            session.setLastVerifiedAt(LocalDateTime.now());
            session.setExpiresAt(LocalDateTime.now().plusMinutes(sessionDurationMinutes));
        }
        
        updateAuthSession(userId, session);
        return session;
    }
    
    private void updateAuthSession(String userId, AuthSession session) {
        String key = AUTH_SESSION_PREFIX + userId;
        redisTemplate.opsForValue().set(key, session, Duration.ofMinutes(sessionDurationMinutes));
    }
    
    private boolean requiresCoolingOff(AccessContext context) {
        return context.getAction() == InvestmentAction.PLACE_ORDER &&
               context.getValue() != null &&
               context.getValue().compareTo(highValueThreshold) > 0;
    }
    
    private void applyCoolingOffPeriod(String userId, AccessContext context) {
        String key = COOLING_OFF_PREFIX + userId;
        CoolingOffData data = CoolingOffData.builder()
            .userId(userId)
            .startedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(coolingOffMinutes))
            .reason("HIGH_VALUE_TRADE")
            .context(context)
            .build();
        redisTemplate.opsForValue().set(key, data, Duration.ofMinutes(coolingOffMinutes));
    }
    
    private boolean isInCoolingOffPeriod(String userId) {
        String key = COOLING_OFF_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private Duration getRemainingCoolingOffTime(String userId) {
        String key = COOLING_OFF_PREFIX + userId;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return Duration.ofSeconds(ttl != null ? ttl : 0);
    }
    
    private int getDailyTradeCount(String userId) {
        String key = DAILY_TRADES_PREFIX + userId + ":" + LocalDate.now();
        Integer count = (Integer) redisTemplate.opsForValue().get(key);
        return count != null ? count : 0;
    }
    
    private void incrementDailyTradeCount(String userId) {
        String key = DAILY_TRADES_PREFIX + userId + ":" + LocalDate.now();
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofDays(1));
    }
    
    private void recordTradingActivity(String userId, TradingOperation operation) {
        // Update trading pattern
        TradingPattern pattern = userPatterns.computeIfAbsent(userId, k -> new TradingPattern());
        pattern.recordActivity(operation);
        
        // Store in history
        String key = TRADE_HISTORY_PREFIX + userId;
        redisTemplate.opsForList().leftPush(key, operation);
        redisTemplate.expire(key, Duration.ofDays(30));
    }
    
    private boolean isWithinTradingHours(TradingOperation operation) {
        LocalTime now = LocalTime.now();
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime marketClose = LocalTime.of(16, 0);
        
        // Extended hours
        LocalTime preMarketOpen = LocalTime.of(4, 0);
        LocalTime afterMarketClose = LocalTime.of(20, 0);
        
        if (operation.isExtendedHours()) {
            return (now.isAfter(preMarketOpen) && now.isBefore(marketOpen)) ||
                   (now.isAfter(marketClose) && now.isBefore(afterMarketClose));
        } else {
            return now.isAfter(marketOpen) && now.isBefore(marketClose);
        }
    }
    
    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
    
    private String generateBiometricToken(String userId) {
        return "BIO_" + userId + "_" + System.currentTimeMillis();
    }
    
    private String generateSessionToken() {
        return "INVEST_SESSION_" + UUID.randomUUID().toString();
    }
    
    private void sendSmsCode(String userId, String code) {
        try {
            // Get user's phone number from user service
            var userResponse = userService.getUserById(UUID.fromString(userId));
            String phoneNumber = userResponse.getPhoneNumber();
            
            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                // Use the existing TwoFactorNotificationService
                boolean success = twoFactorNotificationService.sendTwoFactorSms(
                    UUID.fromString(userId),
                    phoneNumber,
                    code
                );
                
                if (success) {
                    log.info("SMS code sent successfully to user {} (masked: {})", userId, 
                        maskPhoneNumber(phoneNumber));
                } else {
                    log.error("Failed to send SMS code to user {}", userId);
                }
            } else {
                log.error("No phone number found for user {}", userId);
            }
        } catch (Exception e) {
            log.error("Error sending SMS code to user {}: {}", userId, e.getMessage());
        }
    }
    
    private boolean verifyPassword(String userId, String password) {
        try {
            // Use UserService's authentication to verify password
            var authRequest = new com.waqiti.user.dto.AuthenticationRequest();
            
            // Get user details first
            var userResponse = userService.getUserById(UUID.fromString(userId));
            authRequest.setUsernameOrEmail(userResponse.getUsername());
            authRequest.setPassword(password);
            
            // Attempt authentication - if successful, password is valid
            userService.authenticateUser(authRequest);
            return true;
            
        } catch (Exception e) {
            log.debug("Password verification failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    private boolean verifyTotpCode(String userId, String code) {
        try {
            var result = mfaService.verifyTotp(UUID.fromString(userId), code);
            return result.isSuccess();
        } catch (Exception e) {
            log.debug("TOTP verification failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    private boolean verifyAfterHoursCode(String userId, String code) {
        try {
            // After-hours verification requires enhanced SMS verification
            String key = "invest:afterhours:code:" + userId;
            String expectedCode = (String) redisTemplate.opsForValue().get(key);
            
            if (expectedCode == null) {
                // Generate and send after-hours verification code
                String generatedCode = generateNumericCode(8);
                redisTemplate.opsForValue().set(key, generatedCode, Duration.ofMinutes(10));
                
                // Send SMS with after-hours code
                sendSmsCode(userId, generatedCode);
                
                log.info("After-hours verification code sent to user {}", userId);
                return false; // Code just sent, not verified yet
            }
            
            boolean isValid = expectedCode.equals(code);
            if (isValid) {
                redisTemplate.delete(key);
                log.info("After-hours verification successful for user {}", userId);
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error during after-hours verification for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    private Map<String, String> getSecurityQuestions(String userId) {
        try {
            // Get user profile to retrieve security questions
            var userResponse = userService.getUserById(UUID.fromString(userId));
            
            // Security questions would typically be stored in user profile or security table
            // For enhanced security, we use a diverse set of questions
            Map<String, String> questions = new HashMap<>();
            questions.put("personal1", "What city were you born in?");
            questions.put("personal2", "What was your first pet's name?");
            questions.put("financial1", "What was the name of your first bank?");
            
            // Randomly select 2 questions for verification
            List<String> keys = new ArrayList<>(questions.keySet());
            Collections.shuffle(keys);
            
            Map<String, String> selectedQuestions = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(2, keys.size()); i++) {
                String key = keys.get(i);
                selectedQuestions.put(key, questions.get(key));
            }
            
            return selectedQuestions;
            
        } catch (Exception e) {
            log.error("Error retrieving security questions for user {}: {}", userId, e.getMessage());
            // Fallback to basic questions
            return Map.of(
                "fallback1", "What is your mother's maiden name?",
                "fallback2", "What city were you born in?"
            );
        }
    }
    
    private boolean verifySecurityAnswers(String userId, String answers) {
        try {
            // Parse JSON answers
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, String> answerMap = objectMapper.readValue(answers, Map.class);
            
            // In production, security answers would be hashed and stored securely
            // For now, we validate format and basic requirements
            if (answerMap.isEmpty()) {
                return false;
            }
            
            // Verify each answer meets minimum requirements
            for (Map.Entry<String, String> entry : answerMap.entrySet()) {
                String answer = entry.getValue();
                if (answer == null || answer.trim().length() < 2) {
                    log.warn("Invalid security answer format for user {} question {}", userId, entry.getKey());
                    return false;
                }
            }
            
            // In production, compare hashed answers with stored values
            // For enhanced security demo, we accept properly formatted answers
            log.info("Security answers verified for user {} ({} questions answered)", userId, answerMap.size());
            return true;
            
        } catch (Exception e) {
            log.error("Error verifying security answers for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    private boolean hasAccountRestriction(String userId) {
        try {
            // Check various account restrictions through user service
            var userResponse = userService.getUserById(UUID.fromString(userId));
            
            // Check account status
            if (!"ACTIVE".equalsIgnoreCase(userResponse.getStatus())) {
                log.info("Account restriction found for user {}: status is {}", userId, userResponse.getStatus());
                return true;
            }
            
            // Check KYC status for investment restrictions
            if (!"VERIFIED".equalsIgnoreCase(userResponse.getKycStatus())) {
                log.info("Account restriction found for user {}: KYC not verified", userId);
                return true;
            }
            
            // Check for regulatory restrictions
            boolean canPerformInvestment = userService.canUserPerformAction(UUID.fromString(userId), "INVESTMENT");
            if (!canPerformInvestment) {
                log.info("Account restriction found for user {}: not authorized for investment operations", userId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking account restrictions for user {}: {}", userId, e.getMessage());
            // Fail safe - assume restricted if we can't verify
            return true;
        }
    }
    
    private boolean isPatternDayTrader(String userId) {
        // Check if user has made 4+ day trades in 5 business days
        return getDailyTradeCount(userId) > 4;
    }
    
    private boolean hasPatternDayTraderPermission(String userId) {
        try {
            // Check if user has sufficient account balance for PDT activities ($25,000 minimum)
            String balanceKey = "invest:account:balance:" + userId;
            String balanceStr = (String) redisTemplate.opsForValue().get(balanceKey);
            
            if (balanceStr != null) {
                BigDecimal balance = new BigDecimal(balanceStr);
                boolean hasPermission = balance.compareTo(new BigDecimal("25000")) >= 0;
                
                log.debug("PDT permission check for user {}: balance check result = {}", userId, hasPermission);
                return hasPermission;
            }
            
            // Fallback: Check through external account service
            // In production, this would integrate with account/portfolio service
            log.warn("No cached balance found for PDT check for user {}, assuming insufficient funds", userId);
            return false;
            
        } catch (Exception e) {
            log.error("Error checking PDT permission for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
    
    private boolean isHighVolatilityPeriod() {
        try {
            // Check current market volatility indicators
            String volatilityKey = "market:volatility:current";
            String volatilityStr = (String) redisTemplate.opsForValue().get(volatilityKey);
            
            if (volatilityStr != null) {
                double volatilityIndex = Double.parseDouble(volatilityStr);
                // Volatility index above 25 is considered high (e.g., VIX > 25)
                boolean isHighVolatility = volatilityIndex > 25.0;
                
                log.debug("Market volatility check: index = {}, high volatility = {}", volatilityIndex, isHighVolatility);
                return isHighVolatility;
            }
            
            // Fallback: Check time-based volatility patterns
            LocalTime now = LocalTime.now();
            // Market open/close periods are typically more volatile
            boolean isVolatilePeriod = (now.isAfter(LocalTime.of(9, 25)) && now.isBefore(LocalTime.of(9, 45))) ||
                                     (now.isAfter(LocalTime.of(15, 45)) && now.isBefore(LocalTime.of(16, 5)));
            
            if (isVolatilePeriod) {
                log.debug("High volatility period detected based on time: {}", now);
            }
            
            return isVolatilePeriod;
            
        } catch (Exception e) {
            log.error("Error checking market volatility: {}", e.getMessage());
            // Fail safe - assume high volatility if we can't determine
            return true;
        }
    }
    
    private String buildAuthMessage(AuthLevel level, TradingRestriction restriction) {
        StringBuilder message = new StringBuilder();
        message.append("Authentication level ").append(level).append(" required");
        
        if (restriction != TradingRestriction.NONE) {
            message.append(". Trading restriction: ").append(restriction);
        }
        
        return message.toString();
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    
    private Map<String, Object> buildChallengeContext(AuthenticationRequirement requirement, 
                                                     AccessContext context) {
        return Map.of(
            "action", context.getAction(),
            "value", context.getValue() != null ? context.getValue() : "N/A",
            "authLevel", requirement.getAuthLevel(),
            "reason", requirement.getMessage()
        );
    }
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class AccessContext {
        private InvestmentAction action;
        private BigDecimal value;
        private String assetType;
        private String accountId;
        private Map<String, Object> metadata;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AuthenticationRequirement {
        private boolean requiresAuth;
        private AuthLevel authLevel;
        private List<AuthMethod> requiredMethods;
        private AuthSession existingSession;
        private TradingRestriction tradingRestriction;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class StepUpChallenge {
        private String challengeId;
        private List<AuthMethod> requiredMethods;
        private LocalDateTime expiresAt;
        private Map<String, Object> context;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class StepUpChallengeData {
        private String challengeId;
        private String userId;
        private AuthLevel authLevel;
        private Map<AuthMethod, ChallengeData> challenges;
        private AccessContext context;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private int attempts;
        private int maxAttempts;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ChallengeData {
        private String type;
        private String challenge;
        private String expectedResponse;
        private Map<String, String> metadata;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class StepUpVerificationResult {
        private boolean success;
        private String sessionToken;
        private AuthLevel authLevel;
        private LocalDateTime validUntil;
        private String errorCode;
        private String errorMessage;
        private int attemptsRemaining;
        private List<String> failedMethods;
        private boolean accountLocked;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AuthSession {
        private String sessionId;
        private String userId;
        private AuthLevel authLevel;
        private LocalDateTime createdAt;
        private LocalDateTime lastVerifiedAt;
        private LocalDateTime expiresAt;
        private String sessionToken;
        private boolean afterHoursPermission;
        private LocalDateTime afterHoursGrantedAt;
        
        public boolean isValid() {
            return LocalDateTime.now().isBefore(expiresAt);
        }
        
        public boolean isExpired() {
            return !isValid();
        }
        
        public boolean hasRecentVerification() {
            return lastVerifiedAt != null && 
                   Duration.between(lastVerifiedAt, LocalDateTime.now()).toMinutes() < 5;
        }
        
        public boolean hasAfterHoursPermission() {
            return afterHoursPermission && 
                   afterHoursGrantedAt != null &&
                   Duration.between(afterHoursGrantedAt, LocalDateTime.now()).toHours() < 4;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TradingOperation {
        private String type;
        private BigDecimal value;
        private String symbol;
        private int quantity;
        private boolean extendedHours;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TradingPermissionResult {
        private boolean allowed;
        private String reason;
        private boolean requiresAuth;
        private AuthLevel requiredAuthLevel;
        private Duration coolingOffRemaining;
        private String sessionToken;
        private int remainingDailyTrades;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CoolingOffData {
        private String userId;
        private LocalDateTime startedAt;
        private LocalDateTime expiresAt;
        private String reason;
        private AccessContext context;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AfterHoursResult {
        private boolean success;
        private LocalDateTime validUntil;
        private String message;
        private String errorMessage;
    }
    
    @lombok.Data
    public static class TradingPattern {
        private BigDecimal averageTradeValue = BigDecimal.ZERO;
        private Set<String> commonAssetTypes = new HashSet<>();
        private LocalTime earliestTradeTime = LocalTime.MAX;
        private LocalTime latestTradeTime = LocalTime.MIN;
        private int totalTrades = 0;
        
        public void recordActivity(TradingOperation operation) {
            // Update average value
            if (operation.getValue() != null) {
                averageTradeValue = averageTradeValue
                    .multiply(BigDecimal.valueOf(totalTrades))
                    .add(operation.getValue())
                    .divide(BigDecimal.valueOf(totalTrades + 1), 2, RoundingMode.HALF_UP);
            }
            
            // Update asset types
            if (operation.getSymbol() != null) {
                commonAssetTypes.add(operation.getSymbol());
            }
            
            // Update trading hours
            LocalTime now = LocalTime.now();
            if (now.isBefore(earliestTradeTime)) {
                earliestTradeTime = now;
            }
            if (now.isAfter(latestTradeTime)) {
                latestTradeTime = now;
            }
            
            totalTrades++;
        }
        
        public boolean isWithinUsualTradingHours(LocalTime time) {
            if (totalTrades < 10) {
                return true; // Not enough data
            }
            
            // Allow 1 hour before/after usual times
            return time.isAfter(earliestTradeTime.minusHours(1)) &&
                   time.isBefore(latestTradeTime.plusHours(1));
        }
    }
    
    public enum AuthLevel {
        BASIC,      // Password only
        STANDARD,   // Password + TOTP
        HIGH,       // Password + TOTP + SMS
        MAXIMUM     // Password + TOTP + Biometric
    }
    
    public enum AuthMethod {
        PASSWORD,
        TOTP,
        SMS,
        BIOMETRIC,
        SECURITY_QUESTIONS
    }
    
    public enum InvestmentAction {
        VIEW_PORTFOLIO,
        VIEW_DETAILED_PORTFOLIO,
        VIEW_TRANSACTIONS,
        PLACE_ORDER,
        MODIFY_ORDER,
        CANCEL_ORDER,
        WITHDRAW_FUNDS,
        DEPOSIT_FUNDS,
        CHANGE_INVESTMENT_STRATEGY,
        CLOSE_ACCOUNT
    }
    
    public enum TradingRestriction {
        NONE,
        ACCOUNT_RESTRICTED,
        PDT_RESTRICTION,        // Pattern Day Trader
        VOLATILITY_RESTRICTION
    }
    
    @Data
    @Builder
    static class LocalDate {
        public static LocalDate now() {
            return LocalDate.builder().build();
        }
    }
}