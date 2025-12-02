package com.waqiti.payment.qrcode.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.security.EncryptionService;
import com.waqiti.payment.qrcode.domain.QRCodePayment;
import com.waqiti.payment.qrcode.domain.QRCodeType;
import com.waqiti.payment.qrcode.dto.*;
import com.waqiti.payment.qrcode.repository.QRCodePaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.cache.annotation.Cacheable;

/**
 * Service for QR Code based payment processing
 * Supports static and dynamic QR codes for P2P and merchant payments
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QRCodePaymentService {
    
    private final QRCodePaymentRepository qrCodePaymentRepository;
    private final CacheService cacheService;
    private final EncryptionService encryptionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Enhanced features
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Queue<OfflineQRPayment> offlinePaymentQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, QRCodeTemplate> templates = new ConcurrentHashMap<>();
    private final AtomicLong totalQRCodesGenerated = new AtomicLong(0);
    private final AtomicLong totalQRCodesScanned = new AtomicLong(0);
    private final AtomicLong totalPaymentsProcessed = new AtomicLong(0);
    private final Map<String, QRCodeAnalytics> analyticsMap = new ConcurrentHashMap<>();
    
    @Value("${qrcode.payment.expiry-minutes:5}")
    private int qrCodeExpiryMinutes;
    
    @Value("${qrcode.payment.max-amount:10000}")
    private BigDecimal maxQRCodeAmount;
    
    @Value("${qrcode.payment.secret-key}")
    private String qrCodeSecretKey;
    
    @Value("${qrcode.encryption.enabled:true}")
    private boolean encryptionEnabled;
    
    @Value("${qrcode.offline.queue.max:1000}")
    private int maxOfflineQueueSize;
    
    @Value("${qrcode.customization.logo.enabled:true}")
    private boolean logoEnabled;
    
    @Value("${qrcode.customization.logo.path:/logos/default.png}")
    private String defaultLogoPath;
    
    @Value("${qrcode.bulk.max:100}")
    private int maxBulkGeneration;
    
    @Value("${qrcode.deep.link.base:waqiti://pay}")
    private String deepLinkBase;
    
    private static final String QR_CACHE_PREFIX = "qr_code:";
    private static final String QR_PAYMENT_URL_PREFIX = "waqiti://pay/";
    private static final int QR_CODE_SIZE = 300;
    
    // Enhanced QR code types
    public enum EnhancedQRType {
        STATIC_PERSONAL,      // User's permanent QR code
        STATIC_MERCHANT,      // Merchant's permanent QR code  
        DYNAMIC_AMOUNT,       // One-time use with specific amount
        DYNAMIC_INVOICE,      // Invoice payment QR
        DONATION,            // Donation QR code
        SUBSCRIPTION,        // Recurring payment QR
        SPLIT_BILL,         // Group payment QR
        EVENT_TICKET,       // Event ticket payment
        LOYALTY_CARD,       // Loyalty program QR
        GIFT_CARD          // Gift card QR
    }
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Enhanced QR Code Payment Service");
        loadDefaultTemplates();
        startOfflinePaymentProcessor();
        startAnalyticsAggregator();
        log.info("Enhanced QR Code Payment Service initialized");
    }
    
    /**
     * Generate static QR code for user profile with customization
     */
    public QRCodeGenerationResponse generateStaticUserQRCode(String userId, QRCodeCustomization customization) {
        log.info("Generating static QR code for user: {}", userId);
        
        String qrCodeId = "STATIC_USER_" + userId;
        
        // Check if already exists
        Optional<QRCodePayment> existing = qrCodePaymentRepository.findByQrCodeId(qrCodeId);
        if (existing.isPresent() && customization == null) {
            return buildResponseFromPayment(existing.get());
        }
        
        GenerateQRCodeRequest request = GenerateQRCodeRequest.builder()
            .userId(userId)
            .type(QRCodeType.P2P_STATIC)
            .description("Static payment QR for user")
            .metadata(Map.of("static", "true", "userId", userId))
            .build();
        
        // Generate with customization
        QRCodeGenerationResponse response = generatePaymentQRCode(request);
        
        if (customization != null) {
            byte[] customizedImage = applyCustomization(response.getQrCodeImage(), customization);
            response.setQrCodeImage(Base64.getEncoder().encodeToString(customizedImage));
        }
        
        return response;
    }
    
    /**
     * Generate a QR code for receiving payment
     */
    @Transactional
    public QRCodeGenerationResponse generatePaymentQRCode(GenerateQRCodeRequest request) {
        log.info("Generating QR code for user: {}", request.getUserId());
        
        // Validate request
        validateQRCodeRequest(request);
        
        // Generate unique QR code ID
        String qrCodeId = generateQRCodeId();
        
        // Create QR code payment entity
        QRCodePayment qrCodePayment = QRCodePayment.builder()
                .qrCodeId(qrCodeId)
                .userId(request.getUserId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getType())
                .description(request.getDescription())
                .metadata(request.getMetadata())
                .expiresAt(LocalDateTime.now().plusMinutes(
                    request.getExpiryMinutes() != null ? request.getExpiryMinutes() : qrCodeExpiryMinutes
                ))
                .status(QRCodePayment.Status.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Generate secure payment data
        String paymentData = generateSecurePaymentData(qrCodePayment);
        qrCodePayment.setPaymentData(paymentData);
        
        // Save to database
        qrCodePayment = qrCodePaymentRepository.save(qrCodePayment);
        
        // Cache for quick lookup
        cacheQRCode(qrCodeId, qrCodePayment);
        
        // Generate QR code image with error correction
        byte[] qrCodeImage = generateQRCodeImage(paymentData, null);
        
        // Apply customization if requested
        QRCodeCustomization customization = request.getCustomization();
        if (customization != null) {
            qrCodeImage = applyCustomization(Base64.getEncoder().encodeToString(qrCodeImage), customization);
        }
        
        // Track generation
        totalQRCodesGenerated.incrementAndGet();
        publishQREvent("QR_GENERATED", qrCodePayment);
        
        return QRCodeGenerationResponse.builder()
                .qrCodeId(qrCodeId)
                .qrCodeImage(Base64.getEncoder().encodeToString(qrCodeImage))
                .paymentUrl(QR_PAYMENT_URL_PREFIX + qrCodeId)
                .expiresAt(qrCodePayment.getExpiresAt())
                .amount(qrCodePayment.getAmount())
                .currency(qrCodePayment.getCurrency())
                .build();
    }
    
    /**
     * Scan and process a QR code payment
     */
    @Transactional
    public QRCodeScanResponse scanQRCode(ScanQRCodeRequest request) {
        log.info("Scanning QR code by user: {}", request.getScannerUserId());
        
        // Extract QR code ID from scanned data
        String qrCodeId = extractQRCodeId(request.getQrCodeData());
        
        // Retrieve QR code payment details
        QRCodePayment qrCodePayment = getQRCodePayment(qrCodeId);
        
        // Validate QR code
        validateQRCodeForScanning(qrCodePayment, request.getScannerUserId());
        
        // Return payment details for confirmation
        return QRCodeScanResponse.builder()
                .qrCodeId(qrCodeId)
                .recipientId(qrCodePayment.getUserId())
                .recipientName(getRecipientName(qrCodePayment))
                .amount(qrCodePayment.getAmount())
                .currency(qrCodePayment.getCurrency())
                .description(qrCodePayment.getDescription())
                .type(qrCodePayment.getType())
                .metadata(qrCodePayment.getMetadata())
                .requiresConfirmation(qrCodePayment.getAmount() != null)
                .expiresAt(qrCodePayment.getExpiresAt())
                .build();
    }
    
    /**
     * Process QR code payment after scanning
     */
    @Transactional
    public QRCodePaymentResponse processQRCodePayment(ProcessQRCodePaymentRequest request) {
        log.info("Processing QR code payment: {}", request.getQrCodeId());
        
        // Retrieve QR code payment
        QRCodePayment qrCodePayment = getQRCodePayment(request.getQrCodeId());
        
        // Validate payment processing
        validatePaymentProcessing(qrCodePayment, request);
        
        // Update QR code payment status
        qrCodePayment.setStatus(QRCodePayment.Status.PROCESSING);
        qrCodePayment.setPayerUserId(request.getPayerId());
        qrCodePayment.setProcessedAt(LocalDateTime.now());
        
        // Calculate final amount
        BigDecimal finalAmount = request.getAmount() != null ? 
            request.getAmount() : qrCodePayment.getAmount();
        
        if (finalAmount == null) {
            throw new BusinessException("Payment amount is required");
        }
        
        qrCodePayment.setFinalAmount(finalAmount);
        
        // Process the actual payment (integrate with payment service)
        String transactionId = processPaymentTransaction(
            request.getPayerId(),
            qrCodePayment.getUserId(),
            finalAmount,
            qrCodePayment.getCurrency(),
            "QR Code Payment: " + qrCodePayment.getDescription()
        );
        
        // Update payment with transaction details
        qrCodePayment.setTransactionId(transactionId);
        qrCodePayment.setStatus(QRCodePayment.Status.COMPLETED);
        qrCodePayment.setCompletedAt(LocalDateTime.now());
        
        qrCodePaymentRepository.save(qrCodePayment);
        
        // Invalidate cache
        invalidateQRCodeCache(request.getQrCodeId());
        
        return QRCodePaymentResponse.builder()
                .qrCodeId(request.getQrCodeId())
                .transactionId(transactionId)
                .status(QRCodePayment.Status.COMPLETED.name())
                .amount(finalAmount)
                .currency(qrCodePayment.getCurrency())
                .payerId(request.getPayerId())
                .recipientId(qrCodePayment.getUserId())
                .processedAt(qrCodePayment.getProcessedAt())
                .build();
    }
    
    /**
     * Generate static merchant QR code
     */
    @Transactional
    public MerchantQRCodeResponse generateMerchantQRCode(GenerateMerchantQRCodeRequest request) {
        log.info("Generating merchant QR code for: {}", request.getMerchantId());
        
        // Create static merchant QR code
        QRCodePayment merchantQRCode = QRCodePayment.builder()
                .qrCodeId(generateMerchantQRCodeId(request.getMerchantId()))
                .merchantId(request.getMerchantId())
                .merchantName(request.getMerchantName())
                .type(QRCodeType.MERCHANT_STATIC)
                .currency(request.getDefaultCurrency())
                .metadata(Map.of(
                    "store_id", request.getStoreId(),
                    "terminal_id", request.getTerminalId(),
                    "category", request.getMerchantCategory()
                ))
                .status(QRCodePayment.Status.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        
        // Generate secure merchant data
        String merchantData = generateMerchantPaymentData(merchantQRCode);
        merchantQRCode.setPaymentData(merchantData);
        
        // Save merchant QR code
        merchantQRCode = qrCodePaymentRepository.save(merchantQRCode);
        
        // Generate QR code image
        byte[] qrCodeImage = generateQRCodeImage(merchantData);
        
        return MerchantQRCodeResponse.builder()
                .qrCodeId(merchantQRCode.getQrCodeId())
                .merchantId(request.getMerchantId())
                .qrCodeImage(Base64.getEncoder().encodeToString(qrCodeImage))
                .paymentUrl(QR_PAYMENT_URL_PREFIX + "merchant/" + merchantQRCode.getQrCodeId())
                .type(QRCodeType.MERCHANT_STATIC.name())
                .build();
    }
    
    /**
     * Get QR code payment status
     */
    public QRCodeStatusResponse getQRCodeStatus(String qrCodeId) {
        QRCodePayment qrCodePayment = getQRCodePayment(qrCodeId);
        
        return QRCodeStatusResponse.builder()
                .qrCodeId(qrCodeId)
                .status(qrCodePayment.getStatus().name())
                .amount(qrCodePayment.getFinalAmount())
                .transactionId(qrCodePayment.getTransactionId())
                .payerId(qrCodePayment.getPayerUserId())
                .processedAt(qrCodePayment.getProcessedAt())
                .expiresAt(qrCodePayment.getExpiresAt())
                .isExpired(qrCodePayment.getExpiresAt() != null && 
                          LocalDateTime.now().isAfter(qrCodePayment.getExpiresAt()))
                .build();
    }
    
    /**
     * Cancel QR code
     */
    @Transactional
    public void cancelQRCode(String qrCodeId, String userId) {
        QRCodePayment qrCodePayment = getQRCodePayment(qrCodeId);
        
        // Verify ownership
        if (!qrCodePayment.getUserId().equals(userId)) {
            throw new BusinessException("Unauthorized to cancel this QR code");
        }
        
        // Check if already processed
        if (qrCodePayment.getStatus() != QRCodePayment.Status.ACTIVE) {
            throw new BusinessException("QR code cannot be cancelled in current status");
        }
        
        // Cancel QR code
        qrCodePayment.setStatus(QRCodePayment.Status.CANCELLED);
        qrCodePayment.setCancelledAt(LocalDateTime.now());
        qrCodePaymentRepository.save(qrCodePayment);
        
        // Invalidate cache
        invalidateQRCodeCache(qrCodeId);
        
        log.info("QR code cancelled: {}", qrCodeId);
    }
    
    // Private helper methods
    
    private void validateQRCodeRequest(GenerateQRCodeRequest request) {
        if (request.getAmount() != null && request.getAmount().compareTo(maxQRCodeAmount) > 0) {
            throw new BusinessException("Amount exceeds maximum QR code limit");
        }
        
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be positive");
        }
    }
    
    private String generateQRCodeId() {
        return "QR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private String generateMerchantQRCodeId(String merchantId) {
        return "MQR_" + merchantId + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private String generateSecurePaymentData(QRCodePayment qrCodePayment) {
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("id", qrCodePayment.getQrCodeId());
        paymentData.put("v", "1.0"); // Version
        paymentData.put("t", qrCodePayment.getType().name());
        paymentData.put("u", qrCodePayment.getUserId());
        
        if (qrCodePayment.getAmount() != null) {
            paymentData.put("a", qrCodePayment.getAmount());
        }
        if (qrCodePayment.getCurrency() != null) {
            paymentData.put("c", qrCodePayment.getCurrency());
        }
        
        // Add signature for security
        String dataString = serializePaymentData(paymentData);
        String signature = generateSignature(dataString);
        paymentData.put("s", signature);
        
        return Base64.getEncoder().encodeToString(dataString.getBytes(StandardCharsets.UTF_8));
    }
    
    private String generateMerchantPaymentData(QRCodePayment merchantQRCode) {
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("id", merchantQRCode.getQrCodeId());
        paymentData.put("v", "1.0");
        paymentData.put("t", "MERCHANT");
        paymentData.put("m", merchantQRCode.getMerchantId());
        paymentData.put("mn", merchantQRCode.getMerchantName());
        
        String dataString = serializePaymentData(paymentData);
        String signature = generateSignature(dataString);
        paymentData.put("s", signature);
        
        return Base64.getEncoder().encodeToString(dataString.getBytes(StandardCharsets.UTF_8));
    }
    
    private String serializePaymentData(Map<String, Object> data) {
        return data.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .sorted()
            .reduce((a, b) -> a + "&" + b)
            .orElse("");
    }
    
    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                qrCodeSecretKey.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }
    
    /**
     * Bulk generate QR codes for merchants
     */
    @Async
    public CompletableFuture<List<QRCodeGenerationResponse>> bulkGenerateQRCodes(
            String merchantId, int count, QRCodeTemplate template) {
        
        if (count > maxBulkGeneration) {
            throw new IllegalArgumentException("Maximum bulk generation limit is " + maxBulkGeneration);
        }
        
        log.info("Bulk generating {} QR codes for merchant: {}", count, merchantId);
        
        List<CompletableFuture<QRCodeGenerationResponse>> futures = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            final int index = i;
            CompletableFuture<QRCodeGenerationResponse> future = CompletableFuture.supplyAsync(() -> {
                GenerateMerchantQRCodeRequest request = GenerateMerchantQRCodeRequest.builder()
                    .merchantId(merchantId)
                    .merchantName("Merchant " + merchantId)
                    .storeId("STORE_" + index)
                    .terminalId("TERM_" + index)
                    .defaultCurrency("USD")
                    .merchantCategory("RETAIL")
                    .build();
                    
                return generateMerchantQRCode(request);
            }, executorService);
            
            futures.add(future);
        }
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        
        return allOf.thenApply(v -> 
            futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
        );
    }
    
    /**
     * Process offline QR payment
     */
    public void processOfflinePayment(OfflineQRPayment offlinePayment) {
        log.info("Processing offline payment: {}", offlinePayment.getPaymentId());
        
        try {
            if (!validateOfflinePayment(offlinePayment)) {
                throw new IllegalArgumentException("Invalid offline payment");
            }
            
            QRCodePayment qrPayment = qrCodePaymentRepository.findByQrCodeId(offlinePayment.getQrCodeId())
                .orElseThrow(() -> new IllegalArgumentException("QR code not found"));
            
            ProcessQRCodePaymentRequest request = ProcessQRCodePaymentRequest.builder()
                .qrCodeId(offlinePayment.getQrCodeId())
                .payerId(offlinePayment.getPayerId())
                .amount(offlinePayment.getAmount())
                .build();
                
            processQRCodePayment(request);
            
            offlinePayment.setSynced(true);
            publishQREvent("OFFLINE_PAYMENT_SYNCED", offlinePayment);
            
            log.info("Offline payment processed successfully: {}", offlinePayment.getPaymentId());
            
        } catch (Exception e) {
            log.error("Error processing offline payment", e);
            offlinePayment.incrementRetryCount();
            if (offlinePayment.getRetryCount() < 3) {
                offlinePaymentQueue.offer(offlinePayment);
            } else {
                publishQREvent("OFFLINE_PAYMENT_FAILED", offlinePayment);
            }
        }
    }
    
    /**
     * Scan QR code with enhanced optimization
     */
    @Override
    @Transactional
    public QRCodeScanResponse scanQRCode(ScanQRCodeRequest request) {
        log.info("Enhanced scanning QR code by user: {}", request.getScannerUserId());
        
        // Check if offline mode
        if (request.isOfflineMode()) {
            queueOfflinePayment(request);
            return QRCodeScanResponse.builder()
                .qrCodeId(request.getQrCodeData())
                .status("OFFLINE_QUEUED")
                .build();
        }
        
        // Optimize image if provided
        if (request.getQrImageData() != null) {
            try {
                String decodedData = decodeOptimizedQRCode(request.getQrImageData());
                request.setQrCodeData(decodedData);
            } catch (Exception e) {
                log.error("Error decoding QR image", e);
            }
        }
        
        // Record scan analytics
        totalQRCodesScanned.incrementAndGet();
        recordScanAnalytics(request);
        
        return super.scanQRCode(request);
    }
    
    /**
     * Get QR code analytics
     */
    @Cacheable(value = "qr-analytics", key = "#qrCodeId")
    public QRCodeAnalytics getAnalytics(String qrCodeId) {
        return analyticsMap.computeIfAbsent(qrCodeId, k -> new QRCodeAnalytics(k));
    }
    
    private byte[] generateQRCodeImage(String data, QRCodeCustomization customization) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 2);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            
            BitMatrix bitMatrix = qrCodeWriter.encode(
                data, 
                BarcodeFormat.QR_CODE, 
                QR_CODE_SIZE, 
                QR_CODE_SIZE, 
                hints
            );
            
            // Apply customization if provided
            MatrixToImageConfig config = new MatrixToImageConfig(
                customization != null && customization.getForegroundColor() != null ? 
                    Color.decode(customization.getForegroundColor()).getRGB() : Color.BLACK.getRGB(),
                customization != null && customization.getBackgroundColor() != null ?
                    Color.decode(customization.getBackgroundColor()).getRGB() : Color.WHITE.getRGB()
            );
            
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix, config);
            
            // Add logo if enabled
            if (logoEnabled && customization != null && customization.isIncludeLogo()) {
                image = addLogoToQRCode(image, customization);
            }
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);
            
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code image", e);
        }
    }
    
    private void cacheQRCode(String qrCodeId, QRCodePayment qrCodePayment) {
        String cacheKey = QR_CACHE_PREFIX + qrCodeId;
        long ttl = qrCodePayment.getExpiresAt() != null ? 
            java.time.Duration.between(LocalDateTime.now(), qrCodePayment.getExpiresAt()).toMillis() :
            TimeUnit.MINUTES.toMillis(qrCodeExpiryMinutes);
        
        cacheService.put(cacheKey, qrCodePayment, ttl, TimeUnit.MILLISECONDS);
    }
    
    private QRCodePayment getQRCodePayment(String qrCodeId) {
        // Try cache first
        String cacheKey = QR_CACHE_PREFIX + qrCodeId;
        QRCodePayment cached = cacheService.get(cacheKey, QRCodePayment.class);
        
        if (cached != null) {
            return cached;
        }
        
        // Fallback to database
        return qrCodePaymentRepository.findByQrCodeId(qrCodeId)
            .orElseThrow(() -> new BusinessException("QR code not found"));
    }
    
    private void invalidateQRCodeCache(String qrCodeId) {
        cacheService.evict(QR_CACHE_PREFIX + qrCodeId);
    }
    
    private String extractQRCodeId(String qrCodeData) {
        // Handle different QR code formats
        if (qrCodeData.startsWith(QR_PAYMENT_URL_PREFIX)) {
            return qrCodeData.substring(QR_PAYMENT_URL_PREFIX.length());
        }
        
        // Try to decode base64 data
        try {
            String decoded = new String(Base64.getDecoder().decode(qrCodeData), StandardCharsets.UTF_8);
            // Parse and extract ID
            String[] parts = decoded.split("&");
            for (String part : parts) {
                if (part.startsWith("id=")) {
                    return part.substring(3);
                }
            }
        } catch (Exception e) {
            log.error("Failed to decode QR code data", e);
        }
        
        throw new BusinessException("Invalid QR code format");
    }
    
    private void validateQRCodeForScanning(QRCodePayment qrCodePayment, String scannerUserId) {
        // Check expiry
        if (qrCodePayment.getExpiresAt() != null && 
            LocalDateTime.now().isAfter(qrCodePayment.getExpiresAt())) {
            throw new BusinessException("QR code has expired");
        }
        
        // Check status
        if (qrCodePayment.getStatus() != QRCodePayment.Status.ACTIVE) {
            throw new BusinessException("QR code is not active");
        }
        
        // Prevent self-payment
        if (qrCodePayment.getUserId() != null && 
            qrCodePayment.getUserId().equals(scannerUserId)) {
            throw new BusinessException("Cannot pay to yourself");
        }
    }
    
    private void validatePaymentProcessing(QRCodePayment qrCodePayment, ProcessQRCodePaymentRequest request) {
        // Validate QR code status
        if (qrCodePayment.getStatus() != QRCodePayment.Status.ACTIVE) {
            throw new BusinessException("QR code is not available for payment");
        }
        
        // Validate amount if fixed
        if (qrCodePayment.getAmount() != null && request.getAmount() != null) {
            if (qrCodePayment.getAmount().compareTo(request.getAmount()) != 0) {
                throw new BusinessException("Payment amount does not match QR code amount");
            }
        }
        
        // Validate expiry
        if (qrCodePayment.getExpiresAt() != null && 
            LocalDateTime.now().isAfter(qrCodePayment.getExpiresAt())) {
            throw new BusinessException("QR code has expired");
        }
    }
    
    private String getRecipientName(QRCodePayment qrCodePayment) {
        if (qrCodePayment.getMerchantName() != null) {
            return qrCodePayment.getMerchantName();
        }
        // Would integrate with user service to get user name
        return "User " + qrCodePayment.getUserId();
    }
    
    private String processPaymentTransaction(String payerId, String recipientId, 
                                            BigDecimal amount, String currency, String description) {
        // This would integrate with the actual payment service
        // For now, generate a mock transaction ID
        return "TXN" + System.currentTimeMillis();
    }
    
    // Enhanced helper methods
    
    private byte[] applyCustomization(String qrCodeBase64, QRCodeCustomization customization) {
        try {
            byte[] qrImage = Base64.getDecoder().decode(qrCodeBase64);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(qrImage));
            
            if (customization.getBorderWidth() > 0) {
                image = addBorder(image, customization);
            }
            
            if (customization.isIncludeLogo() && logoEnabled) {
                image = addLogoToQRCode(image, customization);
            }
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Error applying customization", e);
            return Base64.getDecoder().decode(qrCodeBase64);
        }
    }
    
    private BufferedImage addLogoToQRCode(BufferedImage qrImage, QRCodeCustomization customization) {
        try {
            String logoPath = customization.getLogoPath() != null ? customization.getLogoPath() : defaultLogoPath;
            BufferedImage logo = ImageIO.read(getClass().getResourceAsStream(logoPath));
            
            int logoSize = customization.getLogoSize() > 0 ? customization.getLogoSize() : qrImage.getWidth() / 6;
            Image scaledLogo = logo.getScaledInstance(logoSize, logoSize, Image.SCALE_SMOOTH);
            
            BufferedImage combined = new BufferedImage(qrImage.getWidth(), qrImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = combined.createGraphics();
            
            g.drawImage(qrImage, 0, 0, null);
            
            int x = (qrImage.getWidth() - logoSize) / 2;
            int y = (qrImage.getHeight() - logoSize) / 2;
            g.setColor(Color.WHITE);
            g.fillRect(x - 5, y - 5, logoSize + 10, logoSize + 10);
            g.drawImage(scaledLogo, x, y, null);
            g.dispose();
            
            return combined;
        } catch (Exception e) {
            log.error("Error adding logo to QR code", e);
            return qrImage;
        }
    }
    
    private BufferedImage addBorder(BufferedImage image, QRCodeCustomization customization) {
        int borderWidth = customization.getBorderWidth();
        Color borderColor = Color.decode(customization.getBorderColor());
        
        BufferedImage bordered = new BufferedImage(
            image.getWidth() + 2 * borderWidth,
            image.getHeight() + 2 * borderWidth,
            BufferedImage.TYPE_INT_ARGB
        );
        
        Graphics2D g = bordered.createGraphics();
        g.setColor(borderColor);
        g.fillRect(0, 0, bordered.getWidth(), bordered.getHeight());
        g.drawImage(image, borderWidth, borderWidth, null);
        g.dispose();
        
        return bordered;
    }
    
    private String decodeOptimizedQRCode(byte[] imageData) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        image = optimizeImageForScanning(image);
        
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        
        MultiFormatReader reader = new MultiFormatReader();
        Result result = reader.decode(bitmap);
        
        return result.getText();
    }
    
    private BufferedImage optimizeImageForScanning(BufferedImage original) {
        BufferedImage grayscale = new BufferedImage(
            original.getWidth(), 
            original.getHeight(),
            BufferedImage.TYPE_BYTE_GRAY
        );
        
        Graphics2D g = grayscale.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();
        
        // Enhance contrast
        for (int y = 0; y < grayscale.getHeight(); y++) {
            for (int x = 0; x < grayscale.getWidth(); x++) {
                int pixel = grayscale.getRGB(x, y);
                int gray = (pixel >> 16) & 0xFF;
                gray = gray < 128 ? Math.max(0, gray - 30) : Math.min(255, gray + 30);
                int newPixel = (gray << 16) | (gray << 8) | gray;
                grayscale.setRGB(x, y, newPixel);
            }
        }
        
        return grayscale;
    }
    
    private void queueOfflinePayment(ScanQRCodeRequest request) {
        if (offlinePaymentQueue.size() >= maxOfflineQueueSize) {
            throw new IllegalStateException("Offline payment queue is full");
        }
        
        OfflineQRPayment offlinePayment = OfflineQRPayment.builder()
            .paymentId(UUID.randomUUID().toString())
            .qrCodeId(extractQRCodeId(request.getQrCodeData()))
            .payerId(request.getScannerUserId())
            .amount(request.getAmount())
            .scannedAt(Instant.now())
            .deviceId(request.getDeviceId())
            .build();
        
        offlinePaymentQueue.offer(offlinePayment);
        log.info("Queued offline payment: {}", offlinePayment.getPaymentId());
    }
    
    private boolean validateOfflinePayment(OfflineQRPayment payment) {
        Duration age = Duration.between(payment.getScannedAt(), Instant.now());
        return age.toHours() < 24;
    }
    
    private void recordScanAnalytics(ScanQRCodeRequest request) {
        String qrCodeId = extractQRCodeId(request.getQrCodeData());
        QRCodeAnalytics analytics = getAnalytics(qrCodeId);
        analytics.incrementScanCount();
        analytics.addScanner(request.getScannerUserId());
        analytics.addLocation(request.getLocation());
        analytics.addDevice(request.getDeviceType());
    }
    
    private void loadDefaultTemplates() {
        templates.put("BASIC", QRCodeTemplate.builder()
            .templateId("BASIC")
            .name("Basic Payment")
            .description("Simple payment QR code")
            .build());
        
        templates.put("DONATION", QRCodeTemplate.builder()
            .templateId("DONATION")
            .name("Donation")
            .description("Donation QR code")
            .build());
    }
    
    private void startOfflinePaymentProcessor() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                while (!offlinePaymentQueue.isEmpty()) {
                    OfflineQRPayment payment = offlinePaymentQueue.poll();
                    if (payment != null) {
                        processOfflinePayment(payment);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing offline payments", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    private void startAnalyticsAggregator() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.debug("Aggregating QR code analytics - Total generated: {}, Total scanned: {}",
                    totalQRCodesGenerated.get(), totalQRCodesScanned.get());
            } catch (Exception e) {
                log.error("Error aggregating analytics", e);
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
    
    private void publishQREvent(String eventType, Object data) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", eventType);
            event.put("timestamp", Instant.now());
            event.put("data", data);
            
            kafkaTemplate.send("qr-code-events", event);
        } catch (Exception e) {
            log.error("Error publishing QR event", e);
        }
    }
    
    private QRCodeGenerationResponse buildResponseFromPayment(QRCodePayment payment) {
        return QRCodeGenerationResponse.builder()
            .qrCodeId(payment.getQrCodeId())
            .paymentUrl(QR_PAYMENT_URL_PREFIX + payment.getQrCodeId())
            .expiresAt(payment.getExpiresAt())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .build();
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down QR Code Payment Service");
        scheduler.shutdown();
        executorService.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}