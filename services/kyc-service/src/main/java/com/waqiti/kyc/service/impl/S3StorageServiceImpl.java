package com.waqiti.kyc.service.impl;

import com.waqiti.kyc.config.KYCProperties;
import com.waqiti.kyc.exception.StorageException;
import com.waqiti.kyc.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * S3-based implementation of StorageService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class S3StorageServiceImpl implements StorageService {
    
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final KYCProperties kycProperties;
    
    private String bucketName;
    
    @PostConstruct
    public void init() {
        this.bucketName = kycProperties.getStorage().getBucketName();
        
        // Create bucket if it doesn't exist
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
            log.info("Using existing S3 bucket: {}", bucketName);
        } catch (NoSuchBucketException e) {
            log.info("Creating S3 bucket: {}", bucketName);
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build());
        }
    }
    
    @Override
    public String uploadFile(String path, byte[] data, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build();
            
            s3Client.putObject(request, RequestBody.fromBytes(data));
            
            log.info("Uploaded file to S3: {}", path);
            return buildS3Url(path);
            
        } catch (Exception e) {
            log.error("Failed to upload file to S3", e);
            throw new StorageException("Failed to upload file: " + e.getMessage());
        }
    }
    
    @Override
    public String uploadFile(String path, InputStream inputStream, long contentLength, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();
            
            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            
            log.info("Uploaded file stream to S3: {}", path);
            return buildS3Url(path);
            
        } catch (Exception e) {
            log.error("Failed to upload file stream to S3", e);
            throw new StorageException("Failed to upload file: " + e.getMessage());
        }
    }
    
    @Override
    public byte[] downloadFile(String path) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            
            try (InputStream stream = s3Client.getObject(request);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = stream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                
                log.info("Downloaded file from S3: {}", path);
                return baos.toByteArray();
            }
            
        } catch (Exception e) {
            log.error("Failed to download file from S3", e);
            throw new StorageException("Failed to download file: " + e.getMessage());
        }
    }
    
    @Override
    public InputStream downloadFileStream(String path) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            
            return s3Client.getObject(request);
            
        } catch (Exception e) {
            log.error("Failed to download file stream from S3", e);
            throw new StorageException("Failed to download file: " + e.getMessage());
        }
    }
    
    @Override
    public void deleteFile(String path) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            
            s3Client.deleteObject(request);
            log.info("Deleted file from S3: {}", path);
            
        } catch (Exception e) {
            log.error("Failed to delete file from S3", e);
            throw new StorageException("Failed to delete file: " + e.getMessage());
        }
    }
    
    @Override
    public boolean fileExists(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            
            s3Client.headObject(request);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Failed to check file existence", e);
            throw new StorageException("Failed to check file existence: " + e.getMessage());
        }
    }
    
    @Override
    public String generatePresignedUrl(String path, int expiryMinutes) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(expiryMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();
            
            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            
            String url = presignedRequest.url().toString();
            log.info("Generated presigned URL for: {}", path);
            
            return url;
            
        } catch (Exception e) {
            log.error("Failed to generate presigned URL", e);
            throw new StorageException("Failed to generate presigned URL: " + e.getMessage());
        }
    }
    
    @Override
    public void copyFile(String sourcePath, String destinationPath) {
        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(sourcePath)
                    .destinationBucket(bucketName)
                    .destinationKey(destinationPath)
                    .build();
            
            s3Client.copyObject(request);
            log.info("Copied file from {} to {}", sourcePath, destinationPath);
            
        } catch (Exception e) {
            log.error("Failed to copy file", e);
            throw new StorageException("Failed to copy file: " + e.getMessage());
        }
    }
    
    @Override
    public void moveFile(String sourcePath, String destinationPath) {
        copyFile(sourcePath, destinationPath);
        deleteFile(sourcePath);
    }
    
    @Override
    public Map<String, String> getFileMetadata(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            
            HeadObjectResponse response = s3Client.headObject(request);
            
            Map<String, String> metadata = new HashMap<>(response.metadata());
            metadata.put("contentType", response.contentType());
            metadata.put("contentLength", String.valueOf(response.contentLength()));
            metadata.put("lastModified", response.lastModified().toString());
            metadata.put("eTag", response.eTag());
            
            return metadata;
            
        } catch (Exception e) {
            log.error("Failed to get file metadata", e);
            throw new StorageException("Failed to get file metadata: " + e.getMessage());
        }
    }
    
    @Override
    public void updateFileMetadata(String path, Map<String, String> metadata) {
        try {
            // S3 requires copying the object to update metadata
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(path)
                    .destinationBucket(bucketName)
                    .destinationKey(path)
                    .metadata(metadata)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .build();
            
            s3Client.copyObject(request);
            log.info("Updated metadata for file: {}", path);
            
        } catch (Exception e) {
            log.error("Failed to update file metadata", e);
            throw new StorageException("Failed to update file metadata: " + e.getMessage());
        }
    }
    
    @Override
    public List<String> listFiles(String prefix) {
        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();
            
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            
            return response.contents().stream()
                    .map(S3Object::key)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to list files", e);
            throw new StorageException("Failed to list files: " + e.getMessage());
        }
    }
    
    @Override
    public long getFileSize(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(path)
                    .build();
            
            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentLength();
            
        } catch (Exception e) {
            log.error("Failed to get file size", e);
            throw new StorageException("Failed to get file size: " + e.getMessage());
        }
    }
    
    @Override
    public void createDirectory(String path) {
        // In S3, directories are virtual and created automatically
        // We can create an empty object with trailing slash to represent a directory
        String directoryKey = path.endsWith("/") ? path : path + "/";
        
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(directoryKey)
                .build();
        
        s3Client.putObject(request, RequestBody.empty());
        log.info("Created directory marker: {}", directoryKey);
    }
    
    @Override
    public void deleteDirectory(String path) {
        try {
            String prefix = path.endsWith("/") ? path : path + "/";
            
            // List all objects with the prefix
            List<String> filesToDelete = listFiles(prefix);
            
            if (!filesToDelete.isEmpty()) {
                // Create delete request for all objects
                List<ObjectIdentifier> objects = filesToDelete.stream()
                        .map(key -> ObjectIdentifier.builder().key(key).build())
                        .collect(Collectors.toList());
                
                DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                        .bucket(bucketName)
                        .delete(Delete.builder()
                                .objects(objects)
                                .build())
                        .build();
                
                s3Client.deleteObjects(deleteRequest);
                log.info("Deleted {} files from directory: {}", filesToDelete.size(), path);
            }
            
        } catch (Exception e) {
            log.error("Failed to delete directory", e);
            throw new StorageException("Failed to delete directory: " + e.getMessage());
        }
    }
    
    private String buildS3Url(String path) {
        return String.format("s3://%s/%s", bucketName, path);
    }
}