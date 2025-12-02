package com.waqiti.common.security.awareness.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Employee Entity for Security Awareness Training
 *
 * Represents employees who undergo security awareness training,
 * phishing simulations, and quarterly assessments.
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 */
@Entity
@Table(name = "employees", indexes = {
        @Index(name = "idx_employee_email", columnList = "email", unique = true),
        @Index(name = "idx_employee_department", columnList = "department"),
        @Index(name = "idx_employee_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"securityProfile"})
@EqualsAndHashCode(of = "id")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "employee_id")
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "job_title", length = 150)
    private String jobTitle;

    @Column(name = "manager_email", length = 255)
    private String managerEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private EmployeeStatus status = EmployeeStatus.ACTIVE;

    @Column(name = "hire_date")
    private LocalDateTime hireDate;

    @Column(name = "last_training_date")
    private LocalDateTime lastTrainingDate;

    @Column(name = "training_due_date")
    private LocalDateTime trainingDueDate;

    @OneToOne(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private EmployeeSecurityProfile securityProfile;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public enum EmployeeStatus {
        ACTIVE,
        INACTIVE,
        ON_LEAVE,
        TERMINATED
    }

    /**
     * Check if employee is due for training
     */
    public boolean isTrainingDue() {
        if (trainingDueDate == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(trainingDueDate);
    }

    /**
     * Update training completion
     */
    public void completeTraining() {
        this.lastTrainingDate = LocalDateTime.now();
        this.trainingDueDate = LocalDateTime.now().plusMonths(6); // 6 months validity
    }
}