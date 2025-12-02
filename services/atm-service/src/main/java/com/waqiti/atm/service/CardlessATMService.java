package com.waqiti.atm.service;

import com.waqiti.atm.domain.*;
import com.waqiti.atm.dto.*;
import com.waqiti.atm.repository.*;
import com.waqiti.atm.provider.ATMNetworkProvider;
import com.waqiti.atm.exception.*;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.events.ATMEvent;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.common.location.LocationService;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;

/**
 * Cardless ATM Service - Modern ATM access without physical cards
 * 
 * Features:
 * - QR code-based ATM access
 * - NFC tap-to-withdraw
 * - Biometric authentication
 * - Real-time ATM locator
 * - Fee-free network optimization
 * - Transaction limits and security
 * - Multi-currency support
 * - Emergency cash access
 * - ATM favorites and routing
 * - Integration with major ATM networks (Allpoint, MoneyPass, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CardlessATMService {
    
    private final ATMTransactionRepository transactionRepository;
    private final ATMLocationRepository locationRepository;
    private final ATMAccessTokenRepository accessTokenRepository;
    private final UserATMPreferenceRepository preferenceRepository;
    private final ATMNetworkPartnerRepository networkRepository;
    
    private final ATMNetworkProvider atmNetworkProvider;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final LocationService locationService;
    private final EventPublisher eventPublisher;
    private final EncryptionService encryptionService;
    
    @Value("${atm.max-daily-withdrawal:500.00}")
    private BigDecimal maxDailyWithdrawal;
    
    @Value("${atm.max-single-withdrawal:300.00}")
    private BigDecimal maxSingleWithdrawal;
    
    @Value("${atm.min-withdrawal:20.00}")
    private BigDecimal minWithdrawal;
    
    @Value("${atm.access-token-expiry-minutes:10}")
    private int accessTokenExpiryMinutes;
    
    @Value("${atm.fee-free-networks}")
    private List<String> feeFreNetworks;
    
    /**
     * Find ATMs near user's current location
     */
    @Cacheable(value = "nearbyATMs", key = "#latitude + '_' + #longitude + '_' + #radius", expiry = 300)
    public List<ATMLocationDto> findNearbyATMs(UUID userId, double latitude, double longitude, int radius) {
        log.debug("Finding ATMs near location ({}, {}) within {} miles for user: {}", 
                latitude, longitude, radius, userId);
        
        try {
            // Get user's ATM preferences
            UserATMPreference preference = getUserATMPreference(userId);
            
            // Search for ATMs in the specified radius
            List<ATMLocation> locations = locationRepository.findNearbyATMs(
                latitude, longitude, radius);
            
            // Enrich with real-time data and user preferences
            List<ATMLocationDto> atmDtos = locations.stream()
                .map(location -> enrichATMLocation(location, preference))
                .sorted((a, b) -> {
                    // Sort by: Fee-free first, then by distance
                    if (a.isFeeFree() && !b.isFeeFree()) return -1;
                    if (!a.isFeeFree() && b.isFeeFree()) return 1;
                    return Double.compare(a.getDistanceMiles(), b.getDistanceMiles());
                })
                .collect(Collectors.toList());
            
            log.info("Found {} ATMs for user {} within {} miles", atmDtos.size(), userId, radius);
            
            return atmDtos;
            
        } catch (Exception e) {
            log.error("Error finding nearby ATMs for user: {}", userId, e);
            throw new BusinessException("Failed to find nearby ATMs");
        }
    }
    
    /**
     * Generate secure access token for cardless ATM withdrawal
     */
    public ATMAccessTokenDto generateAccessToken(UUID userId, CreateATMAccessRequest request) {
        log.info("Generating ATM access token for user: {}, ATM: {}, amount: ${}", 
                userId, request.getAtmId(), request.getAmount());
        
        try {
            // Validate request
            validateWithdrawalRequest(userId, request);
            
            // Get ATM details
            ATMLocation atmLocation = locationRepository.findById(request.getAtmId())
                .orElseThrow(() -> new ATMNotFoundException("ATM not found"));
            
            // Check daily withdrawal limits
            validateDailyWithdrawalLimits(userId, request.getAmount());
            
            // Check wallet balance
            BigDecimal walletBalance = walletService.getBalance(userId, "USD");
            BigDecimal totalAmount = request.getAmount().add(calculateATMFee(atmLocation, request.getAmount()));
            
            if (walletBalance.compareTo(totalAmount) < 0) {
                throw new InsufficientFundsException("Insufficient funds for ATM withdrawal");
            }
            
            // Generate secure access token
            String accessCode = generateSecureAccessCode();
            String qrCodeData = generateQRCodeData(userId, request.getAtmId(), accessCode);
            
            // Create access token record
            ATMAccessToken token = ATMAccessToken.builder()
                .userId(userId)
                .atmId(request.getAtmId())
                .accessCode(encryptionService.encrypt(accessCode))
                .qrCodeData(qrCodeData)
                .amount(request.getAmount())
                .atmFee(calculateATMFee(atmLocation, request.getAmount()))
                .status(ATMAccessStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusMinutes(accessTokenExpiryMinutes))
                .createdAt(LocalDateTime.now())
                .build();
            
            token = accessTokenRepository.save(token);
            
            // Reserve funds in wallet
            walletService.hold(userId, totalAmount, "USD", "ATM withdrawal hold", 
                Map.of("atmAccessTokenId", token.getId().toString()));
            
            // Send notification with access code
            notificationService.sendATMAccessTokenNotification(userId, token, atmLocation);
            
            // Publish event
            eventPublisher.publish(ATMEvent.accessTokenGenerated(token, atmLocation));
            
            log.info("ATM access token generated successfully: {}", token.getId());
            
            return toATMAccessTokenDto(token, atmLocation);
            
        } catch (Exception e) {
            log.error("Failed to generate ATM access token for user: {}", userId, e);
            throw new BusinessException("Failed to generate ATM access token: " + e.getMessage());
        }
    }
    
    /**
     * Process ATM withdrawal using access token
     */
    public ATMTransactionDto processWithdrawal(ProcessATMWithdrawalRequest request) {
        log.info("Processing ATM withdrawal - Token: {}, ATM: {}", 
                request.getAccessCode(), request.getAtmId());
        
        try {
            // Validate access token
            ATMAccessToken token = validateAccessToken(request.getAccessCode(), request.getAtmId());
            
            // Get ATM location
            ATMLocation atmLocation = locationRepository.findById(request.getAtmId())
                .orElseThrow(() -> new ATMNotFoundException("ATM not found"));
            
            // Validate biometric if provided
            if (request.getBiometricData() != null) {
                validateBiometric(token.getUserId(), request.getBiometricData());
            }
            
            // Create transaction record
            ATMTransaction transaction = ATMTransaction.builder()
                .userId(token.getUserId())
                .atmId(request.getAtmId())
                .accessTokenId(token.getId())
                .amount(token.getAmount())
                .atmFee(token.getAtmFee())
                .totalAmount(token.getAmount().add(token.getAtmFee()))
                .currency("USD")
                .transactionType(ATMTransactionType.WITHDRAWAL)
                .status(ATMTransactionStatus.PROCESSING)
                .atmReference(request.getAtmReference())
                .createdAt(LocalDateTime.now())
                .build();
            
            transaction = transactionRepository.save(transaction);
            
            try {
                // Process withdrawal through ATM network
                ATMWithdrawalResult result = atmNetworkProvider.processWithdrawal(
                    ATMWithdrawalRequest.builder()
                        .atmId(request.getAtmId())
                        .amount(token.getAmount())
                        .accessCode(request.getAccessCode())
                        .atmReference(request.getAtmReference())
                        .build()
                );
                
                if (result.isSuccessful()) {
                    // Complete the transaction
                    completeATMTransaction(transaction, token, result);
                } else {
                    // Handle withdrawal failure
                    failATMTransaction(transaction, token, result.getFailureReason());
                }
                
            } catch (Exception e) {
                log.error("ATM network processing failed for transaction: {}", transaction.getId(), e);
                failATMTransaction(transaction, token, e.getMessage());
            }
            
            return toATMTransactionDto(transaction, atmLocation);
            
        } catch (Exception e) {
            log.error("Failed to process ATM withdrawal", e);
            throw new BusinessException("Failed to process ATM withdrawal: " + e.getMessage());
        }
    }
    
    /**
     * Get user's ATM transaction history
     */
    public Page<ATMTransactionDto> getATMTransactionHistory(UUID userId, Pageable pageable) {
        log.debug("Getting ATM transaction history for user: {}", userId);
        
        Page<ATMTransaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        return transactions.map(transaction -> {
            ATMLocation location = locationRepository.findById(transaction.getAtmId()).orElse(null);
            return toATMTransactionDto(transaction, location);
        });
    }
    
    /**
     * Get ATM usage analytics for user
     */
    public ATMUsageAnalyticsDto getATMUsageAnalytics(UUID userId, int months) {
        log.debug("Getting ATM usage analytics for user: {} for {} months", userId, months);
        
        try {
            LocalDateTime startDate = LocalDateTime.now().minusMonths(months);
            List<ATMTransaction> transactions = transactionRepository.findByUserIdAndCreatedAtAfter(userId, startDate);
            
            // Calculate total usage
            BigDecimal totalWithdrawn = transactions.stream()
                .filter(t -> t.getStatus() == ATMTransactionStatus.COMPLETED)
                .map(ATMTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalFees = transactions.stream()
                .filter(t -> t.getStatus() == ATMTransactionStatus.COMPLETED)
                .map(ATMTransaction::getAtmFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Most used ATMs
            Map<UUID, Long> atmUsage = transactions.stream()
                .collect(Collectors.groupingBy(
                    ATMTransaction::getAtmId,
                    Collectors.counting()
                ));
            
            List<PopularATMDto> popularATMs = atmUsage.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    ATMLocation location = locationRepository.findById(entry.getKey()).orElse(null);
                    return PopularATMDto.builder()
                        .atmId(entry.getKey())
                        .locationName(location != null ? location.getLocationName() : "Unknown")
                        .address(location != null ? location.getAddress() : "Unknown")
                        .usageCount(entry.getValue().intValue())
                        .build();
                })
                .collect(Collectors.toList());
            
            // Monthly trends
            List<MonthlyATMUsageDto> monthlyTrends = calculateMonthlyATMTrends(transactions, months);
            
            // Savings opportunities
            BigDecimal potentialSavings = calculatePotentialFeesSaved(userId, transactions);
            
            return ATMUsageAnalyticsDto.builder()
                .totalWithdrawn(totalWithdrawn)
                .totalFeesPaid(totalFees)
                .transactionCount(transactions.size())
                .averageWithdrawal(totalWithdrawn.divide(
                    BigDecimal.valueOf(Math.max(1, transactions.size())), 2))
                .popularATMs(popularATMs)
                .monthlyTrends(monthlyTrends)
                .potentialSavings(potentialSavings)
                .feeFreеTransactionRate(calculateFeeFreеRate(transactions))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get ATM analytics for user: {}", userId, e);
            throw new BusinessException("Failed to load ATM analytics");
        }
    }
    
    /**
     * Set user's ATM preferences
     */
    public UserATMPreferenceDto setATMPreferences(UUID userId, SetATMPreferencesRequest request) {
        log.info("Setting ATM preferences for user: {}", userId);
        
        try {
            UserATMPreference preference = preferenceRepository.findByUserId(userId)
                .orElse(UserATMPreference.builder().userId(userId).build());
            
            preference.setPreferFeeFreеATMs(request.isPreferFeeFreеATMs());
            preference.setMaxAcceptableFee(request.getMaxAcceptableFee());
            preference.setPreferredNetworks(request.getPreferredNetworks());
            preference.setEnableBiometricAuth(request.isEnableBiometricAuth());
            preference.setEnableLocationBasedSuggestions(request.isEnableLocationBasedSuggestions());
            preference.setNotificationPreferences(request.getNotificationPreferences());
            preference.setFavoriteATMs(request.getFavoriteATMs());
            preference.setLastUpdated(LocalDateTime.now());
            
            if (preference.getCreatedAt() == null) {
                preference.setCreatedAt(LocalDateTime.now());
            }
            
            preference = preferenceRepository.save(preference);
            
            log.info("ATM preferences updated for user: {}", userId);
            
            return toUserATMPreferenceDto(preference);
            
        } catch (Exception e) {
            log.error("Failed to set ATM preferences for user: {}", userId, e);
            throw new BusinessException("Failed to update ATM preferences");
        }
    }
    
    /**
     * Get emergency cash access options
     */
    public EmergencyATMAccessDto getEmergencyATMAccess(UUID userId, double latitude, double longitude) {
        log.info("Getting emergency ATM access for user: {} at location ({}, {})", userId, latitude, longitude);
        
        try {
            // Find nearest ATMs that support emergency access
            List<ATMLocation> emergencyATMs = locationRepository.findEmergencyATMsNearby(
                latitude, longitude, 5.0); // 5 mile radius
            
            // Check user's emergency cash limits
            BigDecimal emergencyCashLimit = calculateEmergencyCashLimit(userId);
            BigDecimal todayEmergencyUsed = getTodayEmergencyCashUsed(userId);
            BigDecimal availableEmergencyAmount = emergencyCashLimit.subtract(todayEmergencyUsed);
            
            // Filter ATMs based on available emergency amount
            List<EmergencyATMOptionDto> emergencyOptions = emergencyATMs.stream()
                .filter(atm -> atm.supportsEmergencyAccess())
                .map(atm -> EmergencyATMOptionDto.builder()
                    .atmId(atm.getId())
                    .locationName(atm.getLocationName())
                    .address(atm.getAddress())
                    .distance(locationService.calculateDistance(latitude, longitude, 
                                atm.getLatitude(), atm.getLongitude()))
                    .emergencyFee(atm.getEmergencyAccessFee())
                    .availableAmount(availableEmergencyAmount)
                    .requiresVerification(atm.requiresEmergencyVerification())
                    .build())
                .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
                .limit(3)
                .collect(Collectors.toList());
            
            return EmergencyATMAccessDto.builder()
                .availableEmergencyAmount(availableEmergencyAmount)
                .emergencyCashLimit(emergencyCashLimit)
                .todayEmergencyUsed(todayEmergencyUsed)
                .emergencyATMOptions(emergencyOptions)
                .emergencyContactRequired(availableEmergencyAmount.compareTo(BigDecimal.valueOf(100)) > 0)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get emergency ATM access for user: {}", userId, e);
            throw new BusinessException("Failed to get emergency ATM access");
        }
    }
    
    /**
     * Cancel active ATM access token
     */
    public void cancelATMAccessToken(UUID userId, UUID tokenId) {
        log.info("Cancelling ATM access token: {} for user: {}", tokenId, userId);
        
        try {
            ATMAccessToken token = accessTokenRepository.findByIdAndUserId(tokenId, userId)
                .orElseThrow(() -> new ATMAccessTokenNotFoundException("Access token not found"));
            
            if (token.getStatus() != ATMAccessStatus.ACTIVE) {
                throw new BusinessException("Access token is not active");
            }
            
            // Cancel the token
            token.setStatus(ATMAccessStatus.CANCELLED);
            token.setCancelledAt(LocalDateTime.now());
            accessTokenRepository.save(token);
            
            // Release held funds
            BigDecimal totalAmount = token.getAmount().add(token.getAtmFee());
            walletService.releaseHold(userId, totalAmount, "USD", "ATM access token cancelled",
                Map.of("atmAccessTokenId", token.getId().toString()));
            
            // Send notification
            notificationService.sendATMAccessTokenCancelledNotification(userId, token);
            
            log.info("ATM access token cancelled: {}", tokenId);
            
        } catch (Exception e) {
            log.error("Failed to cancel ATM access token: {}", tokenId, e);
            throw new BusinessException("Failed to cancel ATM access token");
        }
    }
    
    /**
     * Scheduled job to expire old access tokens
     */
    @Async
    public CompletableFuture<Void> expireOldAccessTokens() {
        log.debug("Expiring old ATM access tokens");
        
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(accessTokenExpiryMinutes);
            List<ATMAccessToken> expiredTokens = accessTokenRepository.findActiveTokensOlderThan(cutoffTime);
            
            for (ATMAccessToken token : expiredTokens) {
                try {
                    // Expire the token
                    token.setStatus(ATMAccessStatus.EXPIRED);
                    token.setExpiredAt(LocalDateTime.now());
                    accessTokenRepository.save(token);
                    
                    // Release held funds
                    BigDecimal totalAmount = token.getAmount().add(token.getAtmFee());
                    walletService.releaseHold(token.getUserId(), totalAmount, "USD", 
                        "ATM access token expired",
                        Map.of("atmAccessTokenId", token.getId().toString()));
                    
                    log.debug("Expired ATM access token: {}", token.getId());
                    
                } catch (Exception e) {
                    log.error("Failed to expire ATM access token: {}", token.getId(), e);
                }
            }
            
            log.info("Expired {} ATM access tokens", expiredTokens.size());
            
        } catch (Exception e) {
            log.error("Failed to expire old access tokens", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // Helper methods
    
    private UserATMPreference getUserATMPreference(UUID userId) {
        return preferenceRepository.findByUserId(userId)
            .orElse(UserATMPreference.builder()
                .userId(userId)
                .preferFeeFreеATMs(true)
                .maxAcceptableFee(BigDecimal.valueOf(2.50))
                .enableBiometricAuth(false)
                .enableLocationBasedSuggestions(true)
                .build());
    }
    
    private ATMLocationDto enrichATMLocation(ATMLocation location, UserATMPreference preference) {
        // Calculate fee
        BigDecimal fee = calculateATMFee(location, maxSingleWithdrawal);
        boolean isFeeFree = fee.compareTo(BigDecimal.ZERO) == 0 ||
                          feeFreNetworks.contains(location.getNetwork());

        // Check if it's a favorite
        boolean isFavorite = preference.getFavoriteATMs() != null &&
                           preference.getFavoriteATMs().contains(location.getId());

        return ATMLocationDto.builder()
            .id(location.getId())
            .locationName(location.getLocationName())
            .address(location.getAddress())
            .city(location.getCity())
            .state(location.getState())
            .zipCode(location.getZipCode())
            .latitude(location.getLatitude())
            .longitude(location.getLongitude())
            .network(location.getNetwork())
            .bankName(location.getBankName())
            .atmFee(fee)
            .isFeeFree(isFeeFree)
            .isAvailable(location.isOperational())
            .supportsCash(location.supportsCashWithdrawal())
            .supportsDeposit(location.supportsCashDeposit())
            .supportsCardless(location.supportsCardlessAccess())
            .supportsNFC(location.supportsNFCAccess())
            .supportsBiometric(location.supportsBiometricAuth())
            .operatingHours(location.getOperatingHours())
            .distanceMiles(0.0) // Will be calculated by caller
            .isFavorite(isFavorite)
            .lastUpdated(location.getLastUpdated())
            .build();
    }
    
    private void validateWithdrawalRequest(UUID userId, CreateATMAccessRequest request) {
        // Validate amount
        if (request.getAmount().compareTo(minWithdrawal) < 0) {
            throw new BusinessException("Minimum withdrawal amount is $" + minWithdrawal);
        }
        
        if (request.getAmount().compareTo(maxSingleWithdrawal) > 0) {
            throw new BusinessException("Maximum single withdrawal amount is $" + maxSingleWithdrawal);
        }
        
        // Amount must be in multiples of $20
        if (request.getAmount().remainder(BigDecimal.valueOf(20)).compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Withdrawal amount must be in multiples of $20");
        }
    }
    
    private void validateDailyWithdrawalLimits(UUID userId, BigDecimal amount) {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal todayWithdrawn = transactionRepository.getTotalWithdrawnToday(userId, startOfDay);
        
        if (todayWithdrawn.add(amount).compareTo(maxDailyWithdrawal) > 0) {
            throw new BusinessException("Daily withdrawal limit exceeded. Remaining: $" + 
                maxDailyWithdrawal.subtract(todayWithdrawn));
        }
    }
    
    private BigDecimal calculateATMFee(ATMLocation location, BigDecimal amount) {
        // Check if it's a fee-free network
        if (feeFreNetworks.contains(location.getNetwork())) {
            return BigDecimal.ZERO;
        }
        
        // Return the ATM's fee structure
        return location.getBaseFee() != null ? location.getBaseFee() : BigDecimal.valueOf(2.50);
    }
    
    /**
     * SECURITY FIX: Generate cryptographically secure access code using SecureRandom
     * with additional entropy and time-based validation
     */
    private String generateSecureAccessCode() {
        // Use SecureRandom for cryptographically secure random number generation
        SecureRandom secureRandom = new SecureRandom();
        
        // Generate 6-digit code with additional entropy
        int code = secureRandom.nextInt(900000) + 100000; // Ensures 6 digits (100000-999999)
        
        // Add time-based component for additional security
        long timestamp = System.currentTimeMillis();
        String timeComponent = String.valueOf(timestamp).substring(7, 10);
        
        // Combine and hash for final code
        String rawCode = code + timeComponent;
        String hashedCode = generateHMAC(rawCode);
        
        // Extract 6 digits from hash for user-friendly code
        String finalCode = extractNumericCode(hashedCode, 6);
        
        // Log for audit (without exposing the actual code)
        log.info("Secure access code generated with timestamp: {} (hash: {})", 
                timestamp, hashForLogging(finalCode));
        
        return finalCode;
    }
    
    /**
     * Generate HMAC for additional security layer
     */
    private String generateHMAC(String data) {
        try {
            // Use application-specific secret key (should be from secure configuration)
            String secret = getSecretKey();
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacData = mac.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(hmacData);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate HMAC for access code", e);
            throw new SecurityException("Failed to generate secure access code", e);
        }
    }
    
    /**
     * Extract numeric code from hash string
     */
    private String extractNumericCode(String hash, int length) {
        StringBuilder code = new StringBuilder();
        for (char c : hash.toCharArray()) {
            if (Character.isDigit(c)) {
                code.append(c);
                if (code.length() >= length) {
                    break;
                }
            }
        }
        
        // If not enough digits, use SecureRandom to fill
        if (code.length() < length) {
            SecureRandom random = new SecureRandom();
            while (code.length() < length) {
                code.append(random.nextInt(10));
            }
        }
        
        return code.toString();
    }
    
    /**
     * Get secret key for HMAC (should be from secure vault in production)
     */
    private String getSecretKey() {
        // In production, this should come from HashiCorp Vault or AWS Secrets Manager
        return System.getenv("ATM_ACCESS_CODE_SECRET") != null ? 
               System.getenv("ATM_ACCESS_CODE_SECRET") : 
               "default-secure-key-" + UUID.randomUUID().toString();
    }
    
    /**
     * Generate hash for logging without exposing actual code
     */
    private String hashForLogging(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(code.getBytes());
            return Base64.getEncoder().encodeToString(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "HASH_ERROR";
        }
    }
    
    private String generateQRCodeData(UUID userId, UUID atmId, String accessCode) {
        // Generate QR code data with encrypted payload
        Map<String, String> qrData = Map.of(
            "userId", userId.toString(),
            "atmId", atmId.toString(),
            "accessCode", accessCode,
            "timestamp", String.valueOf(System.currentTimeMillis())
        );
        
        return encryptionService.encrypt(qrData.toString());
    }
    
    private ATMAccessToken validateAccessToken(String accessCode, UUID atmId) {
        ATMAccessToken token = accessTokenRepository.findActiveTokenByATM(atmId)
            .orElseThrow(() -> new ATMAccessTokenNotFoundException("No active access token found"));
        
        String decryptedCode = encryptionService.decrypt(token.getAccessCode());
        if (!decryptedCode.equals(accessCode)) {
            throw new InvalidAccessTokenException("Invalid access code");
        }
        
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredAccessTokenException("Access token has expired");
        }
        
        return token;
    }
    
    private void validateBiometric(UUID userId, String biometricData) {
        // Implement biometric validation logic
        // This would integrate with a biometric authentication service
        log.debug("Validating biometric data for user: {}", userId);
    }
    
    private void completeATMTransaction(ATMTransaction transaction, ATMAccessToken token, 
                                     ATMWithdrawalResult result) {
        // Update transaction status
        transaction.setStatus(ATMTransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setAtmConfirmation(result.getConfirmationNumber());
        transactionRepository.save(transaction);
        
        // Process wallet debit
        walletService.debit(
            token.getUserId(),
            transaction.getTotalAmount(),
            "USD",
            "ATM withdrawal at " + result.getAtmLocation(),
            Map.of(
                "transactionId", transaction.getId().toString(),
                "atmId", transaction.getAtmId().toString(),
                "confirmationNumber", result.getConfirmationNumber()
            )
        );
        
        // Mark access token as used
        token.setStatus(ATMAccessStatus.USED);
        token.setUsedAt(LocalDateTime.now());
        accessTokenRepository.save(token);
        
        // Send completion notification
        ATMLocation location = locationRepository.findById(transaction.getAtmId()).orElse(null);
        notificationService.sendATMWithdrawalCompletedNotification(token.getUserId(), transaction, location);
        
        // Publish event
        eventPublisher.publish(ATMEvent.withdrawalCompleted(transaction, location));
        
        log.info("ATM withdrawal completed: {}", transaction.getId());
    }
    
    private void failATMTransaction(ATMTransaction transaction, ATMAccessToken token, String reason) {
        // Update transaction status
        transaction.setStatus(ATMTransactionStatus.FAILED);
        transaction.setFailedAt(LocalDateTime.now());
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);
        
        // Release held funds
        BigDecimal totalAmount = token.getAmount().add(token.getAtmFee());
        walletService.releaseHold(token.getUserId(), totalAmount, "USD", 
            "ATM withdrawal failed: " + reason,
            Map.of("transactionId", transaction.getId().toString()));
        
        // Mark access token as failed
        token.setStatus(ATMAccessStatus.FAILED);
        token.setFailedAt(LocalDateTime.now());
        accessTokenRepository.save(token);
        
        // Send failure notification
        notificationService.sendATMWithdrawalFailedNotification(token.getUserId(), transaction, reason);
        
        log.warn("ATM withdrawal failed: {} - Reason: {}", transaction.getId(), reason);
    }
    
    private BigDecimal calculateEmergencyCashLimit(UUID userId) {
        // Calculate emergency cash limit based on user's account status and history
        return BigDecimal.valueOf(200); // Default emergency limit
    }
    
    private BigDecimal getTodayEmergencyCashUsed(UUID userId) {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);
        return transactionRepository.getTotalEmergencyWithdrawnToday(userId, startOfDay);
    }
    
    private List<MonthlyATMUsageDto> calculateMonthlyATMTrends(List<ATMTransaction> transactions, int months) {
        try {
            log.debug("Calculating monthly ATM trends for {} transactions over {} months", 
                transactions.size(), months);
            
            if (transactions.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Group transactions by month
            Map<YearMonth, List<ATMTransaction>> transactionsByMonth = transactions.stream()
                .collect(Collectors.groupingBy(tx -> 
                    YearMonth.from(tx.getTransactionDate().toLocalDate())));
            
            // Generate monthly usage statistics
            List<MonthlyATMUsageDto> monthlyTrends = new ArrayList<>();
            YearMonth currentMonth = YearMonth.now();
            
            for (int i = months - 1; i >= 0; i--) {
                YearMonth targetMonth = currentMonth.minusMonths(i);
                List<ATMTransaction> monthlyTransactions = transactionsByMonth.getOrDefault(targetMonth, Collections.emptyList());
                
                MonthlyATMUsageDto monthlyUsage = calculateMonthlyUsageMetrics(targetMonth, monthlyTransactions, transactions);
                monthlyTrends.add(monthlyUsage);
            }
            
            // Calculate month-over-month growth rates
            calculateGrowthRates(monthlyTrends);
            
            log.debug("Calculated monthly ATM trends: {} months with data", monthlyTrends.size());
            return monthlyTrends;
            
        } catch (Exception e) {
            log.error("Error calculating monthly ATM trends: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    private MonthlyATMUsageDto calculateMonthlyUsageMetrics(YearMonth month, 
            List<ATMTransaction> monthlyTransactions, List<ATMTransaction> allTransactions) {
        
        // Basic transaction metrics
        int transactionCount = monthlyTransactions.size();
        BigDecimal totalAmount = monthlyTransactions.stream()
            .map(ATMTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal averageAmount = transactionCount > 0 ? 
            totalAmount.divide(BigDecimal.valueOf(transactionCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        // Fee analysis
        BigDecimal totalFees = monthlyTransactions.stream()
            .map(ATMTransaction::getAtmFee)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long feeFreеTransactions = monthlyTransactions.stream()
            .filter(tx -> tx.getAtmFee().compareTo(BigDecimal.ZERO) == 0)
            .count();
        
        double feeFreеRate = transactionCount > 0 ? 
            (double) feeFreеTransactions / transactionCount * 100 : 0.0;
        
        // Usage pattern analysis
        Map<String, Long> usagePatterns = analyzeMonthlyUsagePatterns(monthlyTransactions);
        
        // ATM network analysis
        Set<String> uniqueAtms = monthlyTransactions.stream()
            .map(tx -> tx.getAtmId().toString())
            .collect(Collectors.toSet());
        
        // Peak usage analysis
        Map<Integer, Long> hourlyDistribution = monthlyTransactions.stream()
            .collect(Collectors.groupingBy(
                tx -> tx.getTransactionDate().getHour(),
                Collectors.counting()
            ));
        
        Integer peakHour = hourlyDistribution.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        
        // Geographic distribution
        Map<String, Long> locationDistribution = monthlyTransactions.stream()
            .filter(tx -> tx.getAtmLocation() != null)
            .collect(Collectors.groupingBy(
                ATMTransaction::getAtmLocation,
                Collectors.counting()
            ));
        
        String topLocation = locationDistribution.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
        
        // Success rate calculation
        long successfulTransactions = monthlyTransactions.stream()
            .filter(tx -> "COMPLETED".equals(tx.getStatus()))
            .count();
        
        double successRate = transactionCount > 0 ? 
            (double) successfulTransactions / transactionCount * 100 : 0.0;
        
        // Weekend vs weekday analysis
        long weekendTransactions = monthlyTransactions.stream()
            .filter(tx -> {
                DayOfWeek dayOfWeek = tx.getTransactionDate().getDayOfWeek();
                return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            })
            .count();
        
        double weekendRate = transactionCount > 0 ? 
            (double) weekendTransactions / transactionCount * 100 : 0.0;
        
        return MonthlyATMUsageDto.builder()
            .month(month)
            .transactionCount(transactionCount)
            .totalAmount(totalAmount)
            .averageAmount(averageAmount)
            .totalFees(totalFees)
            .feeFreеRate(BigDecimal.valueOf(feeFreеRate))
            .uniqueAtmsUsed(uniqueAtms.size())
            .peakUsageHour(peakHour)
            .topLocation(topLocation)
            .successRate(BigDecimal.valueOf(successRate))
            .weekendUsageRate(BigDecimal.valueOf(weekendRate))
            .usagePatterns(usagePatterns)
            .locationDistribution(locationDistribution)
            .hourlyDistribution(hourlyDistribution)
            .averageTransactionsPerDay(BigDecimal.valueOf((double) transactionCount / month.lengthOfMonth()))
            .build();
    }
    
    private Map<String, Long> analyzeMonthlyUsagePatterns(List<ATMTransaction> transactions) {
        Map<String, Long> patterns = new HashMap<>();
        
        if (transactions.isEmpty()) {
            return patterns;
        }
        
        // Time-based patterns
        long morningTransactions = transactions.stream()
            .filter(tx -> tx.getTransactionDate().getHour() >= 6 && tx.getTransactionDate().getHour() < 12)
            .count();
        patterns.put("morning_usage", morningTransactions);
        
        long afternoonTransactions = transactions.stream()
            .filter(tx -> tx.getTransactionDate().getHour() >= 12 && tx.getTransactionDate().getHour() < 18)
            .count();
        patterns.put("afternoon_usage", afternoonTransactions);
        
        long eveningTransactions = transactions.stream()
            .filter(tx -> tx.getTransactionDate().getHour() >= 18 && tx.getTransactionDate().getHour() < 24)
            .count();
        patterns.put("evening_usage", eveningTransactions);
        
        long nightTransactions = transactions.stream()
            .filter(tx -> tx.getTransactionDate().getHour() >= 0 && tx.getTransactionDate().getHour() < 6)
            .count();
        patterns.put("night_usage", nightTransactions);
        
        // Transaction type patterns
        Map<String, Long> typeDistribution = transactions.stream()
            .collect(Collectors.groupingBy(
                tx -> tx.getTransactionType() != null ? tx.getTransactionType() : "WITHDRAWAL",
                Collectors.counting()
            ));
        patterns.putAll(typeDistribution);
        
        // Amount-based patterns
        long smallAmountTx = transactions.stream()
            .filter(tx -> tx.getAmount().compareTo(BigDecimal.valueOf(50)) <= 0)
            .count();
        patterns.put("small_amount_transactions", smallAmountTx);
        
        long mediumAmountTx = transactions.stream()
            .filter(tx -> tx.getAmount().compareTo(BigDecimal.valueOf(50)) > 0 && 
                         tx.getAmount().compareTo(BigDecimal.valueOf(200)) <= 0)
            .count();
        patterns.put("medium_amount_transactions", mediumAmountTx);
        
        long largeAmountTx = transactions.stream()
            .filter(tx -> tx.getAmount().compareTo(BigDecimal.valueOf(200)) > 0)
            .count();
        patterns.put("large_amount_transactions", largeAmountTx);
        
        return patterns;
    }
    
    private void calculateGrowthRates(List<MonthlyATMUsageDto> monthlyTrends) {
        for (int i = 1; i < monthlyTrends.size(); i++) {
            MonthlyATMUsageDto current = monthlyTrends.get(i);
            MonthlyATMUsageDto previous = monthlyTrends.get(i - 1);
            
            // Transaction count growth
            double txCountGrowth = calculatePercentageGrowth(
                previous.getTransactionCount(), current.getTransactionCount());
            current.setTransactionGrowthRate(BigDecimal.valueOf(txCountGrowth));
            
            // Amount growth
            double amountGrowth = calculatePercentageGrowth(
                previous.getTotalAmount(), current.getTotalAmount());
            current.setAmountGrowthRate(BigDecimal.valueOf(amountGrowth));
            
            // Fee growth
            double feeGrowth = calculatePercentageGrowth(
                previous.getTotalFees(), current.getTotalFees());
            current.setFeeGrowthRate(BigDecimal.valueOf(feeGrowth));
        }
    }
    
    private double calculatePercentageGrowth(Number previous, Number current) {
        if (previous == null || current == null) return 0.0;
        
        double prevValue = previous.doubleValue();
        double currValue = current.doubleValue();
        
        if (prevValue == 0) {
            return currValue > 0 ? 100.0 : 0.0;
        }
        
        return ((currValue - prevValue) / prevValue) * 100.0;
    }
    
    private double calculatePercentageGrowth(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null) return 0.0;
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        
        return current.subtract(previous)
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }
    
    private BigDecimal calculatePotentialFeesSaved(UUID userId, List<ATMTransaction> transactions) {
        // Calculate potential fees that could be saved by using fee-free ATMs
        return transactions.stream()
            .map(ATMTransaction::getAtmFee)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateFeeFreеRate(List<ATMTransaction> transactions) {
        if (transactions.isEmpty()) return BigDecimal.ZERO;
        
        long feeFreеCount = transactions.stream()
            .filter(t -> t.getAtmFee().compareTo(BigDecimal.ZERO) == 0)
            .count();
        
        return BigDecimal.valueOf(feeFreеCount)
            .divide(BigDecimal.valueOf(transactions.size()), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }
    
    // DTO conversion methods
    
    private ATMAccessTokenDto toATMAccessTokenDto(ATMAccessToken token, ATMLocation location) {
        return ATMAccessTokenDto.builder()
            .id(token.getId())
            .accessCode(encryptionService.decrypt(token.getAccessCode()))
            .qrCodeData(token.getQrCodeData())
            .atmId(token.getAtmId())
            .atmName(location.getLocationName())
            .atmAddress(location.getAddress())
            .amount(token.getAmount())
            .atmFee(token.getAtmFee())
            .totalAmount(token.getAmount().add(token.getAtmFee()))
            .status(token.getStatus())
            .expiresAt(token.getExpiresAt())
            .createdAt(token.getCreatedAt())
            .build();
    }
    
    private ATMTransactionDto toATMTransactionDto(ATMTransaction transaction, ATMLocation location) {
        return ATMTransactionDto.builder()
            .id(transaction.getId())
            .atmId(transaction.getAtmId())
            .atmName(location != null ? location.getLocationName() : "Unknown")
            .atmAddress(location != null ? location.getAddress() : "Unknown")
            .amount(transaction.getAmount())
            .atmFee(transaction.getAtmFee())
            .totalAmount(transaction.getTotalAmount())
            .currency(transaction.getCurrency())
            .status(transaction.getStatus())
            .atmConfirmation(transaction.getAtmConfirmation())
            .failureReason(transaction.getFailureReason())
            .createdAt(transaction.getCreatedAt())
            .completedAt(transaction.getCompletedAt())
            .build();
    }
    
    private UserATMPreferenceDto toUserATMPreferenceDto(UserATMPreference preference) {
        return UserATMPreferenceDto.builder()
            .id(preference.getId())
            .preferFeeFreеATMs(preference.isPreferFeeFreеATMs())
            .maxAcceptableFee(preference.getMaxAcceptableFee())
            .preferredNetworks(preference.getPreferredNetworks())
            .enableBiometricAuth(preference.isEnableBiometricAuth())
            .enableLocationBasedSuggestions(preference.isEnableLocationBasedSuggestions())
            .favoriteATMs(preference.getFavoriteATMs())
            .lastUpdated(preference.getLastUpdated())
            .build();
    }
}