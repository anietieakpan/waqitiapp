package com.waqiti.auth.repository;

import com.waqiti.auth.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enterprise-grade Permission Repository.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByName(String name);

    List<Permission> findByCategory(Permission.PermissionCategory category);

    List<Permission> findByResource(String resource);

    List<Permission> findByResourceAndAction(String resource, String action);

    List<Permission> findByIsSystemPermissionTrue();

    boolean existsByName(String name);
}
