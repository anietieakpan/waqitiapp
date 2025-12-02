package com.waqiti.user.repository;

import com.waqiti.user.domain.FamilyGuardianship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FamilyGuardianshipRepository extends JpaRepository<FamilyGuardianship, UUID> {

    /**
     * Find all guardianships for a dependent user
     */
    List<FamilyGuardianship> findByDependentUserId(UUID dependentUserId);

    /**
     * Find guardianships by dependent user and status
     */
    List<FamilyGuardianship> findByDependentUserIdAndStatus(UUID dependentUserId, FamilyGuardianship.GuardianshipStatus status);

    /**
     * Find all guardianships where user is a guardian
     */
    List<FamilyGuardianship> findByGuardianUserId(UUID guardianUserId);

    /**
     * Find guardianships where user is guardian with specific status
     */
    List<FamilyGuardianship> findByGuardianUserIdAndStatus(UUID guardianUserId, FamilyGuardianship.GuardianshipStatus status);

    /**
     * Find specific guardianship relationship
     */
    Optional<FamilyGuardianship> findByGuardianUserIdAndDependentUserId(UUID guardianUserId, UUID dependentUserId);

    /**
     * Find active guardianships for a dependent
     */
    @Query("SELECT g FROM FamilyGuardianship g WHERE g.dependentUserId = ?1 AND g.status = 'ACTIVE' AND " +
           "(g.expiresAt IS NULL OR g.expiresAt > CURRENT_TIMESTAMP)")
    List<FamilyGuardianship> findActiveGuardianshipsForDependent(UUID dependentUserId);

    /**
     * Find active guardianships where user is a guardian
     */
    @Query("SELECT g FROM FamilyGuardianship g WHERE g.guardianUserId = ?1 AND g.status = 'ACTIVE' AND " +
           "(g.expiresAt IS NULL OR g.expiresAt > CURRENT_TIMESTAMP)")
    List<FamilyGuardianship> findActiveGuardianshipsForGuardian(UUID guardianUserId);

    /**
     * Find guardianships with specific permission
     */
    @Query("SELECT g FROM FamilyGuardianship g JOIN g.permissions p WHERE g.dependentUserId = ?1 AND " +
           "g.status = 'ACTIVE' AND p = ?2")
    List<FamilyGuardianship> findByDependentAndPermission(UUID dependentUserId, String permission);

    /**
     * Find primary guardians for a dependent
     */
    List<FamilyGuardianship> findByDependentUserIdAndGuardianTypeAndStatus(
        UUID dependentUserId, FamilyGuardianship.GuardianType guardianType, FamilyGuardianship.GuardianshipStatus status);

    /**
     * Check if user has active guardianship over dependent
     */
    @Query("SELECT COUNT(g) > 0 FROM FamilyGuardianship g WHERE g.guardianUserId = ?1 AND " +
           "g.dependentUserId = ?2 AND g.status = 'ACTIVE' AND " +
           "(g.expiresAt IS NULL OR g.expiresAt > CURRENT_TIMESTAMP)")
    boolean hasActiveGuardianship(UUID guardianUserId, UUID dependentUserId);

    /**
     * Find guardianships expiring soon
     */
    @Query("SELECT g FROM FamilyGuardianship g WHERE g.status = 'ACTIVE' AND " +
           "g.expiresAt IS NOT NULL AND g.expiresAt BETWEEN CURRENT_TIMESTAMP AND ?1")
    List<FamilyGuardianship> findExpiringBefore(LocalDateTime dateTime);

    /**
     * Find inactive guardianships (no recent activity)
     */
    @Query("SELECT g FROM FamilyGuardianship g WHERE g.status = 'ACTIVE' AND " +
           "(g.lastActivityAt IS NULL OR g.lastActivityAt < ?1)")
    List<FamilyGuardianship> findInactiveGuardianships(LocalDateTime cutoffDate);

    /**
     * Count active guardians for a dependent
     */
    @Query("SELECT COUNT(g) FROM FamilyGuardianship g WHERE g.dependentUserId = ?1 AND " +
           "g.status = 'ACTIVE' AND (g.expiresAt IS NULL OR g.expiresAt > CURRENT_TIMESTAMP)")
    long countActiveGuardiansForDependent(UUID dependentUserId);

    /**
     * Find guardianships with financial oversight permission
     */
    @Query("SELECT g FROM FamilyGuardianship g WHERE g.dependentUserId = ?1 AND " +
           "g.status = 'ACTIVE' AND g.financialOversight = true AND g.canApproveTransactions = true")
    List<FamilyGuardianship> findFinancialGuardians(UUID dependentUserId);

    /**
     * Find guardianships that can approve amount
     */
    @Query("SELECT g FROM FamilyGuardianship g WHERE g.dependentUserId = ?1 AND " +
           "g.status = 'ACTIVE' AND g.canApproveTransactions = true AND " +
           "(g.maxApprovalAmount IS NULL OR g.maxApprovalAmount >= ?2)")
    List<FamilyGuardianship> findGuardiansWhoCanApproveAmount(UUID dependentUserId, java.math.BigDecimal amount);

    /**
     * Delete all guardianships for a user (when account is deleted)
     */
    void deleteByDependentUserId(UUID dependentUserId);
    void deleteByGuardianUserId(UUID guardianUserId);
}