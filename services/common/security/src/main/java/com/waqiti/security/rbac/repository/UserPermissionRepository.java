package com.waqiti.security.rbac.repository;

import com.waqiti.security.rbac.domain.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for UserPermission entities
 */
@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {

    List<UserPermission> findByUserId(UUID userId);

    @Query("SELECT up FROM UserPermission up WHERE up.userId = :userId AND up.active = true")
    List<UserPermission> findActivePermissionsByUserId(UUID userId);

    @Query("SELECT up FROM UserPermission up WHERE up.userId = :userId AND up.permissionId = :permissionId AND up.active = true")
    List<UserPermission> findByUserIdAndPermissionId(UUID userId, UUID permissionId);

    boolean existsByUserIdAndPermissionId(UUID userId, UUID permissionId);
}
