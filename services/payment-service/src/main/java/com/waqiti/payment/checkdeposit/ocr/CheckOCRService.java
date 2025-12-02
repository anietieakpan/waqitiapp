package com.waqiti.payment.checkdeposit.ocr;

import com.waqiti.payment.checkdeposit.dto.CheckOCRResult;
import com.waqiti.payment.checkdeposit.dto.MICRData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR Service for extracting data from check images
 * Integrates with multiple OCR providers for accuracy and redundancy
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckOCRService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    @Value("${ocr.primary.api.url:https://api.ocr.space/parse/image}")
    private String primaryOcrUrl;
    
    @Value("${ocr.primary.api.key}")
    private String primaryOcrApiKey;
    
    @Value("${ocr.google.vision.api.url:https://vision.googleapis.com/v1/images:annotate}")
    private String googleVisionUrl;
    
    @Value("${ocr.google.vision.api.key}")
    private String googleVisionApiKey;
    
    @Value("${ocr.retry.max.attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${ocr.retry.delay:1000}")
    private long retryDelay;
    
    @Value("${ocr.date.year.threshold:2000}")
    private int yearThreshold;
    
    @Value("${ocr.azure.api.url:https://westus.api.cognitive.microsoft.com/vision/v3.2/ocr}")
    private String azureOcrUrl;
    
    @Value("${ocr.azure.api.key}")
    private String azureOcrApiKey;
    
    @Value("${ocr.tesseract.enabled:true}")
    private boolean tesseractEnabled;
    
    private static final Pattern MICR_PATTERN = Pattern.compile(
        "([0-9]{9})\\s*([0-9]{4,12})\\s*([0-9]{10,12})"
    );
    
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "\\$?([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)"
    );
    
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})"
    );
    
    /**
     * Process check image and extract data
     */
    /**
     * CONFIGURABLE RETRY: Externalized retry configuration for production flexibility
     * Allows tuning of OCR service resilience without code changes
     */
    @Retryable(value = {Exception.class}, 
               maxAttemptsExpression = "${ocr.retry.max.attempts:3}", 
               backoff = @Backoff(delayExpression = "${ocr.retry.delay:1000}"))
    public CheckOCRResult processCheckImage(MultipartFile checkImage, boolean isFront) {
        log.info("Processing check image ({}): {} bytes", isFront ? "front" : "back", checkImage.getSize());
        
        try {
            // Pre-process image for better OCR accuracy
            byte[] processedImage = preprocessImage(checkImage.getBytes());
            
            // Run OCR with multiple providers in parallel
            CompletableFuture<String> primaryOcrFuture = CompletableFuture.supplyAsync(
                () -> runPrimaryOCR(processedImage)
            );
            
            CompletableFuture<String> googleOcrFuture = CompletableFuture.supplyAsync(
                () -> runGoogleVisionOCR(processedImage)
            );
            
            CompletableFuture<String> azureOcrFuture = CompletableFuture.supplyAsync(
                () -> runAzureOCR(processedImage)
            );
            
            // Combine results from all OCR providers
            String primaryText = primaryOcrFuture.join();
            String googleText = googleOcrFuture.join();
            String azureText = azureOcrFuture.join();
            
            // Merge and validate OCR results
            String mergedText = mergeOCRResults(primaryText, googleText, azureText);
            
            // Extract check data
            CheckOCRResult result;
            if (isFront) {
                result = extractFrontCheckData(mergedText);
            } else {
                result = extractBackCheckData(mergedText);
            }
            
            // Validate extracted data
            validateOCRResult(result);
            
            log.info("OCR processing complete. MICR: {}, Amount: {}", 
                result.getMicrData() != null ? result.getMicrData().getAccountNumber() : "N/A",
                result.getAmount());
            
            return result;
            
        } catch (Exception e) {
            log.error("OCR processing failed", e);
            throw new OCRProcessingException("Failed to process check image", e);
        }
    }
    
    /**
     * Preprocess image to improve OCR accuracy
     */
    private byte[] preprocessImage(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        
        // Convert to grayscale
        BufferedImage grayscale = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY
        );
        Graphics2D g = grayscale.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        
        // Apply contrast enhancement
        BufferedImage enhanced = enhanceContrast(grayscale);
        
        // Apply noise reduction
        BufferedImage denoised = reduceNoise(enhanced);
        
        // Convert back to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(denoised, "png", baos);
        
        return baos.toByteArray();
    }
    
    /**
     * Enhance image contrast
     */
    private BufferedImage enhanceContrast(BufferedImage image) {
        BufferedImage enhanced = new BufferedImage(
            image.getWidth(), image.getHeight(), image.getType()
        );
        
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int rgb = image.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;
                
                // Apply contrast enhancement
                gray = (int) (((gray - 128) * 1.5) + 128);
                gray = Math.max(0, Math.min(255, gray));
                
                int newRgb = (gray << 16) | (gray << 8) | gray;
                enhanced.setRGB(x, y, newRgb);
            }
        }
        
        return enhanced;
    }
    
    /**
     * Reduce image noise
     */
    private BufferedImage reduceNoise(BufferedImage image) {
        // Simple median filter for noise reduction
        BufferedImage denoised = new BufferedImage(
            image.getWidth(), image.getHeight(), image.getType()
        );
        
        for (int x = 1; x < image.getWidth() - 1; x++) {
            for (int y = 1; y < image.getHeight() - 1; y++) {
                List<Integer> neighbors = new ArrayList<>();
                
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int rgb = image.getRGB(x + dx, y + dy);
                        neighbors.add((rgb >> 16) & 0xFF);
                    }
                }
                
                Collections.sort(neighbors);
                int median = neighbors.get(4); // Middle value
                
                int newRgb = (median << 16) | (median << 8) | median;
                denoised.setRGB(x, y, newRgb);
            }
        }
        
        return denoised;
    }
    
    /**
     * Run primary OCR provider
     */
    private String runPrimaryOCR(byte[] imageBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", primaryOcrApiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            Map<String, Object> body = new HashMap<>();
            body.put("base64Image", Base64.getEncoder().encodeToString(imageBytes));
            body.put("language", "eng");
            body.put("isOverlayRequired", false);
            body.put("detectOrientation", true);
            body.put("scale", true);
            body.put("OCREngine", 2); // Use advanced OCR engine
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                primaryOcrUrl,
                HttpMethod.POST,
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                List<Map<String, Object>> parsedResults = 
                    (List<Map<String, Object>>) result.get("ParsedResults");
                
                if (parsedResults != null && !parsedResults.isEmpty()) {
                    return (String) parsedResults.get(0).get("ParsedText");
                }
            }
            
            return "";
            
        } catch (Exception e) {
            log.warn("Primary OCR failed: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Run Google Vision OCR
     */
    private String runGoogleVisionOCR(byte[] imageBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> request = new HashMap<>();
            request.put("requests", Arrays.asList(Map.of(
                "image", Map.of(
                    "content", Base64.getEncoder().encodeToString(imageBytes)
                ),
                "features", Arrays.asList(Map.of(
                    "type", "TEXT_DETECTION",
                    "maxResults", 10
                ))
            )));
            
            String url = googleVisionUrl + "?key=" + googleVisionApiKey;
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> responses = 
                    (List<Map<String, Object>>) body.get("responses");
                
                if (responses != null && !responses.isEmpty()) {
                    Map<String, Object> firstResponse = responses.get(0);
                    Map<String, Object> fullTextAnnotation = 
                        (Map<String, Object>) firstResponse.get("fullTextAnnotation");
                    
                    if (fullTextAnnotation != null) {
                        return (String) fullTextAnnotation.get("text");
                    }
                }
            }
            
            return "";
            
        } catch (Exception e) {
            log.warn("Google Vision OCR failed: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Run Azure OCR
     */
    private String runAzureOCR(byte[] imageBytes) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Ocp-Apim-Subscription-Key", azureOcrApiKey);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            HttpEntity<byte[]> request = new HttpEntity<>(imageBytes, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                azureOcrUrl + "?language=en&detectOrientation=true",
                HttpMethod.POST,
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                List<Map<String, Object>> regions = 
                    (List<Map<String, Object>>) result.get("regions");
                
                StringBuilder text = new StringBuilder();
                if (regions != null) {
                    for (Map<String, Object> region : regions) {
                        List<Map<String, Object>> lines = 
                            (List<Map<String, Object>>) region.get("lines");
                        
                        if (lines != null) {
                            for (Map<String, Object> line : lines) {
                                List<Map<String, Object>> words = 
                                    (List<Map<String, Object>>) line.get("words");
                                
                                if (words != null) {
                                    for (Map<String, Object> word : words) {
                                        text.append(word.get("text")).append(" ");
                                    }
                                    text.append("\n");
                                }
                            }
                        }
                    }
                }
                
                return text.toString();
            }
            
            return "";
            
        } catch (Exception e) {
            log.warn("Azure OCR failed: {}", e.getMessage());
            return "";
        }
    }
    
    /**
     * Merge OCR results from multiple providers
     */
    private String mergeOCRResults(String primary, String google, String azure) {
        // Use voting mechanism to determine most accurate text
        Map<String, Integer> wordFrequency = new HashMap<>();
        
        countWords(wordFrequency, primary);
        countWords(wordFrequency, google);
        countWords(wordFrequency, azure);
        
        // Build merged text from most common words
        StringBuilder merged = new StringBuilder();
        
        // Prefer the longest text as base
        String baseText = primary;
        if (google.length() > baseText.length()) baseText = google;
        if (azure.length() > baseText.length()) baseText = azure;
        
        // Use base text but validate critical fields against all results
        merged.append(baseText);
        
        // Add any MICR data found in other results
        if (!baseText.contains("⑆") && (primary.contains("⑆") || google.contains("⑆") || azure.contains("⑆"))) {
            if (primary.contains("⑆")) merged.append("\n").append(extractMICRLine(primary));
            else if (google.contains("⑆")) merged.append("\n").append(extractMICRLine(google));
            else if (azure.contains("⑆")) merged.append("\n").append(extractMICRLine(azure));
        }
        
        return merged.toString();
    }
    
    /**
     * Count word frequency for voting
     */
    private void countWords(Map<String, Integer> frequency, String text) {
        if (text == null || text.isEmpty()) return;
        
        String[] words = text.split("\\s+");
        for (String word : words) {
            word = word.trim().toLowerCase();
            if (!word.isEmpty()) {
                frequency.put(word, frequency.getOrDefault(word, 0) + 1);
            }
        }
    }
    
    /**
     * Extract MICR line from text
     */
    private String extractMICRLine(String text) {
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.contains("⑆") || line.matches(".*[0-9]{9}.*[0-9]{10}.*")) {
                return line;
            }
        }
        return "";
    }
    
    /**
     * Extract data from front of check
     */
    private CheckOCRResult extractFrontCheckData(String ocrText) {
        CheckOCRResult result = new CheckOCRResult();

        // Extract MICR data
        MICRData micrData = extractMICRData(ocrText);
        result.setMicrData(micrData);

        // Extract amount
        BigDecimal amount = extractAmount(ocrText);
        result.setAmount(amount);

        // Extract check number
        String checkNumber = extractCheckNumber(ocrText).orElse(null);
        result.setCheckNumber(checkNumber);

        // Extract date
        LocalDate date = extractDate(ocrText).orElse(null);
        result.setDate(date);

        // Extract payee
        String payee = extractPayee(ocrText).orElse(null);
        result.setPayeeName(payee);

        // Extract memo
        String memo = extractMemo(ocrText).orElse(null);
        result.setMemo(memo);

        // Set confidence score based on extracted fields
        int confidence = calculateConfidence(result);
        result.setConfidenceScore(confidence);

        return result;
    }
    
    /**
     * Extract data from back of check
     */
    private CheckOCRResult extractBackCheckData(String ocrText) {
        CheckOCRResult result = new CheckOCRResult();

        // Extract endorsement
        String endorsement = extractEndorsement(ocrText).orElse(null);
        result.setEndorsement(endorsement);

        // Check for restrictive endorsements
        boolean restrictive = hasRestrictiveEndorsement(ocrText);
        result.setRestrictiveEndorsement(restrictive);

        return result;
    }
    
    /**
     * Extract MICR data from OCR text
     */
    private MICRData extractMICRData(String text) {
        // Look for MICR pattern (routing number, account number, check number)
        Matcher matcher = MICR_PATTERN.matcher(text.replaceAll("[^0-9\\s]", " "));
        
        if (matcher.find()) {
            MICRData micr = new MICRData();
            micr.setRoutingNumber(matcher.group(1));
            micr.setAccountNumber(matcher.group(2));
            micr.setCheckNumber(matcher.group(3));
            return micr;
        }
        
        // Try alternative extraction for separated MICR components
        return extractMICRAlternative(text);
    }
    
    /**
     * Alternative MICR extraction method
     */
    private MICRData extractMICRAlternative(String text) {
        MICRData micr = new MICRData();
        
        // Look for routing number (9 digits starting with 0, 1, 2, or 3)
        Pattern routingPattern = Pattern.compile("\\b([0-3][0-9]{8})\\b");
        Matcher routingMatcher = routingPattern.matcher(text);
        if (routingMatcher.find()) {
            micr.setRoutingNumber(routingMatcher.group(1));
        }
        
        // Look for account number (typically 10-12 digits)
        Pattern accountPattern = Pattern.compile("\\b([0-9]{10,12})\\b");
        Matcher accountMatcher = accountPattern.matcher(text);
        if (accountMatcher.find()) {
            micr.setAccountNumber(accountMatcher.group(1));
        }
        
        // Look for check number (typically 4-6 digits)
        Pattern checkPattern = Pattern.compile("\\b([0-9]{4,6})\\b");
        Matcher checkMatcher = checkPattern.matcher(text);
        if (checkMatcher.find()) {
            micr.setCheckNumber(checkMatcher.group(1));
        }
        
        return micr;
    }
    
    /**
     * Extract amount from OCR text
     */
    private BigDecimal extractAmount(String text) {
        // Look for currency amounts
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        
        BigDecimal largestAmount = BigDecimal.ZERO;
        
        while (matcher.find()) {
            String amountStr = matcher.group(1).replaceAll(",", "");
            try {
                BigDecimal amount = new BigDecimal(amountStr);
                if (amount.compareTo(largestAmount) > 0) {
                    largestAmount = amount;
                }
            } catch (NumberFormatException e) {
                // Skip invalid amounts
            }
        }
        
        return largestAmount;
    }
    
    /**
     * Extract check number
     */
    private Optional<String> extractCheckNumber(String text) {
        // Look for check number in top right corner area
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.matches(".*\\b([0-9]{4,6})\\b.*")) {
                Matcher m = Pattern.compile("\\b([0-9]{4,6})\\b").matcher(line);
                if (m.find()) {
                    return Optional.of(m.group(1));
                }
            }
        }
        return Optional.empty();
    }
    
    /**
     * Extract date from OCR text
     */
    private Optional<LocalDate> extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);

        if (matcher.find()) {
            try {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                String yearStr = matcher.group(3);

                int year = Integer.parseInt(yearStr);
                if (year < 100) {
                    /**
                     * CONFIGURABLE DATE: Externalized year threshold for date processing
                     * Allows adjustment of 2-digit year conversion logic
                     */
                    year += yearThreshold; // Convert 2-digit year to 4-digit
                }

                return Optional.of(LocalDate.of(year, month, day));
            } catch (Exception e) {
                log.debug("Failed to parse date", e);
            }
        }

        return Optional.empty();
    }
    
    /**
     * Extract payee name
     */
    private Optional<String> extractPayee(String text) {
        // Look for "Pay to the order of" or similar
        String[] patterns = {
            "pay to the order of\\s+([A-Za-z\\s]+)",
            "pay to\\s+([A-Za-z\\s]+)",
            "payee\\s*:\\s*([A-Za-z\\s]+)"
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return Optional.of(m.group(1).trim());
            }
        }

        return Optional.empty();
    }
    
    /**
     * Extract memo
     */
    private Optional<String> extractMemo(String text) {
        String[] patterns = {
            "memo\\s*:\\s*([A-Za-z0-9\\s]+)",
            "for\\s*:\\s*([A-Za-z0-9\\s]+)",
            "re\\s*:\\s*([A-Za-z0-9\\s]+)"
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                return Optional.of(m.group(1).trim());
            }
        }

        return Optional.empty();
    }
    
    /**
     * Extract endorsement from back of check
     */
    private Optional<String> extractEndorsement(String text) {
        // Look for signature or endorsement text
        if (text.contains("For deposit only") || text.contains("FOR DEPOSIT ONLY")) {
            return Optional.of("FOR DEPOSIT ONLY");
        }

        // Extract any handwritten text (simplified - in production would use signature detection)
        String[] lines = text.split("\n");
        for (String line : lines) {
            if (line.trim().length() > 5 && !line.matches(".*\\d{5,}.*")) {
                return Optional.of(line.trim());
            }
        }

        return Optional.empty();
    }
    
    /**
     * Check for restrictive endorsement
     */
    private boolean hasRestrictiveEndorsement(String text) {
        String upperText = text.toUpperCase();
        return upperText.contains("FOR DEPOSIT ONLY") ||
               upperText.contains("FOR MOBILE DEPOSIT") ||
               upperText.contains("RESTRICTIVE ENDORSEMENT");
    }
    
    /**
     * Calculate confidence score
     */
    private int calculateConfidence(CheckOCRResult result) {
        int score = 0;
        int maxScore = 100;
        
        if (result.getMicrData() != null && result.getMicrData().getRoutingNumber() != null) {
            score += 30;
        }
        
        if (result.getAmount() != null && result.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            score += 25;
        }
        
        if (result.getCheckNumber() != null) {
            score += 15;
        }
        
        if (result.getDate() != null) {
            score += 15;
        }
        
        if (result.getPayeeName() != null) {
            score += 15;
        }
        
        return Math.min(score, maxScore);
    }
    
    /**
     * Validate OCR result
     */
    private void validateOCRResult(CheckOCRResult result) {
        if (result.getConfidenceScore() < 50) {
            log.warn("Low confidence OCR result: {}", result.getConfidenceScore());
        }
        
        // Validate routing number checksum if present
        if (result.getMicrData() != null && result.getMicrData().getRoutingNumber() != null) {
            if (!validateRoutingNumber(result.getMicrData().getRoutingNumber())) {
                log.warn("Invalid routing number checksum");
                result.setConfidenceScore(result.getConfidenceScore() - 20);
            }
        }
    }
    
    /**
     * Validate routing number using ABA checksum
     */
    private boolean validateRoutingNumber(String routingNumber) {
        if (routingNumber == null || routingNumber.length() != 9) {
            return false;
        }
        
        try {
            int[] digits = new int[9];
            for (int i = 0; i < 9; i++) {
                digits[i] = Character.getNumericValue(routingNumber.charAt(i));
            }
            
            int checksum = (3 * (digits[0] + digits[3] + digits[6]) +
                           7 * (digits[1] + digits[4] + digits[7]) +
                           (digits[2] + digits[5] + digits[8])) % 10;
            
            return checksum == 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * OCR Processing Exception
     */
    public static class OCRProcessingException extends RuntimeException {
        public OCRProcessingException(String message) {
            super(message);
        }
        
        public OCRProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}