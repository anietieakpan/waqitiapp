package com.waqiti.atm.repository;

import com.waqiti.atm.domain.DepositLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DepositLimitRepository extends JpaRepository<DepositLimit, UUID> {
}
