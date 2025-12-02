package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDataExportResponse {
    @NotNull
    private UUID exportId;
    @NotNull
    private UUID userId;
    @NotNull
    private String exportFormat;
    @NotNull
    private String status;
    private String dateRange;
    private int recordCount;
    private String fileName;
    private String fileUrl;
    private String downloadToken;
    private LocalDateTime exportedAt;
    private LocalDateTime expiresAt;
}
