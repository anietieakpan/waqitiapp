package com.waqiti.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.exception.BadRequestException;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.payment.dto.CreatePaymentMethodRequest;
import com.waqiti.payment.dto.PaymentMethod;
import com.waqiti.payment.dto.UpdatePaymentMethodRequest;
import com.waqiti.payment.exception.DuplicatePaymentMethodException;
import com.waqiti.payment.exception.PaymentMethodLimitExceededException;
import com.waqiti.payment.repository.PaymentMethodRepository;
import com.waqiti.payment.service.encryption.PaymentEncryptionService;
import com.waqiti.payment.service.verification.PaymentMethodVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentMethodService {
    
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentEncryptionService encryptionService;
    private final PaymentMethodVerificationService verificationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${payment.method.max-per-user:10}")
    private int maxPaymentMethodsPerUser;
    
    private static final String PAYMENT_METHOD_EVENTS_TOPIC = "payment-method-events";
    
    public PaymentMethod createPaymentMethod(UUID userId, CreatePaymentMethodRequest request) {
        log.info("Creating payment method for user: {} of type: {}", userId, request.getMethodType());
        
        // Check if user has reached the limit
        long activeMethodsCount = paymentMethodRepository.countActiveMethodsByUserId(userId);
        if (activeMethodsCount >= maxPaymentMethodsPerUser) {
            throw new PaymentMethodLimitExceededException(
                String.format("User has reached the maximum limit of %d payment methods", maxPaymentMethodsPerUser)
            );
        }
        
        // Encrypt sensitive details
        String encryptedDetails = encryptionService.encryptPaymentDetails(request.getDetails());
        
        // Check for duplicate
        if (paymentMethodRepository.existsByUserIdAndEncryptedDetailsAndStatus(
                userId, encryptedDetails, com.waqiti.payment.domain.PaymentMethod.PaymentMethodStatus.ACTIVE)) {
            throw new DuplicatePaymentMethodException("This payment method already exists");
        }
        
        // Create masked details for display
        String maskedDetails = createMaskedDetails(request);
        
        // Build payment method entity
        com.waqiti.payment.domain.PaymentMethod paymentMethod = com.waqiti.payment.domain.PaymentMethod.builder()
                .userId(userId)
                .methodType(request.getMethodType())
                .provider(request.getProvider())
                .status(com.waqiti.payment.domain.PaymentMethod.PaymentMethodStatus.ACTIVE)
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : generateDisplayName(request))
                .maskedDetails(maskedDetails)
                .encryptedDetails(encryptedDetails)
                .verificationStatus(com.waqiti.payment.domain.PaymentMethod.VerificationStatus.PENDING)
                .metadata(request.getMetadata())
                .build();
        
        // Set expiry date for cards
        if (request.getMethodType() == com.waqiti.payment.domain.PaymentMethod.PaymentMethodType.CREDIT_CARD ||
            request.getMethodType() == com.waqiti.payment.domain.PaymentMethod.PaymentMethodType.DEBIT_CARD) {
            paymentMethod.setExpiresAt(calculateCardExpiryDate(request.getDetails()));
        }
        
        // Handle default setting
        if (request.isSetAsDefault()) {
            paymentMethodRepository.clearDefaultExcept(userId, UUID.randomUUID());
            paymentMethod.setDefault(true);
        }
        
        com.waqiti.payment.domain.PaymentMethod savedMethod = paymentMethodRepository.save(paymentMethod);
        
        // Initiate verification
        verificationService.initiateVerification(savedMethod);
        
        // Publish event
        publishPaymentMethodEvent("CREATED", savedMethod);
        
        return PaymentMethod.fromEntity(savedMethod);
    }
    
    public Page<PaymentMethod> getUserPaymentMethods(UUID userId, Pageable pageable) {
        return paymentMethodRepository.findByUserId(userId, pageable)
                .map(PaymentMethod::fromEntity);
    }
    
    public List<PaymentMethod> getActivePaymentMethods(UUID userId) {
        return paymentMethodRepository.findByUserIdAndStatus(userId, com.waqiti.payment.domain.PaymentMethod.PaymentMethodStatus.ACTIVE)
                .stream()
                .map(PaymentMethod::fromEntity)
                .collect(Collectors.toList());
    }
    
    public PaymentMethod getPaymentMethod(UUID userId, UUID paymentMethodId) {
        com.waqiti.payment.domain.PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        return PaymentMethod.fromEntity(paymentMethod);
    }
    
    public PaymentMethod updatePaymentMethod(UUID userId, UUID paymentMethodId, UpdatePaymentMethodRequest request) {
        com.waqiti.payment.domain.PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        
        if (request.getDisplayName() != null) {
            paymentMethod.setDisplayName(request.getDisplayName());
        }
        
        if (request.getMetadata() != null) {
            paymentMethod.setMetadata(request.getMetadata());
        }
        
        com.waqiti.payment.domain.PaymentMethod updated = paymentMethodRepository.save(paymentMethod);
        publishPaymentMethodEvent("UPDATED", updated);
        
        return PaymentMethod.fromEntity(updated);
    }
    
    public void setDefaultPaymentMethod(UUID userId, UUID paymentMethodId) {
        com.waqiti.payment.domain.PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        
        if (paymentMethod.getStatus() != com.waqiti.payment.domain.PaymentMethod.PaymentMethodStatus.ACTIVE) {
            throw new BadRequestException("Cannot set inactive payment method as default");
        }
        
        // Clear other defaults
        paymentMethodRepository.clearDefaultExcept(userId, paymentMethodId);
        
        // Set this as default
        paymentMethod.setDefault(true);
        paymentMethodRepository.save(paymentMethod);
        
        publishPaymentMethodEvent("DEFAULT_CHANGED", paymentMethod);
    }
    
    public void deletePaymentMethod(UUID userId, UUID paymentMethodId) {
        com.waqiti.payment.domain.PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        
        if (paymentMethod.isDefault()) {
            throw new BadRequestException("Cannot delete default payment method. Please set another method as default first.");
        }
        
        // Soft delete by setting status to INACTIVE
        paymentMethod.setStatus(com.waqiti.payment.domain.PaymentMethod.PaymentMethodStatus.INACTIVE);
        paymentMethodRepository.save(paymentMethod);
        
        publishPaymentMethodEvent("DELETED", paymentMethod);
    }
    
    public void verifyPaymentMethod(UUID userId, UUID paymentMethodId, Map<String, String> verificationData) {
        com.waqiti.payment.domain.PaymentMethod paymentMethod = paymentMethodRepository.findByIdAndUserId(paymentMethodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));
        
        boolean verified = verificationService.verifyPaymentMethod(paymentMethod, verificationData);
        
        if (verified) {
            paymentMethod.setVerificationStatus(com.waqiti.payment.domain.PaymentMethod.VerificationStatus.VERIFIED);
            paymentMethod.setVerificationData(Collections.singletonMap("verifiedAt", new Date()));
        } else {
            paymentMethod.setVerificationStatus(com.waqiti.payment.domain.PaymentMethod.VerificationStatus.FAILED);
        }
        
        paymentMethodRepository.save(paymentMethod);
        publishPaymentMethodEvent("VERIFICATION_" + (verified ? "SUCCESS" : "FAILED"), paymentMethod);
    }
    
    private String createMaskedDetails(CreatePaymentMethodRequest request) {
        CreatePaymentMethodRequest.PaymentDetails details = request.getDetails();
        
        switch (request.getMethodType()) {
            case BANK_ACCOUNT:
                return String.format("****%s", 
                    details.getAccountNumber().substring(Math.max(0, details.getAccountNumber().length() - 4)));
            case CREDIT_CARD:
            case DEBIT_CARD:
                return String.format("****%s", 
                    details.getCardNumber().substring(Math.max(0, details.getCardNumber().length() - 4)));
            case DIGITAL_WALLET:
                return details.getWalletProvider();
            case CRYPTOCURRENCY:
                String address = details.getWalletAddress();
                return String.format("%s...%s", 
                    address.substring(0, 6), 
                    address.substring(address.length() - 4));
            default:
                return "****";
        }
    }
    
    private String generateDisplayName(CreatePaymentMethodRequest request) {
        String masked = createMaskedDetails(request);
        return String.format("%s %s", request.getMethodType().toString().replace("_", " "), masked);
    }
    
    private LocalDate calculateCardExpiryDate(CreatePaymentMethodRequest.PaymentDetails details) {
        int month = Integer.parseInt(details.getExpiryMonth());
        int year = Integer.parseInt(details.getExpiryYear());
        if (year < 100) {
            year += 2000; // Convert 2-digit year to 4-digit
        }
        return LocalDate.of(year, month, 1).plusMonths(1).minusDays(1);
    }
    
    private void publishPaymentMethodEvent(String eventType, com.waqiti.payment.domain.PaymentMethod paymentMethod) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("paymentMethodId", paymentMethod.getMethodId());
            event.put("userId", paymentMethod.getUserId());
            event.put("methodType", paymentMethod.getMethodType());
            event.put("timestamp", System.currentTimeMillis());
            
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(PAYMENT_METHOD_EVENTS_TOPIC, paymentMethod.getMethodId(), eventJson);
            
            log.info("Published payment method event: {} for method: {}", eventType, paymentMethod.getMethodId());
        } catch (JsonProcessingException e) {
            log.error("Error publishing payment method event", e);
        }
    }
}