package com.waqiti.user.repository;

import com.waqiti.user.model.KycHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycHistoryRepository extends JpaRepository<KycHistory, String> {
    
    List<KycHistory> findByUserIdOrderByTimestampDesc(String userId);
    
    List<KycHistory> findByUserId(String userId);
    
    List<KycHistory> findByUserIdAndStatus(String userId, String status);
}