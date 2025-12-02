package com.waqiti.payment.client;

import java.util.UUID;

/**
 * Client for secure image storage service
 */
public interface ImageStorageClient {
    
    /**
     * Uploads check image to secure storage
     * @param userId User ID
     * @param imageData Image byte array
     * @param imageType "front" or "back"
     * @return Secure URL for accessing the image
     */
    String uploadCheckImage(UUID userId, byte[] imageData, String imageType);
    
    /**
     * Retrieves check image from storage
     * @param imageUrl Secure image URL
     * @return Image byte array
     */
    byte[] retrieveCheckImage(String imageUrl);
    
    /**
     * Deletes check image from storage
     * @param imageUrl Secure image URL
     */
    void deleteCheckImage(String imageUrl);
}