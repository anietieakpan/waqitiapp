// File: services/user-service/src/main/java/com/waqiti/user/repository/MfaConfigurationRepository.java
package com.waqiti.user.repository;

import com.waqiti.user.domain.MfaConfiguration;
import com.waqiti.user.domain.MfaMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MfaConfigurationRepository extends JpaRepository<MfaConfiguration, UUID> {

    /**
     * Find MFA configuration for a user and method
     */
    Optional<MfaConfiguration> findByUserIdAndMethod(UUID userId, MfaMethod method);

    /**
     * Find all MFA configurations for a user
     */
    List<MfaConfiguration> findByUserId(UUID userId);

    /**
     * Find enabled MFA configurations for a user
     */
    List<MfaConfiguration> findByUserIdAndEnabledTrue(UUID userId);

    /**
     * Find enabled and verified MFA configuration for a user and method
     */
    Optional<MfaConfiguration> findByUserIdAndMethodAndEnabledTrueAndVerifiedTrue(UUID userId, MfaMethod method);

    /**
     * Check if a user has MFA enabled
     */
    boolean existsByUserIdAndEnabledTrue(UUID userId);

    /**
     * Check if a user has MFA enabled and verified
     */
    boolean existsByUserIdAndEnabledTrueAndVerifiedTrue(UUID userId);

    /**
     * Delete MFA configuration for a user
     */
    void deleteByUserId(UUID userId);

    /**
     * Delete MFA configuration for a user and method
     */
    void deleteByUserIdAndMethod(UUID userId, MfaMethod method);
}