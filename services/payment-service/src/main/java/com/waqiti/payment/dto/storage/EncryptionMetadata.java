package com.waqiti.payment.dto.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionMetadata {
    private String algorithm;
    private String iv;
    private String key;
}