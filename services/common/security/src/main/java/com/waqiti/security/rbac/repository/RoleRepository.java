package com.waqiti.security.rbac.repository;

import com.waqiti.security.rbac.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for Role entity with optimized fetch strategies
 * Prevents N+1 queries when loading permissions and role hierarchies
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    
    /**
     * Find role by name - basic info only (no permissions fetching)
     */
    Optional<Role> findByName(String name);
    
    /**
     * Find role with permissions - explicit JOIN FETCH for authorization
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.name = :name")
    Optional<Role> findByNameWithPermissions(@Param("name") String name);
    
    /**
     * Find role with full hierarchy and permissions - complete role info
     */
    @Query("SELECT r FROM Role r " +
           "LEFT JOIN FETCH r.permissions p " +
           "LEFT JOIN FETCH r.parentRoles pr " +
           "LEFT JOIN FETCH r.childRoles cr " +
           "WHERE r.name = :name")
    Optional<Role> findByNameWithFullHierarchy(@Param("name") String name);
    
    /**
     * Find all active roles with permissions - user assignment UI
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions " +
           "WHERE r.active = true ORDER BY r.hierarchyLevel ASC, r.priority ASC")
    List<Role> findAllActiveWithPermissions();
    
    /**
     * Find assignable roles for user management - role assignment
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions " +
           "WHERE r.active = true AND r.assignable = true " +
           "ORDER BY r.priority ASC")
    List<Role> findAssignableRolesWithPermissions();
    
    /**
     * Find roles by type with permissions - system vs custom roles
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions " +
           "WHERE r.type = :type AND r.active = true " +
           "ORDER BY r.hierarchyLevel ASC")
    List<Role> findByTypeWithPermissions(@Param("type") Role.RoleType type);
    
    /**
     * Find root roles (no parent) with hierarchy - top-level roles
     */
    @Query("SELECT r FROM Role r " +
           "LEFT JOIN FETCH r.permissions " +
           "LEFT JOIN FETCH r.childRoles " +
           "WHERE r.parentRoles IS EMPTY AND r.active = true " +
           "ORDER BY r.priority ASC")
    List<Role> findRootRolesWithHierarchy();
    
    /**
     * Find child roles of a parent - role hierarchy navigation
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions " +
           "WHERE :parentRole MEMBER OF r.parentRoles " +
           "ORDER BY r.priority ASC")
    List<Role> findChildRolesWithPermissions(@Param("parentRole") Role parentRole);
    
    /**
     * Find roles by hierarchy level - role management
     */
    @Query("SELECT r FROM Role r WHERE r.hierarchyLevel = :level AND r.active = true " +
           "ORDER BY r.priority ASC")
    List<Role> findByHierarchyLevel(@Param("level") Integer level);
    
    /**
     * Find roles with specific permission - permission audit
     */
    @Query("SELECT r FROM Role r JOIN r.permissions p " +
           "WHERE p.name = :permissionName AND r.active = true")
    List<Role> findRolesWithPermission(@Param("permissionName") String permissionName);
    
    /**
     * Find high-privilege roles - security audit
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions p " +
           "WHERE r.hierarchyLevel <= :maxLevel AND r.active = true " +
           "ORDER BY r.hierarchyLevel ASC")
    List<Role> findHighPrivilegeRolesWithPermissions(@Param("maxLevel") Integer maxLevel);
    
    /**
     * Check if role exists and is active - lightweight validation
     */
    @Query("SELECT COUNT(r) > 0 FROM Role r WHERE r.name = :name AND r.active = true")
    boolean existsByNameAndActive(@Param("name") String name);
    
    /**
     * Find roles assigned to user count - capacity management
     */
    @Query("SELECT r, SIZE(r.userRoles) FROM Role r WHERE r.maxUsers IS NOT NULL")
    List<Object[]> findRolesWithUserCounts();
    
    /**
     * Find roles approaching user limit - capacity alerts
     */
    @Query("SELECT r FROM Role r WHERE r.maxUsers IS NOT NULL " +
           "AND SIZE(r.userRoles) >= (r.maxUsers * 0.8)")
    List<Role> findRolesApproachingUserLimit();
    
    /**
     * Get paginated roles with permissions for admin UI
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions " +
           "WHERE (:active IS NULL OR r.active = :active) " +
           "AND (:type IS NULL OR r.type = :type) " +
           "ORDER BY r.hierarchyLevel ASC, r.priority ASC")
    Page<Role> findAllWithPermissions(@Param("active") Boolean active, 
                                      @Param("type") Role.RoleType type, 
                                      Pageable pageable);
    
    /**
     * Update role active status efficiently
     */
    @Modifying
    @Query("UPDATE Role r SET r.active = :active, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id IN :roleIds")
    int updateActiveStatus(@Param("roleIds") List<UUID> roleIds, @Param("active") Boolean active);
    
    /**
     * Update role priority for ordering
     */
    @Modifying
    @Query("UPDATE Role r SET r.priority = :priority, r.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE r.id = :roleId")
    int updatePriority(@Param("roleId") UUID roleId, @Param("priority") Integer priority);
    
    /**
     * Find roles that can be assigned by user with given roles - delegation
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions " +
           "WHERE r.active = true AND r.assignable = true " +
           "AND r.hierarchyLevel > :userMaxHierarchyLevel " +
           "ORDER BY r.priority ASC")
    List<Role> findAssignableByHierarchyLevel(@Param("userMaxHierarchyLevel") Integer userMaxHierarchyLevel);
    
    /**
     * Get role inheritance chain - permission calculation
     */
    @Query("WITH RECURSIVE role_hierarchy AS (" +
           "  SELECT r.id, r.name, r.hierarchy_level, 0 as depth " +
           "  FROM roles r WHERE r.id = :roleId " +
           "  UNION ALL " +
           "  SELECT pr.id, pr.name, pr.hierarchy_level, rh.depth + 1 " +
           "  FROM roles pr " +
           "  JOIN role_hierarchy_relations rhr ON pr.id = rhr.parent_role_id " +
           "  JOIN role_hierarchy rh ON rhr.child_role_id = rh.id " +
           "  WHERE rh.depth < 10 " +
           ") " +
           "SELECT r FROM Role r WHERE r.id IN (SELECT id FROM role_hierarchy)")
    List<Role> findRoleInheritanceChain(@Param("roleId") UUID roleId);
    
    /**
     * Find effective permissions for role (including inherited) - authorization
     */
    @Query("SELECT DISTINCT p FROM Role r " +
           "JOIN r.permissions p " +
           "WHERE r.id IN (" +
           "  WITH RECURSIVE role_hierarchy AS (" +
           "    SELECT id, 0 as depth FROM roles WHERE id = :roleId " +
           "    UNION ALL " +
           "    SELECT rhr.parent_role_id, rh.depth + 1 " +
           "    FROM role_hierarchy_relations rhr " +
           "    JOIN role_hierarchy rh ON rhr.child_role_id = rh.id " +
           "    WHERE rh.depth < 10" +
           "  ) " +
           "  SELECT id FROM role_hierarchy" +
           ")")
    Set<String> findEffectivePermissions(@Param("roleId") UUID roleId);
    
    /**
     * Find conflicting roles - mutual exclusion check
     */
    @Query("SELECT r1, r2 FROM Role r1, Role r2 " +
           "WHERE r1.id != r2.id " +
           "AND r1.active = true AND r2.active = true " +
           "AND EXISTS (SELECT 1 FROM r1.permissions p1 " +
           "           WHERE p1 IN (SELECT p2 FROM r2.permissions p2 WHERE p2.exclusive = true))")
    List<Object[]> findConflictingRoles();
    
    /**
     * Get role statistics for analytics
     */
    @Query("SELECT r.type, COUNT(r), AVG(SIZE(r.permissions)), AVG(SIZE(r.userRoles)) " +
           "FROM Role r WHERE r.active = true GROUP BY r.type")
    List<Object[]> getRoleStatistics();
    
    /**
     * Find orphaned roles - roles without users or children
     */
    @Query("SELECT r FROM Role r WHERE r.active = true " +
           "AND SIZE(r.userRoles) = 0 " +
           "AND SIZE(r.childRoles) = 0 " +
           "AND r.type = 'CUSTOM'")
    List<Role> findOrphanedRoles();
}