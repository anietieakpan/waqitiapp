package com.waqiti.user.repository;

import com.waqiti.user.model.KycStatusRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycStatusRepository extends JpaRepository<KycStatusRecord, String> {
    
    Optional<KycStatusRecord> findByUserId(String userId);
}