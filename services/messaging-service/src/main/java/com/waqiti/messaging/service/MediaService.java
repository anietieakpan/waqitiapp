package com.waqiti.messaging.service;

import com.waqiti.messaging.domain.AttachmentType;
import com.waqiti.messaging.domain.MessageAttachment;
import com.waqiti.messaging.dto.AttachmentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class MediaService {
    
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    private final S3Client s3Client;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Value("${messaging.storage.media.bucket}")
    private String mediaBucket;
    
    @Value("${messaging.storage.media.expiry-days}")
    private int expiryDays;
    
    public MessageAttachment processAttachment(AttachmentRequest request, String senderId) {
        try {
            MultipartFile file = request.getFile();
            
            // Generate unique file name
            String fileId = UUID.randomUUID().toString();
            String fileName = fileId + "_" + file.getOriginalFilename();
            
            // Determine attachment type
            AttachmentType type = determineAttachmentType(file.getContentType());
            
            // Generate encryption key
            SecretKey encryptionKey = generateEncryptionKey();
            
            // Encrypt file content
            byte[] encryptedContent = encryptFile(file.getBytes(), encryptionKey);
            
            // Upload encrypted file to S3
            String encryptedUrl = uploadToS3(fileName, encryptedContent, file.getContentType());
            
            // Create attachment entity
            MessageAttachment attachment = MessageAttachment.builder()
                .type(type)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .encryptedUrl(encryptedUrl)
                .encryptedKey(Base64.getEncoder().encodeToString(encryptionKey.getEncoded()))
                .checksum(generateChecksum(file.getBytes()))
                .isEncrypted(true)
                .build();
            
            // Process based on type
            if (type == AttachmentType.IMAGE) {
                processImageAttachment(file, attachment, encryptionKey);
            } else if (type == AttachmentType.VIDEO) {
                processVideoAttachment(file, attachment);
            } else if (type == AttachmentType.LOCATION && request.getLocation() != null) {
                attachment.setLatitude(request.getLocation().getLatitude());
                attachment.setLongitude(request.getLocation().getLongitude());
                attachment.setAddress(request.getLocation().getAddress());
            }
            
            // Set expiry
            if (request.getIsEphemeral() != null && request.getIsEphemeral()) {
                attachment.setExpiresAt(LocalDateTime.now().plusDays(1));
            } else {
                attachment.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));
            }
            
            return attachment;
            
        } catch (Exception e) {
            log.error("Failed to process attachment", e);
            throw new RuntimeException("Failed to process attachment", e);
        }
    }
    
    private AttachmentType determineAttachmentType(String contentType) {
        if (contentType == null) {
            return AttachmentType.DOCUMENT;
        }
        
        if (contentType.startsWith("image/")) {
            return AttachmentType.IMAGE;
        } else if (contentType.startsWith("video/")) {
            return AttachmentType.VIDEO;
        } else if (contentType.startsWith("audio/")) {
            return AttachmentType.AUDIO;
        } else {
            return AttachmentType.DOCUMENT;
        }
    }
    
    private SecretKey generateEncryptionKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }
    
    private byte[] encryptFile(byte[] content, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
        
        byte[] cipherText = cipher.doFinal(content);
        
        // Combine IV and cipher text
        byte[] encrypted = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, encrypted, 0, iv.length);
        System.arraycopy(cipherText, 0, encrypted, iv.length, cipherText.length);
        
        return encrypted;
    }
    
    public byte[] decryptFile(byte[] encryptedContent, String encryptedKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(encryptedKey);
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        
        // Extract IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(encryptedContent, 0, iv, 0, iv.length);
        
        // Extract cipher text
        byte[] cipherText = new byte[encryptedContent.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedContent, GCM_IV_LENGTH, cipherText, 0, cipherText.length);
        
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
        
        return cipher.doFinal(cipherText);
    }
    
    private String uploadToS3(String fileName, byte[] content, String contentType) {
        String key = "messages/" + fileName;
        
        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(mediaBucket)
            .key(key)
            .contentType(contentType)
            .build();
        
        s3Client.putObject(putRequest, RequestBody.fromBytes(content));
        
        return String.format("s3://%s/%s", mediaBucket, key);
    }
    
    private void processImageAttachment(MultipartFile file, MessageAttachment attachment, SecretKey encryptionKey) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
        
        if (image != null) {
            attachment.setWidth(image.getWidth());
            attachment.setHeight(image.getHeight());
            
            // Generate thumbnail
            ByteArrayOutputStream thumbnailStream = new ByteArrayOutputStream();
            Thumbnails.of(image)
                .size(200, 200)
                .outputFormat("jpg")
                .toOutputStream(thumbnailStream);
            
            byte[] thumbnailBytes = thumbnailStream.toByteArray();
            byte[] encryptedThumbnail = encryptFile(thumbnailBytes, encryptionKey);
            
            String thumbnailUrl = uploadToS3("thumb_" + UUID.randomUUID() + ".jpg", 
                                            encryptedThumbnail, "image/jpeg");
            
            attachment.setThumbnailUrl(thumbnailUrl);
            attachment.setThumbnailKey(attachment.getEncryptedKey()); // Same key for thumbnail
        }
    }
    
    private void processVideoAttachment(MultipartFile file, MessageAttachment attachment) {
        // Extract video metadata (duration, dimensions)
        // This would require a video processing library
        // For now, just log
        log.info("Processing video attachment: {}", file.getOriginalFilename());
    }
    
    private String generateChecksum(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate checksum", e);
            return null;
        }
    }
}