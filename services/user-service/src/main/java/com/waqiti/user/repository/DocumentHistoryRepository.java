package com.waqiti.user.repository;

import com.waqiti.user.model.DocumentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentHistoryRepository extends JpaRepository<DocumentHistory, String> {
    
    List<DocumentHistory> findByUserId(String userId);
    
    List<DocumentHistory> findByUserIdAndDocumentType(String userId, String documentType);
}