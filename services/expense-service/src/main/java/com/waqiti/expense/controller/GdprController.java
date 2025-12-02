package com.waqiti.expense.controller;

import com.waqiti.expense.service.GdprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * GDPR Compliance Controller
 * Implements data subject rights under GDPR
 */
@RestController
@RequestMapping("/api/v1/gdpr")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "GDPR Compliance", description = "GDPR data subject rights endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class GdprController {

    private final GdprService gdprService;

    @GetMapping("/export")
    @Operation(summary = "Export user data (Right to Data Portability)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> exportUserData() {
        log.info("GDPR: Exporting user data");

        byte[] data = gdprService.exportUserData();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", "user-data.json");

        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }

    @DeleteMapping("/delete-data")
    @Operation(summary = "Delete all user data (Right to Erasure)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteUserData() {
        log.info("GDPR: Deleting user data");

        gdprService.deleteUserData();

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/data-summary")
    @Operation(summary = "Get summary of stored data (Right to Access)")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Object> getDataSummary() {
        log.info("GDPR: Getting data summary");

        Object summary = gdprService.getDataSummary();

        return ResponseEntity.ok(summary);
    }
}
