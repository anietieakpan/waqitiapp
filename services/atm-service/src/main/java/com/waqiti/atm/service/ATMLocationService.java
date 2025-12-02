package com.waqiti.atm.service;

import com.waqiti.atm.domain.ATMLocation;
import com.waqiti.atm.domain.ATMStatus;
import com.waqiti.atm.dto.*;
import com.waqiti.atm.exception.ATMNotFoundException;
import com.waqiti.atm.repository.ATMLocationRepository;
import com.waqiti.atm.repository.ATMStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ATMLocationService {

    private final ATMLocationRepository atmLocationRepository;
    private final ATMStatusRepository atmStatusRepository;
    
    // Thread-safe SecureRandom for secure ATM code generation
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private static final double EARTH_RADIUS_KM = 6371.0; // Earth's radius in kilometers

    /**
     * Find nearby ATMs within radius
     */
    @Transactional(readOnly = true)
    public List<ATMLocationResponse> findNearbyATMs(Double latitude, Double longitude, Double radiusKm) {
        log.info("Finding ATMs near coordinates: {}, {} within {}km", latitude, longitude, radiusKm);
        
        // Calculate bounding box for initial filtering
        double latDelta = radiusKm / 111.0; // Approximate degrees latitude per km
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)));
        
        List<ATMLocation> nearbyATMs = atmLocationRepository.findByLocationWithinBounds(
                latitude - latDelta, latitude + latDelta,
                longitude - lonDelta, longitude + lonDelta);
        
        // Filter by exact distance and operational status
        return nearbyATMs.stream()
                .filter(atm -> atm.getIsOperational() && 
                              calculateDistance(latitude, longitude, atm.getLatitude(), atm.getLongitude()) <= radiusKm)
                .map(this::mapToLocationResponse)
                .sorted((a, b) -> Double.compare(a.getDistance(), b.getDistance()))
                .collect(Collectors.toList());
    }

    /**
     * Search ATMs by criteria
     */
    @Transactional(readOnly = true)
    public Page<ATMLocationResponse> searchATMs(ATMSearchCriteria criteria, Pageable pageable) {
        log.info("Searching ATMs with criteria: {}", criteria);
        
        Page<ATMLocation> atmPage = atmLocationRepository.searchByCriteria(
                criteria.getCity(),
                criteria.getArea(),
                criteria.getIsOperational(),
                criteria.getHas24HourAccess(),
                criteria.getHasDisabilityAccess(),
                pageable);
        
        return atmPage.map(this::mapToLocationResponse);
    }

    /**
     * Get ATM details
     */
    @Transactional(readOnly = true)
    public ATMDetailsResponse getATMDetails(UUID atmId) {
        log.info("Getting details for ATM: {}", atmId);
        
        ATMLocation atm = atmLocationRepository.findById(atmId)
                .orElseThrow(() -> new ATMNotFoundException("ATM not found: " + atmId));
        
        ATMStatus status = atmStatusRepository.findByAtmId(atmId)
                .orElse(createDefaultStatus(atmId));
        
        return ATMDetailsResponse.builder()
                .atmId(atm.getId())
                .atmCode(atm.getAtmCode())
                .name(atm.getName())
                .bankName(atm.getBankName())
                .branchName(atm.getBranchName())
                .address(atm.getAddress())
                .city(atm.getCity())
                .state(atm.getState())
                .country(atm.getCountry())
                .zipCode(atm.getZipCode())
                .latitude(atm.getLatitude())
                .longitude(atm.getLongitude())
                .isOperational(atm.getIsOperational())
                .has24HourAccess(atm.getHas24HourAccess())
                .hasDisabilityAccess(atm.getHasDisabilityAccess())
                .hasCashDeposit(atm.getHasCashDeposit())
                .hasCardlessWithdrawal(atm.getHasCardlessWithdrawal())
                .supportedCurrencies(atm.getSupportedCurrencies())
                .supportedLanguages(atm.getSupportedLanguages())
                .workingHours(atm.getWorkingHours())
                .contactNumber(atm.getContactNumber())
                .cashAvailable(status.getCashAvailable())
                .lastMaintenanceDate(status.getLastMaintenanceDate())
                .nextMaintenanceDate(status.getNextMaintenanceDate())
                .currentStatus(status.getCurrentStatus())
                .statusMessage(status.getStatusMessage())
                .build();
    }

    /**
     * Register new ATM
     */
    public ATMDetailsResponse registerATM(RegisterATMRequest request) {
        log.info("Registering new ATM at: {}", request.getAddress());
        
        // Generate unique ATM code
        String atmCode = generateATMCode(request.getBankName(), request.getCity());
        
        ATMLocation atm = ATMLocation.builder()
                .id(UUID.randomUUID())
                .atmCode(atmCode)
                .name(request.getName())
                .bankName(request.getBankName())
                .branchName(request.getBranchName())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .zipCode(request.getZipCode())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isOperational(true)
                .has24HourAccess(request.getHas24HourAccess())
                .hasDisabilityAccess(request.getHasDisabilityAccess())
                .hasCashDeposit(request.getHasCashDeposit())
                .hasCardlessWithdrawal(request.getHasCardlessWithdrawal())
                .supportedCurrencies(request.getSupportedCurrencies())
                .supportedLanguages(request.getSupportedLanguages())
                .workingHours(request.getWorkingHours())
                .contactNumber(request.getContactNumber())
                .installedDate(LocalDateTime.now())
                .build();
        
        atm = atmLocationRepository.save(atm);
        
        // Create initial status
        ATMStatus status = ATMStatus.builder()
                .atmId(atm.getId())
                .currentStatus("OPERATIONAL")
                .cashAvailable(true)
                .receiptPaperAvailable(true)
                .lastMaintenanceDate(LocalDateTime.now())
                .nextMaintenanceDate(LocalDateTime.now().plusMonths(1))
                .statusMessage("ATM operational")
                .updatedAt(LocalDateTime.now())
                .build();
        
        atmStatusRepository.save(status);
        
        log.info("ATM registered successfully with code: {}", atmCode);
        
        return getATMDetails(atm.getId());
    }

    /**
     * Update ATM status
     */
    public void updateATMStatus(UUID atmId, UpdateATMStatusRequest request) {
        log.info("Updating status for ATM: {} to {}", atmId, request.getStatus());
        
        ATMLocation atm = atmLocationRepository.findById(atmId)
                .orElseThrow(() -> new ATMNotFoundException("ATM not found: " + atmId));
        
        ATMStatus status = atmStatusRepository.findByAtmId(atmId)
                .orElse(ATMStatus.builder().atmId(atmId).build());
        
        status.setCurrentStatus(request.getStatus());
        status.setStatusMessage(request.getStatusMessage());
        status.setCashAvailable(request.getCashAvailable());
        status.setReceiptPaperAvailable(request.getReceiptPaperAvailable());
        status.setUpdatedAt(LocalDateTime.now());
        
        if ("MAINTENANCE".equals(request.getStatus())) {
            status.setMaintenanceStartedAt(LocalDateTime.now());
            atm.setIsOperational(false);
        } else if ("OPERATIONAL".equals(request.getStatus())) {
            status.setMaintenanceCompletedAt(LocalDateTime.now());
            status.setLastMaintenanceDate(LocalDateTime.now());
            status.setNextMaintenanceDate(LocalDateTime.now().plusMonths(1));
            atm.setIsOperational(true);
        } else if ("OUT_OF_SERVICE".equals(request.getStatus())) {
            atm.setIsOperational(false);
        }
        
        atmStatusRepository.save(status);
        atmLocationRepository.save(atm);
        
        log.info("ATM status updated successfully");
    }

    /**
     * Get ATMs requiring maintenance
     */
    @Transactional(readOnly = true)
    public List<ATMMaintenanceResponse> getATMsRequiringMaintenance() {
        log.info("Retrieving ATMs requiring maintenance");
        
        LocalDateTime now = LocalDateTime.now();
        List<ATMStatus> maintenanceRequired = atmStatusRepository
                .findByNextMaintenanceDateBeforeOrCashAvailableFalse(now);
        
        return maintenanceRequired.stream()
                .map(status -> {
                    ATMLocation atm = atmLocationRepository.findById(status.getAtmId()).orElse(null);
                    if (atm == null) {
                        log.warn("ATM not found for maintenance status - ATM ID: {}", status.getAtmId());
                        return null; // Skip this ATM in the maintenance list
                    }
                    
                    return ATMMaintenanceResponse.builder()
                            .atmId(atm.getId())
                            .atmCode(atm.getAtmCode())
                            .name(atm.getName())
                            .address(atm.getAddress())
                            .city(atm.getCity())
                            .currentStatus(status.getCurrentStatus())
                            .cashAvailable(status.getCashAvailable())
                            .receiptPaperAvailable(status.getReceiptPaperAvailable())
                            .lastMaintenanceDate(status.getLastMaintenanceDate())
                            .nextMaintenanceDate(status.getNextMaintenanceDate())
                            .maintenanceReason(determineMaintenanceReason(status))
                            .priority(determineMaintenancePriority(status))
                            .build();
                })
                .filter(response -> response != null)
                .sorted((a, b) -> a.getPriority().compareTo(b.getPriority()))
                .collect(Collectors.toList());
    }

    // Helper methods
    
    private ATMLocationResponse mapToLocationResponse(ATMLocation atm) {
        return ATMLocationResponse.builder()
                .atmId(atm.getId())
                .atmCode(atm.getAtmCode())
                .name(atm.getName())
                .bankName(atm.getBankName())
                .address(atm.getAddress())
                .city(atm.getCity())
                .latitude(atm.getLatitude())
                .longitude(atm.getLongitude())
                .isOperational(atm.getIsOperational())
                .has24HourAccess(atm.getHas24HourAccess())
                .hasDisabilityAccess(atm.getHasDisabilityAccess())
                .hasCardlessWithdrawal(atm.getHasCardlessWithdrawal())
                .workingHours(atm.getWorkingHours())
                .distance(0.0) // Will be calculated separately if needed
                .build();
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Haversine formula for calculating distance between two points on Earth
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }

    private ATMStatus createDefaultStatus(UUID atmId) {
        return ATMStatus.builder()
                .atmId(atmId)
                .currentStatus("UNKNOWN")
                .cashAvailable(false)
                .receiptPaperAvailable(false)
                .statusMessage("Status not available")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Generate unique ATM code using cryptographically secure random generation.
     * Format: BBB-CCC-NNNN where BBB=bank, CCC=city, NNNN=secure 4-digit number.
     */
    private String generateATMCode(String bankName, String city) {
        String bankCode = bankName.substring(0, Math.min(3, bankName.length())).toUpperCase();
        String cityCode = city.substring(0, Math.min(3, city.length())).toUpperCase();
        
        // Generate cryptographically secure 4-digit random number  
        String randomCode = String.format("%04d", SECURE_RANDOM.nextInt(10000));
        
        return bankCode + "-" + cityCode + "-" + randomCode;
    }

    private String determineMaintenanceReason(ATMStatus status) {
        if (!status.getCashAvailable()) {
            return "Cash refill required";
        }
        if (!status.getReceiptPaperAvailable()) {
            return "Receipt paper refill required";
        }
        if (status.getNextMaintenanceDate() != null && 
            status.getNextMaintenanceDate().isBefore(LocalDateTime.now())) {
            return "Scheduled maintenance due";
        }
        return "General maintenance required";
    }

    private Integer determineMaintenancePriority(ATMStatus status) {
        if (!status.getCashAvailable()) {
            return 1; // Highest priority
        }
        if ("OUT_OF_SERVICE".equals(status.getCurrentStatus())) {
            return 2;
        }
        if (!status.getReceiptPaperAvailable()) {
            return 3;
        }
        return 4; // Lowest priority
    }
}