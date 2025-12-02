package com.waqiti.business.domain;

import com.waqiti.common.encryption.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "business_employees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class BusinessEmployee {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "employee_number", unique = true, length = 50)
    private String employeeNumber;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "spending_limit", precision = 19, scale = 2)
    private BigDecimal spendingLimit;

    @Column(name = "monthly_spending_limit", precision = 19, scale = 2)
    private BigDecimal monthlySpendingLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @ElementCollection
    @CollectionTable(name = "business_employee_permissions", 
                    joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "permission")
    private List<String> permissions;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "address", length = 500)
    private String address;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", length = 500)
    private String taxId; // PCI DSS: Encrypted SSN/Tax ID

    @Column(name = "salary", precision = 19, scale = 2)
    private BigDecimal salary;

    @Column(name = "employee_type", length = 50)
    @Builder.Default
    private String employeeType = "FULL_TIME";

    @Column(name = "access_level", length = 50)
    @Builder.Default
    private String accessLevel = "STANDARD";

    @Column(name = "notes", length = 1000)
    private String notes;

    @PrePersist
    private void prePersist() {
        if (employeeNumber == null) {
            employeeNumber = generateEmployeeNumber();
        }
    }

    private String generateEmployeeNumber() {
        return "EMP-" + accountId.toString().substring(0, 8).toUpperCase() + 
               "-" + String.format("%04d", System.currentTimeMillis() % 10000);
    }

    public enum EmployeeStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        TERMINATED,
        ON_LEAVE
    }
}