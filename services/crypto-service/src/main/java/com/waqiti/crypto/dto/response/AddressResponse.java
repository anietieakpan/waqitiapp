/**
 * Address Response DTO
 * Response containing address information
 */
package com.waqiti.crypto.dto.response;

import com.waqiti.crypto.entity.AddressType;
import com.waqiti.crypto.entity.CryptoCurrency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {
    
    private UUID addressId;
    private UUID walletId;
    private String address;
    private CryptoCurrency currency;
    private AddressType type;
    private Integer derivationIndex;
    private String derivationPath;
    private BigDecimal balance;
    private Integer transactionCount;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private String qrCode;
}