package com.waqiti.compliance.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Column(name = "country", length = 2)
    private String country;

    @Column(name = "national_id")
    private String nationalId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "kyc_status")
    private String kycStatus;

    @Column(name = "account_locked")
    private boolean accountLocked;

    @Column(name = "enhanced_monitoring")
    private boolean enhancedMonitoring;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_sanctions_screening")
    private LocalDateTime lastSanctionsScreening;
}