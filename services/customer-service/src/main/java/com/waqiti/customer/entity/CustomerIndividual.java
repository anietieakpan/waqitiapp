package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Individual Entity
 *
 * Represents individual customer profile data including personal information,
 * employment details, and demographic data.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_individual", indexes = {
    @Index(name = "idx_customer_individual_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_individual_name", columnList = "last_name, first_name"),
    @Index(name = "idx_customer_individual_dob", columnList = "date_of_birth")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "id")
public class CustomerIndividual {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_id", unique = true, nullable = false, length = 100)
    private String customerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "nationality", length = 3)
    private String nationality;

    @Column(name = "country_of_residence", length = 3)
    private String countryOfResidence;

    @Column(name = "tax_id_encrypted", length = 255)
    private String taxIdEncrypted;

    @Column(name = "tax_id_hash", length = 128)
    private String taxIdHash;

    @Column(name = "ssn_encrypted", length = 255)
    private String ssnEncrypted;

    @Column(name = "ssn_hash", length = 128)
    private String ssnHash;

    @Column(name = "employment_status", length = 50)
    private String employmentStatus;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "employer_name", length = 255)
    private String employerName;

    @Column(name = "annual_income", precision = 15, scale = 2)
    private BigDecimal annualIncome;

    @Column(name = "income_currency", length = 3)
    @Builder.Default
    private String incomeCurrency = "USD";

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(name = "education_level", length = 50)
    private String educationLevel;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Get full name of the individual
     *
     * @return full name
     */
    public String getFullName() {
        StringBuilder fullName = new StringBuilder(firstName);
        if (middleName != null && !middleName.isEmpty()) {
            fullName.append(" ").append(middleName);
        }
        fullName.append(" ").append(lastName);
        return fullName.toString();
    }

    /**
     * Check if individual is employed
     *
     * @return true if employed
     */
    public boolean isEmployed() {
        return "EMPLOYED".equalsIgnoreCase(employmentStatus) ||
               "FULL_TIME".equalsIgnoreCase(employmentStatus) ||
               "PART_TIME".equalsIgnoreCase(employmentStatus);
    }

    /**
     * Check if individual has tax information
     *
     * @return true if tax ID is present
     */
    public boolean hasTaxId() {
        return taxIdEncrypted != null && !taxIdEncrypted.isEmpty();
    }

    /**
     * Check if individual has SSN
     *
     * @return true if SSN is present
     */
    public boolean hasSsn() {
        return ssnEncrypted != null && !ssnEncrypted.isEmpty();
    }
}
