package com.waqiti.insurance.repository;

import com.waqiti.insurance.entity.Premium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PremiumRepository extends JpaRepository<Premium, UUID> {

    List<Premium> findByPolicyId(UUID policyId);

    List<Premium> findByStatus(Premium.PremiumStatus status);

    @Query("SELECT p FROM Premium p WHERE p.status = 'PENDING' AND p.dueDate < :date")
    List<Premium> findOverduePremiums(@Param("date") LocalDate date);

    @Query("SELECT p FROM Premium p WHERE p.policy.id = :policyId AND p.status = 'PENDING' ORDER BY p.dueDate ASC")
    List<Premium> findPendingPremiumsByPolicy(@Param("policyId") UUID policyId);
}
