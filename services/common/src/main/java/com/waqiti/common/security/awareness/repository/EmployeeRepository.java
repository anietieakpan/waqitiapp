package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;

/**
 * Repository for Employee entities
 *
 * @author Waqiti Platform Team
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    /**
     * Find employee by email
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Find all employees by department
     */
    List<Employee> findByDepartment(String department);

    /**
     * Find employees whose training is due
     */
    @Query("SELECT e FROM Employee e WHERE e.status = 'ACTIVE' AND " +
            "(e.trainingDueDate IS NULL OR e.trainingDueDate < :now)")
    List<Employee> findEmployeesWithTrainingDue(@Param("now") LocalDateTime now);

    /**
     * Find active employees
     */
    List<Employee> findByStatus(Employee.EmployeeStatus status);

    /**
     * Check if employee exists by email
     */
    boolean existsByEmail(String email);

    /**
     * Count active employees
     */
    @Query("SELECT COUNT(e) FROM Employee e WHERE e.status = 'ACTIVE'")
    Long countActiveEmployees();

    /**
     * Find employees by department list
     */
    List<Employee> findByDepartmentIn(List<String> departments);

    /**
     * Find all active employees
     */
    @Query("SELECT e FROM Employee e WHERE e.status = 'ACTIVE'")
    List<Employee> findAllActiveEmployees();

    /**
     * Find employees by roles
     */
    @Query("SELECT e FROM Employee e WHERE e.jobTitle IN :roles")
    List<Employee> findByRolesIn(@Param("roles") List<String> roles);
}