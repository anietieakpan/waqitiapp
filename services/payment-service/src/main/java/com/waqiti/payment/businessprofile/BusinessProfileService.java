package com.waqiti.payment.businessprofile;

import com.waqiti.common.exceptions.BusinessException;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.payment.businessprofile.dto.*;
import com.waqiti.payment.businessprofile.repository.BusinessProfileRepository;
import com.waqiti.payment.businessprofile.validation.BusinessValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessProfileService {

    private final BusinessProfileRepository profileRepository;
    private final BusinessValidator businessValidator;
    private final TaxService taxService;
    private final ComplianceService complianceService;
    private final PaymentMethodService paymentMethodService;
    private final NotificationService notificationService;
    private final AnalyticsService analyticsService;
    private final SecurityContext securityContext;

    @Transactional
    public BusinessProfile createBusinessProfile(BusinessProfileRequest request) {
        log.info("Creating business profile for user: {}", securityContext.getUserId());
        
        // Validate business information
        validateBusinessInformation(request);
        
        // Check for duplicate business
        checkDuplicateBusiness(request);
        
        // Create profile
        BusinessProfile profile = BusinessProfile.builder()
                .id(UUID.randomUUID())
                .userId(securityContext.getUserId())
                .businessName(request.getBusinessName())
                .legalName(request.getLegalName())
                .businessType(request.getBusinessType())
                .industry(request.getIndustry())
                .description(request.getDescription())
                .logo(request.getLogo())
                .website(request.getWebsite())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .taxId(request.getTaxId())
                .registrationNumber(request.getRegistrationNumber())
                .incorporationDate(request.getIncorporationDate())
                .status(ProfileStatus.PENDING_VERIFICATION)
                .createdAt(Instant.now())
                .build();
        
        // Set business hours
        if (request.getBusinessHours() != null) {
            profile.setBusinessHours(request.getBusinessHours());
        } else {
            profile.setBusinessHours(getDefaultBusinessHours());
        }
        
        // Set payment settings
        profile.setPaymentSettings(createDefaultPaymentSettings(request));
        
        // Set invoice settings
        profile.setInvoiceSettings(createDefaultInvoiceSettings(request));
        
        // Save profile
        profile = profileRepository.save(profile);
        
        // Initiate verification process
        initiateVerificationProcess(profile);
        
        // Send welcome notification
        sendWelcomeNotification(profile);
        
        // Track analytics
        trackProfileCreation(profile);
        
        log.info("Business profile created: {}", profile.getId());
        return profile;
    }

    @Transactional
    public BusinessProfile updateBusinessProfile(UUID profileId, BusinessProfileUpdateRequest request) {
        log.info("Updating business profile: {}", profileId);
        
        BusinessProfile profile = getBusinessProfile(profileId);
        
        // Verify ownership
        if (!profile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to business profile");
        }
        
        // Update fields
        if (request.getBusinessName() != null) {
            profile.setBusinessName(request.getBusinessName());
        }
        if (request.getDescription() != null) {
            profile.setDescription(request.getDescription());
        }
        if (request.getLogo() != null) {
            profile.setLogo(request.getLogo());
        }
        if (request.getWebsite() != null) {
            profile.setWebsite(request.getWebsite());
        }
        if (request.getEmail() != null) {
            profile.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }
        if (request.getBusinessHours() != null) {
            profile.setBusinessHours(request.getBusinessHours());
        }
        
        profile.setUpdatedAt(Instant.now());
        
        // Re-verify if critical fields changed
        if (request.getTaxId() != null || request.getRegistrationNumber() != null) {
            profile.setStatus(ProfileStatus.PENDING_VERIFICATION);
            initiateVerificationProcess(profile);
        }
        
        return profileRepository.save(profile);
    }

    @Transactional
    public void verifyBusinessProfile(UUID profileId, VerificationResult result) {
        log.info("Verifying business profile: {}", profileId);
        
        BusinessProfile profile = getBusinessProfile(profileId);
        
        if (result.isVerified()) {
            profile.setStatus(ProfileStatus.VERIFIED);
            profile.setVerifiedAt(Instant.now());
            profile.setVerificationDetails(result.getDetails());
            
            // Enable advanced features
            enableAdvancedFeatures(profile);
            
            // Send verification success notification
            sendVerificationSuccessNotification(profile);
        } else {
            profile.setStatus(ProfileStatus.VERIFICATION_FAILED);
            profile.setVerificationFailureReason(result.getFailureReason());
            
            // Send verification failure notification
            sendVerificationFailureNotification(profile, result.getFailureReason());
        }
        
        profileRepository.save(profile);
    }

    @Cacheable(value = "business-profiles", key = "#profileId")
    public BusinessProfile getBusinessProfile(UUID profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new BusinessException("Business profile not found"));
    }

    public Page<BusinessProfile> searchBusinessProfiles(BusinessSearchCriteria criteria, Pageable pageable) {
        return profileRepository.searchProfiles(
                criteria.getQuery(),
                criteria.getIndustry(),
                criteria.getBusinessType(),
                criteria.getVerifiedOnly(),
                pageable
        );
    }

    @Transactional
    public PaymentSettings updatePaymentSettings(UUID profileId, PaymentSettingsRequest request) {
        BusinessProfile profile = getBusinessProfile(profileId);
        
        // Verify ownership
        if (!profile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to business profile");
        }
        
        PaymentSettings settings = profile.getPaymentSettings();
        if (settings == null) {
            settings = new PaymentSettings();
        }
        
        // Update payment methods
        if (request.getAcceptedPaymentMethods() != null) {
            settings.setAcceptedPaymentMethods(request.getAcceptedPaymentMethods());
        }
        
        // Update currencies
        if (request.getAcceptedCurrencies() != null) {
            settings.setAcceptedCurrencies(request.getAcceptedCurrencies());
        }
        
        // Update limits
        if (request.getMinTransactionAmount() != null) {
            settings.setMinTransactionAmount(request.getMinTransactionAmount());
        }
        if (request.getMaxTransactionAmount() != null) {
            settings.setMaxTransactionAmount(request.getMaxTransactionAmount());
        }
        if (request.getDailyLimit() != null) {
            settings.setDailyLimit(request.getDailyLimit());
        }
        
        // Update fees
        if (request.getTransactionFeePercentage() != null) {
            settings.setTransactionFeePercentage(request.getTransactionFeePercentage());
        }
        if (request.getFixedTransactionFee() != null) {
            settings.setFixedTransactionFee(request.getFixedTransactionFee());
        }
        
        // Update settlement
        if (request.getSettlementFrequency() != null) {
            settings.setSettlementFrequency(request.getSettlementFrequency());
        }
        if (request.getSettlementAccount() != null) {
            settings.setSettlementAccount(request.getSettlementAccount());
        }
        
        profile.setPaymentSettings(settings);
        profileRepository.save(profile);
        
        return settings;
    }

    @Transactional
    public InvoiceSettings updateInvoiceSettings(UUID profileId, InvoiceSettingsRequest request) {
        BusinessProfile profile = getBusinessProfile(profileId);
        
        // Verify ownership
        if (!profile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to business profile");
        }
        
        InvoiceSettings settings = profile.getInvoiceSettings();
        if (settings == null) {
            settings = new InvoiceSettings();
        }
        
        // Update invoice settings
        if (request.getInvoicePrefix() != null) {
            settings.setInvoicePrefix(request.getInvoicePrefix());
        }
        if (request.getStartingNumber() != null) {
            settings.setStartingNumber(request.getStartingNumber());
        }
        if (request.getDefaultDueDays() != null) {
            settings.setDefaultDueDays(request.getDefaultDueDays());
        }
        if (request.getDefaultPaymentTerms() != null) {
            settings.setDefaultPaymentTerms(request.getDefaultPaymentTerms());
        }
        if (request.getDefaultNotes() != null) {
            settings.setDefaultNotes(request.getDefaultNotes());
        }
        if (request.getLogoUrl() != null) {
            settings.setLogoUrl(request.getLogoUrl());
        }
        if (request.getTemplate() != null) {
            settings.setTemplate(request.getTemplate());
        }
        if (request.getTaxSettings() != null) {
            settings.setTaxSettings(request.getTaxSettings());
        }
        if (request.isAutoSendEnabled() != null) {
            settings.setAutoSendEnabled(request.isAutoSendEnabled());
        }
        if (request.isAutoRemindersEnabled() != null) {
            settings.setAutoRemindersEnabled(request.isAutoRemindersEnabled());
        }
        
        profile.setInvoiceSettings(settings);
        profileRepository.save(profile);
        
        return settings;
    }

    @Transactional
    public BusinessMetrics getBusinessMetrics(UUID profileId, MetricsPeriod period) {
        BusinessProfile profile = getBusinessProfile(profileId);
        
        // Verify ownership
        if (!profile.getUserId().equals(securityContext.getUserId())) {
            throw new BusinessException("Unauthorized access to business profile");
        }
        
        return BusinessMetrics.builder()
                .profileId(profileId)
                .period(period)
                .revenue(calculateRevenue(profileId, period))
                .transactionCount(getTransactionCount(profileId, period))
                .averageTransactionValue(calculateAverageTransactionValue(profileId, period))
                .customerCount(getCustomerCount(profileId, period))
                .newCustomers(getNewCustomerCount(profileId, period))
                .topProducts(getTopProducts(profileId, period))
                .topCustomers(getTopCustomers(profileId, period))
                .revenueByDay(getRevenueByDay(profileId, period))
                .revenueByCategory(getRevenueByCategory(profileId, period))
                .paymentMethodBreakdown(getPaymentMethodBreakdown(profileId, period))
                .build();
    }

    @CacheEvict(value = "business-profiles", key = "#profileId")
    @Transactional
    public void suspendBusinessProfile(UUID profileId, String reason) {
        BusinessProfile profile = getBusinessProfile(profileId);
        profile.setStatus(ProfileStatus.SUSPENDED);
        profile.setSuspensionReason(reason);
        profile.setSuspendedAt(Instant.now());
        profileRepository.save(profile);
        
        // Notify business owner
        sendSuspensionNotification(profile, reason);
    }

    @CacheEvict(value = "business-profiles", key = "#profileId")
    @Transactional
    public void reactivateBusinessProfile(UUID profileId) {
        BusinessProfile profile = getBusinessProfile(profileId);
        profile.setStatus(ProfileStatus.ACTIVE);
        profile.setSuspensionReason(null);
        profile.setSuspendedAt(null);
        profileRepository.save(profile);
        
        // Notify business owner
        sendReactivationNotification(profile);
    }

    private void validateBusinessInformation(BusinessProfileRequest request) {
        // Validate tax ID
        if (!businessValidator.isValidTaxId(request.getTaxId(), request.getCountry())) {
            throw new BusinessException("Invalid tax ID for country: " + request.getCountry());
        }
        
        // Validate registration number
        if (!businessValidator.isValidRegistrationNumber(request.getRegistrationNumber(), request.getCountry())) {
            throw new BusinessException("Invalid registration number");
        }
        
        // Validate business type
        if (!businessValidator.isValidBusinessType(request.getBusinessType())) {
            throw new BusinessException("Invalid business type");
        }
    }

    private void checkDuplicateBusiness(BusinessProfileRequest request) {
        // Check by tax ID
        if (profileRepository.existsByTaxId(request.getTaxId())) {
            throw new BusinessException("Business with this tax ID already exists");
        }
        
        // Check by registration number
        if (profileRepository.existsByRegistrationNumber(request.getRegistrationNumber())) {
            throw new BusinessException("Business with this registration number already exists");
        }
    }

    private Map<String, BusinessHours> getDefaultBusinessHours() {
        Map<String, BusinessHours> hours = new HashMap<>();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")) {
            hours.put(day, BusinessHours.builder()
                    .openTime(LocalTime.of(9, 0))
                    .closeTime(LocalTime.of(18, 0))
                    .isOpen(true)
                    .build());
        }
        hours.put("SATURDAY", BusinessHours.builder()
                .openTime(LocalTime.of(10, 0))
                .closeTime(LocalTime.of(14, 0))
                .isOpen(true)
                .build());
        hours.put("SUNDAY", BusinessHours.builder()
                .isOpen(false)
                .build());
        return hours;
    }

    private PaymentSettings createDefaultPaymentSettings(BusinessProfileRequest request) {
        return PaymentSettings.builder()
                .acceptedPaymentMethods(Set.of("CARD", "BANK_TRANSFER", "WALLET", "QR_CODE"))
                .acceptedCurrencies(Set.of("USD", "EUR", "GBP"))
                .minTransactionAmount(BigDecimal.ONE)
                .maxTransactionAmount(BigDecimal.valueOf(100000))
                .dailyLimit(BigDecimal.valueOf(500000))
                .transactionFeePercentage(BigDecimal.valueOf(2.5))
                .fixedTransactionFee(BigDecimal.valueOf(0.30))
                .settlementFrequency(SettlementFrequency.DAILY)
                .instantPayoutEnabled(false)
                .refundsEnabled(true)
                .partialRefundsEnabled(true)
                .build();
    }

    private InvoiceSettings createDefaultInvoiceSettings(BusinessProfileRequest request) {
        return InvoiceSettings.builder()
                .invoicePrefix("INV")
                .startingNumber(1000L)
                .defaultDueDays(30)
                .defaultPaymentTerms("Net 30")
                .template("standard")
                .autoSendEnabled(true)
                .autoRemindersEnabled(true)
                .reminderDays(List.of(7, 3, 1))
                .lateFeeEnabled(false)
                .build();
    }

    private void initiateVerificationProcess(BusinessProfile profile) {
        CompletableFuture.runAsync(() -> {
            try {
                // Verify with government databases
                boolean govVerified = complianceService.verifyWithGovernmentDatabase(
                        profile.getTaxId(),
                        profile.getRegistrationNumber(),
                        profile.getCountry()
                );
                
                // Verify bank account
                boolean bankVerified = paymentMethodService.verifyBankAccount(
                        profile.getPaymentSettings().getSettlementAccount()
                );
                
                // Check sanctions lists
                boolean sanctionsClean = complianceService.checkSanctionsList(
                        profile.getLegalName(),
                        profile.getTaxId()
                );
                
                // Create verification result
                VerificationResult result = VerificationResult.builder()
                        .verified(govVerified && bankVerified && sanctionsClean)
                        .details(Map.of(
                                "government_verified", govVerified,
                                "bank_verified", bankVerified,
                                "sanctions_clean", sanctionsClean
                        ))
                        .build();
                
                verifyBusinessProfile(profile.getId(), result);
            } catch (Exception e) {
                log.error("Error verifying business profile: {}", profile.getId(), e);
            }
        });
    }

    private void enableAdvancedFeatures(BusinessProfile profile) {
        // Enable instant payouts
        profile.getPaymentSettings().setInstantPayoutEnabled(true);
        
        // Enable higher limits
        profile.getPaymentSettings().setMaxTransactionAmount(BigDecimal.valueOf(1000000));
        profile.getPaymentSettings().setDailyLimit(BigDecimal.valueOf(5000000));
        
        // Enable additional payment methods
        profile.getPaymentSettings().getAcceptedPaymentMethods().add("CRYPTO");
        profile.getPaymentSettings().getAcceptedPaymentMethods().add("LIGHTNING");
    }

    private BigDecimal calculateRevenue(UUID profileId, MetricsPeriod period) {
        return analyticsService.calculateRevenue(profileId, period.getStartDate(), period.getEndDate());
    }

    private long getTransactionCount(UUID profileId, MetricsPeriod period) {
        return analyticsService.getTransactionCount(profileId, period.getStartDate(), period.getEndDate());
    }

    private BigDecimal calculateAverageTransactionValue(UUID profileId, MetricsPeriod period) {
        return analyticsService.calculateAverageTransactionValue(profileId, period.getStartDate(), period.getEndDate());
    }

    private long getCustomerCount(UUID profileId, MetricsPeriod period) {
        return analyticsService.getUniqueCustomerCount(profileId, period.getStartDate(), period.getEndDate());
    }

    private long getNewCustomerCount(UUID profileId, MetricsPeriod period) {
        return analyticsService.getNewCustomerCount(profileId, period.getStartDate(), period.getEndDate());
    }

    private List<ProductMetric> getTopProducts(UUID profileId, MetricsPeriod period) {
        return analyticsService.getTopProducts(profileId, period.getStartDate(), period.getEndDate(), 10);
    }

    private List<CustomerMetric> getTopCustomers(UUID profileId, MetricsPeriod period) {
        return analyticsService.getTopCustomers(profileId, period.getStartDate(), period.getEndDate(), 10);
    }

    private Map<String, BigDecimal> getRevenueByDay(UUID profileId, MetricsPeriod period) {
        return analyticsService.getRevenueByDay(profileId, period.getStartDate(), period.getEndDate());
    }

    private Map<String, BigDecimal> getRevenueByCategory(UUID profileId, MetricsPeriod period) {
        return analyticsService.getRevenueByCategory(profileId, period.getStartDate(), period.getEndDate());
    }

    private Map<String, Long> getPaymentMethodBreakdown(UUID profileId, MetricsPeriod period) {
        return analyticsService.getPaymentMethodBreakdown(profileId, period.getStartDate(), period.getEndDate());
    }

    private void sendWelcomeNotification(BusinessProfile profile) {
        notificationService.sendBusinessWelcomeEmail(profile);
    }

    private void sendVerificationSuccessNotification(BusinessProfile profile) {
        notificationService.sendVerificationSuccessEmail(profile);
    }

    private void sendVerificationFailureNotification(BusinessProfile profile, String reason) {
        notificationService.sendVerificationFailureEmail(profile, reason);
    }

    private void sendSuspensionNotification(BusinessProfile profile, String reason) {
        notificationService.sendSuspensionEmail(profile, reason);
    }

    private void sendReactivationNotification(BusinessProfile profile) {
        notificationService.sendReactivationEmail(profile);
    }

    private void trackProfileCreation(BusinessProfile profile) {
        analyticsService.trackEvent("business_profile_created", Map.of(
                "profile_id", profile.getId(),
                "business_type", profile.getBusinessType(),
                "industry", profile.getIndustry()
        ));
    }
}