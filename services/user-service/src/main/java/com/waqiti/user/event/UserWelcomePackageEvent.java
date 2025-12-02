package com.waqiti.user.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Event for user welcome package initialization
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserWelcomePackageEvent extends UserEvent {
    
    private String email;
    private String username;
    private String firstName;
    private String packageType; // BASIC, PREMIUM, VIP, REFERRAL, PROMOTIONAL
    private BigDecimal welcomeBonus;
    private String currency;
    private List<String> includedFeatures;
    private Map<String, String> promotions;
    private String referralCode;
    private String referredBy;
    private BigDecimal referralBonus;
    private LocalDateTime packageActivatedAt;
    private LocalDateTime packageExpiresAt;
    private boolean firstTimeUser;
    private String accountType;
    private String region;
    private List<String> tutorialSteps;
    private Map<String, Object> personalizedContent;
    private boolean emailSent;
    private boolean smsSent;
    private boolean pushNotificationSent;
    
    public UserWelcomePackageEvent() {
        super("USER_WELCOME_PACKAGE");
    }
    
    public static UserWelcomePackageEvent standardPackage(String userId, String email, String username, 
                                                        String firstName, String accountType) {
        UserWelcomePackageEvent event = new UserWelcomePackageEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setFirstName(firstName);
        event.setPackageType("BASIC");
        event.setAccountType(accountType);
        event.setFirstTimeUser(true);
        event.setPackageActivatedAt(LocalDateTime.now());
        event.setPackageExpiresAt(LocalDateTime.now().plusDays(30));
        return event;
    }
    
    public static UserWelcomePackageEvent referralPackage(String userId, String email, String username, 
                                                        String firstName, String referralCode, 
                                                        String referredBy, BigDecimal referralBonus) {
        UserWelcomePackageEvent event = new UserWelcomePackageEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setFirstName(firstName);
        event.setPackageType("REFERRAL");
        event.setReferralCode(referralCode);
        event.setReferredBy(referredBy);
        event.setReferralBonus(referralBonus);
        event.setCurrency("USD");
        event.setFirstTimeUser(true);
        event.setPackageActivatedAt(LocalDateTime.now());
        event.setPackageExpiresAt(LocalDateTime.now().plusDays(60));
        return event;
    }
    
    public static UserWelcomePackageEvent premiumPackage(String userId, String email, String username, 
                                                       String firstName, BigDecimal welcomeBonus, 
                                                       List<String> features) {
        UserWelcomePackageEvent event = new UserWelcomePackageEvent();
        event.setUserId(userId);
        event.setEmail(email);
        event.setUsername(username);
        event.setFirstName(firstName);
        event.setPackageType("PREMIUM");
        event.setWelcomeBonus(welcomeBonus);
        event.setCurrency("USD");
        event.setIncludedFeatures(features);
        event.setFirstTimeUser(true);
        event.setPackageActivatedAt(LocalDateTime.now());
        event.setPackageExpiresAt(LocalDateTime.now().plusDays(90));
        return event;
    }
}