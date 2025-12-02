package com.waqiti.common.events.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * User Registered Event
 * 
 * Published when a new user successfully completes registration.
 * This event triggers welcome workflows, analytics tracking, and initial account setup.
 * 
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    @JsonProperty("eventVersion")
    private String eventVersion;

    @JsonProperty("source")
    private String source;

    @JsonProperty("userId")
    private UUID userId;

    @JsonProperty("username")
    private String username;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phoneNumber")
    private String phoneNumber;

    @JsonProperty("externalId")
    private String externalId;

    @JsonProperty("userStatus")
    private String userStatus;

    @JsonProperty("emailVerified")
    private Boolean emailVerified;

    @JsonProperty("phoneVerified")
    private Boolean phoneVerified;

    @JsonProperty("kycStatus")
    private String kycStatus;

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("dateOfBirth")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @JsonProperty("country")
    private String country;

    @JsonProperty("preferredLanguage")
    private String preferredLanguage;

    @JsonProperty("preferredCurrency")
    private String preferredCurrency;

    @JsonProperty("registrationSource")
    private String registrationSource;

    @JsonProperty("referralCode")
    private String referralCode;

    @JsonProperty("registeredAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant registeredAt;

    @JsonProperty("userType")
    private String userType;

    @JsonProperty("accountTier")
    private String accountTier;

    @JsonProperty("marketingOptIn")
    private Boolean marketingOptIn;

    @JsonProperty("notificationsEnabled")
    private Boolean notificationsEnabled;

    @JsonProperty("metadata")
    private Map<String, String> metadata;
}