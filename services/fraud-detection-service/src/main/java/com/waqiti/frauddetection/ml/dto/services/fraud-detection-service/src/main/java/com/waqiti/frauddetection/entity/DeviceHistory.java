package com.waqiti.frauddetection.entity;
import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "device_history")
@Data
public class DeviceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String deviceFingerprint;
    private UUID userId;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
}
