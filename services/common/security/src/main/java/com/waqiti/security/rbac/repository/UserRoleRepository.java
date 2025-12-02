package com.waqiti.security.rbac.repository;

import com.waqiti.security.rbac.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for UserRole entities
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByRoleId(UUID roleId);

    @Query("SELECT ur FROM UserRole ur WHERE ur.userId = :userId AND ur.active = true")
    List<UserRole> findActiveRolesByUserId(UUID userId);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
}
