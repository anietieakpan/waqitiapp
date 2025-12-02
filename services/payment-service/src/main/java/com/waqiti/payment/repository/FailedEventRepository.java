package com.waqiti.payment.repository;

import com.waqiti.payment.model.FailedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, UUID> {
}
