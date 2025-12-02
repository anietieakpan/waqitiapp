package com.waqiti.payment.dto.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadRequest {
    private String userId;
    private String imageData;
    private String imageType;
    private String objectKey;
    private String bucket;
    private String region;
    private String provider;
    private String contentType;
    private String imageHash;
    private boolean encrypted;
    private boolean compressed;
    private ImageMetadata metadata;
}