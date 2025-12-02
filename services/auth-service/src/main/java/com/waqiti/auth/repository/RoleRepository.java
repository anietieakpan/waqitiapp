package com.waqiti.auth.repository;

import com.waqiti.auth.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise-grade Role Repository for RBAC management.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByNameAndDeletedFalse(String name);

    List<Role> findByDeletedFalse();

    List<Role> findByRoleTypeAndDeletedFalse(Role.RoleType roleType);

    List<Role> findByIsActiveTrue();

    List<Role> findByIsSystemRoleTrue();

    @Query("SELECT r FROM Role r WHERE r.deleted = false ORDER BY r.priority ASC")
    List<Role> findAllActiveOrderedByPriority();

    @Query("SELECT r FROM Role r JOIN r.permissions p WHERE p.name = :permissionName AND r.deleted = false")
    List<Role> findRolesByPermission(@Param("permissionName") String permissionName);

    boolean existsByNameAndDeletedFalse(String name);
}
