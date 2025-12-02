package com.waqiti.common.security.awareness.repository;

import com.waqiti.common.security.awareness.domain.EmployeeTrainingRecord;
import com.waqiti.common.security.awareness.model.*;
import com.waqiti.common.security.awareness.dto.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface EmployeeTrainingRecordRepositoryExtensions extends JpaRepository<EmployeeTrainingRecord, UUID> {

    List<EmployeeTrainingRecord> findByEmployeeId(UUID employeeId);

    List<EmployeeTrainingRecord> findByModuleId(UUID moduleId);
}

