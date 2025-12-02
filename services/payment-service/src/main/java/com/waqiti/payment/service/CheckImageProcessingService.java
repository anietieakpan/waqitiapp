package com.waqiti.payment.service;

import com.waqiti.payment.client.ImageStorageClient;
import com.waqiti.payment.dto.CheckDepositRequest;
import com.waqiti.payment.dto.CheckImageAnalysisResult;
import com.waqiti.payment.exception.CheckImageProcessingException;
import com.waqiti.payment.util.CheckImageProcessor;
import com.waqiti.payment.util.CheckImageProcessor.ImageQualityResult;
import com.waqiti.payment.util.CheckImageProcessor.AmountExtractionResult;
import com.waqiti.payment.util.CheckImageProcessor.CheckDetails;
import com.waqiti.payment.service.check.CheckImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

/**
 * Focused service for check image processing
 * Extracted from CheckDepositService.java (1,436 LOC)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CheckImageProcessingService {

    private final CheckImageProcessor imageProcessor;
    private final ImageStorageClient imageStorageClient;
    private final CheckImageValidator imageValidator;

    /**
     * Process check images and extract data
     */
    public CompletableFuture<CheckImageAnalysisResult> processCheckImages(CheckDepositRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing check images for deposit request: {}", request.getDepositId());
                
                // 1. Validate image quality
                ImageQualityResult frontQuality = imageProcessor.analyzeImageQuality(request.getFrontImage());
                ImageQualityResult backQuality = imageProcessor.analyzeImageQuality(request.getBackImage());
                
                if (!frontQuality.isAcceptable() || !backQuality.isAcceptable()) {
                    throw new CheckImageProcessingException("Image quality insufficient for processing");
                }
                
                // 2. Extract check details from front image
                CheckDetails checkDetails = imageProcessor.extractCheckDetails(request.getFrontImage());
                
                // 3. Extract amount
                AmountExtractionResult amountResult = imageProcessor.extractAmount(
                    request.getFrontImage(), checkDetails);
                
                // 4. Validate extracted data
                validateExtractedData(checkDetails, amountResult, request);
                
                // 5. Store processed images
                String frontImageUrl = imageStorageClient.storeImage(
                    request.getFrontImage(), "front_" + request.getDepositId());
                String backImageUrl = imageStorageClient.storeImage(
                    request.getBackImage(), "back_" + request.getDepositId());
                
                return CheckImageAnalysisResult.builder()
                    .depositId(request.getDepositId())
                    .frontImageUrl(frontImageUrl)
                    .backImageUrl(backImageUrl)
                    .extractedAmount(amountResult.getAmount())
                    .amountConfidence(amountResult.getConfidence())
                    .routingNumber(checkDetails.getRoutingNumber())
                    .accountNumber(checkDetails.getAccountNumber())
                    .checkNumber(checkDetails.getCheckNumber())
                    .payerName(checkDetails.getPayerName())
                    .imageQualityScore((frontQuality.getScore() + backQuality.getScore()) / 2)
                    .processingSuccessful(true)
                    .build();
                    
            } catch (Exception e) {
                log.error("Check image processing failed: ", e);
                return CheckImageAnalysisResult.failed(
                    request.getDepositId(), 
                    "Image processing failed: " + e.getMessage()
                );
            }
        });
    }

    /**
     * Reprocess check images with enhanced settings
     */
    public CompletableFuture<CheckImageAnalysisResult> reprocessWithEnhancement(CheckDepositRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Reprocessing check images with enhancement for: {}", request.getDepositId());
                
                // Apply image enhancement before processing
                byte[] enhancedFrontImage = imageProcessor.enhanceImage(request.getFrontImage());
                byte[] enhancedBackImage = imageProcessor.enhanceImage(request.getBackImage());
                
                CheckDepositRequest enhancedRequest = request.toBuilder()
                    .frontImage(enhancedFrontImage)
                    .backImage(enhancedBackImage)
                    .build();
                
                return processCheckImages(enhancedRequest).join();
                
            } catch (Exception e) {
                log.error("Enhanced image processing failed: ", e);
                return CheckImageAnalysisResult.failed(
                    request.getDepositId(), 
                    "Enhanced processing failed: " + e.getMessage()
                );
            }
        });
    }

    private void validateExtractedData(CheckDetails checkDetails, 
                                     AmountExtractionResult amountResult, 
                                     CheckDepositRequest request) {
        // Validate amount matches user input
        if (request.getAmount() != null && amountResult.getAmount() != null) {
            BigDecimal tolerance = request.getAmount().multiply(new BigDecimal("0.01")); // 1% tolerance
            BigDecimal difference = request.getAmount().subtract(amountResult.getAmount()).abs();
            
            if (difference.compareTo(tolerance) > 0) {
                throw new CheckImageProcessingException(
                    "Extracted amount differs significantly from user input");
            }
        }
        
        // Validate routing number
        if (checkDetails.getRoutingNumber() == null || checkDetails.getRoutingNumber().length() != 9) {
            throw new CheckImageProcessingException("Invalid routing number extracted");
        }
        
        // Validate account number
        if (checkDetails.getAccountNumber() == null || checkDetails.getAccountNumber().length() < 4) {
            throw new CheckImageProcessingException("Invalid account number extracted");
        }
        
        // Validate amount confidence
        if (amountResult.getConfidence() < 0.7) {
            log.warn("Low confidence in amount extraction: {}", amountResult.getConfidence());
        }
    }
}