package com.waqiti.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {
    @Id
    private UUID userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;

    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    private String country;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(name = "preferred_language")
    private String preferredLanguage;

    @Column(name = "preferred_currency")
    private String preferredCurrency;

    private String timezone;

    // Notification Settings
    @Column(name = "email_notifications", columnDefinition = "boolean default true")
    private Boolean emailNotifications = true;

    @Column(name = "sms_notifications", columnDefinition = "boolean default true")
    private Boolean smsNotifications = true;

    @Column(name = "push_notifications", columnDefinition = "boolean default true")
    private Boolean pushNotifications = true;

    @Column(name = "transaction_notifications", columnDefinition = "boolean default true")
    private Boolean transactionNotifications = true;

    @Column(name = "security_notifications", columnDefinition = "boolean default true")
    private Boolean securityNotifications = true;

    @Column(name = "marketing_notifications", columnDefinition = "boolean default false")
    private Boolean marketingNotifications = false;

    // Privacy Settings
    @Column(name = "profile_visibility")
    private String profileVisibility = "PRIVATE";

    @Column(name = "allow_data_sharing", columnDefinition = "boolean default false")
    private Boolean allowDataSharing = false;

    @Column(name = "allow_analytics_tracking", columnDefinition = "boolean default true")
    private Boolean allowAnalyticsTracking = true;

    @Column(name = "allow_third_party_integrations", columnDefinition = "boolean default false")
    private Boolean allowThirdPartyIntegrations = false;

    @Column(name = "allow_location_tracking", columnDefinition = "boolean default false")
    private Boolean allowLocationTracking = false;

    @Column(name = "show_transaction_history", columnDefinition = "boolean default true")
    private Boolean showTransactionHistory = true;

    @Column(name = "data_retention_preference")
    private String dataRetentionPreference = "DEFAULT";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Audit fields
    @Setter
    @Column(name = "created_by")
    private String createdBy;
    
    @Setter
    @Column(name = "updated_by")
    private String updatedBy;

    /**
     * Creates a new user profile
     */
    public static UserProfile create(User user) {
        UserProfile profile = new UserProfile();
        profile.user = user;
        profile.preferredLanguage = "en";
        profile.preferredCurrency = "USD";
        profile.createdAt = LocalDateTime.now();
        profile.updatedAt = LocalDateTime.now();
        return profile;
    }

    /**
     * Updates the user's name
     */
    public void updateName(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the user's date of birth
     */
    public void updateDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the user's address
     */
    public void updateAddress(String addressLine1, String addressLine2, String city, 
                              String state, String postalCode, String country) {
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the user's profile picture
     */
    public void updateProfilePicture(String profilePictureUrl) {
        this.profilePictureUrl = profilePictureUrl;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Updates the user's preferences
     */
    public void updatePreferences(String preferredLanguage, String preferredCurrency) {
        if (preferredLanguage != null && !preferredLanguage.isEmpty()) {
            this.preferredLanguage = preferredLanguage;
        }
        
        if (preferredCurrency != null && !preferredCurrency.isEmpty()) {
            this.preferredCurrency = preferredCurrency;
        }
        
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Gets the user's full name
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return "";
        }
    }

    // Setters for new fields

    public void setTimezone(String timezone) {
        this.timezone = timezone;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
        this.updatedAt = LocalDateTime.now();
    }

    // Notification settings setters
    public void setEmailNotifications(Boolean emailNotifications) {
        this.emailNotifications = emailNotifications;
        this.updatedAt = LocalDateTime.now();
    }

    public void setSmsNotifications(Boolean smsNotifications) {
        this.smsNotifications = smsNotifications;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPushNotifications(Boolean pushNotifications) {
        this.pushNotifications = pushNotifications;
        this.updatedAt = LocalDateTime.now();
    }

    public void setTransactionNotifications(Boolean transactionNotifications) {
        this.transactionNotifications = transactionNotifications;
        this.updatedAt = LocalDateTime.now();
    }

    public void setSecurityNotifications(Boolean securityNotifications) {
        this.securityNotifications = securityNotifications;
        this.updatedAt = LocalDateTime.now();
    }

    public void setMarketingNotifications(Boolean marketingNotifications) {
        this.marketingNotifications = marketingNotifications;
        this.updatedAt = LocalDateTime.now();
    }

    // Privacy settings setters
    public void setProfileVisibility(String profileVisibility) {
        this.profileVisibility = profileVisibility;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAllowDataSharing(Boolean allowDataSharing) {
        this.allowDataSharing = allowDataSharing;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAllowAnalyticsTracking(Boolean allowAnalyticsTracking) {
        this.allowAnalyticsTracking = allowAnalyticsTracking;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAllowThirdPartyIntegrations(Boolean allowThirdPartyIntegrations) {
        this.allowThirdPartyIntegrations = allowThirdPartyIntegrations;
        this.updatedAt = LocalDateTime.now();
    }

    public void setAllowLocationTracking(Boolean allowLocationTracking) {
        this.allowLocationTracking = allowLocationTracking;
        this.updatedAt = LocalDateTime.now();
    }

    public void setShowTransactionHistory(Boolean showTransactionHistory) {
        this.showTransactionHistory = showTransactionHistory;
        this.updatedAt = LocalDateTime.now();
    }

    public void setDataRetentionPreference(String dataRetentionPreference) {
        this.dataRetentionPreference = dataRetentionPreference;
        this.updatedAt = LocalDateTime.now();
    }
}