package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.AccountLimits;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Account Limits Repository
 *
 * Provides data access for account limits.
 */
@Repository
public interface AccountLimitsRepository extends JpaRepository<AccountLimits, String> {

    /**
     * Find account limits by user ID
     */
    Optional<AccountLimits> findByUserId(String userId);

    /**
     * Check if account limits exist for a user
     */
    boolean existsByUserId(String userId);

    /**
     * Delete account limits for a user
     */
    void deleteByUserId(String userId);
}
