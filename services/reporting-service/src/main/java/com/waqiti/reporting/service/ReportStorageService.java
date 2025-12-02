package com.waqiti.reporting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportStorageService {
    
    @Value("${report.storage.path:/var/waqiti/reports}")
    private String storagePath;
    
    public String saveReport(UUID reportId, byte[] content, String format) {
        try {
            Path directory = Paths.get(storagePath);
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            
            String filename = String.format("%s.%s", reportId.toString(), format.toLowerCase());
            Path filePath = directory.resolve(filename);
            
            Files.write(filePath, content);
            log.info("Report saved: {}", filePath);
            
            return filePath.toString();
            
        } catch (IOException e) {
            log.error("Failed to save report", e);
            throw new RuntimeException("Failed to save report", e);
        }
    }
    
    public byte[] retrieveReport(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new RuntimeException("Report file not found: " + filePath);
            }
            
            return Files.readAllBytes(path);
            
        } catch (IOException e) {
            log.error("Failed to retrieve report", e);
            throw new RuntimeException("Failed to retrieve report", e);
        }
    }
    
    public void deleteReport(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("Report deleted: {}", filePath);
            }
        } catch (IOException e) {
            log.error("Failed to delete report", e);
        }
    }
    
    public boolean reportExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
}