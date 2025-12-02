package com.waqiti.auth.repository;

import com.waqiti.auth.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise-grade User Repository with comprehensive query methods.
 *
 * Features:
 * - Optimized queries with indexes
 * - Soft delete support
 * - Security-focused queries
 * - Pagination support
 * - Custom queries for complex operations
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // Basic finders
    Optional<User> findByUsernameAndDeletedFalse(String username);

    Optional<User> findByEmailAndDeletedFalse(String email);

    Optional<User> findByPhoneNumberAndDeletedFalse(String phoneNumber);

    boolean existsByUsernameAndDeletedFalse(String username);

    boolean existsByEmailAndDeletedFalse(String email);

    boolean existsByPhoneNumberAndDeletedFalse(String phoneNumber);

    // Security queries
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.deleted = false")
    Optional<User> findActiveUserByUsername(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deleted = false AND u.emailVerified = true")
    Optional<User> findVerifiedUserByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.accountStatus = 'ACTIVE' AND u.deleted = false")
    List<User> findAllActiveUsers();

    @Query("SELECT u FROM User u WHERE u.accountStatus = 'LOCKED' AND u.deleted = false")
    List<User> findAllLockedUsers();

    @Query("SELECT u FROM User u WHERE u.accountLockedUntil IS NOT NULL AND u.accountLockedUntil < :now AND u.deleted = false")
    List<User> findUsersWithExpiredLocks(@Param("now") LocalDateTime now);

    // Password management
    @Query("SELECT u FROM User u WHERE u.passwordExpiresAt IS NOT NULL AND u.passwordExpiresAt < :now AND u.deleted = false")
    List<User> findUsersWithExpiredPasswords(@Param("now") LocalDateTime now);

    @Query("SELECT u FROM User u WHERE u.passwordExpiresAt IS NOT NULL AND u.passwordExpiresAt < :thresholdDate AND u.deleted = false")
    List<User> findUsersWithPasswordsExpiringSoon(@Param("thresholdDate") LocalDateTime thresholdDate);

    // Account verification
    @Query("SELECT u FROM User u WHERE u.emailVerified = false AND u.deleted = false")
    List<User> findUsersWithUnverifiedEmail();

    @Query("SELECT u FROM User u WHERE u.phoneVerified = false AND u.deleted = false")
    List<User> findUsersWithUnverifiedPhone();

    // Two-factor authentication
    @Query("SELECT u FROM User u WHERE u.twoFactorEnabled = true AND u.deleted = false")
    List<User> findUsersWithTwoFactorEnabled();

    @Query("SELECT u FROM User u WHERE u.twoFactorEnabled = false AND u.deleted = false")
    List<User> findUsersWithoutTwoFactor();

    // Failed login tracking
    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts >= :threshold AND u.deleted = false")
    List<User> findUsersWithMultipleFailedLogins(@Param("threshold") Integer threshold);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.accountLockedUntil = null WHERE u.id = :userId")
    void resetFailedLoginAttempts(@Param("userId") UUID userId);

    // Activity tracking
    @Query("SELECT u FROM User u WHERE u.lastLoginAt < :cutoffDate AND u.deleted = false")
    List<User> findInactiveUsers(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT u FROM User u WHERE u.lastLoginAt BETWEEN :startDate AND :endDate AND u.deleted = false")
    List<User> findUsersActiveInDateRange(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    // Role-based queries
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.deleted = false")
    List<User> findUsersByRole(@Param("roleName") String roleName);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId AND u.deleted = false")
    List<User> findUsersByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.deleted = false")
    Long countUsersByRole(@Param("roleName") String roleName);

    // Pagination queries
    Page<User> findByDeletedFalse(Pageable pageable);

    Page<User> findByAccountStatusAndDeletedFalse(User.AccountStatus status, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.deleted = false AND " +
           "(LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Statistics
    @Query("SELECT COUNT(u) FROM User u WHERE u.deleted = false")
    Long countActiveUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.accountStatus = :status AND u.deleted = false")
    Long countUsersByStatus(@Param("status") User.AccountStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since AND u.deleted = false")
    Long countUsersCreatedSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(u) FROM User u WHERE u.twoFactorEnabled = true AND u.deleted = false")
    Long countUsersWithTwoFactorEnabled();

    // Bulk operations
    @Modifying
    @Query("UPDATE User u SET u.accountStatus = :status WHERE u.id IN :userIds")
    void updateAccountStatus(@Param("userIds") List<UUID> userIds, @Param("status") User.AccountStatus status);

    @Modifying
    @Query("UPDATE User u SET u.deleted = true, u.deletedAt = :deletedAt WHERE u.id = :userId")
    void softDelete(@Param("userId") UUID userId, @Param("deletedAt") LocalDateTime deletedAt);

    @Modifying
    @Query("UPDATE User u SET u.deleted = true, u.deletedAt = :deletedAt WHERE u.id IN :userIds")
    void softDeleteBatch(@Param("userIds") List<UUID> userIds, @Param("deletedAt") LocalDateTime deletedAt);

    // Cleanup operations
    @Query("SELECT u FROM User u WHERE u.deleted = true AND u.deletedAt < :cutoffDate")
    List<User> findUsersForPermanentDeletion(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM User u WHERE u.deleted = true AND u.deletedAt < :cutoffDate")
    void permanentlyDeleteOldUsers(@Param("cutoffDate") LocalDateTime cutoffDate);
}
