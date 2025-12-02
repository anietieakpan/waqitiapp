package com.waqiti.frauddetection.repository;
import com.waqiti.frauddetection.entity.DeviceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface DeviceHistoryRepository extends JpaRepository<DeviceHistory, Long> {
    Optional<DeviceHistory> findByDeviceFingerprint(String fingerprint);
    List<DeviceHistory> findByUserId(UUID userId);
}
