package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event for phone number alerts
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PhoneNumberAlertEvent extends UserEvent {
    
    private String phoneNumber;
    private String previousPhoneNumber;
    private String alertType; // NUMBER_CHANGED, NUMBER_VERIFIED, NUMBER_BLACKLISTED, SIM_SWAP_DETECTED, CARRIER_CHANGE
    private String alertSeverity; // LOW, MEDIUM, HIGH, CRITICAL
    private String country;
    private String carrier;
    private String previousCarrier;
    private LocalDateTime changeDetectedAt;
    private boolean verified;
    private String verificationMethod;
    private boolean simSwapDetected;
    private LocalDateTime simSwapTime;
    private boolean suspiciousActivity;
    private String riskScore;
    private Map<String, String> alertDetails;
    private boolean actionTaken;
    private String actionDescription;
    
    public PhoneNumberAlertEvent() {
        super("PHONE_NUMBER_ALERT");
    }
    
    public static PhoneNumberAlertEvent numberChanged(String userId, String previousNumber, String newNumber, 
                                                    String country, boolean verified) {
        PhoneNumberAlertEvent event = new PhoneNumberAlertEvent();
        event.setUserId(userId);
        event.setPreviousPhoneNumber(previousNumber);
        event.setPhoneNumber(newNumber);
        event.setAlertType("NUMBER_CHANGED");
        event.setAlertSeverity(verified ? "LOW" : "MEDIUM");
        event.setCountry(country);
        event.setVerified(verified);
        event.setChangeDetectedAt(LocalDateTime.now());
        return event;
    }
    
    public static PhoneNumberAlertEvent simSwapDetected(String userId, String phoneNumber, LocalDateTime swapTime, 
                                                      String carrier, String previousCarrier) {
        PhoneNumberAlertEvent event = new PhoneNumberAlertEvent();
        event.setUserId(userId);
        event.setPhoneNumber(phoneNumber);
        event.setAlertType("SIM_SWAP_DETECTED");
        event.setAlertSeverity("CRITICAL");
        event.setSimSwapDetected(true);
        event.setSimSwapTime(swapTime);
        event.setCarrier(carrier);
        event.setPreviousCarrier(previousCarrier);
        event.setSuspiciousActivity(true);
        event.setChangeDetectedAt(LocalDateTime.now());
        event.setActionTaken(true);
        event.setActionDescription("Account temporarily locked pending verification");
        return event;
    }
    
    public static PhoneNumberAlertEvent numberBlacklisted(String userId, String phoneNumber, String reason) {
        PhoneNumberAlertEvent event = new PhoneNumberAlertEvent();
        event.setUserId(userId);
        event.setPhoneNumber(phoneNumber);
        event.setAlertType("NUMBER_BLACKLISTED");
        event.setAlertSeverity("HIGH");
        event.setSuspiciousActivity(true);
        event.setChangeDetectedAt(LocalDateTime.now());
        Map<String, String> details = Map.of("blacklist_reason", reason);
        event.setAlertDetails(details);
        return event;
    }
}