package com.waqiti.payment.service;

import com.waqiti.payment.client.CheckProcessingClient;
import com.waqiti.payment.client.FraudDetectionClient;
import com.waqiti.payment.client.ImageStorageClient;
import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.*;
import com.waqiti.payment.exception.*;
import com.waqiti.payment.repository.CheckDepositRepository;
import com.waqiti.payment.util.MICRParser;
import com.waqiti.payment.util.MICRParser.MICRData;
import com.waqiti.payment.util.CheckImageProcessor;
import com.waqiti.payment.util.CheckImageProcessor.ImageQualityResult;
import com.waqiti.payment.util.CheckImageProcessor.AmountExtractionResult;
import com.waqiti.payment.util.CheckImageProcessor.CheckDetails;
import com.waqiti.payment.util.RoutingNumberValidator;
import com.waqiti.common.security.encryption.KeyManagementService;
import com.waqiti.common.security.encryption.EncryptedData;
import com.waqiti.payment.exception.CheckDepositException;
import com.waqiti.common.exception.EncryptionException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for handling mobile check deposits
 * Provides check image processing, validation, fraud detection, and deposit processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CheckDepositService {
    
    private final CheckDepositRepository checkDepositRepository;
    private final CheckProcessingClient checkProcessingClient;
    private final FraudDetectionClient fraudDetectionClient;
    private final ImageStorageClient imageStorageClient;
    private final UnifiedWalletServiceClient walletServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final CheckImageProcessor checkImageProcessor;
    private final MICRParser micrParser;
    private final KeyManagementService keyManagementService;
    
    @Value("${check.deposit.daily-limit:5000}")
    private BigDecimal dailyLimit;
    
    @Value("${check.deposit.monthly-limit:20000}")
    private BigDecimal monthlyLimit;
    
    @Value("${check.deposit.single-check-limit:2500}")
    private BigDecimal singleCheckLimit;
    
    @Value("${check.deposit.new-user-limit:500}")
    private BigDecimal newUserLimit;
    
    @Value("${check.deposit.duplicate-window-days:180}")
    private int duplicateWindowDays;
    
    @Value("${check.deposit.high-risk-threshold:0.7}")
    private BigDecimal highRiskThreshold;
    
    @Value("${check.deposit.amount-variance-threshold:0.05}")
    private BigDecimal amountVarianceThreshold;
    
    // Encryption key removed - now using KeyManagementService
    
    @Value("${check.deposit.processing.enabled:true}")
    private boolean processingEnabled;
    
    private static final String CHECK_DEPOSIT_TOPIC = "check-deposits";
    private static final String CHECK_STATUS_TOPIC = "check-deposit-status";
    private static final String FRAUD_ALERT_TOPIC = "fraud-alerts";
    
    /**
     * Initiates a check deposit with comprehensive validation and processing
     */
    @Transactional
    @CircuitBreaker(name = "check-deposit", fallbackMethod = "handleCheckDepositFallback")
    @Retry(name = "check-deposit")
    public CompletableFuture<CheckDepositResponse> initiateCheckDeposit(CheckDepositRequest request) {
        log.info("Initiating check deposit for user: {} amount: {}", request.getUserId(), request.getAmount());
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Validate processing is enabled
            if (!processingEnabled) {
                throw new ServiceUnavailableException("Check deposit service is temporarily unavailable");
            }
            
            // Basic validation
            validateCheckDepositRequest(request);
            
            // Check for idempotency
            if (request.getIdempotencyKey() != null) {
                Optional<CheckDeposit> existing = checkDepositRepository
                    .findByIdempotencyKey(request.getIdempotencyKey());
                if (existing.isPresent()) {
                    return CompletableFuture.completedFuture(buildResponse(existing.get()));
                }
            }
            
            // Process check images
            CheckImageAnalysis imageAnalysis = analyzeCheckImages(request);
            
            // Check for duplicate deposits
            checkForDuplicates(imageAnalysis, request);
            
            // Check deposit limits
            checkDepositLimits(request.getUserId(), request.getAmount());
            
            // Create initial deposit record
            CheckDeposit deposit = createCheckDeposit(request, imageAnalysis);
            
            // Start asynchronous processing pipeline
            return processCheckDepositAsync(deposit, imageAnalysis)
                .whenComplete((response, error) -> {
                    meterRegistry.timer("check.deposit.duration").stop(sample);
                    if (error != null) {
                        incrementErrorCounter("initiation", error);
                    } else {
                        incrementSuccessCounter("initiation");
                    }
                });
                
        } catch (Exception e) {
            meterRegistry.timer("check.deposit.duration", "status", "error").stop(sample);
            log.error("Failed to initiate check deposit", e);
            throw new CheckDepositException("Failed to initiate check deposit", e);
        }
    }
    
    /**
     * Validates check deposit request
     */
    private void validateCheckDepositRequest(CheckDepositRequest request) {
        // Validate amount
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Amount must be greater than zero");
        }
        
        if (request.getAmount().compareTo(singleCheckLimit) > 0) {
            throw new ValidationException("Amount exceeds single check limit of $" + singleCheckLimit);
        }
        
        // Validate images are provided
        if (request.getFrontImageBase64() == null || request.getFrontImageBase64().isEmpty()) {
            throw new ValidationException("Front check image is required");
        }
        
        if (request.getBackImageBase64() == null || request.getBackImageBase64().isEmpty()) {
            throw new ValidationException("Back check image is required");
        }
        
        // Additional validation for new users
        boolean isNewUser = !checkDepositRepository.hasSuccessfulDeposits(request.getUserId());
        if (isNewUser && request.getAmount().compareTo(newUserLimit) > 0) {
            throw new ValidationException("First-time deposit limit is $" + newUserLimit);
        }
    }
    
    /**
     * Analyzes check images for quality and extracts data
     */
    private CheckImageAnalysis analyzeCheckImages(CheckDepositRequest request) {
        log.info("Analyzing check images for deposit request");
        
        try {
            // Decode and validate images
            byte[] frontImage = Base64.getDecoder().decode(request.getFrontImageBase64());
            byte[] backImage = Base64.getDecoder().decode(request.getBackImageBase64());
            
            // Check image quality
            ImageQualityResult frontQuality = checkImageProcessor.analyzeImageQuality(frontImage);
            ImageQualityResult backQuality = checkImageProcessor.analyzeImageQuality(backImage);
            
            if (!frontQuality.isAcceptable()) {
                throw new InvalidCheckImageException(
                    "Front image quality is unacceptable: " + frontQuality.getReason(),
                    "front", frontQuality.getReason()
                );
            }
            
            if (!backQuality.isAcceptable()) {
                throw new InvalidCheckImageException(
                    "Back image quality is unacceptable: " + backQuality.getReason(),
                    "back", backQuality.getReason()
                );
            }
            
            // Generate image hashes for duplicate detection
            String frontHash = generateImageHash(frontImage);
            String backHash = generateImageHash(backImage);
            
            // Extract MICR data from front image
            MICRData micrData = micrParser.extractMICR(frontImage);
            if (micrData == null || !micrData.isValid()) {
                throw new ValidationException("Unable to read MICR line from check");
            }
            
            // Extract amount from check image
            AmountExtractionResult amountResult = checkImageProcessor.extractAmount(frontImage);
            
            // Extract other check details
            CheckDetails checkDetails = checkImageProcessor.extractCheckDetails(frontImage);
            
            // Store images securely
            String frontImageUrl = imageStorageClient.uploadCheckImage(
                request.getUserId(), frontImage, "front"
            );
            String backImageUrl = imageStorageClient.uploadCheckImage(
                request.getUserId(), backImage, "back"
            );
            
            return CheckImageAnalysis.builder()
                .frontImageUrl(encryptSensitiveData(frontImageUrl))
                .backImageUrl(encryptSensitiveData(backImageUrl))
                .frontImageHash(frontHash)
                .backImageHash(backHash)
                .micrData(micrData)
                .extractedAmount(amountResult.getAmount())
                .amountConfidence(amountResult.getConfidence())
                .checkDetails(checkDetails)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to analyze check images", e);
            throw new CheckDepositException("Failed to process check images", e);
        }
    }
    
    /**
     * Checks for duplicate deposits
     */
    private void checkForDuplicates(CheckImageAnalysis analysis, CheckDepositRequest request) {
        log.info("Checking for duplicate deposits");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(duplicateWindowDays);
        
        // Check by image hash
        List<CheckDeposit> imageMatches = checkDepositRepository.findByImageHash(
            analysis.getFrontImageHash(),
            analysis.getBackImageHash(),
            cutoffDate
        );
        
        if (!imageMatches.isEmpty()) {
            CheckDeposit duplicate = imageMatches.get(0);
            throw new DuplicateCheckException(
                "This check has already been deposited",
                duplicate.getId()
            );
        }
        
        // Check by MICR data and amount if available
        if (analysis.getMicrData() != null) {
            List<CheckDeposit> micrMatches = checkDepositRepository.findPotentialDuplicates(
                encryptSensitiveData(analysis.getMicrData().getRoutingNumber()),
                encryptSensitiveData(analysis.getMicrData().getAccountNumber()),
                analysis.getMicrData().getCheckNumber(),
                request.getAmount(),
                cutoffDate
            );
            
            if (!micrMatches.isEmpty()) {
                CheckDeposit duplicate = micrMatches.get(0);
                throw new DuplicateCheckException(
                    "A check with the same account and check number has already been deposited",
                    duplicate.getId()
                );
            }
        }
    }
    
    /**
     * Checks deposit limits
     */
    private void checkDepositLimits(UUID userId, BigDecimal amount) {
        // Check daily limit
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal dailyTotal = checkDepositRepository.calculateDailyTotal(userId, startOfDay);
        
        if (dailyTotal.add(amount).compareTo(dailyLimit) > 0) {
            BigDecimal remaining = dailyLimit.subtract(dailyTotal);
            throw new LimitExceededException(String.format(
                "Daily check deposit limit exceeded. Limit: $%s, Remaining: $%s",
                dailyLimit, remaining.max(BigDecimal.ZERO)
            ));
        }
        
        // Check monthly limit
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal monthlyTotal = checkDepositRepository.calculateMonthlyTotal(userId, startOfMonth);
        
        if (monthlyTotal.add(amount).compareTo(monthlyLimit) > 0) {
            BigDecimal remaining = monthlyLimit.subtract(monthlyTotal);
            throw new LimitExceededException(String.format(
                "Monthly check deposit limit exceeded. Limit: $%s, Remaining: $%s",
                monthlyLimit, remaining.max(BigDecimal.ZERO)
            ));
        }
    }
    
    /**
     * Creates check deposit entity
     */
    private CheckDeposit createCheckDeposit(CheckDepositRequest request, CheckImageAnalysis analysis) {
        CheckDeposit deposit = new CheckDeposit();
        deposit.setUserId(request.getUserId());
        deposit.setWalletId(request.getWalletId());
        deposit.setAmount(request.getAmount());
        deposit.setStatus(CheckDepositStatus.PENDING);
        
        // Set image data
        deposit.setFrontImageUrl(analysis.getFrontImageUrl());
        deposit.setBackImageUrl(analysis.getBackImageUrl());
        deposit.setFrontImageHash(analysis.getFrontImageHash());
        deposit.setBackImageHash(analysis.getBackImageHash());
        
        // Set MICR data if available
        if (analysis.getMicrData() != null) {
            deposit.setMicrRoutingNumber(encryptSensitiveData(analysis.getMicrData().getRoutingNumber()));
            deposit.setMicrAccountNumber(encryptSensitiveData(analysis.getMicrData().getAccountNumber()));
            deposit.setCheckNumber(analysis.getMicrData().getCheckNumber());
            deposit.setMicrRawData(encryptSensitiveData(analysis.getMicrData().getRawMicr()));
        }
        
        // Set extracted amount
        deposit.setExtractedAmount(analysis.getExtractedAmount());
        deposit.setAmountConfidence(analysis.getAmountConfidence());
        
        // Check if manual review is needed
        boolean needsReview = determineIfManualReviewRequired(request, analysis);
        deposit.setManualReviewRequired(needsReview);
        
        // Set check details
        if (analysis.getCheckDetails() != null) {
            deposit.setPayeeName(analysis.getCheckDetails().getPayeeName());
            deposit.setPayorName(analysis.getCheckDetails().getPayorName());
            deposit.setCheckDate(analysis.getCheckDetails().getCheckDate());
            deposit.setMemo(analysis.getCheckDetails().getMemo());
        }
        
        // Set device and location info
        deposit.setDeviceId(request.getDeviceId());
        deposit.setDeviceType(request.getDeviceType());
        deposit.setIpAddress(request.getIpAddress());
        deposit.setLatitude(request.getLatitude());
        deposit.setLongitude(request.getLongitude());
        
        // Set idempotency key
        deposit.setIdempotencyKey(request.getIdempotencyKey());
        
        return checkDepositRepository.save(deposit);
    }
    
    /**
     * Processes check deposit asynchronously
     */
    @Async
    private CompletableFuture<CheckDepositResponse> processCheckDepositAsync(
            CheckDeposit deposit, CheckImageAnalysis analysis) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Update status to processing
                deposit.setStatus(CheckDepositStatus.IMAGE_PROCESSING);
                deposit.setProcessingStartedAt(LocalDateTime.now());
                checkDepositRepository.save(deposit);
                
                // Step 1: MICR validation
                validateMICRData(deposit, analysis.getMicrData());
                
                // Step 2: Amount verification
                verifyCheckAmount(deposit, analysis);
                
                // Step 3: Fraud detection
                performFraudDetection(deposit, analysis);
                
                // Step 4: Determine hold policy
                CheckHoldPolicy holdPolicy = determineHoldPolicy(deposit);
                applyHoldPolicy(deposit, holdPolicy);
                
                // Step 5: Send to external processor
                if (!deposit.isManualReviewRequired()) {
                    processWithExternalProcessor(deposit);
                } else {
                    deposit.setStatus(CheckDepositStatus.MANUAL_REVIEW);
                    checkDepositRepository.save(deposit);
                    sendForManualReview(deposit);
                }
                
                // Send notifications
                publishDepositEvent(deposit);
                
                return buildResponse(deposit);
                
            } catch (Exception e) {
                handleDepositFailure(deposit, e);
                throw e;
            }
        });
    }
    
    /**
     * Validates MICR data
     */
    private void validateMICRData(CheckDeposit deposit, MICRData micrData) {
        log.info("Validating MICR data for deposit: {}", deposit.getId());
        
        deposit.setStatus(CheckDepositStatus.MICR_VALIDATION);
        checkDepositRepository.save(deposit);
        
        if (micrData == null) {
            throw new ValidationException("Unable to read MICR data from check");
        }
        
        // Validate routing number
        if (!RoutingNumberValidator.isValid(micrData.getRoutingNumber())) {
            deposit.setRejectionReason("Invalid routing number");
            throw new ValidationException("Invalid routing number on check");
        }
        
        // Validate account number format
        if (!isValidAccountNumber(micrData.getAccountNumber())) {
            deposit.setRejectionReason("Invalid account number format");
            throw new ValidationException("Invalid account number format");
        }
        
        // Validate check number
        if (micrData.getCheckNumber() == null || micrData.getCheckNumber().isEmpty()) {
            log.warn("Check number not found in MICR data for deposit: {}", deposit.getId());
        }
    }
    
    /**
     * Verifies check amount
     */
    private void verifyCheckAmount(CheckDeposit deposit, CheckImageAnalysis analysis) {
        log.info("Verifying check amount for deposit: {}", deposit.getId());
        
        deposit.setStatus(CheckDepositStatus.AMOUNT_VERIFICATION);
        checkDepositRepository.save(deposit);
        
        BigDecimal requestedAmount = deposit.getAmount();
        BigDecimal extractedAmount = analysis.getExtractedAmount();
        BigDecimal confidence = analysis.getAmountConfidence();
        
        // Check if OCR extraction was successful
        if (extractedAmount == null || confidence.compareTo(new BigDecimal("0.8")) < 0) {
            log.warn("Low confidence in amount extraction for deposit: {} confidence: {}", 
                deposit.getId(), confidence);
            deposit.setManualReviewRequired(true);
            return;
        }
        
        // Calculate variance between requested and extracted amounts
        BigDecimal variance = requestedAmount.subtract(extractedAmount).abs()
            .divide(requestedAmount, 4, RoundingMode.HALF_UP);
        
        if (variance.compareTo(amountVarianceThreshold) > 0) {
            log.warn("Amount mismatch for deposit: {} requested: {} extracted: {}", 
                deposit.getId(), requestedAmount, extractedAmount);
            deposit.setManualReviewRequired(true);
            deposit.setVerificationStatus("AMOUNT_MISMATCH");
        }
    }
    
    /**
     * Performs fraud detection
     */
    private void performFraudDetection(CheckDeposit deposit, CheckImageAnalysis analysis) {
        log.info("Performing fraud detection for deposit: {}", deposit.getId());
        
        deposit.setStatus(CheckDepositStatus.FRAUD_CHECK);
        checkDepositRepository.save(deposit);
        
        // Build fraud check request
        CheckFraudRequest fraudRequest = CheckFraudRequest.builder()
            .depositId(deposit.getId())
            .userId(deposit.getUserId())
            .amount(deposit.getAmount())
            .checkImageFront(deposit.getFrontImageUrl())
            .checkImageBack(deposit.getBackImageUrl())
            .micrRoutingNumber(deposit.getMicrRoutingNumber())
            .micrAccountNumber(deposit.getMicrAccountNumber())
            .checkNumber(deposit.getCheckNumber())
            .deviceId(deposit.getDeviceId())
            .ipAddress(deposit.getIpAddress())
            .latitude(deposit.getLatitude())
            .longitude(deposit.getLongitude())
            .userDepositHistory(getUserDepositHistory(deposit.getUserId()))
            .build();
        
        // Perform fraud check
        CheckFraudResponse fraudResponse = fraudDetectionClient.analyzeCheckDeposit(fraudRequest);
        
        // Update deposit with fraud detection results
        deposit.setRiskScore(fraudResponse.getRiskScore());
        deposit.setFraudIndicators(fraudResponse.getFraudIndicatorsJson());
        deposit.setVerificationStatus(fraudResponse.getVerificationStatus());
        
        // Handle high-risk deposits
        if (fraudResponse.getRiskScore().compareTo(highRiskThreshold) > 0) {
            log.warn("High fraud risk detected for deposit: {} score: {}", 
                deposit.getId(), fraudResponse.getRiskScore());
            
            if (fraudResponse.shouldReject()) {
                deposit.setStatus(CheckDepositStatus.REJECTED);
                deposit.setRejectionReason("Failed fraud detection: " + fraudResponse.getReason());
                deposit.setRejectedAt(LocalDateTime.now());
                checkDepositRepository.save(deposit);
                
                // Send fraud alert
                sendFraudAlert(deposit, fraudResponse);
                
                throw new FraudDetectedException("Check deposit rejected due to fraud risk");
            } else {
                deposit.setManualReviewRequired(true);
            }
        }
    }
    
    /**
     * Determines hold policy based on various factors
     */
    private CheckHoldPolicy determineHoldPolicy(CheckDeposit deposit) {
        log.info("Determining hold policy for deposit: {}", deposit.getId());
        
        // Get user deposit history
        Object[] summary = checkDepositRepository.getUserDepositSummary(deposit.getUserId());
        long successfulDeposits = (Long) summary[0];
        BigDecimal totalDeposited = (BigDecimal) summary[1];
        
        // New customer - apply longer hold
        if (successfulDeposits == 0) {
            return CheckHoldPolicy.builder()
                .holdType(CheckHoldType.FIVE_DAY)
                .immediatelyAvailable(new BigDecimal("200.00").min(deposit.getAmount()))
                .build();
        }
        
        // Large deposit - apply extended hold
        if (deposit.getAmount().compareTo(new BigDecimal("5000.00")) > 0) {
            return CheckHoldPolicy.builder()
                .holdType(CheckHoldType.SEVEN_DAY)
                .immediatelyAvailable(new BigDecimal("200.00"))
                .build();
        }
        
        // High risk score - apply longer hold
        if (deposit.getRiskScore() != null && 
            deposit.getRiskScore().compareTo(new BigDecimal("0.5")) > 0) {
            return CheckHoldPolicy.builder()
                .holdType(CheckHoldType.FIVE_DAY)
                .immediatelyAvailable(BigDecimal.ZERO)
                .build();
        }
        
        // Established customer with good history - minimal hold
        if (successfulDeposits >= 10 && totalDeposited.compareTo(new BigDecimal("10000.00")) > 0) {
            return CheckHoldPolicy.builder()
                .holdType(CheckHoldType.NEXT_DAY)
                .immediatelyAvailable(deposit.getAmount())
                .build();
        }
        
        // Default hold policy
        return CheckHoldPolicy.builder()
            .holdType(CheckHoldType.TWO_DAY)
            .immediatelyAvailable(new BigDecimal("200.00").min(deposit.getAmount()))
            .build();
    }
    
    /**
     * Applies hold policy to deposit
     */
    private void applyHoldPolicy(CheckDeposit deposit, CheckHoldPolicy policy) {
        deposit.setHoldType(policy.getHoldType());
        
        LocalDate holdReleaseDate = calculateHoldReleaseDate(policy.getHoldType());
        deposit.setHoldReleaseDate(holdReleaseDate);
        deposit.setFundsAvailableDate(holdReleaseDate);
        
        if (policy.getImmediatelyAvailable().compareTo(BigDecimal.ZERO) > 0) {
            deposit.setPartialAvailabilityAmount(policy.getImmediatelyAvailable());
        }
        
        checkDepositRepository.save(deposit);
    }
    
    /**
     * Calculates hold release date based on hold type
     */
    private LocalDate calculateHoldReleaseDate(CheckHoldType holdType) {
        LocalDate baseDate = LocalDate.now();
        
        return switch (holdType) {
            case NONE -> baseDate;
            case NEXT_DAY -> getNextBusinessDay(baseDate, 1);
            case TWO_DAY -> getNextBusinessDay(baseDate, 2);
            case FIVE_DAY -> getNextBusinessDay(baseDate, 5);
            case SEVEN_DAY -> getNextBusinessDay(baseDate, 7);
            case EXTENDED -> getNextBusinessDay(baseDate, 10);
            case PARTIAL -> getNextBusinessDay(baseDate, 2);
        };
    }
    
    /**
     * Gets next business day
     */
    private LocalDate getNextBusinessDay(LocalDate date, int businessDays) {
        LocalDate result = date;
        int daysAdded = 0;
        
        while (daysAdded < businessDays) {
            result = result.plusDays(1);
            if (result.getDayOfWeek() != DayOfWeek.SATURDAY && 
                result.getDayOfWeek() != DayOfWeek.SUNDAY) {
                daysAdded++;
            }
        }
        
        return result;
    }
    
    /**
     * Processes deposit with external check processor
     */
    private void processWithExternalProcessor(CheckDeposit deposit) {
        log.info("Sending deposit to external processor: {}", deposit.getId());
        
        try {
            deposit.setStatus(CheckDepositStatus.PROCESSING);
            checkDepositRepository.save(deposit);
            
            // Build external processor request
            ExternalCheckRequest processorRequest = ExternalCheckRequest.builder()
                .depositId(deposit.getId())
                .frontImageUrl(decryptSensitiveData(deposit.getFrontImageUrl()))
                .backImageUrl(decryptSensitiveData(deposit.getBackImageUrl()))
                .amount(deposit.getAmount())
                .micrData(buildMicrDataForProcessor(deposit))
                .accountDetails(getAccountDetailsForDeposit(deposit))
                .build();
            
            // Send to processor
            ExternalCheckResponse processorResponse = checkProcessingClient
                .submitCheckDeposit(processorRequest);
            
            // Update deposit with processor response
            deposit.setExternalProcessorId(processorResponse.getProcessorId());
            deposit.setExternalReferenceId(processorResponse.getReferenceId());
            deposit.setProcessorResponse(processorResponse.toJson());
            
            if (processorResponse.isAccepted()) {
                deposit.setStatus(CheckDepositStatus.APPROVED);
                deposit.setApprovedAt(LocalDateTime.now());
                
                // Schedule for final processing
                scheduleDepositCompletion(deposit);
            } else {
                deposit.setStatus(CheckDepositStatus.REJECTED);
                deposit.setRejectionReason(processorResponse.getRejectionReason());
                deposit.setRejectedAt(LocalDateTime.now());
            }
            
            checkDepositRepository.save(deposit);
            
        } catch (Exception e) {
            log.error("Failed to process with external processor", e);
            deposit.setStatus(CheckDepositStatus.MANUAL_REVIEW);
            deposit.setManualReviewRequired(true);
            checkDepositRepository.save(deposit);
        }
    }
    
    /**
     * Schedules deposit completion
     */
    private void scheduleDepositCompletion(CheckDeposit deposit) {
        // If immediate funds are available, credit them now
        if (deposit.getPartialAvailabilityAmount() != null && 
            deposit.getPartialAvailabilityAmount().compareTo(BigDecimal.ZERO) > 0) {
            
            creditWallet(deposit.getWalletId(), 
                deposit.getPartialAvailabilityAmount(), 
                "Check Deposit (Partial) - Check #" + deposit.getCheckNumber(),
                deposit.getId());
            
            deposit.setStatus(CheckDepositStatus.PARTIAL_HOLD);
        } else if (deposit.getHoldType() == CheckHoldType.NONE) {
            // No hold - credit full amount
            creditWallet(deposit.getWalletId(), 
                deposit.getAmount(), 
                "Check Deposit - Check #" + deposit.getCheckNumber(),
                deposit.getId());
            
            deposit.setStatus(CheckDepositStatus.DEPOSITED);
            deposit.setDepositedAt(LocalDateTime.now());
        } else {
            // Full hold
            deposit.setStatus(CheckDepositStatus.FULL_HOLD);
        }
        
        checkDepositRepository.save(deposit);
    }
    
    /**
     * Credits wallet with deposit amount
     */
    private void creditWallet(UUID walletId, BigDecimal amount, String description, UUID depositId) {
        WalletCreditRequest creditRequest = WalletCreditRequest.builder()
            .walletId(walletId)
            .amount(amount)
            .transactionType("CHECK_DEPOSIT")
            .referenceId(depositId.toString())
            .description(description)
            .build();
        
        walletServiceClient.creditWallet(creditRequest);
    }
    
    /**
     * Handles deposit failure
     */
    private void handleDepositFailure(CheckDeposit deposit, Exception error) {
        log.error("Check deposit failed for deposit: {}", deposit.getId(), error);
        
        deposit.setStatus(CheckDepositStatus.REJECTED);
        deposit.setRejectionReason("Processing error: " + error.getMessage());
        deposit.setRejectedAt(LocalDateTime.now());
        checkDepositRepository.save(deposit);
        
        // Send failure notification
        publishDepositEvent(deposit);
    }
    
    /**
     * Sends deposit for manual review
     */
    private void sendForManualReview(CheckDeposit deposit) {
        ManualReviewRequest reviewRequest = ManualReviewRequest.builder()
            .depositId(deposit.getId())
            .userId(deposit.getUserId())
            .amount(deposit.getAmount())
            .riskScore(deposit.getRiskScore())
            .fraudIndicators(deposit.getFraudIndicators())
            .reason(determineManualReviewReason(deposit))
            .priority(calculateReviewPriority(deposit))
            .build();
        
        kafkaTemplate.send("manual-review-queue", reviewRequest);
    }
    
    /**
     * Publishes deposit event
     */
    private void publishDepositEvent(CheckDeposit deposit) {
        CheckDepositEvent event = CheckDepositEvent.builder()
            .depositId(deposit.getId())
            .userId(deposit.getUserId())
            .status(deposit.getStatus())
            .amount(deposit.getAmount())
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send(CHECK_STATUS_TOPIC, event);
    }
    
    /**
     * Sends fraud alert
     */
    private void sendFraudAlert(CheckDeposit deposit, CheckFraudResponse fraudResponse) {
        FraudAlertEvent alert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .type("CHECK_DEPOSIT")
            .severity(fraudResponse.getRiskScore().compareTo(new BigDecimal("0.9")) > 0 ? 
                "HIGH" : "MEDIUM")
            .userId(deposit.getUserId())
            .transactionId(deposit.getId())
            .amount(deposit.getAmount())
            .riskScore(fraudResponse.getRiskScore())
            .fraudIndicators(fraudResponse.getFraudIndicators())
            .timestamp(LocalDateTime.now())
            .build();
        
        kafkaTemplate.send(FRAUD_ALERT_TOPIC, alert);
    }
    
    /**
     * Handles check deposit status webhook
     */
    @Transactional
    public void handleCheckStatusWebhook(CheckWebhookRequest webhook) {
        log.info("Processing check webhook for deposit: {} status: {}", 
            webhook.getDepositId(), webhook.getStatus());
        
        CheckDeposit deposit = checkDepositRepository
            .findByExternalReferenceId(webhook.getReferenceId())
            .orElseThrow(() -> new ValidationException("Deposit not found: " + webhook.getReferenceId()));
        
        switch (webhook.getStatus()) {
            case "CLEARED":
                handleCheckCleared(deposit);
                break;
            case "RETURNED":
                handleCheckReturned(deposit, webhook.getReturnCode(), webhook.getReturnReason());
                break;
            case "HELD":
                handleCheckHeld(deposit, webhook.getHoldReason());
                break;
            default:
                log.warn("Unknown webhook status: {}", webhook.getStatus());
        }
        
        publishDepositEvent(deposit);
    }
    
    /**
     * Handles cleared check
     */
    private void handleCheckCleared(CheckDeposit deposit) {
        if (deposit.getStatus() == CheckDepositStatus.FULL_HOLD || 
            deposit.getStatus() == CheckDepositStatus.PARTIAL_HOLD) {
            
            // Calculate remaining amount to credit
            BigDecimal remainingAmount = deposit.getAmount();
            if (deposit.getPartialAvailabilityAmount() != null) {
                remainingAmount = remainingAmount.subtract(deposit.getPartialAvailabilityAmount());
            }
            
            if (remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
                creditWallet(deposit.getWalletId(), 
                    remainingAmount, 
                    "Check Deposit (Hold Released) - Check #" + deposit.getCheckNumber(),
                    deposit.getId());
            }
        }
        
        deposit.setStatus(CheckDepositStatus.DEPOSITED);
        deposit.setDepositedAt(LocalDateTime.now());
        checkDepositRepository.save(deposit);
        
        // Send success notification
        sendDepositNotification(deposit, "completed");
    }
    
    /**
     * Handles returned check
     */
    private void handleCheckReturned(CheckDeposit deposit, String returnCode, String returnReason) {
        deposit.setStatus(CheckDepositStatus.RETURNED);
        deposit.setReturnCode(returnCode);
        deposit.setReturnReason(returnReason);
        deposit.setReturnedAt(LocalDateTime.now());
        checkDepositRepository.save(deposit);
        
        // Reverse the deposit
        reverseCheckDeposit(deposit);
        
        // Send return notification
        sendDepositNotification(deposit, "returned");
    }
    
    /**
     * Reverses a check deposit
     */
    private void reverseCheckDeposit(CheckDeposit deposit) {
        BigDecimal amountToReverse = deposit.getAmount();
        
        WalletDebitRequest debitRequest = WalletDebitRequest.builder()
            .walletId(deposit.getWalletId())
            .amount(amountToReverse)
            .transactionType("CHECK_DEPOSIT_REVERSAL")
            .referenceId(deposit.getId().toString())
            .description("Check Return - " + deposit.getReturnReason())
            .build();
        
        try {
            walletServiceClient.debitWallet(debitRequest);
        } catch (InsufficientFundsException e) {
            // Handle insufficient funds for reversal
            log.error("Insufficient funds to reverse check deposit: {}", deposit.getId());
            // Create negative balance or collection case
            createCollectionCase(deposit, amountToReverse);
        }
    }
    
    /**
     * Creates collection case for NSF reversal
     */
    private void createCollectionCase(CheckDeposit deposit, BigDecimal amount) {
        CollectionCaseRequest caseRequest = CollectionCaseRequest.builder()
            .userId(deposit.getUserId())
            .type("CHECK_RETURN_NSF")
            .amount(amount)
            .referenceId(deposit.getId().toString())
            .description("Returned check - insufficient funds for reversal")
            .build();
        
        kafkaTemplate.send("collection-cases", caseRequest);
    }
    
    /**
     * Handles check held for additional review
     */
    private void handleCheckHeld(CheckDeposit deposit, String holdReason) {
        deposit.setManualReviewRequired(true);
        deposit.setVerificationStatus("HELD: " + holdReason);
        checkDepositRepository.save(deposit);
        
        sendForManualReview(deposit);
    }
    
    /**
     * Sends deposit notification
     */
    private void sendDepositNotification(CheckDeposit deposit, String type) {
        NotificationRequest notification = NotificationRequest.builder()
            .userId(deposit.getUserId())
            .type("CHECK_DEPOSIT_" + type.toUpperCase())
            .title(getNotificationTitle(type))
            .message(getNotificationMessage(deposit, type))
            .data(Map.of(
                "depositId", deposit.getId().toString(),
                "amount", deposit.getAmount().toString(),
                "checkNumber", deposit.getCheckNumber() != null ? deposit.getCheckNumber() : "",
                "status", deposit.getStatus().name()
            ))
            .build();
        
        kafkaTemplate.send("notifications", notification);
    }
    
    /**
     * Gets notification title
     */
    private String getNotificationTitle(String type) {
        return switch (type) {
            case "completed" -> "Check Deposit Complete";
            case "returned" -> "Check Deposit Returned";
            case "rejected" -> "Check Deposit Rejected";
            case "hold_released" -> "Check Hold Released";
            default -> "Check Deposit Update";
        };
    }
    
    /**
     * Gets notification message
     */
    private String getNotificationMessage(CheckDeposit deposit, String type) {
        String amount = "$" + deposit.getAmount();
        return switch (type) {
            case "completed" -> "Your check deposit of " + amount + " is now available.";
            case "returned" -> "Your check deposit of " + amount + " was returned: " + deposit.getReturnReason();
            case "rejected" -> "Your check deposit of " + amount + " was rejected: " + deposit.getRejectionReason();
            case "hold_released" -> "The hold on your check deposit of " + amount + " has been released.";
            default -> "Your check deposit status has been updated.";
        };
    }
    
    /**
     * Builds response DTO
     */
    private CheckDepositResponse buildResponse(CheckDeposit deposit) {
        return CheckDepositResponse.builder()
            .depositId(deposit.getId())
            .userId(deposit.getUserId())
            .walletId(deposit.getWalletId())
            .amount(deposit.getAmount())
            .status(deposit.getStatus())
            .message(getStatusMessage(deposit))
            .holdType(deposit.getHoldType())
            .holdReleaseDate(deposit.getHoldReleaseDate())
            .fundsAvailableDate(deposit.getFundsAvailableDate())
            .immediatelyAvailableAmount(deposit.getPartialAvailabilityAmount())
            .heldAmount(calculateHeldAmount(deposit))
            .checkNumber(deposit.getCheckNumber())
            .payorName(deposit.getPayorName())
            .payeeName(deposit.getPayeeName())
            .checkDate(deposit.getCheckDate())
            .riskScore(deposit.getRiskScore())
            .manualReviewRequired(deposit.isManualReviewRequired())
            .externalReferenceId(deposit.getExternalReferenceId())
            .submittedAt(deposit.getSubmittedAt())
            .estimatedCompletionTime(calculateEstimatedCompletion(deposit))
            .build();
    }
    
    /**
     * Gets status message
     */
    private String getStatusMessage(CheckDeposit deposit) {
        return switch (deposit.getStatus()) {
            case PENDING -> "Check deposit received and is being processed";
            case IMAGE_PROCESSING -> "Processing check images";
            case MICR_VALIDATION -> "Validating check information";
            case AMOUNT_VERIFICATION -> "Verifying check amount";
            case FRAUD_CHECK -> "Performing security checks";
            case MANUAL_REVIEW -> "Check is under review";
            case APPROVED -> "Check approved for deposit";
            case PROCESSING -> "Processing deposit";
            case DEPOSITED -> "Check successfully deposited";
            case PARTIAL_HOLD -> "Check deposited with partial hold";
            case FULL_HOLD -> "Check deposited with hold";
            case REJECTED -> "Check deposit rejected: " + deposit.getRejectionReason();
            case RETURNED -> "Check returned: " + deposit.getReturnReason();
            case CANCELLED -> "Check deposit cancelled";
        };
    }
    
    /**
     * Calculates held amount
     */
    private BigDecimal calculateHeldAmount(CheckDeposit deposit) {
        if (deposit.getPartialAvailabilityAmount() != null) {
            return deposit.getAmount().subtract(deposit.getPartialAvailabilityAmount());
        }
        return deposit.getHoldType() != CheckHoldType.NONE ? deposit.getAmount() : BigDecimal.ZERO;
    }
    
    /**
     * Calculates estimated completion time
     */
    private LocalDateTime calculateEstimatedCompletion(CheckDeposit deposit) {
        return switch (deposit.getStatus()) {
            case PENDING, IMAGE_PROCESSING, MICR_VALIDATION, 
                 AMOUNT_VERIFICATION, FRAUD_CHECK -> LocalDateTime.now().plusMinutes(10);
            case MANUAL_REVIEW -> LocalDateTime.now().plusHours(4);
            case APPROVED, PROCESSING -> LocalDateTime.now().plusMinutes(30);
            default -> null;
        };
    }
    
    /**
     * Determines if manual review is required
     */
    private boolean determineIfManualReviewRequired(CheckDepositRequest request, 
                                                   CheckImageAnalysis analysis) {
        // Low amount confidence
        if (analysis.getAmountConfidence().compareTo(new BigDecimal("0.8")) < 0) {
            return true;
        }
        
        // Amount mismatch
        if (analysis.getExtractedAmount() != null) {
            BigDecimal variance = request.getAmount()
                .subtract(analysis.getExtractedAmount()).abs()
                .divide(request.getAmount(), 4, RoundingMode.HALF_UP);
            if (variance.compareTo(amountVarianceThreshold) > 0) {
                return true;
            }
        }
        
        // First deposit for user
        if (!checkDepositRepository.hasSuccessfulDeposits(request.getUserId())) {
            return request.getAmount().compareTo(new BigDecimal("1000.00")) > 0;
        }
        
        return false;
    }
    
    /**
     * Determines manual review reason
     */
    private String determineManualReviewReason(CheckDeposit deposit) {
        List<String> reasons = new ArrayList<>();
        
        if (deposit.getAmountConfidence() != null && 
            deposit.getAmountConfidence().compareTo(new BigDecimal("0.8")) < 0) {
            reasons.add("Low amount extraction confidence");
        }
        
        if (deposit.getRiskScore() != null && 
            deposit.getRiskScore().compareTo(new BigDecimal("0.5")) > 0) {
            reasons.add("Elevated risk score");
        }
        
        if (deposit.getVerificationStatus() != null && 
            deposit.getVerificationStatus().contains("MISMATCH")) {
            reasons.add("Amount mismatch detected");
        }
        
        return String.join(", ", reasons);
    }
    
    /**
     * Calculates review priority
     */
    private String calculateReviewPriority(CheckDeposit deposit) {
        if (deposit.getAmount().compareTo(new BigDecimal("5000.00")) > 0) {
            return "HIGH";
        }
        if (deposit.getRiskScore() != null && 
            deposit.getRiskScore().compareTo(new BigDecimal("0.7")) > 0) {
            return "HIGH";
        }
        return "NORMAL";
    }
    
    /**
     * Gets user deposit history for fraud detection
     */
    private UserDepositHistory getUserDepositHistory(UUID userId) {
        Object[] summary = checkDepositRepository.getUserDepositSummary(userId);
        
        return UserDepositHistory.builder()
            .totalDeposits((Long) summary[0])
            .totalAmount((BigDecimal) summary[1])
            .averageAmount((BigDecimal) summary[2])
            .largestDeposit((BigDecimal) summary[3])
            .build();
    }
    
    /**
     * Validates account number format
     */
    private boolean isValidAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.isEmpty()) {
            return false;
        }
        // Basic validation - adjust based on requirements
        return accountNumber.matches("^[0-9]{4,17}$");
    }
    
    /**
     * Generates image hash for duplicate detection
     */
    private String generateImageHash(byte[] imageData) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(imageData);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new ImageProcessingException("Failed to generate image hash", "hash_generation", e);
        }
    }
    
    /**
     * Encrypts sensitive data using the centralized key management service
     */
    private String encryptSensitiveData(String data) {
        if (data == null) return null;
        try {
            EncryptedData encrypted = keyManagementService.encrypt(data, "check-deposit");
            // Store as a compact JSON string for database storage
            return String.format("{\"ct\":\"%s\",\"iv\":\"%s\",\"kid\":\"%s\"}",
                encrypted.getCiphertext(),
                encrypted.getIv(),
                encrypted.getKeyId()
            );
        } catch (Exception e) {
            log.error("Failed to encrypt sensitive data", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypts sensitive data using the centralized key management service
     */
    private String decryptSensitiveData(String encryptedData) {
        if (encryptedData == null) return null;
        try {
            // Parse the compact JSON format
            String ciphertext = encryptedData.substring(
                encryptedData.indexOf("\"ct\":\"") + 6,
                encryptedData.indexOf("\",\"iv\"")
            );
            String iv = encryptedData.substring(
                encryptedData.indexOf("\"iv\":\"") + 6,
                encryptedData.indexOf("\",\"kid\"")
            );
            String keyId = encryptedData.substring(
                encryptedData.indexOf("\"kid\":\"") + 7,
                encryptedData.indexOf("\"}")
            );
            
            EncryptedData encrypted = EncryptedData.builder()
                .ciphertext(ciphertext)
                .iv(iv)
                .keyId(keyId)
                .algorithm("AES/GCM/NoPadding")
                .build();
                
            return keyManagementService.decrypt(encrypted, "check-deposit");
        } catch (Exception e) {
            log.error("Failed to decrypt sensitive data", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Builds MICR data for processor
     */
    private Map<String, String> buildMicrDataForProcessor(CheckDeposit deposit) {
        Map<String, String> micrData = new HashMap<>();
        if (deposit.getMicrRoutingNumber() != null) {
            micrData.put("routingNumber", decryptSensitiveData(deposit.getMicrRoutingNumber()));
        }
        if (deposit.getMicrAccountNumber() != null) {
            micrData.put("accountNumber", decryptSensitiveData(deposit.getMicrAccountNumber()));
        }
        if (deposit.getCheckNumber() != null) {
            micrData.put("checkNumber", deposit.getCheckNumber());
        }
        return micrData;
    }
    
    /**
     * Gets account details for deposit
     */
    private Map<String, String> getAccountDetailsForDeposit(CheckDeposit deposit) {
        // This would fetch from user's linked accounts
        // For now, return basic details
        return Map.of(
            "depositAccountId", deposit.getWalletId().toString(),
            "accountType", "CHECKING"
        );
    }
    
    /**
     * Fallback method for circuit breaker
     */
    private CompletableFuture<CheckDepositResponse> handleCheckDepositFallback(
            CheckDepositRequest request, Exception ex) {
        log.error("Check deposit circuit breaker fallback triggered", ex);
        
        return CompletableFuture.completedFuture(
            CheckDepositResponse.builder()
                .status(CheckDepositStatus.REJECTED)
                .message("Check deposit service is temporarily unavailable. Please try again later.")
                .errorMessage(ex.getMessage())
                .build()
        );
    }
    
    /**
     * Gets check deposit status
     */
    public CheckDepositStatusResponse getCheckDepositStatus(UUID depositId) {
        log.info("Retrieving check deposit status for ID: {}", depositId);
        
        CheckDeposit deposit = checkDepositRepository.findById(depositId)
            .orElseThrow(() -> new ValidationException("Check deposit not found"));
        
        return CheckDepositStatusResponse.builder()
            .depositId(deposit.getId())
            .status(deposit.getStatus())
            .amount(deposit.getAmount())
            .statusDescription(getStatusMessage(deposit))
            .lastUpdated(deposit.getUpdatedAt())
            .currentStep(getCurrentStep(deposit.getStatus()))
            .progressPercentage(getProgressPercentage(deposit.getStatus()))
            .holdType(deposit.getHoldType())
            .fundsAvailableDate(deposit.getFundsAvailableDate())
            .availableAmount(calculateAvailableAmount(deposit))
            .pendingAmount(calculatePendingAmount(deposit))
            .submittedAt(deposit.getSubmittedAt())
            .approvedAt(deposit.getApprovedAt())
            .depositedAt(deposit.getDepositedAt())
            .rejectedAt(deposit.getRejectedAt())
            .returnedAt(deposit.getReturnedAt())
            .rejectionReason(deposit.getRejectionReason())
            .returnReason(deposit.getReturnReason())
            .build();
    }
    
    /**
     * Gets current processing step
     */
    private String getCurrentStep(CheckDepositStatus status) {
        return switch (status) {
            case PENDING -> "Received";
            case IMAGE_PROCESSING -> "Processing Images";
            case MICR_VALIDATION -> "Validating Check Data";
            case AMOUNT_VERIFICATION -> "Verifying Amount";
            case FRAUD_CHECK -> "Security Check";
            case MANUAL_REVIEW -> "Under Review";
            case APPROVED -> "Approved";
            case PROCESSING -> "Processing Deposit";
            case DEPOSITED, PARTIAL_HOLD, FULL_HOLD -> "Deposited";
            case REJECTED -> "Rejected";
            case RETURNED -> "Returned";
            case CANCELLED -> "Cancelled";
        };
    }
    
    /**
     * Gets progress percentage
     */
    private Integer getProgressPercentage(CheckDepositStatus status) {
        return switch (status) {
            case PENDING -> 10;
            case IMAGE_PROCESSING -> 20;
            case MICR_VALIDATION -> 40;
            case AMOUNT_VERIFICATION -> 60;
            case FRAUD_CHECK -> 80;
            case MANUAL_REVIEW -> 85;
            case APPROVED -> 90;
            case PROCESSING -> 95;
            case DEPOSITED, PARTIAL_HOLD, FULL_HOLD -> 100;
            case REJECTED, RETURNED, CANCELLED -> 100;
        };
    }
    
    /**
     * Calculates available amount
     */
    private BigDecimal calculateAvailableAmount(CheckDeposit deposit) {
        if (deposit.getStatus() == CheckDepositStatus.DEPOSITED) {
            return deposit.getAmount();
        }
        if (deposit.getStatus() == CheckDepositStatus.PARTIAL_HOLD) {
            return deposit.getPartialAvailabilityAmount();
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Calculates pending amount
     */
    private BigDecimal calculatePendingAmount(CheckDeposit deposit) {
        if (deposit.getStatus() == CheckDepositStatus.PARTIAL_HOLD) {
            return deposit.getAmount().subtract(deposit.getPartialAvailabilityAmount());
        }
        if (deposit.getStatus() == CheckDepositStatus.FULL_HOLD) {
            return deposit.getAmount();
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Manually approves a check deposit
     */
    @Transactional
    public void manuallyApproveDeposit(UUID depositId, String approverNotes) {
        log.info("Manually approving check deposit: {}", depositId);
        
        CheckDeposit deposit = checkDepositRepository.findById(depositId)
            .orElseThrow(() -> new ValidationException("Check deposit not found"));
        
        if (deposit.getStatus() != CheckDepositStatus.MANUAL_REVIEW) {
            throw new ValidationException("Only deposits in manual review can be approved");
        }
        
        deposit.setStatus(CheckDepositStatus.APPROVED);
        deposit.setApprovedAt(LocalDateTime.now());
        deposit.setManualReviewRequired(false);
        deposit.setVerificationStatus("MANUALLY_APPROVED: " + approverNotes);
        checkDepositRepository.save(deposit);
        
        // Continue with processing
        processWithExternalProcessor(deposit);
    }
    
    /**
     * Manually rejects a check deposit
     */
    @Transactional
    public void manuallyRejectDeposit(UUID depositId, String reason) {
        log.info("Manually rejecting check deposit: {}", depositId);
        
        CheckDeposit deposit = checkDepositRepository.findById(depositId)
            .orElseThrow(() -> new ValidationException("Check deposit not found"));
        
        if (deposit.getStatus() == CheckDepositStatus.DEPOSITED) {
            throw new ValidationException("Cannot reject an already deposited check");
        }
        
        deposit.setStatus(CheckDepositStatus.REJECTED);
        deposit.setRejectionReason("Manual rejection: " + reason);
        deposit.setRejectedAt(LocalDateTime.now());
        checkDepositRepository.save(deposit);
        
        // Send rejection notification
        sendDepositNotification(deposit, "rejected");
        publishDepositEvent(deposit);
    }
    
    /**
     * Processes deposits with expired holds
     */
    @Transactional
    public void processExpiredHolds() {
        log.info("Processing check deposits with expired holds");
        
        List<CheckDeposit> expiredHolds = checkDepositRepository.findDepositsWithExpiredHolds();
        
        for (CheckDeposit deposit : expiredHolds) {
            try {
                handleCheckCleared(deposit);
            } catch (Exception e) {
                log.error("Failed to process expired hold for deposit: {}", deposit.getId(), e);
            }
        }
    }
    
    private void incrementSuccessCounter(String operation) {
        meterRegistry.counter("check.deposit.success", "operation", operation).increment();
    }
    
    private void incrementErrorCounter(String operation, Throwable error) {
        meterRegistry.counter("check.deposit.error", 
            "operation", operation,
            "error", error.getClass().getSimpleName()
        ).increment();
    }
    
    // Inner classes for DTOs used internally
    @Data
    @Builder
    private static class CheckImageAnalysis {
        private String frontImageUrl;
        private String backImageUrl;
        private String frontImageHash;
        private String backImageHash;
        private MICRData micrData;
        private BigDecimal extractedAmount;
        private BigDecimal amountConfidence;
        private CheckDetails checkDetails;
    }
    
    @Data
    @Builder
    private static class CheckHoldPolicy {
        private CheckHoldType holdType;
        private BigDecimal immediatelyAvailable;
    }
    
    @Data
    @Builder
    private static class UserDepositHistory {
        private Long totalDeposits;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
        private BigDecimal largestDeposit;
    }
}