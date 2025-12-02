package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.EmployeeTrainingRecord;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for EmployeeTrainingRecord entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface EmployeeTrainingRecordRepository extends JpaRepository<EmployeeTrainingRecord, UUID> {

    /**
     * Find all training records for an employee
     */
    List<EmployeeTrainingRecord> findByEmployeeIdField(UUID employeeId);

    /**
     * Find all training records by employee ID (alias for compatibility)
     */
    default List<EmployeeTrainingRecord> findByEmployeeId(UUID employeeId) {
        return findByEmployeeIdField(employeeId);
    }

    /**
     * Find training record by employee and module
     */
    Optional<EmployeeTrainingRecord> findByEmployeeIdFieldAndModuleIdField(UUID employeeId, UUID moduleId);

    /**
     * Find completed training records for employee
     */
    @Query("SELECT r FROM EmployeeTrainingRecord r WHERE r.employeeIdField = :employeeId " +
            "AND r.status = 'COMPLETED'")
    List<EmployeeTrainingRecord> findCompletedRecordsByEmployeeId(@Param("employeeId") UUID employeeId);

    /**
     * Count completed trainings for employee
     */
    @Query("SELECT COUNT(r) FROM EmployeeTrainingRecord r WHERE r.employeeIdField = :employeeId " +
            "AND r.status = 'COMPLETED'")
    Long countCompletedByEmployeeId(@Param("employeeId") UUID employeeId);

    /**
     * Find latest training record by employee and module
     */
    @Query("SELECT r FROM EmployeeTrainingRecord r WHERE r.employeeIdField = :employeeId " +
            "AND r.moduleIdField = :moduleId ORDER BY r.createdAt DESC")
    Optional<EmployeeTrainingRecord> findLatestByEmployeeIdAndModuleId(@Param("employeeId") UUID employeeId,
                                                                        @Param("moduleId") UUID moduleId);

    /**
     * Find by employee, module, and status
     */
    Optional<EmployeeTrainingRecord> findByEmployeeIdFieldAndModuleIdFieldAndStatus(UUID employeeId, UUID moduleId, EmployeeTrainingRecord.TrainingStatus status);

    /**
     * Alias for finding by employee, module, and status (compatibility)
     */
    default Optional<EmployeeTrainingRecord> findByEmployeeIdAndModuleIdAndStatus(UUID employeeId, UUID moduleId, com.waqiti.common.security.awareness.model.TrainingStatus status) {
        // Convert model TrainingStatus to domain TrainingStatus
        EmployeeTrainingRecord.TrainingStatus domainStatus = EmployeeTrainingRecord.TrainingStatus.valueOf(status.name());
        return findByEmployeeIdFieldAndModuleIdFieldAndStatus(employeeId, moduleId, domainStatus);
    }

    /**
     * Count all training records by employee
     */
    long countByEmployeeIdField(UUID employeeId);

    /**
     * Count training records by employee and status
     */
    long countByEmployeeIdFieldAndStatus(UUID employeeId, EmployeeTrainingRecord.TrainingStatus status);
}