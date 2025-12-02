package com.waqiti.atm.repository;

import com.waqiti.atm.domain.CheckImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CheckImageRepository extends JpaRepository<CheckImage, UUID> {

    List<CheckImage> findByDepositId(UUID depositId);
}
