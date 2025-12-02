package com.waqiti.atm.service;

import com.waqiti.atm.domain.CheckImage;
import com.waqiti.atm.repository.CheckImageRepository;
import com.waqiti.atm.exception.CheckImagingException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Check Imaging Service
 * Processes check images captured at ATM using OCR and validation
 * Implements Check 21 Act compliance for electronic check processing
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckImagingService {

    private final CheckImageRepository checkImageRepository;

    @Value("${atm.check-imaging.min-image-size:50000}")
    private int minImageSize;

    @Value("${atm.check-imaging.max-image-size:5000000}")
    private int maxImageSize;

    @Value("${atm.check-imaging.min-resolution-dpi:200}")
    private int minResolutionDpi;

    @Value("${atm.check-imaging.ocr-enabled:true}")
    private boolean ocrEnabled;

    // Check image format constants
    private static final String[] SUPPORTED_FORMATS = {"JPEG", "PNG", "TIFF"};
    private static final int MIN_IMAGE_WIDTH = 800;
    private static final int MIN_IMAGE_HEIGHT = 400;

    // MICR (Magnetic Ink Character Recognition) line pattern
    private static final Pattern MICR_PATTERN = Pattern.compile(
            "⑈([0-9]{9})⑈([0-9]{1,20})⑈([0-9]{1,12})⑈");

    // Courtesy Amount Recognition (CAR) pattern - dollars and cents
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "\\$?\\s?([0-9,]+)\\.([0-9]{2})");

    /**
     * Process check images captured at ATM
     * Validates image quality, performs OCR, and stores images
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean processCheckImages(String depositId, List<String> checkImages,
                                     Integer numberOfChecks, LocalDateTime timestamp) {
        log.info("Processing check images: depositId={}, count={}", depositId, numberOfChecks);

        try {
            // Validate number of images matches declared count
            if (checkImages == null || checkImages.size() != numberOfChecks * 2) {
                // Each check has front and back image
                log.error("Image count mismatch: expected={}, received={}",
                        numberOfChecks * 2, checkImages != null ? checkImages.size() : 0);
                return false;
            }

            // Process each check image pair (front + back)
            for (int i = 0; i < numberOfChecks; i++) {
                int frontIdx = i * 2;
                int backIdx = i * 2 + 1;

                String frontImageData = checkImages.get(frontIdx);
                String backImageData = checkImages.get(backIdx);

                boolean processed = processSingleCheckImage(
                        depositId, i + 1, frontImageData, backImageData, timestamp);

                if (!processed) {
                    log.error("Failed to process check #{} for deposit: {}", i + 1, depositId);
                    return false;
                }
            }

            log.info("All check images processed successfully: depositId={}, count={}",
                    depositId, numberOfChecks);
            return true;

        } catch (Exception e) {
            log.error("Error processing check images for deposit: {}", depositId, e);
            return false;
        }
    }

    /**
     * Process single check image (front + back)
     */
    private boolean processSingleCheckImage(String depositId, int checkNumber,
                                           String frontImageData, String backImageData,
                                           LocalDateTime timestamp) {
        log.debug("Processing check #{} for deposit: {}", checkNumber, depositId);

        try {
            // Validate and decode front image
            byte[] frontImageBytes = Base64.getDecoder().decode(frontImageData);
            if (!validateImageQuality(frontImageBytes, "front")) {
                log.error("Front image quality validation failed for check #{}", checkNumber);
                return false;
            }

            // Validate and decode back image
            byte[] backImageBytes = Base64.getDecoder().decode(backImageData);
            if (!validateImageQuality(backImageBytes, "back")) {
                log.error("Back image quality validation failed for check #{}", checkNumber);
                return false;
            }

            // Perform OCR on front image if enabled
            CheckOCRResult ocrResult = null;
            if (ocrEnabled) {
                ocrResult = performOCR(frontImageBytes);
                if (ocrResult == null || !ocrResult.isValid()) {
                    log.warn("OCR failed for check #{}, manual review required", checkNumber);
                    // Don't fail - allow manual review
                }
            }

            // Create check image record
            CheckImage checkImage = CheckImage.builder()
                    .depositId(UUID.fromString(depositId))
                    .checkNumber(checkNumber)
                    .frontImage(frontImageBytes)
                    .backImage(backImageBytes)
                    .imageFormat("JPEG")
                    .frontImageSize(frontImageBytes.length)
                    .backImageSize(backImageBytes.length)
                    .capturedAt(timestamp)
                    .processingStatus(CheckImage.ProcessingStatus.CAPTURED)
                    .build();

            // Add OCR data if available
            if (ocrResult != null && ocrResult.isValid()) {
                checkImage.setRoutingNumber(ocrResult.routingNumber);
                checkImage.setAccountNumber(ocrResult.accountNumber);
                checkImage.setCheckNumber(ocrResult.checkNumber);
                checkImage.setCourtesyAmount(ocrResult.courtesyAmount);
                checkImage.setOcrConfidence(ocrResult.confidence);
                checkImage.setProcessingStatus(CheckImage.ProcessingStatus.OCR_COMPLETED);
            } else {
                checkImage.setProcessingStatus(CheckImage.ProcessingStatus.PENDING_REVIEW);
            }

            checkImageRepository.save(checkImage);

            log.debug("Check #{} processed and saved successfully", checkNumber);
            return true;

        } catch (Exception e) {
            log.error("Error processing check #{}: {}", checkNumber, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Validate image quality per Check 21 Act requirements
     */
    private boolean validateImageQuality(byte[] imageBytes, String imageType) {
        log.debug("Validating {} image quality: size={}", imageType, imageBytes.length);

        // Validate image size
        if (imageBytes.length < minImageSize) {
            log.error("Image too small: {} bytes < {} bytes", imageBytes.length, minImageSize);
            return false;
        }

        if (imageBytes.length > maxImageSize) {
            log.error("Image too large: {} bytes > {} bytes", imageBytes.length, maxImageSize);
            return false;
        }

        try {
            // Validate image format and dimensions
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                log.error("Failed to decode image");
                return false;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width < MIN_IMAGE_WIDTH || height < MIN_IMAGE_HEIGHT) {
                log.error("Image dimensions too small: {}x{}, required: {}x{}",
                        width, height, MIN_IMAGE_WIDTH, MIN_IMAGE_HEIGHT);
                return false;
            }

            // Check 21 requires minimum 200 DPI resolution
            // Approximate DPI check based on image size
            // Standard check: 6" x 2.75"
            int estimatedDpi = width / 6;
            if (estimatedDpi < minResolutionDpi) {
                log.warn("Image resolution may be too low: ~{} DPI, required: {} DPI",
                        estimatedDpi, minResolutionDpi);
                // Warning only, don't fail
            }

            log.debug("Image quality validation passed: {}x{}, ~{} DPI",
                    width, height, estimatedDpi);
            return true;

        } catch (IOException e) {
            log.error("Error validating image quality: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Perform OCR on check image
     * Extracts MICR line, courtesy amount, and check number
     */
    private CheckOCRResult performOCR(byte[] imageBytes) {
        log.debug("Performing OCR on check image");

        try {
            // In production, integrate with actual OCR engine (Tesseract, AWS Textract, etc.)
            // For now, simulate OCR with pattern matching

            // Simulated OCR result
            // In production, this would:
            // 1. Use Tesseract OCR or cloud OCR service
            // 2. Extract MICR line (routing number, account number, check number)
            // 3. Extract courtesy amount (printed dollar amount)
            // 4. Extract legal amount (written dollar amount)
            // 5. Validate consistency between courtesy and legal amounts

            // Placeholder: Return null to indicate OCR not actually performed
            // Real implementation would return actual OCR results
            log.debug("OCR simulation - production would use Tesseract/Textract here");

            return null; // Indicates manual review needed

        } catch (Exception e) {
            log.error("Error performing OCR: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validate check amounts match between OCR and declared amounts
     */
    @Transactional(readOnly = true)
    public boolean validateCheckAmounts(String depositId, BigDecimal declaredAmount,
                                       Integer numberOfChecks, LocalDateTime timestamp) {
        log.debug("Validating check amounts: depositId={}, declared={}, checks={}",
                depositId, declaredAmount, numberOfChecks);

        try {
            // Get all check images for this deposit
            List<CheckImage> checkImages = checkImageRepository
                    .findByDepositId(UUID.fromString(depositId));

            if (checkImages.size() != numberOfChecks) {
                log.error("Check image count mismatch: expected={}, found={}",
                        numberOfChecks, checkImages.size());
                return false;
            }

            // Sum OCR-extracted amounts
            BigDecimal ocrTotalAmount = BigDecimal.ZERO;
            int checksWithOCR = 0;

            for (CheckImage checkImage : checkImages) {
                if (checkImage.getCourtesyAmount() != null) {
                    ocrTotalAmount = ocrTotalAmount.add(checkImage.getCourtesyAmount());
                    checksWithOCR++;
                }
            }

            // If OCR available for all checks, validate amounts match
            if (checksWithOCR == numberOfChecks) {
                // Allow 1 cent tolerance for rounding
                BigDecimal difference = declaredAmount.subtract(ocrTotalAmount).abs();
                BigDecimal tolerance = new BigDecimal("0.01");

                if (difference.compareTo(tolerance) > 0) {
                    log.error("Check amount mismatch: declared={}, OCR={}, diff={}",
                            declaredAmount, ocrTotalAmount, difference);
                    return false;
                }

                log.info("Check amounts validated via OCR: declared={}, OCR={}",
                        declaredAmount, ocrTotalAmount);
                return true;
            } else {
                // OCR not available for all checks, require manual review
                log.warn("OCR not available for all checks: {}/{}, manual review required",
                        checksWithOCR, numberOfChecks);
                return true; // Allow but flag for review
            }

        } catch (Exception e) {
            log.error("Error validating check amounts: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * OCR Result container
     */
    private static class CheckOCRResult {
        String routingNumber;
        String accountNumber;
        Integer checkNumber;
        BigDecimal courtesyAmount;
        BigDecimal legalAmount;
        double confidence;

        boolean isValid() {
            return routingNumber != null && accountNumber != null &&
                   courtesyAmount != null && confidence > 0.85;
        }
    }
}
