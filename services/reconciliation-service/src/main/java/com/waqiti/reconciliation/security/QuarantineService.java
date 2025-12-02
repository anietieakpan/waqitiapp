package com.waqiti.reconciliation.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for quarantining suspicious files
 */
@Service
@Slf4j
public class QuarantineService {
    
    @Value("${file.quarantine.path:/tmp/quarantine}")
    private String quarantinePath;
    
    /**
     * Quarantine a suspicious file
     */
    public void quarantineFile(Path filePath, VirusScanResult scanResult) {
        try {
            Path quarantineDir = Path.of(quarantinePath);
            Files.createDirectories(quarantineDir);
            
            String quarantineFilename = String.format("QUARANTINE_%s_%s_%s",
                    System.currentTimeMillis(),
                    UUID.randomUUID().toString().substring(0, 8),
                    filePath.getFileName().toString());
            
            Path quarantineFile = quarantineDir.resolve(quarantineFilename);
            Files.copy(filePath, quarantineFile, StandardCopyOption.REPLACE_EXISTING);
            
            // Write quarantine metadata
            Path metadataFile = quarantineDir.resolve(quarantineFilename + ".metadata");
            String metadata = String.format("Timestamp: %s\nThreats: %s\nOriginal: %s\nScanEngines: %s",
                    LocalDateTime.now(), 
                    String.join(", ", scanResult.getThreats()),
                    filePath.getFileName().toString(),
                    String.join(", ", scanResult.getScanEngines()));
            Files.writeString(metadataFile, metadata);
            
            log.warn("File quarantined: {} -> {}, Threats: {}", 
                    filePath.getFileName(), quarantineFilename, scanResult.getThreats());
            
        } catch (IOException e) {
            log.error("Failed to quarantine file: {}", filePath.getFileName(), e);
        }
    }
}