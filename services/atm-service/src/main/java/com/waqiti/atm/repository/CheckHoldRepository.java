package com.waqiti.atm.repository;

import com.waqiti.atm.domain.CheckHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CheckHoldRepository extends JpaRepository<CheckHold, UUID> {
}
