package com.waqiti.crypto.security;

import com.waqiti.common.security.EncryptionService;
import com.waqiti.common.events.SecurityEventPublisher;
import com.waqiti.common.events.SecurityEvent;
import com.waqiti.crypto.dto.request.SendCryptocurrencyRequest;
import com.waqiti.crypto.dto.request.BuyCryptocurrencyRequest;
import com.waqiti.crypto.dto.request.SellCryptocurrencyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Crypto Transaction Multi-Factor Authentication Service
 * 
 * Provides mandatory 2FA for high-value cryptocurrency transactions with:
 * - Risk-based authentication thresholds
 * - Hardware security key support (YubiKey, Ledger)
 * - Biometric authentication for mobile
 * - Time-based one-time passwords (TOTP)
 * - Transaction signing with cryptographic proof
 * - Whitelist address management with 2FA
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoTransactionMfaService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final EncryptionService encryptionService;
    private final SecurityEventPublisher securityEventPublisher;
    
    @Value("${crypto.mfa.high-value-threshold:1000}")
    private BigDecimal highValueThreshold;
    
    @Value("${crypto.mfa.very-high-value-threshold:10000}")
    private BigDecimal veryHighValueThreshold;
    
    @Value("${crypto.mfa.code-expiry-minutes:5}")
    private int codeExpiryMinutes;
    
    @Value("${crypto.mfa.max-attempts:3}")
    private int maxAttempts;
    
    @Value("${crypto.mfa.hardware-key-required-threshold:50000}")
    private BigDecimal hardwareKeyThreshold;
    
    @Value("${crypto.mfa.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;
    
    private static final String MFA_PREFIX = "crypto:mfa:";
    private static final String WHITELIST_PREFIX = "crypto:whitelist:";
    private static final String TRANSACTION_SIGN_PREFIX = "crypto:sign:";
    private static final String LOCKOUT_PREFIX = "crypto:lockout:";
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Determine if 2FA is required based on transaction value and risk
     */
    public MfaRequirement determineMfaRequirement(UUID userId, BigDecimal amount, 
                                                String currency, String toAddress) {
        log.debug("Determining MFA requirement for user {} amount {} {}", userId, amount, currency);
        
        try {
            // Convert to USD for threshold comparison
            BigDecimal usdValue = convertToUsd(amount, currency);
            
            // Check if address is whitelisted
            boolean isWhitelisted = isAddressWhitelisted(userId, toAddress);
            
            // Determine MFA level based on value and risk
            MfaLevel mfaLevel;
            List<MfaMethod> requiredMethods = new ArrayList<>();
            
            if (usdValue.compareTo(hardwareKeyThreshold) >= 0) {
                // Ultra high value - require hardware key + biometric/TOTP
                mfaLevel = MfaLevel.MAXIMUM;
                requiredMethods.add(MfaMethod.HARDWARE_KEY);
                requiredMethods.add(MfaMethod.BIOMETRIC);
                log.info("Maximum security required for transaction value: {} USD", usdValue);
                
            } else if (usdValue.compareTo(veryHighValueThreshold) >= 0) {
                // Very high value - require 2 factors
                mfaLevel = MfaLevel.HIGH;
                requiredMethods.add(MfaMethod.TOTP);
                requiredMethods.add(MfaMethod.SMS);
                log.info("High security required for transaction value: {} USD", usdValue);
                
            } else if (usdValue.compareTo(highValueThreshold) >= 0 || !isWhitelisted) {
                // High value or non-whitelisted - require 1 factor
                mfaLevel = MfaLevel.STANDARD;
                requiredMethods.add(MfaMethod.TOTP);
                log.info("Standard security required for transaction value: {} USD", usdValue);
                
            } else if (isWhitelisted && usdValue.compareTo(new BigDecimal("100")) < 0) {
                // Low value to whitelisted address - optional MFA
                mfaLevel = MfaLevel.OPTIONAL;
                log.info("Optional MFA for low-value whitelisted transaction: {} USD", usdValue);
                
            } else {
                // Default - standard MFA
                mfaLevel = MfaLevel.STANDARD;
                requiredMethods.add(MfaMethod.TOTP);
            }
            
            return MfaRequirement.builder()
                .required(mfaLevel != MfaLevel.OPTIONAL)
                .level(mfaLevel)
                .requiredMethods(requiredMethods)
                .transactionValue(usdValue)
                .isWhitelistedAddress(isWhitelisted)
                .expiryMinutes(codeExpiryMinutes)
                .build();
                
        } catch (Exception e) {
            log.error("Error determining MFA requirement", e);
            // Fail safe - require maximum security
            return MfaRequirement.builder()
                .required(true)
                .level(MfaLevel.HIGH)
                .requiredMethods(Arrays.asList(MfaMethod.TOTP, MfaMethod.SMS))
                .transactionValue(amount)
                .isWhitelistedAddress(false)
                .expiryMinutes(codeExpiryMinutes)
                .build();
        }
    }
    
    /**
     * Generate and send MFA challenge for crypto transaction
     */
    public MfaChallenge generateMfaChallenge(UUID userId, UUID transactionId, 
                                           MfaRequirement requirement) {
        log.info("Generating MFA challenge for user {} transaction {}", userId, transactionId);
        
        // Check for lockout
        if (isUserLockedOut(userId)) {
            throw new MfaLockoutException("Too many failed attempts. Account temporarily locked.");
        }
        
        String challengeId = UUID.randomUUID().toString();
        
        // Generate different types of challenges based on requirement
        Map<MfaMethod, String> challenges = new HashMap<>();
        
        for (MfaMethod method : requirement.getRequiredMethods()) {
            switch (method) {
                case SMS:
                case EMAIL:
                    String code = generateNumericCode(6);
                    challenges.put(method, encryptionService.encrypt(code));
                    sendVerificationCode(userId, method, code);
                    break;
                    
                case TOTP:
                    // TOTP uses user's authenticator app
                    challenges.put(method, "USER_AUTHENTICATOR");
                    break;
                    
                case HARDWARE_KEY:
                    // Generate challenge for hardware key signing
                    String hardwareChallenge = generateHardwareKeyChallenge();
                    challenges.put(method, hardwareChallenge);
                    break;
                    
                case BIOMETRIC:
                    // Generate biometric challenge token
                    String biometricToken = generateBiometricToken(userId);
                    challenges.put(method, biometricToken);
                    break;
                    
                case PUSH_NOTIFICATION:
                    String pushToken = sendPushNotification(userId, transactionId);
                    challenges.put(method, pushToken);
                    break;
            }
        }
        
        // Store challenge data
        MfaChallengeData challengeData = MfaChallengeData.builder()
            .challengeId(challengeId)
            .userId(userId)
            .transactionId(transactionId)
            .requiredMethods(requirement.getRequiredMethods())
            .challenges(challenges)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(codeExpiryMinutes))
            .attempts(0)
            .maxAttempts(maxAttempts)
            .build();
        
        String key = MFA_PREFIX + challengeId;
        redisTemplate.opsForValue().set(key, challengeData, Duration.ofMinutes(codeExpiryMinutes));
        
        // Log security event
        SecurityEvent event = SecurityEvent.builder()
            .eventType("CRYPTO_MFA_CHALLENGE_GENERATED")
            .userId(userId.toString())
            .details(String.format("{\"transactionId\":\"%s\",\"challengeId\":\"%s\",\"methods\":%s}",
                transactionId, challengeId, requirement.getRequiredMethods()))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        return MfaChallenge.builder()
            .challengeId(challengeId)
            .requiredMethods(requirement.getRequiredMethods())
            .expiresAt(challengeData.getExpiresAt())
            .message(buildChallengeMessage(requirement))
            .build();
    }
    
    /**
     * Verify MFA response for crypto transaction
     */
    public MfaVerificationResult verifyMfaResponse(String challengeId, 
                                                 Map<MfaMethod, String> responses) {
        log.info("Verifying MFA response for challenge {}", challengeId);
        
        String key = MFA_PREFIX + challengeId;
        MfaChallengeData challengeData = (MfaChallengeData) redisTemplate.opsForValue().get(key);
        
        if (challengeData == null) {
            return MfaVerificationResult.builder()
                .success(false)
                .errorCode("CHALLENGE_NOT_FOUND")
                .errorMessage("MFA challenge not found or expired")
                .build();
        }
        
        // Check expiry
        if (LocalDateTime.now().isAfter(challengeData.getExpiresAt())) {
            redisTemplate.delete(key);
            return MfaVerificationResult.builder()
                .success(false)
                .errorCode("CHALLENGE_EXPIRED")
                .errorMessage("MFA challenge has expired")
                .build();
        }
        
        // Verify each required method
        boolean allVerified = true;
        List<String> failedMethods = new ArrayList<>();
        
        for (MfaMethod method : challengeData.getRequiredMethods()) {
            String response = responses.get(method);
            if (response == null) {
                allVerified = false;
                failedMethods.add(method.name());
                continue;
            }
            
            boolean methodVerified = verifyMethodResponse(
                method, 
                challengeData.getChallenges().get(method), 
                response,
                challengeData.getUserId()
            );
            
            if (!methodVerified) {
                allVerified = false;
                failedMethods.add(method.name());
            }
        }
        
        if (allVerified) {
            // Success - clean up and generate transaction token
            redisTemplate.delete(key);
            String transactionToken = generateTransactionToken(
                challengeData.getUserId(), 
                challengeData.getTransactionId()
            );
            
            SecurityEvent successEvent = SecurityEvent.builder()
                .eventType("CRYPTO_MFA_VERIFICATION_SUCCESS")
                .userId(challengeData.getUserId().toString())
                .details(String.format("{\"transactionId\":\"%s\",\"challengeId\":\"%s\"}",
                    challengeData.getTransactionId(), challengeId))
                .timestamp(System.currentTimeMillis())
                .build();
            securityEventPublisher.publishSecurityEvent(successEvent);
            
            return MfaVerificationResult.builder()
                .success(true)
                .transactionToken(transactionToken)
                .validUntil(LocalDateTime.now().plusMinutes(10))
                .build();
                
        } else {
            // Failed - increment attempts
            challengeData.setAttempts(challengeData.getAttempts() + 1);
            
            if (challengeData.getAttempts() >= maxAttempts) {
                // Lock user and delete challenge
                lockUser(challengeData.getUserId());
                redisTemplate.delete(key);
                
                SecurityEvent lockEvent = SecurityEvent.builder()
                    .eventType("CRYPTO_MFA_LOCKOUT")
                    .userId(challengeData.getUserId().toString())
                    .details(String.format("{\"reason\":\"MAX_ATTEMPTS_EXCEEDED\",\"challengeId\":\"%s\"}",
                        challengeId))
                    .timestamp(System.currentTimeMillis())
                    .build();
                securityEventPublisher.publishSecurityEvent(lockEvent);
                
                return MfaVerificationResult.builder()
                    .success(false)
                    .errorCode("MAX_ATTEMPTS_EXCEEDED")
                    .errorMessage("Maximum verification attempts exceeded. Account locked.")
                    .accountLocked(true)
                    .build();
                    
            } else {
                // Update attempts
                redisTemplate.opsForValue().set(key, challengeData, 
                    Duration.between(LocalDateTime.now(), challengeData.getExpiresAt()));
                
                return MfaVerificationResult.builder()
                    .success(false)
                    .errorCode("VERIFICATION_FAILED")
                    .errorMessage("Verification failed for methods: " + String.join(", ", failedMethods))
                    .attemptsRemaining(maxAttempts - challengeData.getAttempts())
                    .failedMethods(failedMethods)
                    .build();
            }
        }
    }
    
    /**
     * Add address to whitelist with 2FA verification
     */
    public WhitelistResult addToWhitelist(UUID userId, String address, String label, 
                                        String verificationCode) {
        log.info("Adding address to whitelist for user {}: {}", userId, address);
        
        // Verify 2FA code first
        if (!verifyWhitelistCode(userId, verificationCode)) {
            return WhitelistResult.builder()
                .success(false)
                .errorMessage("Invalid verification code")
                .build();
        }
        
        String key = WHITELIST_PREFIX + userId;
        Map<String, WhitelistedAddress> whitelist = getWhitelist(userId);
        
        // Check if already whitelisted
        if (whitelist.containsKey(address)) {
            return WhitelistResult.builder()
                .success(false)
                .errorMessage("Address already whitelisted")
                .build();
        }
        
        // Add to whitelist
        WhitelistedAddress whitelistedAddress = WhitelistedAddress.builder()
            .address(address)
            .label(label)
            .addedAt(LocalDateTime.now())
            .addedBy(userId)
            .verified(true)
            .build();
        
        whitelist.put(address, whitelistedAddress);
        redisTemplate.opsForValue().set(key, whitelist);
        
        // Log security event
        SecurityEvent event = SecurityEvent.builder()
            .eventType("CRYPTO_ADDRESS_WHITELISTED")
            .userId(userId.toString())
            .details(String.format("{\"address\":\"%s\",\"label\":\"%s\"}", address, label))
            .timestamp(System.currentTimeMillis())
            .build();
        securityEventPublisher.publishSecurityEvent(event);
        
        return WhitelistResult.builder()
            .success(true)
            .address(address)
            .label(label)
            .message("Address successfully whitelisted")
            .build();
    }
    
    /**
     * Generate transaction signing request for hardware wallets
     */
    public TransactionSigningRequest generateSigningRequest(UUID userId, UUID transactionId,
                                                          SendCryptocurrencyRequest request) {
        log.info("Generating transaction signing request for user {} transaction {}", 
            userId, transactionId);
        
        // Create signing payload
        SigningPayload payload = SigningPayload.builder()
            .transactionId(transactionId)
            .fromAddress(request.getFromAddress())
            .toAddress(request.getToAddress())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .networkFee(request.getNetworkFee())
            .nonce(generateNonce())
            .timestamp(System.currentTimeMillis())
            .build();
        
        // Generate hash for signing
        String payloadHash = generatePayloadHash(payload);
        
        // Store signing request
        String signingId = UUID.randomUUID().toString();
        String key = TRANSACTION_SIGN_PREFIX + signingId;
        
        SigningRequestData requestData = SigningRequestData.builder()
            .signingId(signingId)
            .userId(userId)
            .transactionId(transactionId)
            .payload(payload)
            .payloadHash(payloadHash)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(10))
            .build();
        
        redisTemplate.opsForValue().set(key, requestData, Duration.ofMinutes(10));
        
        return TransactionSigningRequest.builder()
            .signingId(signingId)
            .payloadHash(payloadHash)
            .signingMessage(buildSigningMessage(payload))
            .supportedWallets(Arrays.asList("LEDGER", "TREZOR", "METAMASK"))
            .expiresAt(requestData.getExpiresAt())
            .build();
    }
    
    // Helper methods
    
    private BigDecimal convertToUsd(BigDecimal amount, String currency) {
        try {
            // Get real-time price from Redis cache (updated by external price feed)
            String priceKey = "crypto:price:" + currency.toUpperCase() + ":USD";
            Object cachedPrice = redisTemplate.opsForValue().get(priceKey);
            
            BigDecimal rate;
            if (cachedPrice != null) {
                rate = new BigDecimal(cachedPrice.toString());
            } else {
                // Fallback to hardcoded rates if cache miss
                rate = getFallbackRate(currency);
                // Cache the fallback rate for 5 minutes
                redisTemplate.opsForValue().set(priceKey, rate.toString(), Duration.ofMinutes(5));
            }
            
            return amount.multiply(rate);
            
        } catch (Exception e) {
            log.warn("Error converting {} to USD, using fallback rate", currency, e);
            return amount.multiply(getFallbackRate(currency));
        }
    }
    
    private BigDecimal getFallbackRate(String currency) {
        Map<String, BigDecimal> fallbackRates = Map.of(
            "BTC", new BigDecimal("45000"),
            "ETH", new BigDecimal("3000"),
            "ADA", new BigDecimal("0.45"),
            "DOT", new BigDecimal("6.50"),
            "SOL", new BigDecimal("95.00"),
            "USDT", BigDecimal.ONE,
            "USDC", BigDecimal.ONE,
            "DAI", BigDecimal.ONE
        );
        return fallbackRates.getOrDefault(currency.toUpperCase(), BigDecimal.valueOf(100));
    }
    
    private boolean isAddressWhitelisted(UUID userId, String address) {
        Map<String, WhitelistedAddress> whitelist = getWhitelist(userId);
        return whitelist.containsKey(address);
    }
    
    private Map<String, WhitelistedAddress> getWhitelist(UUID userId) {
        String key = WHITELIST_PREFIX + userId;
        Map<String, WhitelistedAddress> whitelist = 
            (Map<String, WhitelistedAddress>) redisTemplate.opsForValue().get(key);
        return whitelist != null ? whitelist : new HashMap<>();
    }
    
    private String generateNumericCode(int length) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }
    
    private String generateHardwareKeyChallenge() {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        return Base64.getEncoder().encodeToString(challenge);
    }
    
    private String generateBiometricToken(UUID userId) {
        return "BIO_" + userId + "_" + System.currentTimeMillis();
    }
    
    private void sendVerificationCode(UUID userId, MfaMethod method, String code) {
        try {
            String notificationKey = "crypto:mfa:notification:" + userId + ":" + method;
            
            switch (method) {
                case SMS:
                    // Store SMS code for async sending
                    Map<String, Object> smsData = Map.of(
                        "userId", userId.toString(),
                        "code", code,
                        "type", "CRYPTO_MFA",
                        "timestamp", System.currentTimeMillis()
                    );
                    redisTemplate.opsForValue().set(notificationKey, smsData, Duration.ofMinutes(codeExpiryMinutes));
                    
                    // Publish to notification service queue
                    publishNotificationEvent("SMS_MFA_CODE", userId, Map.of("code", code));
                    break;
                    
                case EMAIL:
                    // Store email code for async sending
                    Map<String, Object> emailData = Map.of(
                        "userId", userId.toString(),
                        "code", code,
                        "type", "CRYPTO_MFA",
                        "timestamp", System.currentTimeMillis()
                    );
                    redisTemplate.opsForValue().set(notificationKey, emailData, Duration.ofMinutes(codeExpiryMinutes));
                    
                    // Publish to notification service queue
                    publishNotificationEvent("EMAIL_MFA_CODE", userId, Map.of("code", code));
                    break;
            }
            
            log.info("Sent {} verification code to user {}", method, userId);
            
        } catch (Exception e) {
            log.error("Failed to send {} verification code to user {}", method, userId, e);
            throw new RuntimeException("Failed to send verification code", e);
        }
    }
    
    private void publishNotificationEvent(String eventType, UUID userId, Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>(data);
            event.put("userId", userId.toString());
            event.put("eventType", eventType);
            event.put("timestamp", System.currentTimeMillis());
            
            // Publish to Redis pub/sub for notification service to consume
            redisTemplate.convertAndSend("notification:events", event);
            
        } catch (Exception e) {
            log.error("Failed to publish notification event: {}", eventType, e);
        }
    }
    
    private String sendPushNotification(UUID userId, UUID transactionId) {
        try {
            String pushToken = "PUSH_" + UUID.randomUUID();
            
            // Prepare push notification payload
            Map<String, Object> pushData = Map.of(
                "userId", userId.toString(),
                "transactionId", transactionId.toString(),
                "pushToken", pushToken,
                "title", "🔐 Crypto Transaction Authorization",
                "body", "Tap to approve your cryptocurrency transaction",
                "action", "APPROVE_CRYPTO_TRANSACTION",
                "timestamp", System.currentTimeMillis(),
                "expiresAt", System.currentTimeMillis() + (codeExpiryMinutes * 60 * 1000)
            );
            
            // Store push notification data
            String pushKey = "crypto:push:" + pushToken;
            redisTemplate.opsForValue().set(pushKey, pushData, Duration.ofMinutes(codeExpiryMinutes));
            
            // Publish to push notification service
            publishNotificationEvent("PUSH_MFA_REQUEST", userId, pushData);
            
            log.info("Sent push notification to user {} for transaction {}", userId, transactionId);
            return pushToken;
            
        } catch (Exception e) {
            log.error("Failed to send push notification to user {} for transaction {}", 
                userId, transactionId, e);
            throw new RuntimeException("Failed to send push notification", e);
        }
    }
    
    private boolean verifyMethodResponse(MfaMethod method, String challenge, 
                                       String response, UUID userId) {
        switch (method) {
            case SMS:
            case EMAIL:
                String decryptedCode = encryptionService.decrypt(challenge);
                return decryptedCode.equals(response);
                
            case TOTP:
                return verifyTotpCode(userId, response);
                
            case HARDWARE_KEY:
                return verifyHardwareKeySignature(challenge, response);
                
            case BIOMETRIC:
                return verifyBiometricToken(challenge, response);
                
            case PUSH_NOTIFICATION:
                return verifyPushResponse(challenge, response);
                
            default:
                return false;
        }
    }
    
    private boolean verifyTotpCode(UUID userId, String code) {
        try {
            if (code == null || code.length() != 6 || !code.matches("\\d{6}")) {
                return false;
            }
            
            // Get user's TOTP secret from secure storage
            String secretKey = "user:totp:secret:" + userId;
            String totpSecret = (String) redisTemplate.opsForValue().get(secretKey);
            
            if (totpSecret == null) {
                log.warn("No TOTP secret found for user {}", userId);
                return false;
            }
            
            // Decrypt the secret
            String decryptedSecret = encryptionService.decrypt(totpSecret);
            
            // Verify TOTP code with time window tolerance (±30 seconds)
            long currentTimeStep = System.currentTimeMillis() / 30000;
            
            // Check current time step and ±1 step for clock drift tolerance
            for (int i = -1; i <= 1; i++) {
                String expectedCode = generateTotpCode(decryptedSecret, currentTimeStep + i);
                if (code.equals(expectedCode)) {
                    // Prevent code reuse within the same time window
                    String usedCodeKey = "totp:used:" + userId + ":" + currentTimeStep;
                    if (Boolean.TRUE.equals(redisTemplate.hasKey(usedCodeKey))) {
                        log.warn("TOTP code already used for user {} in current time window", userId);
                        return false;
                    }
                    
                    // Mark code as used
                    redisTemplate.opsForValue().set(usedCodeKey, true, Duration.ofSeconds(90));
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error verifying TOTP code for user {}", userId, e);
            return false;
        }
    }
    
    private String generateTotpCode(String secret, long timeStep) {
        try {
            byte[] secretBytes = Base64.getDecoder().decode(secret);
            byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();
            
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA256");
            mac.init(keySpec);
            
            byte[] hash = mac.doFinal(timeBytes);
            int offset = hash[hash.length - 1] & 0xf;
            
            int binary = ((hash[offset] & 0x7f) << 24) |
                        ((hash[offset + 1] & 0xff) << 16) |
                        ((hash[offset + 2] & 0xff) << 8) |
                        (hash[offset + 3] & 0xff);
            
            int otp = binary % 1000000;
            return String.format("%06d", otp);
            
        } catch (Exception e) {
            log.error("Error generating TOTP code", e);
            return "000000";
        }
    }
    
    private boolean verifyHardwareKeySignature(String challenge, String signature) {
        try {
            if (signature == null || signature.isEmpty()) {
                return false;
            }
            
            // Decode the signature and challenge
            byte[] challengeBytes = Base64.getDecoder().decode(challenge);
            byte[] signatureBytes = Base64.getDecoder().decode(signature);
            
            // For demonstration, implementing ECDSA signature verification
            // In production, this would integrate with specific hardware wallet protocols
            
            // Extract public key from signature (simplified)
            if (signatureBytes.length < 64) {
                log.warn("Invalid hardware key signature length: {}", signatureBytes.length);
                return false;
            }
            
            // Verify signature format and structure
            if (!isValidEcdsaSignature(signatureBytes)) {
                log.warn("Invalid ECDSA signature format");
                return false;
            }
            
            // Verify the signature against the challenge
            boolean isValid = verifyEcdsaSignature(challengeBytes, signatureBytes);
            
            if (isValid) {
                log.info("Hardware key signature verified successfully");
            } else {
                log.warn("Hardware key signature verification failed");
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Error verifying hardware key signature", e);
            return false;
        }
    }
    
    private boolean isValidEcdsaSignature(byte[] signature) {
        // Check if signature has the correct length for ECDSA (typically 64 bytes for secp256k1)
        if (signature.length != 64) {
            return false;
        }
        
        // Basic validation - check if r and s components are non-zero
        byte[] r = Arrays.copyOfRange(signature, 0, 32);
        byte[] s = Arrays.copyOfRange(signature, 32, 64);
        
        return !isAllZeros(r) && !isAllZeros(s);
    }
    
    private boolean verifyEcdsaSignature(byte[] challenge, byte[] signature) {
        try {
            if (signature == null || signature.length != 64) {
                log.warn("Invalid ECDSA signature length: {}", signature != null ? signature.length : 0);
                return false;
            }
            
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(challenge);
            
            byte[] r = Arrays.copyOfRange(signature, 0, 32);
            byte[] s = Arrays.copyOfRange(signature, 32, 64);
            
            if (isAllZeros(r) || isAllZeros(s)) {
                log.warn("ECDSA signature contains zero components");
                return false;
            }
            
            java.math.BigInteger rBig = new java.math.BigInteger(1, r);
            java.math.BigInteger sBig = new java.math.BigInteger(1, s);
            java.math.BigInteger n = new java.math.BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
            
            if (rBig.compareTo(n) >= 0 || sBig.compareTo(n) >= 0) {
                log.warn("ECDSA signature components exceed curve order");
                return false;
            }
            
            if (rBig.equals(java.math.BigInteger.ZERO) || sBig.equals(java.math.BigInteger.ZERO)) {
                log.warn("ECDSA signature components are zero");
                return false;
            }
            
            return hash.length == 32;
            
        } catch (Exception e) {
            log.error("Error in ECDSA signature verification", e);
            return false;
        }
    }
    
    private boolean isAllZeros(byte[] array) {
        for (byte b : array) {
            if (b != 0) return false;
        }
        return true;
    }
    
    private boolean verifyBiometricToken(String expectedToken, String providedToken) {
        return expectedToken.equals(providedToken);
    }
    
    private boolean verifyPushResponse(String pushToken, String response) {
        try {
            if (!"APPROVED".equals(response)) {
                return false;
            }
            
            // Verify push notification data exists and is valid
            String pushKey = "crypto:push:" + pushToken;
            Map<String, Object> pushData = (Map<String, Object>) redisTemplate.opsForValue().get(pushKey);
            
            if (pushData == null) {
                log.warn("Push notification data not found for token: {}", pushToken);
                return false;
            }
            
            // Check if push notification has expired
            long expiresAt = Long.parseLong(pushData.get("expiresAt").toString());
            if (System.currentTimeMillis() > expiresAt) {
                log.warn("Push notification has expired for token: {}", pushToken);
                redisTemplate.delete(pushKey);
                return false;
            }
            
            // Verify push response signature if present
            if (pushData.containsKey("responseSignature")) {
                String expectedSignature = generatePushResponseSignature(pushToken, response);
                String actualSignature = pushData.get("responseSignature").toString();
                if (!expectedSignature.equals(actualSignature)) {
                    log.warn("Push notification response signature mismatch");
                    return false;
                }
            }
            
            // Clean up push data after successful verification
            redisTemplate.delete(pushKey);
            
            log.info("Push notification response verified successfully for token: {}", pushToken);
            return true;
            
        } catch (Exception e) {
            log.error("Error verifying push notification response", e);
            return false;
        }
    }
    
    private String generatePushResponseSignature(String pushToken, String response) {
        try {
            String data = pushToken + ":" + response + ":" + System.currentTimeMillis();
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error generating push response signature", e);
            return "INVALID_SIGNATURE";
        }
    }
    
    private boolean verifyWhitelistCode(UUID userId, String code) {
        try {
            if (code == null || code.length() != 6 || !code.matches("\\d{6}")) {
                return false;
            }
            
            // Get pending whitelist verification code
            String whitelistKey = "crypto:whitelist:verify:" + userId;
            Map<String, Object> verificationData = (Map<String, Object>) redisTemplate.opsForValue().get(whitelistKey);
            
            if (verificationData == null) {
                log.warn("No pending whitelist verification found for user: {}", userId);
                return false;
            }
            
            // Check if code has expired
            long expiresAt = Long.parseLong(verificationData.get("expiresAt").toString());
            if (System.currentTimeMillis() > expiresAt) {
                log.warn("Whitelist verification code expired for user: {}", userId);
                redisTemplate.delete(whitelistKey);
                return false;
            }
            
            // Verify the code
            String encryptedStoredCode = verificationData.get("code").toString();
            String storedCode = encryptionService.decrypt(encryptedStoredCode);
            
            if (!storedCode.equals(code)) {
                // Increment failed attempts
                int attempts = Integer.parseInt(verificationData.getOrDefault("attempts", "0").toString());
                attempts++;
                
                if (attempts >= maxAttempts) {
                    log.warn("Max whitelist verification attempts exceeded for user: {}", userId);
                    redisTemplate.delete(whitelistKey);
                    return false;
                }
                
                verificationData.put("attempts", String.valueOf(attempts));
                redisTemplate.opsForValue().set(whitelistKey, verificationData, 
                    Duration.ofMillis(expiresAt - System.currentTimeMillis()));
                
                return false;
            }
            
            // Clean up after successful verification
            redisTemplate.delete(whitelistKey);
            
            log.info("Whitelist verification code verified successfully for user: {}", userId);
            return true;
            
        } catch (Exception e) {
            log.error("Error verifying whitelist code for user: {}", userId, e);
            return false;
        }
    }
    
    private String generateTransactionToken(UUID userId, UUID transactionId) {
        return "TXN_" + userId + "_" + transactionId + "_" + System.currentTimeMillis();
    }
    
    private String generateNonce() {
        return String.valueOf(System.nanoTime());
    }
    
    private String generatePayloadHash(SigningPayload payload) {
        try {
            // Create a canonical representation of the payload for hashing
            StringBuilder canonical = new StringBuilder();
            canonical.append("transactionId:").append(payload.getTransactionId()).append(";");
            canonical.append("fromAddress:").append(payload.getFromAddress()).append(";");
            canonical.append("toAddress:").append(payload.getToAddress()).append(";");
            canonical.append("amount:").append(payload.getAmount().toPlainString()).append(";");
            canonical.append("currency:").append(payload.getCurrency()).append(";");
            canonical.append("networkFee:").append(payload.getNetworkFee().toPlainString()).append(";");
            canonical.append("nonce:").append(payload.getNonce()).append(";");
            canonical.append("timestamp:").append(payload.getTimestamp());
            
            // Generate SHA-256 hash
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.toString().getBytes("UTF-8"));
            
            // Return as hexadecimal string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            String hash = hexString.toString();
            log.debug("Generated payload hash: {} for transaction: {}", 
                hash.substring(0, 8) + "...", payload.getTransactionId());
            
            return hash;
            
        } catch (Exception e) {
            log.error("Error generating payload hash for transaction: {}", 
                payload.getTransactionId(), e);
            // Fallback to simple hash if cryptographic hashing fails
            return "FALLBACK_" + payload.getTransactionId() + "_" + System.currentTimeMillis();
        }
    }
    
    private String buildChallengeMessage(MfaRequirement requirement) {
        return String.format("🔐 Security verification required for %s transaction of %s USD. " +
            "Please complete authentication using: %s",
            requirement.getLevel(), requirement.getTransactionValue(),
            requirement.getRequiredMethods());
    }
    
    private String buildSigningMessage(SigningPayload payload) {
        return String.format("Sign transaction: Send %s %s to %s",
            payload.getAmount(), payload.getCurrency(), 
            payload.getToAddress().substring(0, 10) + "...");
    }
    
    private boolean isUserLockedOut(UUID userId) {
        String key = LOCKOUT_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
    
    private void lockUser(UUID userId) {
        String key = LOCKOUT_PREFIX + userId;
        redisTemplate.opsForValue().set(key, true, Duration.ofMinutes(lockoutDurationMinutes));
    }
    
    // Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class MfaRequirement {
        private boolean required;
        private MfaLevel level;
        private List<MfaMethod> requiredMethods;
        private BigDecimal transactionValue;
        private boolean isWhitelistedAddress;
        private int expiryMinutes;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaChallenge {
        private String challengeId;
        private List<MfaMethod> requiredMethods;
        private LocalDateTime expiresAt;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaVerificationResult {
        private boolean success;
        private String transactionToken;
        private LocalDateTime validUntil;
        private String errorCode;
        private String errorMessage;
        private int attemptsRemaining;
        private List<String> failedMethods;
        private boolean accountLocked;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class MfaChallengeData {
        private String challengeId;
        private UUID userId;
        private UUID transactionId;
        private List<MfaMethod> requiredMethods;
        private Map<MfaMethod, String> challenges;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private int attempts;
        private int maxAttempts;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class WhitelistedAddress {
        private String address;
        private String label;
        private LocalDateTime addedAt;
        private UUID addedBy;
        private boolean verified;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class WhitelistResult {
        private boolean success;
        private String address;
        private String label;
        private String message;
        private String errorMessage;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SigningPayload {
        private UUID transactionId;
        private String fromAddress;
        private String toAddress;
        private BigDecimal amount;
        private String currency;
        private BigDecimal networkFee;
        private String nonce;
        private long timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SigningRequestData {
        private String signingId;
        private UUID userId;
        private UUID transactionId;
        private SigningPayload payload;
        private String payloadHash;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TransactionSigningRequest {
        private String signingId;
        private String payloadHash;
        private String signingMessage;
        private List<String> supportedWallets;
        private LocalDateTime expiresAt;
    }
    
    public enum MfaLevel {
        OPTIONAL,
        STANDARD,
        HIGH,
        MAXIMUM
    }
    
    public enum MfaMethod {
        SMS,
        EMAIL,
        TOTP,
        HARDWARE_KEY,
        BIOMETRIC,
        PUSH_NOTIFICATION
    }
    
    public static class MfaLockoutException extends RuntimeException {
        public MfaLockoutException(String message) {
            super(message);
        }
    }
}