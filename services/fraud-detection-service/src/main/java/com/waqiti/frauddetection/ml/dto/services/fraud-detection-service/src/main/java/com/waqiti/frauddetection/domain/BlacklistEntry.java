package com.waqiti.frauddetection.domain;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blacklist_entries")
@Data
public class BlacklistEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String entryType;
    private String value;
    private String reason;
    private LocalDateTime blacklistedAt;
    private LocalDateTime expiresAt;
}
