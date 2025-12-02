package com.waqiti.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.kyc-bucket}")
    private String kycBucket;

    @Value("${aws.s3.region}")
    private String region;

    public String storeDocument(String userId, String documentType, MultipartFile file) {
        try {
            String key = generateDocumentKey(userId, documentType, file.getOriginalFilename());
            
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(kycBucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .metadata(java.util.Map.of(
                            "userId", userId,
                            "documentType", documentType,
                            "uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    ))
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            
            log.info("Document stored successfully: {}", key);
            return key;
            
        } catch (IOException e) {
            log.error("Error storing document for user: {}", userId, e);
            throw new RuntimeException("Failed to store document", e);
        }
    }

    public String storeDocument(String userId, String documentType, String sourceUrl, String contentType) {
        try {
            // Download from URL
            URL url = new URL(sourceUrl);
            Path tempFile = Files.createTempFile("kyc-doc", ".tmp");
            
            try {
                Files.copy(url.openStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
                
                String key = generateDocumentKey(userId, documentType, "report.pdf");
                
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(kycBucket)
                        .key(key)
                        .contentType(contentType)
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .metadata(java.util.Map.of(
                                "userId", userId,
                                "documentType", documentType,
                                "sourceUrl", sourceUrl,
                                "uploadedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        ))
                        .build();

                s3Client.putObject(putRequest, RequestBody.fromFile(tempFile));
                
                log.info("Document stored from URL successfully: {}", key);
                return key;
                
            } finally {
                Files.deleteIfExists(tempFile);
            }
            
        } catch (Exception e) {
            log.error("Error storing document from URL for user: {}", userId, e);
            throw new RuntimeException("Failed to store document from URL", e);
        }
    }

    public String getDocumentUrl(String documentKey) {
        return getDocumentUrl(documentKey, 3600); // 1 hour expiry
    }

    public String getDocumentUrl(String documentKey, int expirySeconds) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(kycBucket)
                    .key(documentKey)
                    .build();

            // Generate presigned URL
            software.amazon.awssdk.services.s3.presigner.S3Presigner presigner = 
                    software.amazon.awssdk.services.s3.presigner.S3Presigner.create();
            
            software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest = 
                    software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                            .signatureDuration(java.time.Duration.ofSeconds(expirySeconds))
                            .getObjectRequest(getRequest)
                            .build();

            software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest presignedRequest = 
                    presigner.presignGetObject(presignRequest);
            
            return presignedRequest.url().toString();
            
        } catch (Exception e) {
            log.error("Error generating presigned URL for document: {}", documentKey, e);
            throw new RuntimeException("Failed to generate document URL", e);
        }
    }

    public void deleteDocument(String documentKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(kycBucket)
                    .key(documentKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Document deleted successfully: {}", documentKey);
            
        } catch (Exception e) {
            log.error("Error deleting document: {}", documentKey, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    public boolean documentExists(String documentKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(kycBucket)
                    .key(documentKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking document existence: {}", documentKey, e);
            return false;
        }
    }

    public void moveToArchive(String documentKey) {
        try {
            String archiveKey = "archive/" + documentKey;
            
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(kycBucket)
                    .sourceKey(documentKey)
                    .destinationBucket(kycBucket)
                    .destinationKey(archiveKey)
                    .build();

            s3Client.copyObject(copyRequest);
            
            // Delete original after successful copy
            deleteDocument(documentKey);
            
            log.info("Document moved to archive: {} -> {}", documentKey, archiveKey);
            
        } catch (Exception e) {
            log.error("Error moving document to archive: {}", documentKey, e);
            throw new RuntimeException("Failed to archive document", e);
        }
    }

    private String generateDocumentKey(String userId, String documentType, String originalFilename) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);
        
        return String.format("kyc-documents/%s/%s/%s-%s%s", 
                userId, documentType, timestamp, uuid, extension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}