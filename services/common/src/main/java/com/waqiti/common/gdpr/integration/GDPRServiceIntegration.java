package com.waqiti.common.gdpr.integration;

import com.waqiti.common.gdpr.GDPRDataRepository;
import com.waqiti.common.gdpr.repository.GDPRDataRepositoryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;

/**
 * GDPR Service Integration
 *
 * Auto-discovers and registers all GDPR data repositories across microservices.
 * Each service that stores personal data should implement GDPRDataRepository
 * and this component will automatically register it.
 *
 * Example implementation in your services:
 *
 * <pre>
 * {@literal @}Component
 * public class UserProfileGDPRRepository implements GDPRDataRepository {
 *     {@literal @}Override
 *     public String getDataCategory() {
 *         return "user_profile";
 *     }
 *     // ... implement other methods
 * }
 * </pre>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-20
 */
@Slf4j
@Component
public class GDPRServiceIntegration {

    private final ApplicationContext applicationContext;
    private final GDPRDataRepositoryRegistry repositoryRegistry;

    public GDPRServiceIntegration(
            ApplicationContext applicationContext,
            GDPRDataRepositoryRegistry repositoryRegistry) {
        this.applicationContext = applicationContext;
        this.repositoryRegistry = repositoryRegistry;
    }

    /**
     * Auto-discover and register all GDPR data repositories on startup
     */
    @PostConstruct
    public void registerGDPRRepositories() {
        log.info("Starting GDPR repository auto-discovery...");

        Map<String, GDPRDataRepository> repositories =
                applicationContext.getBeansOfType(GDPRDataRepository.class);

        if (repositories.isEmpty()) {
            log.warn("No GDPR data repositories found! This may indicate a configuration issue.");
            log.warn("Each service storing personal data should implement GDPRDataRepository");
            return;
        }

        log.info("Found {} GDPR data repositories", repositories.size());

        for (Map.Entry<String, GDPRDataRepository> entry : repositories.entrySet()) {
            String beanName = entry.getKey();
            GDPRDataRepository repository = entry.getValue();

            try {
                String dataCategory = repository.getDataCategory();
                repositoryRegistry.register(dataCategory, repository);

                log.info("Registered GDPR repository: {} -> {}",
                        dataCategory, beanName);

                // Log retention policy for compliance
                Duration retentionPeriod = repository.getRetentionPeriod();
                String legalBasis = repository.getRetentionLegalBasis();
                log.info("  Retention: {} ({})", retentionPeriod, legalBasis);

            } catch (Exception e) {
                log.error("Failed to register GDPR repository: {}", beanName, e);
            }
        }

        log.info("GDPR repository registration completed. Total registered: {}",
                repositoryRegistry.getAllRepositories().size());

        // Validate critical data categories are registered
        validateCriticalDataCategories();
    }

    /**
     * Validate that critical data categories are registered
     */
    private void validateCriticalDataCategories() {
        List<String> criticalCategories = List.of(
                "user_profile",
                "payment_data",
                "kyc_documents",
                "transaction_history",
                "contact_information"
        );

        List<String> missing = new ArrayList<>();
        for (String category : criticalCategories) {
            if (repositoryRegistry.getRepository(category) == null) {
                missing.add(category);
            }
        }

        if (!missing.isEmpty()) {
            log.warn("WARNING: Critical data categories not registered: {}", missing);
            log.warn("GDPR compliance may be incomplete. Please implement GDPRDataRepository for these categories.");
        }
    }
}

/**
 * Example GDPR Repository Implementations
 * These should be in your actual service modules
 */

// Example 1: User Profile Repository
/*
@Component
@Slf4j
public class UserProfileGDPRRepository implements GDPRDataRepository {

    @Autowired
    private UserRepository userRepository;

    @Override
    public String getDataCategory() {
        return "user_profile";
    }

    @Override
    public Object getUserData(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "phoneNumber", user.getPhoneNumber(),
                        "address", user.getAddress(),
                        "createdAt", user.getCreatedAt()
                ))
                .orElse(null);
    }

    @Override
    public boolean softDeleteUserData(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setDeleted(true);
                    user.setDeletedAt(LocalDateTime.now());
                    userRepository.save(user);
                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean hardDeleteUserData(UUID userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            return true;
        }
        return false;
    }

    @Override
    public void rectifyUserData(UUID userId, Map<String, Object> corrections) {
        userRepository.findById(userId).ifPresent(user -> {
            corrections.forEach((field, value) -> {
                switch (field) {
                    case "email" -> user.setEmail((String) value);
                    case "firstName" -> user.setFirstName((String) value);
                    case "lastName" -> user.setLastName((String) value);
                    case "phoneNumber" -> user.setPhoneNumber((String) value);
                    case "address" -> user.setAddress((String) value);
                }
            });
            userRepository.save(user);
        });
    }

    @Override
    public void restrictProcessing(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setProcessingRestricted(true);
            userRepository.save(user);
        });
    }

    @Override
    public void resumeProcessing(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setProcessingRestricted(false);
            userRepository.save(user);
        });
    }

    @Override
    public boolean mustRetainForLegalReasons(UUID userId) {
        // Check if user has pending legal matters, open disputes, etc.
        return false; // Implement actual logic
    }

    @Override
    public Duration getRetentionPeriod() {
        return Duration.ofDays(2555); // 7 years
    }

    @Override
    public String getRetentionLegalBasis() {
        return "General user data retention policy";
    }
}
*/

// Example 2: Payment Data Repository
/*
@Component
@Slf4j
public class PaymentDataGDPRRepository implements GDPRDataRepository {

    @Autowired
    private PaymentRepository paymentRepository;

    @Override
    public String getDataCategory() {
        return "payment_data";
    }

    @Override
    public Object getUserData(UUID userId) {
        List<Payment> payments = paymentRepository.findByUserId(userId);
        return payments.stream()
                .map(payment -> Map.of(
                        "id", payment.getId(),
                        "amount", payment.getAmount(),
                        "currency", payment.getCurrency(),
                        "status", payment.getStatus(),
                        "createdAt", payment.getCreatedAt(),
                        "maskedCardNumber", payment.getMaskedCardNumber()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public boolean softDeleteUserData(UUID userId) {
        // Cannot soft delete - must retain for 7 years per financial regulations
        log.warn("Attempted to delete payment data for user {} - retained per legal requirements", userId);
        return false;
    }

    @Override
    public boolean hardDeleteUserData(UUID userId) {
        // Check if retention period has passed
        LocalDateTime retentionCutoff = LocalDateTime.now().minusYears(7);
        List<Payment> oldPayments = paymentRepository.findByUserIdAndCreatedAtBefore(userId, retentionCutoff);

        if (!oldPayments.isEmpty()) {
            paymentRepository.deleteAll(oldPayments);
            return true;
        }
        return false;
    }

    @Override
    public void rectifyUserData(UUID userId, Map<String, Object> corrections) {
        // Payment data is immutable - cannot be rectified
        log.warn("Attempted to rectify payment data - not permitted");
    }

    @Override
    public void restrictProcessing(UUID userId) {
        // Mark payments as restricted but keep for compliance
        paymentRepository.findByUserId(userId).forEach(payment -> {
            payment.setProcessingRestricted(true);
            paymentRepository.save(payment);
        });
    }

    @Override
    public void resumeProcessing(UUID userId) {
        paymentRepository.findByUserId(userId).forEach(payment -> {
            payment.setProcessingRestricted(false);
            paymentRepository.save(payment);
        });
    }

    @Override
    public boolean mustRetainForLegalReasons(UUID userId) {
        // Always retain payment data for 7 years per financial regulations
        LocalDateTime retentionCutoff = LocalDateTime.now().minusYears(7);
        return paymentRepository.existsByUserIdAndCreatedAtAfter(userId, retentionCutoff);
    }

    @Override
    public Duration getRetentionPeriod() {
        return Duration.ofDays(2555); // 7 years - Financial regulations
    }

    @Override
    public String getRetentionLegalBasis() {
        return "Financial Services Regulations - 7 year retention requirement";
    }
}
*/

// Example 3: KYC Documents Repository
/*
@Component
@Slf4j
public class KYCDocumentsGDPRRepository implements GDPRDataRepository {

    @Autowired
    private KYCDocumentRepository kycRepository;

    @Autowired
    private FileStorageService fileStorage;

    @Override
    public String getDataCategory() {
        return "kyc_documents";
    }

    @Override
    public Object getUserData(UUID userId) {
        List<KYCDocument> documents = kycRepository.findByUserId(userId);
        return documents.stream()
                .map(doc -> Map.of(
                        "id", doc.getId(),
                        "documentType", doc.getDocumentType(),
                        "uploadedAt", doc.getUploadedAt(),
                        "verificationStatus", doc.getVerificationStatus(),
                        "expiryDate", doc.getExpiryDate()
                        // Don't include actual document content in export
                ))
                .collect(Collectors.toList());
    }

    @Override
    public boolean softDeleteUserData(UUID userId) {
        // Anonymize but retain metadata for AML compliance
        kycRepository.findByUserId(userId).forEach(doc -> {
            doc.setAnonymized(true);
            doc.setUserId(null); // Remove user link
            kycRepository.save(doc);
        });
        return true;
    }

    @Override
    public boolean hardDeleteUserData(UUID userId) {
        // Delete files and records after retention period
        LocalDateTime retentionCutoff = LocalDateTime.now().minusYears(5);
        List<KYCDocument> oldDocs = kycRepository.findByUserIdAndUploadedAtBefore(userId, retentionCutoff);

        for (KYCDocument doc : oldDocs) {
            // Delete physical file
            fileStorage.delete(doc.getFilePath());
            // Delete database record
            kycRepository.delete(doc);
        }

        return !oldDocs.isEmpty();
    }

    @Override
    public void rectifyUserData(UUID userId, Map<String, Object> corrections) {
        // KYC documents cannot be rectified - must re-upload
        log.warn("KYC documents cannot be rectified - user must re-upload");
    }

    @Override
    public void restrictProcessing(UUID userId) {
        kycRepository.findByUserId(userId).forEach(doc -> {
            doc.setProcessingRestricted(true);
            kycRepository.save(doc);
        });
    }

    @Override
    public void resumeProcessing(UUID userId) {
        kycRepository.findByUserId(userId).forEach(doc -> {
            doc.setProcessingRestricted(false);
            kycRepository.save(doc);
        });
    }

    @Override
    public boolean mustRetainForLegalReasons(UUID userId) {
        // Retain KYC for 5 years per AML regulations
        LocalDateTime retentionCutoff = LocalDateTime.now().minusYears(5);
        return kycRepository.existsByUserIdAndUploadedAtAfter(userId, retentionCutoff);
    }

    @Override
    public Duration getRetentionPeriod() {
        return Duration.ofDays(1825); // 5 years - AML regulations
    }

    @Override
    public String getRetentionLegalBasis() {
        return "Anti-Money Laundering (AML) Regulations - 5 year retention";
    }
}
*/