package com.waqiti.frauddetection.repository;
import com.waqiti.frauddetection.domain.BlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BlacklistRepository extends JpaRepository<BlacklistEntry, Long> {
    boolean existsByEntryTypeAndValue(String entryType, String value);
    List<BlacklistEntry> findByEntryType(String entryType);
}
