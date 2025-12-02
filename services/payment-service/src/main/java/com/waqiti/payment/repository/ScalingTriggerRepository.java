package com.waqiti.payment.repository;

import com.waqiti.payment.domain.ScalingTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for ScalingTrigger entities
 */
@Repository
public interface ScalingTriggerRepository extends JpaRepository<ScalingTrigger, UUID> {
}
