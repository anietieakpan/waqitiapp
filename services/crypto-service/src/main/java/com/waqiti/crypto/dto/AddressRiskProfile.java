/**
 * Address Risk Profile DTO
 * Contains risk analysis results for a cryptocurrency address
 */
package com.waqiti.crypto.dto;

import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressRiskProfile {
    private String address;
    private CryptoCurrency currency;
    private int riskScore;
    private boolean knownExchange;
    private boolean mixer;
    private boolean darkMarket;
    private boolean gambling;
    private boolean ransomware;
    private boolean newAddress;
    private boolean highFrequency;
    private boolean riskyConnections;
    private boolean privacyCoinInteraction;
    private boolean highRiskJurisdiction;
    
    public boolean isKnownExchange() {
        return knownExchange;
    }
    
    public boolean isMixer() {
        return mixer;
    }
    
    public boolean isDarkMarket() {
        return darkMarket;
    }
    
    public boolean isGambling() {
        return gambling;
    }
    
    public boolean isRansomware() {
        return ransomware;
    }
    
    public boolean isNewAddress() {
        return newAddress;
    }
    
    public boolean isHighFrequency() {
        return highFrequency;
    }
    
    public boolean hasRiskyConnections() {
        return riskyConnections;
    }
    
    public boolean hasPrivacyCoinInteraction() {
        return privacyCoinInteraction;
    }
    
    public boolean isHighRiskJurisdiction() {
        return highRiskJurisdiction;
    }
}