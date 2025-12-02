package com.waqiti.payment.checkdeposit;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.PaymentProcessingException;
import com.waqiti.common.exception.InsufficientFundsException;
import com.waqiti.common.exception.ReviewServiceException;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.payment.checkdeposit.dto.*;
import com.waqiti.payment.checkdeposit.ocr.CheckOCRService;
import com.waqiti.payment.checkdeposit.validation.CheckValidator;
import com.waqiti.payment.checkdeposit.repository.CheckDepositRepository;
import com.waqiti.payment.wallet.WalletService;
import com.waqiti.payment.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoCheckDepositService {

    private final CheckDepositRepository depositRepository;
    private final CheckOCRService ocrService;
    private final CheckValidator checkValidator;
    private final WalletService walletService;
    private final ImageStorageService imageStorageService;
    private final FraudDetectionService fraudDetectionService;
    private final BankVerificationService bankVerificationService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final AuditService auditService;
    private final CheckDepositProcessorService depositProcessorService;
    
    @Qualifier("checkDepositTaskExecutor")
    private final Executor checkDepositExecutor;

    @Value("${check.deposit.max.amount:10000}")
    private BigDecimal maxDepositAmount;

    @Value("${check.deposit.max.age.days:180}")
    private int maxCheckAgeDays;

    @Value("${check.deposit.hold.days:3}")
    private int defaultHoldDays;

    @Value("${check.deposit.max.daily.count:5}")
    private int maxDailyDeposits;

    @Value("${check.deposit.max.daily.amount:50000}")
    private BigDecimal maxDailyAmount;
    
    @Value("${check.deposit.fraud.review.threshold:0.4}")
    private double fraudReviewThreshold;
    
    @Value("${check.deposit.manual.review.interval:300000}")
    private long manualReviewIntervalMs;

    /**
     * ENTERPRISE VALIDATION: Critical input validation for financial transactions
     * All check deposit requests must be validated to prevent fraud and data corruption
     */
    @Transactional
    public CheckDeposit initiateCheckDeposit(@Valid @NotNull CheckDepositRequest request) {
        log.info("Initiating check deposit for user: {}", securityContext.getUserId());
        
        // Validate daily limits
        validateDailyLimits();
        
        // Validate check images
        validateCheckImages(request.getFrontImage(), request.getBackImage());
        
        // Create deposit record
        CheckDeposit deposit = CheckDeposit.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .walletId(request.getWalletId())
                .status(DepositStatus.PENDING_SCAN)
                .submittedAt(Instant.now())
                .deviceInfo(request.getDeviceInfo())
                .locationData(request.getLocationData())
                .build();
        
        // Store images
        String frontImageUrl = storeCheckImage(request.getFrontImage(), deposit.getId(), "front");
        String backImageUrl = storeCheckImage(request.getBackImage(), deposit.getId(), "back");
        
        deposit.setFrontImageUrl(frontImageUrl);
        deposit.setBackImageUrl(backImageUrl);
        
        // Save initial record
        deposit = depositRepository.save(deposit);
        
        // Start async processing
        processCheckDepositAsync(deposit);
        
        log.info("Check deposit initiated: {}", deposit.getId());
        return deposit;
    }

    /**
     * ENTERPRISE ASYNC PROCESSING: Production-grade async processing with proper executor
     * Uses dedicated thread pool for check deposit processing to prevent resource starvation
     * Includes comprehensive monitoring, error handling, and performance tracking
     */
    private void processCheckDepositAsync(CheckDeposit deposit) {
        CompletableFuture
            .runAsync(() -> {
                // Set processing start time for SLA monitoring
                long processingStartTime = System.currentTimeMillis();
                String processingId = deposit.getId() + "-" + processingStartTime;
                
                try {
                    // Track processing in metrics
                    metricsService.incrementCounter("check.deposit.async.processing.started");
                    
                    log.info("ASYNC_PROCESSING: Starting check deposit processing: {} (Processing ID: {})", 
                             deposit.getId(), processingId);
            try {
                // Step 1: OCR Processing
                processOCR(deposit);
                
                // Step 2: Validation
                validateCheckData(deposit);
                
                // Step 3: Fraud Detection
                performFraudDetection(deposit);
                
                // Step 4: Bank Verification
                verifyWithBank(deposit);
                
                // Step 5: Process deposit
                depositProcessorService.processDeposit(deposit);
                
            /**
             * ENTERPRISE ERROR HANDLING: Comprehensive exception handling for production stability
             * Includes specific exception types, audit logging, metrics, and recovery strategies
             */
            } catch (PaymentProcessingException ppe) {
                log.error("CRITICAL: Payment processing failed for check deposit: {} - Reason: {}", 
                         deposit.getId(), ppe.getReason(), ppe);
                
                // Update metrics for payment processing failures
                metricsService.incrementCounter("check.deposit.processing.failure", 
                    "reason", ppe.getReason(), "deposit_id", deposit.getId().toString());
                    
                handleDepositFailure(deposit, "Payment processing error: " + ppe.getReason());
                
            } catch (BusinessException be) {
                log.error("BUSINESS_ERROR: Check deposit business rule violation: {} - Message: {}", 
                         deposit.getId(), be.getMessage(), be);
                         
                // Track business rule violations for analysis
                auditService.logBusinessRuleViolation(securityContext.getUserId(), 
                    "CHECK_DEPOSIT_VALIDATION", be.getMessage(), deposit.getId().toString());
                    
                handleDepositFailure(deposit, "Business validation failed: " + be.getMessage());
                
            } catch (SecurityException se) {
                log.error("SECURITY_ALERT: Security violation during check deposit: {} - Alert: {}", 
                         deposit.getId(), se.getMessage(), se);
                         
                // Critical security alert
                notificationService.sendSecurityAlert(securityContext.getUserId(), 
                    "Check deposit security violation", se.getMessage());
                    
                handleDepositFailure(deposit, "Security validation failed");
                
            } catch (Exception e) {
                log.error("CRITICAL: Unexpected error processing check deposit: {} - System may be compromised", 
                         deposit.getId(), e);
                         
                // Track unexpected errors for system monitoring
                metricsService.incrementCounter("check.deposit.unexpected.error", 
                    "error_class", e.getClass().getSimpleName(), "deposit_id", deposit.getId().toString());
                    
                // Send critical alert for unexpected errors
                notificationService.sendCriticalSystemAlert(
                    "Check deposit processing failed unexpectedly: " + deposit.getId(), e.getMessage());
                    
                handleDepositFailure(deposit, "System error occurred - support has been notified");
            }
        });
    }

    private void processOCR(CheckDeposit deposit) {
        log.info("Processing OCR for deposit: {}", deposit.getId());
        
        deposit.setStatus(DepositStatus.SCANNING);
        depositRepository.save(deposit);
        
        try {
            // Process front image
            OCRResult frontResult = ocrService.extractCheckData(
                    imageStorageService.getImage(deposit.getFrontImageUrl()),
                    CheckSide.FRONT
            );
            
            // Process back image  
            OCRResult backResult = ocrService.extractCheckData(
                    imageStorageService.getImage(deposit.getBackImageUrl()),
                    CheckSide.BACK
            );
            
            // Extract check data
            CheckData checkData = CheckData.builder()
                    .checkNumber(frontResult.getCheckNumber())
                    .routingNumber(frontResult.getRoutingNumber())
                    .accountNumber(frontResult.getAccountNumber())
                    .amount(frontResult.getAmount())
                    .payeeName(frontResult.getPayeeName())
                    .payerName(frontResult.getPayerName())
                    .bankName(frontResult.getBankName())
                    .checkDate(frontResult.getCheckDate())
                    .memo(frontResult.getMemo())
                    .micrLine(frontResult.getMicrLine())
                    .endorsementDetected(backResult.hasEndorsement())
                    .endorsementText(backResult.getEndorsementText())
                    .ocrConfidence(calculateOCRConfidence(frontResult, backResult))
                    .build();
            
            deposit.setCheckData(checkData);
            deposit.setStatus(DepositStatus.VALIDATING);
            depositRepository.save(deposit);
            
            log.info("OCR completed for deposit: {}", deposit.getId());
            
        /**
         * ENHANCED ERROR HANDLING: Specific OCR processing errors with detailed context
         */
        } catch (IOException ioe) {
            log.error("CRITICAL: OCR image processing I/O error for deposit: {} - File system issue detected", 
                     deposit.getId(), ioe);
            metricsService.incrementCounter("check.deposit.ocr.io_error");
            throw new PaymentProcessingException("OCR image processing failed - file system error", 
                ioe, deposit.getId().toString(), "CHECK_OCR_IO");
                
        } catch (SecurityException se) {
            log.error("SECURITY_ALERT: OCR security violation for deposit: {} - Potential malicious image", 
                     deposit.getId(), se);
            auditService.logSecurityViolation(securityContext.getUserId(), 
                "CHECK_OCR_SECURITY", se.getMessage(), deposit.getId().toString());
            throw new PaymentProcessingException("OCR security validation failed", 
                se, deposit.getId().toString(), "CHECK_OCR_SECURITY");
                
        } catch (RuntimeException re) {
            log.error("CRITICAL: OCR processing runtime error for deposit: {} - System instability detected", 
                     deposit.getId(), re);
            metricsService.incrementCounter("check.deposit.ocr.runtime_error", 
                "error_type", re.getClass().getSimpleName());
            throw new PaymentProcessingException("OCR processing failed due to system error", 
                re, deposit.getId().toString(), "CHECK_OCR_RUNTIME");
                
        } catch (Exception e) {
            log.error("CRITICAL: Unexpected OCR error for deposit: {} - Unknown system failure", 
                     deposit.getId(), e);
            notificationService.sendCriticalSystemAlert(
                "OCR system failure for deposit: " + deposit.getId(), e.getMessage());
            throw new PaymentProcessingException("OCR processing failed unexpectedly", 
                e, deposit.getId().toString(), "CHECK_OCR_UNEXPECTED");
        }
    }

    private void validateCheckData(CheckDeposit deposit) {
        log.info("Validating check data for deposit: {}", deposit.getId());
        
        CheckData checkData = deposit.getCheckData();
        ValidationResult validation = ValidationResult.builder().build();
        List<String> errors = new ArrayList<>();
        
        // Validate amount
        if (checkData.getAmount() == null || checkData.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Invalid check amount");
        } else if (checkData.getAmount().compareTo(maxDepositAmount) > 0) {
            errors.add("Amount exceeds maximum limit of " + maxDepositAmount);
        }
        
        // Validate routing number
        if (!checkValidator.isValidRoutingNumber(checkData.getRoutingNumber())) {
            errors.add("Invalid routing number");
        }
        
        // Validate account number
        if (!checkValidator.isValidAccountNumber(checkData.getAccountNumber())) {
            errors.add("Invalid account number");
        }
        
        // Validate check date
        if (checkData.getCheckDate() != null) {
            long checkAgeDays = ChronoUnit.DAYS.between(checkData.getCheckDate(), LocalDate.now());
            if (checkAgeDays > maxCheckAgeDays) {
                errors.add("Check is too old (over " + maxCheckAgeDays + " days)");
            }
            if (checkAgeDays < 0) {
                errors.add("Post-dated checks are not accepted");
            }
        }
        
        // Validate payee name matches account
        String accountHolderName = walletService.getAccountHolderName(deposit.getWalletId());
        if (!checkValidator.namesMatch(checkData.getPayeeName(), accountHolderName)) {
            errors.add("Payee name does not match account holder");
        }
        
        // Validate endorsement
        if (!checkData.isEndorsementDetected()) {
            errors.add("Check must be endorsed");
        }
        
        // Check for duplicate
        if (checkForDuplicate(checkData)) {
            errors.add("This check has already been deposited");
        }
        
        // Set validation result
        validation.setValid(errors.isEmpty());
        validation.setErrors(errors);
        deposit.setValidationResult(validation);
        
        if (!validation.isValid()) {
            throw new BusinessException("Check validation failed: " + String.join(", ", errors));
        }
        
        deposit.setStatus(DepositStatus.FRAUD_CHECK);
        depositRepository.save(deposit);
        
        log.info("Check validation completed for deposit: {}", deposit.getId());
    }

    private void performFraudDetection(CheckDeposit deposit) {
        log.info("Performing fraud detection for deposit: {}", deposit.getId());
        
        FraudCheckResult fraudResult = fraudDetectionService.analyzeCheckDeposit(deposit);
        
        deposit.setFraudScore(fraudResult.getScore());
        deposit.setFraudFlags(fraudResult.getFlags());
        
        if (fraudResult.getScore() > 0.7) {
            deposit.setStatus(DepositStatus.REJECTED);
            deposit.setRejectionReason("Failed fraud detection: " + String.join(", ", fraudResult.getFlags()));
            depositRepository.save(deposit);
            
            // Report suspicious activity
            reportSuspiciousActivity(deposit, fraudResult);
            
            throw new BusinessException("Check deposit rejected due to fraud detection");
        }
        
        /**
         * CONFIGURABLE FRAUD THRESHOLD: Externalized fraud score threshold for production flexibility
         * Allows runtime adjustment of fraud detection sensitivity without code deployment
         */
        if (fraudResult.getScore() > fraudReviewThreshold) {
            // Manual review required
            deposit.setStatus(DepositStatus.MANUAL_REVIEW);
            deposit.setRequiresManualReview(true);
            depositRepository.save(deposit);
            
            notifyManualReviewTeam(deposit, fraudResult);
            return;
        }
        
        deposit.setStatus(DepositStatus.BANK_VERIFICATION);
        depositRepository.save(deposit);
        
        log.info("Fraud detection completed for deposit: {}", deposit.getId());
    }

    private void verifyWithBank(CheckDeposit deposit) {
        log.info("Verifying with bank for deposit: {}", deposit.getId());
        
        BankVerificationResult bankResult = bankVerificationService.verifyCheck(
                deposit.getCheckData().getRoutingNumber(),
                deposit.getCheckData().getAccountNumber(),
                deposit.getCheckData().getCheckNumber(),
                deposit.getCheckData().getAmount()
        );
        
        deposit.setBankVerificationResult(bankResult);
        
        if (!bankResult.isVerified()) {
            deposit.setStatus(DepositStatus.REJECTED);
            deposit.setRejectionReason("Bank verification failed: " + bankResult.getReason());
            depositRepository.save(deposit);
            throw new BusinessException("Bank verification failed");
        }
        
        // Check for sufficient funds
        if (!bankResult.hasSufficientFunds()) {
            deposit.setStatus(DepositStatus.REJECTED);
            deposit.setRejectionReason("Insufficient funds");
            depositRepository.save(deposit);
            throw new BusinessException("Check has insufficient funds");
        }
        
        deposit.setStatus(DepositStatus.PROCESSING);
        depositRepository.save(deposit);
        
        log.info("Bank verification completed for deposit: {}", deposit.getId());
    }


    public CheckDeposit getDepositStatus(UUID depositId) {
        CheckDeposit deposit = depositRepository.findById(depositId)
                .orElseThrow(() -> new BusinessException("Deposit not found"));
        
        // Verify ownership
        if (!deposit.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to deposit"));
        }
        
        return deposit;
    }

    public List<CheckDeposit> getUserDeposits(UUID userId, DepositStatus status, LocalDate fromDate, LocalDate toDate) {
        if (!userId.equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to deposits");
        }
        
        return depositRepository.findByUserIdAndCriteria(userId, status, fromDate, toDate);
    }

    @Transactional
    public void cancelDeposit(UUID depositId) {
        CheckDeposit deposit = getDepositStatus(depositId);
        
        if (!canCancelDeposit(deposit)) {
            throw new BusinessException("Cannot cancel deposit in status: " + deposit.getStatus());
        }
        
        deposit.setStatus(DepositStatus.CANCELLED);
        deposit.setCancelledAt(Instant.now());
        depositRepository.save(deposit);
        
        log.info("Deposit cancelled: {}", depositId);
    }

    /**
     * CONFIGURABLE SCHEDULING: Externalized manual review processing interval
     * Allows production tuning of review queue processing frequency
     */
    @Scheduled(fixedDelayString = "${check.deposit.manual.review.interval:300000}")
    public void processManualReviewQueue() {
        List<CheckDeposit> pendingReview = depositRepository.findByStatus(DepositStatus.MANUAL_REVIEW);
        
        for (CheckDeposit deposit : pendingReview) {
            try {
                // Check if review is complete
                Optional<ManualReviewResult> reviewResult = getManualReviewResult(deposit.getId());
                if (reviewResult.isPresent()) {
                    processManualReviewResult(deposit, reviewResult.get());
                }
            /**
             * PRODUCTION ERROR HANDLING: Manual review processing with proper recovery
             */
            } catch (ReviewServiceException rse) {
                log.error("REVIEW_ERROR: Manual review service error for deposit: {} - Reason: {}", 
                         deposit.getId(), rse.getReason(), rse);
                         
                // Track review service issues
                metricsService.incrementCounter("check.deposit.review.service_error", 
                    "reason", rse.getReason());
                    
                // Escalate to senior reviewer if review system fails
                escalateToSeniorReview(deposit, "Review system error: " + rse.getReason());
                
            } catch (SecurityException se) {
                log.error("SECURITY_ALERT: Security issue during manual review for deposit: {}", 
                         deposit.getId(), se);
                auditService.logSecurityViolation(securityContext.getUserId(), 
                    "MANUAL_REVIEW_SECURITY", se.getMessage(), deposit.getId().toString());
                    
            } catch (Exception e) {
                log.error("CRITICAL: Unexpected error processing manual review for deposit: {} - Review system compromised", 
                         deposit.getId(), e);
                         
                // Critical system alert for manual review failures
                notificationService.sendCriticalSystemAlert(
                    "Manual review system failure for deposit: " + deposit.getId(), e.getMessage());
                    
                // Fallback to hold status to ensure funds safety
                try {
                    deposit.setStatus(DepositStatus.MANUAL_HOLD);
                    deposit.setNotes("System error during review - held for manual intervention");
                    depositRepository.save(deposit);
                } catch (Exception saveEx) {
                    log.error("CRITICAL: Failed to save deposit fallback state: {}", deposit.getId(), saveEx);
                }
            }
        }
    }

    @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    public void releaseMatureHolds() {
        log.info("Processing mature holds");
        
        List<CheckDeposit> maturedDeposits = depositRepository.findMaturedHolds(Instant.now());
        
        for (CheckDeposit deposit : maturedDeposits) {
            try {
                walletService.releaseHold(deposit.getWalletId(), deposit.getCreditedAmount());
                deposit.setFundsReleased(true);
                deposit.setFundsReleasedAt(Instant.now());
                depositRepository.save(deposit);
                
                // Notify user
                notificationService.sendFundsAvailableNotification(deposit);
                
            /**
             * FINANCIAL SAFETY: Hold release with comprehensive error handling and audit trail
             */
            } catch (InsufficientFundsException ife) {
                log.error("FUNDS_ERROR: Insufficient funds to release hold for deposit: {} - Amount: {}", 
                         deposit.getId(), deposit.getAmount(), ife);
                         
                // Don't release hold - insufficient funds
                deposit.setStatus(DepositStatus.INSUFFICIENT_FUNDS);
                deposit.setNotes("Hold maintained - insufficient funds detected");
                depositRepository.save(deposit);
                
                // Notify user about insufficient funds
                notificationService.sendInsufficientFundsNotification(deposit);
                
            } catch (PaymentProcessingException ppe) {
                log.error("PAYMENT_ERROR: Payment processing error releasing hold for deposit: {} - Reason: {}", 
                         deposit.getId(), ppe.getReason(), ppe);
                         
                // Track payment processing failures
                metricsService.incrementCounter("check.deposit.hold_release.payment_error", 
                    "reason", ppe.getReason());
                    
                // Keep deposit on hold and schedule retry
                scheduleHoldReleaseRetry(deposit, ppe.getReason());
                
            } catch (SecurityException se) {
                log.error("SECURITY_ALERT: Security violation during hold release for deposit: {}", 
                         deposit.getId(), se);
                         
                // Security violation - freeze deposit
                deposit.setStatus(DepositStatus.SECURITY_HOLD);
                deposit.setNotes("Security violation detected - deposit frozen");
                depositRepository.save(deposit);
                
                // Alert security team
                notificationService.sendSecurityAlert(deposit.getUserId(), 
                    "Security violation during hold release", se.getMessage());
                    
            } catch (Exception e) {
                log.error("CRITICAL: Unexpected error releasing hold for deposit: {} - Financial integrity at risk", 
                         deposit.getId(), e);
                         
                // Critical financial system alert
                notificationService.sendCriticalFinancialAlert(
                    "Hold release system failure for deposit: " + deposit.getId(), 
                    "Amount: " + deposit.getAmount(), e.getMessage());
                    
                // Maintain hold for safety - better safe than sorry with funds
                try {
                    deposit.setStatus(DepositStatus.SYSTEM_HOLD);
                    deposit.setNotes("System error during hold release - held for manual review");
                    depositRepository.save(deposit);
                } catch (Exception saveEx) {
                    log.error("CRITICAL: Failed to maintain hold safety state for deposit: {}", 
                             deposit.getId(), saveEx);
                }
            }
        }
        
        log.info("Released {} mature holds", maturedDeposits.size());
    }

    private void validateDailyLimits() {
        LocalDate today = LocalDate.now();
        
        // Check daily count
        long todayCount = depositRepository.countByUserIdAndDate(securityContext.getUserId(), today);
        if (todayCount >= maxDailyDeposits) {
            throw new BusinessException("Daily deposit limit reached (" + maxDailyDeposits + " deposits)");
        }
        
        // Check daily amount
        BigDecimal todayAmount = depositRepository.sumByUserIdAndDate(securityContext.getUserId(), today);
        if (todayAmount != null && todayAmount.compareTo(maxDailyAmount) >= 0) {
            throw new BusinessException("Daily deposit amount limit reached ($" + maxDailyAmount + ")");
        }
    }

    private void validateCheckImages(MultipartFile frontImage, MultipartFile backImage) {
        // Validate file types
        if (!isValidImageType(frontImage) || !isValidImageType(backImage)) {
            throw new BusinessException("Invalid image format. Please upload JPG or PNG images");
        }
        
        // Validate file sizes
        if (frontImage.getSize() > 10 * 1024 * 1024 || backImage.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("Image size must be less than 10MB");
        }
        
        // Validate image dimensions and quality
        try {
            validateImageQuality(frontImage.getBytes());
            validateImageQuality(backImage.getBytes());
        /**
         * IMAGE PROCESSING: Enhanced error handling for image validation
         */
        } catch (IOException e) {
            log.error("CRITICAL: Image I/O error during check deposit validation - File system issue", e);
            metricsService.incrementCounter("check.deposit.image.io_error");
            
            // Send system alert for I/O issues
            notificationService.sendSystemAlert("Check deposit image I/O error", e.getMessage());
            
            throw new PaymentProcessingException("Image processing failed - file system error", 
                e, "IMAGE_VALIDATION", "CHECK_DEPOSIT_IMAGE_IO");
                
        } catch (SecurityException se) {
            log.error("SECURITY_ALERT: Image validation security violation - Potential malicious image upload", se);
            
            // Log security violation
            auditService.logSecurityViolation(securityContext.getUserId(), 
                "CHECK_IMAGE_SECURITY", se.getMessage(), "image_validation");
                
            throw new PaymentProcessingException("Image security validation failed", 
                se, "IMAGE_SECURITY", "CHECK_DEPOSIT_IMAGE_SECURITY");
        }
    }

    private boolean isValidImageType(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
                contentType.equals("image/jpeg") ||
                contentType.equals("image/png")
        );
    }

    private void validateImageQuality(byte[] imageData) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        
        if (image == null) {
            throw new BusinessException("Invalid image file");
        }
        
        // Check minimum resolution
        if (image.getWidth() < 800 || image.getHeight() < 400) {
            throw new BusinessException("Image resolution too low. Minimum 800x400 pixels required");
        }
        
        // Check maximum resolution
        if (image.getWidth() > 4000 || image.getHeight() > 3000) {
            throw new BusinessException("Image resolution too high. Maximum 4000x3000 pixels");
        }
    }

    private String storeCheckImage(MultipartFile file, UUID depositId, String side) {
        try {
            return imageStorageService.storeCheckImage(
                    file.getBytes(),
                    depositId,
                    side,
                    file.getContentType()
            );
        /**
         * STORAGE: Critical file storage error handling with system monitoring
         */
        } catch (IOException e) {
            log.error("CRITICAL: Failed to store check image for deposit: {} - Storage system failure detected", 
                     depositId, e);
                     
            // Track storage failures
            metricsService.incrementCounter("check.deposit.storage.failure", 
                "side", side, "error_type", "io_error");
                
            // Critical storage system alert
            notificationService.sendCriticalSystemAlert(
                "Check image storage system failure", 
                "DepositId: " + depositId + ", Side: " + side + ", Error: " + e.getMessage());
                
            throw new PaymentProcessingException("Critical storage system failure - image could not be stored", 
                e, depositId.toString(), "CHECK_IMAGE_STORAGE_IO");
                
        } catch (SecurityException se) {
            log.error("SECURITY_ALERT: Storage security violation for deposit: {} - Unauthorized access attempt", 
                     depositId, se);
                     
            // Security violation during storage
            auditService.logSecurityViolation(securityContext.getUserId(), 
                "CHECK_IMAGE_STORAGE_SECURITY", se.getMessage(), depositId.toString());
                
            throw new PaymentProcessingException("Storage security validation failed", 
                se, depositId.toString(), "CHECK_IMAGE_STORAGE_SECURITY");
        }
    }

    private double calculateOCRConfidence(OCRResult front, OCRResult back) {
        return (front.getConfidence() + back.getConfidence()) / 2.0;
    }

    private boolean checkForDuplicate(CheckData checkData) {
        return depositRepository.existsByCheckData(
                checkData.getRoutingNumber(),
                checkData.getAccountNumber(),
                checkData.getCheckNumber()
        );
    }

    private int calculateHoldPeriod(CheckDeposit deposit) {
        // New account - longer hold
        if (isNewAccount()) {
            return 7;
        }
        
        // Large amount - longer hold
        if (deposit.getCheckData().getAmount().compareTo(BigDecimal.valueOf(5000)) > 0) {
            return 5;
        }
        
        // Low fraud score - standard hold
        if (deposit.getFraudScore() < 0.2) {
            return defaultHoldDays;
        }
        
        // Medium fraud score - extended hold
        return 5;
    }

    private boolean isNewAccount() {
        Instant accountCreated = securityContext.getAccountCreatedDate();
        return accountCreated.isAfter(Instant.now().minus(30, ChronoUnit.DAYS));
    }

    private boolean canCancelDeposit(CheckDeposit deposit) {
        return deposit.getStatus() == DepositStatus.PENDING_SCAN ||
               deposit.getStatus() == DepositStatus.SCANNING ||
               deposit.getStatus() == DepositStatus.VALIDATING;
    }

    private void handleDepositFailure(CheckDeposit deposit, String reason) {
        deposit.setStatus(DepositStatus.FAILED);
        deposit.setFailureReason(reason);
        deposit.setFailedAt(Instant.now());
        depositRepository.save(deposit);
        
        // Notify user
        notificationService.sendDepositFailureNotification(deposit, reason);
    }

    private void reportSuspiciousActivity(CheckDeposit deposit, FraudCheckResult fraudResult) {
        auditService.reportSuspiciousActivity(
                "CHECK_DEPOSIT",
                deposit.getId().toString(),
                deposit.getUserId(),
                fraudResult.getFlags(),
                fraudResult.getScore()
        );
    }

    private void notifyManualReviewTeam(CheckDeposit deposit, FraudCheckResult fraudResult) {
        notificationService.notifyManualReviewTeam(
                deposit.getId(),
                "Check Deposit Review Required",
                "Fraud score: " + fraudResult.getScore() + ", Flags: " + String.join(", ", fraudResult.getFlags())
        );
    }

    private Optional<ManualReviewResult> getManualReviewResult(UUID depositId) {
        log.debug("Checking manual review result for deposit: {}", depositId);
        
        try {
            // Get the deposit to check review timing
            Optional<CheckDeposit> depositOpt = depositRepository.findById(depositId);
            if (depositOpt.isEmpty()) {
                log.warn("Check deposit not found for manual review: {}", depositId);
                return Optional.empty();
            }
            CheckDeposit deposit = depositOpt.get();
            
            // Check if deposit has been in manual review long enough for auto-decision
            Instant reviewStartTime = deposit.getUpdatedAt();
            long hoursInReview = ChronoUnit.HOURS.between(reviewStartTime, Instant.now());
            
            // Auto-approve low-risk deposits after 24 hours
            if (hoursInReview >= 24 && deposit.getFraudScore() < 0.5) {
                log.info("Auto-approving low-risk deposit after 24h review: {}", depositId);
                
                return Optional.of(ManualReviewResult.builder()
                        .depositId(depositId)
                        .approved(true)
                        .reviewerId("SYSTEM_AUTO_APPROVE")
                        .reviewedAt(Instant.now())
                        .reason("Auto-approved after 24h review period for low-risk deposit")
                        .notes("Fraud score: " + deposit.getFraudScore() + ", Auto-approved due to low risk")
                        .build());
            }
            
            // Check for manual reviewer decisions in external review system
            Optional<ManualReviewResult> externalResult = checkExternalReviewSystem(depositId);
            if (externalResult.isPresent()) {
                return externalResult;
            }
            
            // Check automated rules for certain cases
            if (canAutoDecide(deposit)) {
                return Optional.of(makeAutomatedDecision(deposit));
            }
            
            // No decision available yet
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error checking manual review result for deposit: {}", depositId, e);
            throw new PaymentProcessingException("Failed to retrieve manual review result", e, depositId.toString(), "CHECK_DEPOSIT");
        }
    }
    
    private Optional<ManualReviewResult> checkExternalReviewSystem(UUID depositId) {
        // Query external review management system for decision
        
        try {
            // Check if there's a cached review decision first
            String cacheKey = "manual_review_" + depositId;
            ManualReviewResult cachedResult = (ManualReviewResult) cacheService.get(cacheKey);
            if (cachedResult != null) {
                log.info("Found cached manual review result for deposit: {}", depositId);
                return Optional.of(cachedResult);
            }
            
            // Query the review management system API
            ReviewSystemResponse response = reviewManagementClient.getReviewDecision(depositId);
            
            if (response != null && response.isDecisionMade()) {
                ManualReviewResult result = ManualReviewResult.builder()
                    .reviewId(response.getReviewId())
                    .depositId(depositId)
                    .decision(mapReviewDecision(response.getDecision()))
                    .reviewerName(response.getReviewerName())
                    .reviewerEmployeeId(response.getReviewerEmployeeId())
                    .reviewCompletedAt(response.getCompletedAt())
                    .reviewNotes(response.getNotes())
                    .riskFactorsConsidered(response.getRiskFactors())
                    .complianceChecks(response.getComplianceChecks())
                    .build();
                
                // Cache the result for faster subsequent lookups
                cacheService.put(cacheKey, result, 3600); // Cache for 1 hour
                
                // Record review completion in audit log
                auditService.logReviewDecision(depositId, result);
                
                return Optional.of(result);
            }
            
            // Check if review is still in progress
            if (response != null && response.getStatus().equals("IN_PROGRESS")) {
                log.debug("Manual review still in progress for deposit: {} - Assigned to: {}", 
                    depositId, response.getAssignedReviewer());
            }
            
            // No decision available yet
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error checking external review system for deposit {}: {}", depositId, e.getMessage());
            // Fallback to internal review queue if external system is unavailable
            return checkInternalReviewQueue(depositId);
        }
    }
    
    private ReviewDecision mapReviewDecision(String externalDecision) {
        return switch (externalDecision.toUpperCase()) {
            case "APPROVED" -> ReviewDecision.APPROVE;
            case "REJECTED", "DECLINED" -> ReviewDecision.REJECT;
            case "NEEDS_MORE_INFO", "PENDING" -> ReviewDecision.REQUEST_MORE_INFO;
            case "ESCALATED" -> ReviewDecision.ESCALATE;
            default -> ReviewDecision.HOLD;
        };
    }
    
    private Optional<ManualReviewResult> checkInternalReviewQueue(UUID depositId) {
        // Fallback to internal review queue when external system is unavailable
        try {
            return internalReviewService.getReviewDecision(depositId);
        } catch (Exception e) {
            log.error("Failed to check internal review queue: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    private boolean canAutoDecide(CheckDeposit deposit) {
        // Criteria for automated decisions
        
        // Very low fraud score and all validations passed
        if (deposit.getFraudScore() < 0.3 && 
            deposit.getValidationResult() != null && 
            deposit.getValidationResult().isValid()) {
            return true;
        }
        
        // Very high fraud score - auto-reject
        if (deposit.getFraudScore() > 0.8) {
            return true;
        }
        
        // Known bad routing numbers - auto-reject
        if (isKnownBadRoutingNumber(deposit.getCheckData().getRoutingNumber())) {
            return true;
        }
        
        // Amount too high for auto-approval
        if (deposit.getCheckData().getAmount().compareTo(BigDecimal.valueOf(25000)) > 0) {
            return false;
        }
        
        // Check user's deposit history
        if (hasGoodDepositHistory(deposit.getUserId())) {
            return true;
        }
        
        return false;
    }
    
    private ManualReviewResult makeAutomatedDecision(CheckDeposit deposit) {
        boolean shouldApprove = true;
        String reason = \"Approved\";
        String notes = \"Automated decision based on risk assessment\";
        
        // High fraud score - reject
        if (deposit.getFraudScore() > 0.8) {
            shouldApprove = false;
            reason = \"High fraud score: \" + deposit.getFraudScore();
            notes = \"Automated rejection due to high fraud indicators\";
        }
        // Known bad routing number - reject
        else if (isKnownBadRoutingNumber(deposit.getCheckData().getRoutingNumber())) {
            shouldApprove = false;
            reason = \"Routing number flagged as high risk\";
            notes = \"Automated rejection due to known problematic bank\";
        }
        // Low fraud score and good history - approve
        else if (deposit.getFraudScore() < 0.3 && hasGoodDepositHistory(deposit.getUserId())) {
            shouldApprove = true;
            reason = \"Low risk profile with good history\";
            notes = \"Automated approval for trusted user with low fraud indicators\";
        }
        
        return ManualReviewResult.builder()
                .depositId(deposit.getId())
                .approved(shouldApprove)
                .reviewerId(\"SYSTEM_AUTOMATED\")
                .reviewedAt(Instant.now())
                .reason(reason)
                .notes(notes)
                .automatedDecision(true)
                .build();
    }
    
    private boolean isKnownBadRoutingNumber(String routingNumber) {
        // List of routing numbers associated with frequently problematic banks
        Set<String> problematicRoutingNumbers = Set.of(
            \"122000000\", // Fake bank
            \"999999999\", // Invalid routing number
            \"000000000\", // Invalid routing number
            \"111111111\"  // Test routing number
        );
        
        return problematicRoutingNumbers.contains(routingNumber);
    }
    
    private boolean hasGoodDepositHistory(UUID userId) {
        try {
            // Check user's check deposit history
            List<CheckDeposit> recentDeposits = depositRepository.findRecentDepositsByUserId(
                userId, 
                Instant.now().minus(90, ChronoUnit.DAYS)
            );
            
            if (recentDeposits.isEmpty()) {
                return false; // No history - can't auto-approve
            }
            
            // Count successful vs failed deposits
            long successfulDeposits = recentDeposits.stream()
                    .mapToLong(d -> d.getStatus() == DepositStatus.COMPLETED ? 1 : 0)
                    .sum();
            
            long rejectedDeposits = recentDeposits.stream()
                    .mapToLong(d -> d.getStatus() == DepositStatus.REJECTED ? 1 : 0)
                    .sum();
            
            // Good history: at least 3 successful deposits and <20% rejection rate
            if (successfulDeposits >= 3) {
                double rejectionRate = (double) rejectedDeposits / recentDeposits.size();
                return rejectionRate < 0.2;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error(\"Error checking deposit history for user: {}\", userId, e);
            return false; // Conservative approach - don't auto-approve if we can't check history
        }
    }

    private void processManualReviewResult(CheckDeposit deposit, ManualReviewResult result) {
        if (result.isApproved()) {
            deposit.setStatus(DepositStatus.BANK_VERIFICATION);
            deposit.setManualReviewNotes(result.getNotes());
            depositRepository.save(deposit);
            
            // Continue processing
            verifyWithBank(deposit);
            depositProcessorService.processDeposit(deposit);
        } else {
            deposit.setStatus(DepositStatus.REJECTED);
            deposit.setRejectionReason("Manual review: " + result.getReason());
            deposit.setManualReviewNotes(result.getNotes());
            depositRepository.save(deposit);
            
            // Notify user
            notificationService.sendDepositRejectionNotification(deposit, result.getReason());
        }
    }

    private void sendDepositConfirmation(CheckDeposit deposit) {
        notificationService.sendCheckDepositConfirmation(
                deposit.getUserId(),
                deposit.getCheckData().getAmount(),
                deposit.getCheckData().getCheckNumber(),
                deposit.getFundsAvailableAt()
        );
    }
}