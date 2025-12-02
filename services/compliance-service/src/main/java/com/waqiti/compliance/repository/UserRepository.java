package com.waqiti.compliance.repository;

import com.waqiti.compliance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User Repository for Compliance Service
 * Provides user data access for sanctions screening and compliance operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u.id FROM User u WHERE u.status = 'ACTIVE' AND u.accountLocked = false")
    List<UUID> findAllActiveUserIds();

    @Query("SELECT u.id FROM User u WHERE u.createdAt >= :since")
    List<UUID> findNewUsersSince(@Param("since") LocalDateTime since);

    @Query("SELECT u FROM User u WHERE u.id IN :userIds")
    List<User> findByIdIn(@Param("userIds") List<UUID> userIds);

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = 'ACTIVE'")
    long countActiveUsers();

    @Query("SELECT u FROM User u WHERE u.kycStatus IN :statuses")
    List<User> findByKycStatusIn(@Param("statuses") List<String> statuses);

    @Query("SELECT u FROM User u WHERE u.enhancedMonitoring = true")
    List<User> findUsersUnderEnhancedMonitoring();
}