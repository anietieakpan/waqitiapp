package com.waqiti.familyaccount.repository;

import com.waqiti.familyaccount.domain.FamilyAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Family Account Repository
 *
 * Data access layer for FamilyAccount entities
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Repository
public interface FamilyAccountRepository extends JpaRepository<FamilyAccount, Long> {

    /**
     * Find family account by family ID
     */
    Optional<FamilyAccount> findByFamilyId(String familyId);

    /**
     * Find family account by primary parent user ID
     */
    Optional<FamilyAccount> findByPrimaryParentUserId(String primaryParentUserId);

    /**
     * Check if family account exists for primary parent
     */
    boolean existsByPrimaryParentUserId(String primaryParentUserId);

    /**
     * Find family accounts where user is primary or secondary parent
     */
    @Query("SELECT fa FROM FamilyAccount fa WHERE fa.primaryParentUserId = :userId OR fa.secondaryParentUserId = :userId")
    List<FamilyAccount> findByParentUserId(@Param("userId") String userId);

    /**
     * Find family accounts by allowance day of month
     */
    List<FamilyAccount> findByAllowanceDayOfMonth(Integer dayOfMonth);

    /**
     * Find family accounts created between dates
     */
    List<FamilyAccount> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find family accounts with auto savings enabled
     */
    List<FamilyAccount> findByAutoSavingsEnabled(Boolean enabled);

    /**
     * Count family accounts by primary parent
     */
    long countByPrimaryParentUserId(String primaryParentUserId);
}
