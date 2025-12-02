package com.waqiti.payment.dwolla.service;

import com.waqiti.payment.dwolla.DwollaApiClient;
import com.waqiti.payment.dwolla.dto.DwollaCustomer;
import com.waqiti.payment.dwolla.dto.DwollaCustomerRequest;
import com.waqiti.payment.dwolla.model.DwollaCustomerRecord;
import com.waqiti.payment.dwolla.repository.DwollaCustomerRepository;
import com.waqiti.payment.exception.CustomerVerificationException;
import com.waqiti.payment.exception.PaymentProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing Dwolla customers
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DwollaCustomerService {

    private final DwollaCustomerRepository dwollaCustomerRepository;
    private final DwollaApiClient dwollaApiClient;
    
    /**
     * Create a new Dwolla customer
     */
    @Transactional
    public DwollaCustomerRecord createCustomer(String userId, DwollaCustomerRequest request) {
        log.info("Creating Dwolla customer for user: {}", userId);
        
        // Check if customer already exists
        if (dwollaCustomerRepository.existsByUserId(userId)) {
            throw new PaymentProviderException("Customer already exists for user: " + userId, "Dwolla");
        }
        
        // Validate email uniqueness
        if (dwollaCustomerRepository.existsByEmail(request.getEmail())) {
            throw new PaymentProviderException("Email already registered: " + request.getEmail(), "Dwolla");
        }
        
        try {
            // Create customer record
            DwollaCustomerRecord customer = DwollaCustomerRecord.builder()
                    .userId(userId)
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .email(request.getEmail())
                    .type(request.getType() != null ? request.getType() : "personal")
                    .status("unverified")
                    .address1(request.getAddress1())
                    .address2(request.getAddress2())
                    .city(request.getCity())
                    .state(request.getState())
                    .postalCode(request.getPostalCode())
                    .country("US")
                    .phoneNumber(request.getPhone())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .kycVerified(false)
                    .kycStatus("pending")
                    .retryCount(0)
                    .build();
            
            // Set business-specific fields
            if ("business".equalsIgnoreCase(request.getType())) {
                customer.setBusinessName(request.getBusinessName());
                customer.setEin(request.getEin());
                customer.setBusinessType(request.getBusinessType());
                customer.setBusinessClassification(request.getBusinessClassification());
            } else {
                customer.setSsnLast4(extractLast4Ssn(request.getSsn()));
                customer.setDateOfBirth(request.getDateOfBirth() != null ? 
                    request.getDateOfBirth().atStartOfDay() : null);
            }
            
            // Integrate with actual Dwolla API to create customer
            DwollaCustomer dwollaCustomer = dwollaApiClient.createCustomer(request);

            customer.setDwollaCustomerId(dwollaCustomer.getId());
            customer.setDwollaResourceLocation(dwollaCustomer.getResourceHref());
            customer.setFundingSourcesUrl(dwollaCustomer.getResourceHref() + "/funding-sources");
            customer.setStatus(dwollaCustomer.getStatus() != null ? dwollaCustomer.getStatus() : "unverified");
            
            return dwollaCustomerRepository.save(customer);
            
        } catch (Exception e) {
            log.error("Failed to create Dwolla customer for user: {}", userId, e);
            throw new PaymentProviderException("Failed to create customer: " + e.getMessage(), "Dwolla", e);
        }
    }
    
    /**
     * Get customer by user ID
     */
    public Optional<DwollaCustomerRecord> getCustomerByUserId(String userId) {
        return dwollaCustomerRepository.findByUserId(userId);
    }
    
    /**
     * Get customer by Dwolla customer ID
     */
    public Optional<DwollaCustomerRecord> getCustomerByDwollaId(String dwollaCustomerId) {
        return dwollaCustomerRepository.findByDwollaCustomerId(dwollaCustomerId);
    }
    
    /**
     * Update customer status
     */
    @Transactional
    public DwollaCustomerRecord updateCustomerStatus(String userId, String status) {
        DwollaCustomerRecord customer = dwollaCustomerRepository.findByUserId(userId)
                .orElseThrow(() -> new PaymentProviderException("Customer not found for user: " + userId, "Dwolla"));
        
        customer.setStatus(status);
        customer.setUpdatedAt(LocalDateTime.now());
        
        if ("verified".equalsIgnoreCase(status)) {
            customer.setVerifiedAt(LocalDateTime.now());
            customer.setKycVerified(true);
            customer.setKycStatus("verified");
            customer.setKycVerifiedAt(LocalDateTime.now());
        }
        
        return dwollaCustomerRepository.save(customer);
    }
    
    /**
     * Verify customer
     */
    @Transactional
    public DwollaCustomerRecord verifyCustomer(String userId) {
        DwollaCustomerRecord customer = dwollaCustomerRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomerVerificationException("Customer not found for user: " + userId, userId));
        
        try {
            // Integrate with actual Dwolla API for verification
            // Get customer status from Dwolla
            DwollaCustomer dwollaCustomer = dwollaApiClient.getCustomer(customer.getDwollaCustomerId());

            if ("suspended".equalsIgnoreCase(dwollaCustomer.getStatus()) ||
                "deactivated".equalsIgnoreCase(dwollaCustomer.getStatus())) {
                throw new CustomerVerificationException(
                    "Cannot verify customer in status: " + dwollaCustomer.getStatus(),
                    userId,
                    "KYC",
                    "Invalid customer status"
                );
            }

            // Update local record with Dwolla status
            customer.setStatus(dwollaCustomer.getStatus());
            customer.setUpdatedAt(LocalDateTime.now());

            // If Dwolla reports customer as verified, update verification fields
            if ("verified".equalsIgnoreCase(dwollaCustomer.getStatus())) {
                customer.setVerifiedAt(LocalDateTime.now());
                customer.setKycVerified(true);
                customer.setKycStatus("verified");
                customer.setKycVerifiedAt(LocalDateTime.now());
                log.info("Customer verified successfully via Dwolla API for user: {}", userId);
            } else {
                log.info("Customer verification pending - Dwolla status: {} for user: {}",
                    dwollaCustomer.getStatus(), userId);
            }

            return dwollaCustomerRepository.save(customer);
            
        } catch (CustomerVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify customer for user: {}", userId, e);
            
            // Update failure status
            customer.setLastError(e.getMessage());
            customer.setLastErrorAt(LocalDateTime.now());
            customer.setRetryCount(customer.getRetryCount() + 1);
            dwollaCustomerRepository.save(customer);
            
            throw new CustomerVerificationException(
                "Verification failed: " + e.getMessage(),
                userId,
                "KYC",
                e.getMessage(),
                e
            );
        }
    }
    
    /**
     * Get customers by status
     */
    public List<DwollaCustomerRecord> getCustomersByStatus(String status) {
        return dwollaCustomerRepository.findByStatus(status);
    }
    
    /**
     * Get unverified customers
     */
    public List<DwollaCustomerRecord> getUnverifiedCustomers() {
        return dwollaCustomerRepository.findByKycVerified(false);
    }
    
    /**
     * Get customer statistics
     */
    public Map<String, Object> getCustomerStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCustomers", dwollaCustomerRepository.count());
        stats.put("verifiedCustomers", dwollaCustomerRepository.countByStatus("verified"));
        stats.put("unverifiedCustomers", dwollaCustomerRepository.countByStatus("unverified"));
        stats.put("suspendedCustomers", dwollaCustomerRepository.countByStatus("suspended"));
        stats.put("kycVerifiedCustomers", dwollaCustomerRepository.countByKycVerified(true));
        return stats;
    }
    
    /**
     * Extract last 4 digits of SSN
     */
    private String extractLast4Ssn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return null;
        }
        return ssn.substring(ssn.length() - 4);
    }
}