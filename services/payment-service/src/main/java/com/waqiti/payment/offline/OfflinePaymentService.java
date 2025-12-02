package com.waqiti.payment.offline;

import com.waqiti.payment.offline.domain.OfflinePayment;
import com.waqiti.payment.offline.domain.OfflinePaymentStatus;
import com.waqiti.payment.offline.dto.OfflinePaymentRequest;
import com.waqiti.payment.offline.dto.OfflinePaymentResponse;
import com.waqiti.payment.offline.repository.OfflinePaymentRepository;
import com.waqiti.payment.core.model.UnifiedPaymentRequest;
import com.waqiti.payment.core.model.PaymentResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling offline P2P payments
 * Allows users to create payments while offline that sync when connectivity is restored
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfflinePaymentService {
    
    private final OfflinePaymentRepository offlinePaymentRepository;
    private final OfflinePaymentSyncService syncService;
    private final OfflinePaymentValidator validator;
    
    private static final BigDecimal MAX_OFFLINE_AMOUNT = new BigDecimal("500.00");
    private static final int MAX_PENDING_OFFLINE_PAYMENTS = 10;
    
    /**
     * Create an offline payment that will be processed when connectivity is restored
     */
    @Transactional
    public OfflinePaymentResponse createOfflinePayment(OfflinePaymentRequest request, String userId) {
        log.info("Creating offline payment for user: {} to recipient: {}", userId, request.getRecipientId());
        
        // Validate the offline payment
        validator.validateOfflinePayment(request, userId);
        
        // Check offline payment limits
        validateOfflinePaymentLimits(userId, request.getAmount());
        
        // Create offline payment record
        OfflinePayment offlinePayment = OfflinePayment.builder()
            .id(UUID.randomUUID())
            .senderId(userId)
            .recipientId(request.getRecipientId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .description(request.getDescription())
            .deviceId(request.getDeviceId())
            .clientTimestamp(request.getClientTimestamp())
            .status(OfflinePaymentStatus.PENDING_SYNC)
            .createdAt(LocalDateTime.now())
            .offlineSignature(generateOfflineSignature(request, userId))
            .qrCode(generateQRCode(request, userId))
            .bluetoothToken(request.getBluetoothToken())
            .nfcData(request.getNfcData())
            .build();
        
        offlinePayment = offlinePaymentRepository.save(offlinePayment);
        
        log.info("Offline payment created with ID: {}", offlinePayment.getId());
        
        return mapToResponse(offlinePayment);
    }
    
    /**
     * Process pending offline payments when connectivity is restored
     */
    @Transactional
    public void processPendingOfflinePayments(String userId) {
        log.info("Processing pending offline payments for user: {}", userId);
        
        List<OfflinePayment> pendingPayments = offlinePaymentRepository
            .findBySenderIdAndStatus(userId, OfflinePaymentStatus.PENDING_SYNC);
        
        for (OfflinePayment payment : pendingPayments) {
            try {
                syncService.syncOfflinePayment(payment);
                payment.setStatus(OfflinePaymentStatus.SYNCED);
                payment.setSyncedAt(LocalDateTime.now());
                offlinePaymentRepository.save(payment);
                
                log.info("Successfully synced offline payment: {}", payment.getId());
            } catch (Exception e) {
                log.error("Failed to sync offline payment: {}", payment.getId(), e);
                payment.setStatus(OfflinePaymentStatus.SYNC_FAILED);
                payment.setSyncError(e.getMessage());
                payment.setSyncAttempts(payment.getSyncAttempts() + 1);
                offlinePaymentRepository.save(payment);
            }
        }
    }
    
    /**
     * Accept an offline payment using QR code or proximity transfer
     */
    @Transactional
    public OfflinePaymentResponse acceptOfflinePayment(String paymentId, String recipientId, String verificationData) {
        log.info("Accepting offline payment: {} by recipient: {}", paymentId, recipientId);
        
        OfflinePayment payment = offlinePaymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new IllegalArgumentException("Offline payment not found"));
        
        // Verify recipient
        if (!payment.getRecipientId().equals(recipientId)) {
            throw new IllegalArgumentException("Invalid recipient for this payment");
        }
        
        // Verify the payment data (QR code, NFC, or Bluetooth)
        if (!verifyOfflinePaymentData(payment, verificationData)) {
            throw new IllegalArgumentException("Invalid verification data");
        }
        
        // Mark as accepted
        payment.setStatus(OfflinePaymentStatus.ACCEPTED_OFFLINE);
        payment.setAcceptedAt(LocalDateTime.now());
        payment.setRecipientVerificationData(verificationData);
        
        offlinePaymentRepository.save(payment);
        
        log.info("Offline payment accepted: {}", paymentId);
        
        return mapToResponse(payment);
    }
    
    /**
     * Get pending offline payments for a user
     */
    public List<OfflinePaymentResponse> getPendingOfflinePayments(String userId) {
        List<OfflinePayment> pendingPayments = offlinePaymentRepository
            .findBySenderIdAndStatus(userId, OfflinePaymentStatus.PENDING_SYNC);
        
        return pendingPayments.stream()
            .map(this::mapToResponse)
            .toList();
    }
    
    /**
     * Cancel an offline payment before it's synced
     */
    @Transactional
    public void cancelOfflinePayment(String paymentId, String userId) {
        OfflinePayment payment = offlinePaymentRepository.findById(UUID.fromString(paymentId))
            .orElseThrow(() -> new IllegalArgumentException("Offline payment not found"));
        
        if (!payment.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized to cancel this payment");
        }
        
        if (payment.getStatus() != OfflinePaymentStatus.PENDING_SYNC) {
            throw new IllegalStateException("Can only cancel pending offline payments");
        }
        
        payment.setStatus(OfflinePaymentStatus.CANCELLED);
        payment.setCancelledAt(LocalDateTime.now());
        
        offlinePaymentRepository.save(payment);
        
        log.info("Offline payment cancelled: {}", paymentId);
    }
    
    private void validateOfflinePaymentLimits(String userId, BigDecimal amount) {
        // Check amount limit
        if (amount.compareTo(MAX_OFFLINE_AMOUNT) > 0) {
            throw new IllegalArgumentException("Offline payment amount exceeds maximum limit of " + MAX_OFFLINE_AMOUNT);
        }
        
        // Check pending payments count
        long pendingCount = offlinePaymentRepository.countBySenderIdAndStatus(userId, OfflinePaymentStatus.PENDING_SYNC);
        if (pendingCount >= MAX_PENDING_OFFLINE_PAYMENTS) {
            throw new IllegalStateException("Maximum number of pending offline payments reached");
        }
        
        // Check daily offline limit
        BigDecimal dailyTotal = offlinePaymentRepository.getDailyOfflineTotal(userId, LocalDateTime.now().minusDays(1));
        if (dailyTotal.add(amount).compareTo(MAX_OFFLINE_AMOUNT.multiply(new BigDecimal("2"))) > 0) {
            throw new IllegalStateException("Daily offline payment limit exceeded");
        }
    }
    
    private String generateOfflineSignature(OfflinePaymentRequest request, String userId) {
        // Generate a cryptographic signature for offline verification
        String data = userId + request.getRecipientId() + request.getAmount() + request.getClientTimestamp();
        return UUID.randomUUID().toString(); // Simplified - should use proper cryptographic signing
    }
    
    private String generateQRCode(OfflinePaymentRequest request, String userId) {
        // Generate QR code data for offline transfer
        return String.format("waqiti://offline-payment/%s/%s/%s/%s", 
            userId, request.getRecipientId(), request.getAmount(), request.getClientTimestamp());
    }
    
    private boolean verifyOfflinePaymentData(OfflinePayment payment, String verificationData) {
        // Verify QR code, NFC data, or Bluetooth token
        // Simplified verification - should implement proper cryptographic verification
        return verificationData != null && !verificationData.isEmpty();
    }
    
    private OfflinePaymentResponse mapToResponse(OfflinePayment payment) {
        return OfflinePaymentResponse.builder()
            .id(payment.getId().toString())
            .senderId(payment.getSenderId())
            .recipientId(payment.getRecipientId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .description(payment.getDescription())
            .status(payment.getStatus().name())
            .qrCode(payment.getQrCode())
            .createdAt(payment.getCreatedAt())
            .syncedAt(payment.getSyncedAt())
            .build();
    }
    
    /**
     * Check if payment can be processed offline
     */
    public boolean canProcessOffline(UnifiedPaymentRequest request) {
        // Check criteria for offline processing
        if (request.getAmount() > 100) { // Limit offline payments to $100
            return false;
        }
        
        // Check if payment type is supported offline
        switch (request.getPaymentType()) {
            case P2P:
            case MERCHANT:
                return true;
            case INTERNATIONAL:
            case CRYPTO:
            case BILL:
                return false; // These require online processing
            default:
                return false;
        }
    }
    
    /**
     * Queue payment for offline processing
     */
    public PaymentResult queueForOfflineProcessing(UnifiedPaymentRequest request) {
        log.info("Queueing payment for offline processing: {}", request.getRequestId());
        
        // Create offline payment record
        OfflinePayment offlinePayment = new OfflinePayment();
        offlinePayment.setSenderId(request.getUserId());
        offlinePayment.setRecipientId(request.getRecipientId());
        offlinePayment.setAmount(BigDecimal.valueOf(request.getAmount()));
        offlinePayment.setCurrency(request.getCurrency());
        offlinePayment.setDescription(request.getDescription());
        offlinePayment.setStatus(OfflinePaymentStatus.PENDING);
        offlinePayment.setMetadata(request.getMetadata());
        offlinePayment.setOfflineToken(generateOfflineToken());
        offlinePayment.setQrCode(generateQRCode(offlinePayment));
        offlinePayment.setCreatedAt(LocalDateTime.now());
        
        offlinePaymentRepository.save(offlinePayment);
        
        return PaymentResult.builder()
                .paymentId(offlinePayment.getId().toString())
                .requestId(request.getRequestId())
                .status(PaymentResult.PaymentStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .provider("OFFLINE")
                .processedAt(LocalDateTime.now())
                .metadata(Map.of(
                        "offlineToken", offlinePayment.getOfflineToken(),
                        "qrCode", offlinePayment.getQrCode(),
                        "message", "Payment queued for offline processing. Will sync when connection is restored."
                ))
                .build();
    }
    
    private String generateOfflineToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}