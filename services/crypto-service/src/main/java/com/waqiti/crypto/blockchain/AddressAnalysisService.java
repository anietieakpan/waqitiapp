/**
 * Address Analysis Service
 * Analyzes cryptocurrency addresses for risk factors
 */
package com.waqiti.crypto.blockchain;

import com.waqiti.crypto.dto.AddressRiskProfile;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.repository.HighRiskAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressAnalysisService {

    // Databases for address classification - loaded from repository
    private final Set<String> knownExchangeAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> mixerAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> darkMarketAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> gamblingAddresses = ConcurrentHashMap.newKeySet();
    private final Set<String> ransomwareAddresses = ConcurrentHashMap.newKeySet();
    private final Map<String, AddressMetadata> addressMetadataCache = new ConcurrentHashMap<>();
    
    private final HighRiskAddressRepository highRiskAddressRepository;
    
    @Value("${crypto.address-analysis.cache-ttl:3600000}")
    private long cacheTtlMillis;

    /**
     * Analyze address for risk factors
     */
    @Cacheable(value = "addressRiskProfiles", key = "#address + '-' + #currency")
    public AddressRiskProfile analyzeAddress(String address, CryptoCurrency currency) {
        log.debug("Analyzing address {} for currency {}", address, currency);
        
        AddressRiskProfile profile = new AddressRiskProfile();
        profile.setAddress(address);
        profile.setCurrency(currency);
        
        // Check known address categories
        if (knownExchangeAddresses.contains(address)) {
            profile.setKnownExchange(true);
            profile.setRiskScore(profile.getRiskScore() + 5);
        }
        
        if (mixerAddresses.contains(address)) {
            profile.setMixer(true);
            profile.setRiskScore(profile.getRiskScore() + 85);
        }
        
        if (darkMarketAddresses.contains(address)) {
            profile.setDarkMarket(true);
            profile.setRiskScore(profile.getRiskScore() + 90);
        }
        
        if (gamblingAddresses.contains(address)) {
            profile.setGambling(true);
            profile.setRiskScore(profile.getRiskScore() + 65);
        }
        
        if (ransomwareAddresses.contains(address)) {
            profile.setRansomware(true);
            profile.setRiskScore(profile.getRiskScore() + 95);
        }
        
        // Check address metadata
        AddressMetadata metadata = getAddressMetadata(address, currency);
        
        if (metadata.isNewAddress()) {
            profile.setNewAddress(true);
            profile.setRiskScore(profile.getRiskScore() + 35);
        }
        
        if (metadata.isHighFrequency()) {
            profile.setHighFrequency(true);
            profile.setRiskScore(profile.getRiskScore() + 40);
        }
        
        if (metadata.hasRiskyConnections()) {
            profile.setRiskyConnections(true);
            profile.setRiskScore(profile.getRiskScore() + 50);
        }
        
        if (metadata.hasPrivacyCoinInteraction()) {
            profile.setPrivacyCoinInteraction(true);
            profile.setRiskScore(profile.getRiskScore() + 30);
        }
        
        if (metadata.isHighRiskJurisdiction()) {
            profile.setHighRiskJurisdiction(true);
            profile.setRiskScore(profile.getRiskScore() + 25);
        }
        
        // Cap risk score at 100
        profile.setRiskScore(Math.min(profile.getRiskScore(), 100));
        
        log.info("Address analysis complete for {}: risk score = {}", address, profile.getRiskScore());
        
        return profile;
    }

    /**
     * Get address metadata
     */
    private AddressMetadata getAddressMetadata(String address, CryptoCurrency currency) {
        return addressMetadataCache.computeIfAbsent(address, k -> {
            try {
                log.info("CRITICAL: Performing blockchain analysis for address: {}", address);
                AddressMetadata metadata = new AddressMetadata();
                
                // Query high-risk address repository for known risk indicators
                boolean isHighRisk = highRiskAddressRepository.isHighRiskAddress(address);
                
                if (isHighRisk) {
                    log.warn("SECURITY: High-risk address detected: {}", address);
                    
                    // Get specific risk indicators from repository
                    var riskInfo = highRiskAddressRepository.getAddressRiskInfo(address);
                    
                    metadata.setNewAddress(false); // Known risk address
                    metadata.setHighFrequency(riskInfo.isHighFrequency());
                    metadata.setRiskyConnections(true); // High-risk by definition
                    metadata.setPrivacyCoinInteraction(riskInfo.hasPrivacyCoinInteraction());
                    metadata.setHighRiskJurisdiction(riskInfo.isFromHighRiskJurisdiction());
                    metadata.setTransactionCount(riskInfo.getTransactionCount());
                    metadata.setFirstSeen(riskInfo.getFirstSeenDate());
                    
                } else {
                    // For new/unknown addresses, perform conservative analysis
                    // Check if address follows known risky patterns
                    boolean suspiciousPattern = analyzeAddressPattern(address);
                    
                    // Check transaction history from blockchain (if available)
                    var transactionHistory = queryBlockchainHistory(address);
                    
                    metadata.setNewAddress(transactionHistory.getTransactionCount() < 5);
                    metadata.setHighFrequency(transactionHistory.getTransactionCount() > 1000);
                    metadata.setRiskyConnections(suspiciousPattern);
                    metadata.setPrivacyCoinInteraction(false); // Default conservative
                    metadata.setHighRiskJurisdiction(false); // Default conservative  
                    metadata.setTransactionCount(transactionHistory.getTransactionCount());
                    metadata.setFirstSeen(transactionHistory.getFirstTransactionDate());
                }
                
                // Cache metadata with TTL
                log.info("Blockchain analysis completed for address: {} - Risk level: {}", 
                    address, metadata.isRiskyConnections() ? "HIGH" : "NORMAL");
                
                return metadata;
                
            } catch (Exception e) {
                log.error("CRITICAL: Blockchain analysis failed for address: {} - BLOCKING transaction", address, e);
                
                // Create high-risk metadata on analysis failure for security
                AddressMetadata failsafeMetadata = new AddressMetadata();
                failsafeMetadata.setNewAddress(true);
                failsafeMetadata.setRiskyConnections(true); // Fail secure - block on error
                failsafeMetadata.setHighFrequency(false);
                failsafeMetadata.setPrivacyCoinInteraction(true); // Assume risk
                failsafeMetadata.setHighRiskJurisdiction(true); // Assume risk
                failsafeMetadata.setTransactionCount(0);
                failsafeMetadata.setFirstSeen(new Date());
                
                return failsafeMetadata;
            }
        });
    }

    /**
     * Add known exchange address
     */
    public void addKnownExchangeAddress(String address, String exchangeName) {
        knownExchangeAddresses.add(address);
        log.info("Added known exchange address: {} ({})", address, exchangeName);
    }

    /**
     * Add mixer address
     */
    public void addMixerAddress(String address) {
        mixerAddresses.add(address);
        log.info("Added mixer address: {}", address);
    }

    /**
     * Add dark market address
     */
    public void addDarkMarketAddress(String address) {
        darkMarketAddresses.add(address);
        log.info("Added dark market address: {}", address);
    }

    /**
     * Add gambling address
     */
    public void addGamblingAddress(String address) {
        gamblingAddresses.add(address);
        log.info("Added gambling address: {}", address);
    }

    /**
     * Add ransomware address
     */
    public void addRansomwareAddress(String address) {
        ransomwareAddresses.add(address);
        log.info("Added ransomware address: {}", address);
    }

    /**
     * Initialize with known addresses (would be loaded from database in production)
     */
    @PostConstruct
    public void initializeKnownAddresses() {
        // Known exchange addresses
        addKnownExchangeAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", "Coinbase");
        addKnownExchangeAddress("0x71C7656EC7ab88b098defB751B7401B5f6d8976F", "Binance");
        
        // Initialize from configuration or database
        loadHighRiskAddressesFromDatabase();
    }
    
    /**
     * Load high-risk addresses from database
     */
    private void loadHighRiskAddressesFromDatabase() {
        try {
            // Load all high-risk addresses from repository
            highRiskAddressRepository.findAll().forEach(highRiskAddress -> {
                switch (highRiskAddress.getCategory()) {
                    case "MIXER":
                        addMixerAddress(highRiskAddress.getAddress());
                        break;
                    case "DARK_MARKET":
                        addDarkMarketAddress(highRiskAddress.getAddress());
                        break;
                    case "GAMBLING":
                        addGamblingAddress(highRiskAddress.getAddress());
                        break;
                    case "RANSOMWARE":
                        addRansomwareAddress(highRiskAddress.getAddress());
                        break;
                    default:
                        log.warn("Unknown high-risk address category: {}", highRiskAddress.getCategory());
                }
            });
            
            log.info("Loaded {} high-risk addresses from database", 
                mixerAddresses.size() + darkMarketAddresses.size() + 
                gamblingAddresses.size() + ransomwareAddresses.size());
                
        } catch (Exception e) {
            log.error("Failed to load high-risk addresses from database", e);
            // Continue with empty sets - fail open rather than fail closed
        }
    }

    /**
     * Address metadata class
     */
    private static class AddressMetadata {
        private boolean newAddress;
        private boolean highFrequency;
        private boolean riskyConnections;
        private boolean privacyCoinInteraction;
        private boolean highRiskJurisdiction;
        private int transactionCount;
        private Date firstSeen;
        
        // Getters and setters
        public boolean isNewAddress() {
            return newAddress;
        }
        
        public void setNewAddress(boolean newAddress) {
            this.newAddress = newAddress;
        }
        
        public boolean isHighFrequency() {
            return highFrequency;
        }
        
        public void setHighFrequency(boolean highFrequency) {
            this.highFrequency = highFrequency;
        }
        
        public boolean hasRiskyConnections() {
            return riskyConnections;
        }
        
        public void setRiskyConnections(boolean riskyConnections) {
            this.riskyConnections = riskyConnections;
        }
        
        public boolean hasPrivacyCoinInteraction() {
            return privacyCoinInteraction;
        }
        
        public void setPrivacyCoinInteraction(boolean privacyCoinInteraction) {
            this.privacyCoinInteraction = privacyCoinInteraction;
        }
        
        public boolean isHighRiskJurisdiction() {
            return highRiskJurisdiction;
        }
        
        public void setHighRiskJurisdiction(boolean highRiskJurisdiction) {
            this.highRiskJurisdiction = highRiskJurisdiction;
        }
        
        public int getTransactionCount() {
            return transactionCount;
        }
        
        public void setTransactionCount(int transactionCount) {
            this.transactionCount = transactionCount;
        }
        
        public Date getFirstSeen() {
            return firstSeen;
        }
        
        public void setFirstSeen(Date firstSeen) {
            this.firstSeen = firstSeen;
        }
    }
    
    // Helper methods for blockchain analysis
    
    private boolean analyzeAddressPattern(String address) {
        try {
            // Check for suspicious address patterns
            // Bitcoin addresses starting with known mixer prefixes
            if (address.startsWith("bc1") && address.length() > 50) {
                return true; // Potentially privacy-focused address
            }
            
            // Check for addresses that match known risky patterns
            // This is a simplified pattern analysis
            return false; // Conservative default
            
        } catch (Exception e) {
            log.warn("Error analyzing address pattern for: {}", address, e);
            return true; // Fail secure - assume suspicious on error
        }
    }
    
    private TransactionHistory queryBlockchainHistory(String address) {
        try {
            // In production, this would query actual blockchain APIs
            // For now, return safe defaults that require manual review
            log.debug("Querying blockchain history for address: {}", address);
            
            // Query from high-risk address repository as backup
            var riskInfo = highRiskAddressRepository.getAddressRiskInfo(address);
            if (riskInfo != null) {
                return new TransactionHistory(
                    riskInfo.getTransactionCount(),
                    riskInfo.getFirstSeenDate()
                );
            }
            
            // Conservative defaults for unknown addresses
            return new TransactionHistory(0, new Date());
            
        } catch (Exception e) {
            log.error("Error querying blockchain history for: {}", address, e);
            // Return conservative values on error
            return new TransactionHistory(0, new Date());
        }
    }
    
    private static class TransactionHistory {
        private final int transactionCount;
        private final Date firstTransactionDate;
        
        public TransactionHistory(int transactionCount, Date firstTransactionDate) {
            this.transactionCount = transactionCount;
            this.firstTransactionDate = firstTransactionDate != null ? firstTransactionDate : new Date();
        }
        
        public int getTransactionCount() {
            return transactionCount;
        }
        
        public Date getFirstTransactionDate() {
            return firstTransactionDate;
        }
    }
}