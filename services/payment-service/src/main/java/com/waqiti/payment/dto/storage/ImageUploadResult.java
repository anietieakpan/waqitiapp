package com.waqiti.payment.dto.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResult {
    private String imageUrl;
    private String imageId;
    private String objectKey;
    private String imageHash;
    private int imageSize;
    private boolean encrypted;
    private boolean compressed;
    private String cdnUrl;
    private boolean duplicate;
    private Instant uploadTimestamp;
}