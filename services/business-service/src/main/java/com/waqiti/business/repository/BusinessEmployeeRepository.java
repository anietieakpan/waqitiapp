package com.waqiti.business.repository;

import com.waqiti.business.domain.BusinessEmployee;
import com.waqiti.business.domain.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessEmployeeRepository extends JpaRepository<BusinessEmployee, UUID> {
    
    Page<BusinessEmployee> findByAccountId(UUID accountId, Pageable pageable);
    
    List<BusinessEmployee> findByAccountIdAndStatus(UUID accountId, EmployeeStatus status);
    
    Optional<BusinessEmployee> findByIdAndAccountId(UUID id, UUID accountId);
    
    Optional<BusinessEmployee> findByAccountIdAndEmail(UUID accountId, String email);
    
    boolean existsByAccountIdAndEmail(UUID accountId, String email);
    
    Optional<BusinessEmployee> findByEmployeeNumber(String employeeNumber);
    
    boolean existsByEmployeeNumber(String employeeNumber);
    
    long countByAccountId(UUID accountId);
    
    long countByAccountIdAndStatus(UUID accountId, EmployeeStatus status);
    
    @Query("SELECT e FROM BusinessEmployee e WHERE e.accountId = :accountId AND " +
           "(:department IS NULL OR e.department = :department) AND " +
           "(:role IS NULL OR e.role = :role) AND " +
           "(:status IS NULL OR e.status = :status)")
    Page<BusinessEmployee> findByFilters(@Param("accountId") UUID accountId,
                                        @Param("department") String department,
                                        @Param("role") String role,
                                        @Param("status") String status,
                                        Pageable pageable);
    
    @Query("SELECT e FROM BusinessEmployee e WHERE e.managerId = :managerId")
    List<BusinessEmployee> findByManagerId(@Param("managerId") UUID managerId);
    
    @Query("SELECT e.department, COUNT(e) FROM BusinessEmployee e " +
           "WHERE e.accountId = :accountId AND e.status = 'ACTIVE' GROUP BY e.department")
    List<Object[]> getEmployeeCountByDepartment(@Param("accountId") UUID accountId);
    
    @Query("SELECT e.role, COUNT(e) FROM BusinessEmployee e " +
           "WHERE e.accountId = :accountId AND e.status = 'ACTIVE' GROUP BY e.role")
    List<Object[]> getEmployeeCountByRole(@Param("accountId") UUID accountId);
    
    @Query("SELECT e FROM BusinessEmployee e WHERE e.accountId = :accountId AND " +
           "e.hireDate BETWEEN :startDate AND :endDate")
    List<BusinessEmployee> findByHireDateBetween(@Param("accountId") UUID accountId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);
    
    @Query("SELECT e FROM BusinessEmployee e WHERE e.spendingLimit > :amount")
    List<BusinessEmployee> findEmployeesWithSpendingLimitAbove(@Param("amount") BigDecimal amount);
    
    @Query("SELECT AVG(e.salary) FROM BusinessEmployee e WHERE e.accountId = :accountId AND e.salary IS NOT NULL")
    BigDecimal getAverageSalaryByAccount(@Param("accountId") UUID accountId);
    
    @Query("SELECT e FROM BusinessEmployee e WHERE e.status = 'ACTIVE' AND " +
           "e.terminationDate IS NULL AND e.accountId = :accountId")
    List<BusinessEmployee> findActiveEmployees(@Param("accountId") UUID accountId);
}