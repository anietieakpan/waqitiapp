package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface SecurityTrainingModuleRepository extends JpaRepository<SecurityTrainingModule, UUID> {
    List<SecurityTrainingModule> findByIsMandatoryTrueAndTargetRolesContaining(String role);
    List<SecurityTrainingModule> findByIsActiveTrue();
}






