package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.FraudModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FraudModelRepository extends JpaRepository<FraudModel, String> {
    
    @Query("SELECT m FROM FraudModel m WHERE m.isActive = true")
    List<FraudModel> findActiveModels();
    
    Optional<FraudModel> findByModelNameAndVersion(String modelName, String version);
    
    @Query("SELECT m FROM FraudModel m WHERE m.modelType = ?1 AND m.isActive = true")
    List<FraudModel> findByModelTypeAndActive(String modelType);
}