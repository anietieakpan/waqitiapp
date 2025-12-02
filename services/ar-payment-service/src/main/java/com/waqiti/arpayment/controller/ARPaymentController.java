package com.waqiti.arpayment.controller;

import com.waqiti.arpayment.dto.*;
import com.waqiti.arpayment.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ar-payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AR Payments", description = "Augmented Reality payment operations")
public class ARPaymentController {
    
    private final ARPaymentService arPaymentService;
    private final ARSessionService arSessionService;
    private final ARVisualizationService arVisualizationService;
    private final ARGestureRecognitionService gestureRecognitionService;
    
    @PostMapping("/session/initialize")
    @Operation(summary = "Initialize AR payment session",
              description = "Start a new AR payment session with device capabilities")
    @ApiResponse(responseCode = "201", description = "AR session created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid device capabilities")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARSessionResponse> initializeARSession(
            @Valid @RequestBody ARSessionRequest request) {
        
        log.info("Initializing AR session for user: {}", request.getUserId());
        
        try {
            ARSessionResponse response = arSessionService.initializeSession(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error initializing AR session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARSessionResponse.error("Failed to initialize AR session"));
        }
    }
    
    @PostMapping("/session/{sessionId}/scan")
    @Operation(summary = "Process AR scan",
              description = "Process QR code or AR marker scan in AR session")
    @ApiResponse(responseCode = "200", description = "Scan processed successfully")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARScanResponse> processARScan(
            @PathVariable String sessionId,
            @Valid @RequestBody ARScanRequest request) {
        
        log.info("Processing AR scan for session: {}", sessionId);
        
        try {
            ARScanResponse response = arPaymentService.processARScan(sessionId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing AR scan", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARScanResponse.error("Failed to process AR scan"));
        }
    }
    
    @PostMapping("/session/{sessionId}/gesture")
    @Operation(summary = "Process AR gesture",
              description = "Process hand gesture for AR payment")
    @ApiResponse(responseCode = "200", description = "Gesture processed successfully")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARGestureResponse> processARGesture(
            @PathVariable String sessionId,
            @Valid @RequestBody ARGestureRequest request) {
        
        log.info("Processing AR gesture for session: {}", sessionId);
        
        try {
            ARGestureResponse response = gestureRecognitionService.processGesture(sessionId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing AR gesture", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARGestureResponse.error("Failed to process AR gesture"));
        }
    }
    
    @PostMapping("/session/{sessionId}/spatial-payment")
    @Operation(summary = "Create spatial payment",
              description = "Drop payment in 3D space for AR pickup")
    @ApiResponse(responseCode = "201", description = "Spatial payment created")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARSpatialPaymentResponse> createSpatialPayment(
            @PathVariable String sessionId,
            @Valid @RequestBody ARSpatialPaymentRequest request) {
        
        log.info("Creating spatial payment for session: {}", sessionId);
        
        try {
            ARSpatialPaymentResponse response = arPaymentService.createSpatialPayment(sessionId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Error creating spatial payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARSpatialPaymentResponse.error("Failed to create spatial payment"));
        }
    }
    
    @PostMapping("/session/{sessionId}/object-payment")
    @Operation(summary = "Process object-based payment",
              description = "Pay by pointing at real-world objects")
    @ApiResponse(responseCode = "200", description = "Object payment processed")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARObjectPaymentResponse> processObjectPayment(
            @PathVariable String sessionId,
            @Valid @RequestBody ARObjectPaymentRequest request) {
        
        log.info("Processing object payment for session: {}", sessionId);
        
        try {
            ARObjectPaymentResponse response = arPaymentService.processObjectPayment(sessionId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error processing object payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARObjectPaymentResponse.error("Failed to process object payment"));
        }
    }
    
    @PostMapping("/session/{sessionId}/shopping/add-item")
    @Operation(summary = "Add item to AR shopping cart",
              description = "Add product to virtual shopping cart in AR")
    @ApiResponse(responseCode = "200", description = "Item added to cart")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARShoppingResponse> addToARCart(
            @PathVariable String sessionId,
            @Valid @RequestBody ARShoppingItemRequest request) {
        
        log.info("Adding item to AR cart for session: {}", sessionId);
        
        try {
            ARShoppingResponse response = arPaymentService.addToARCart(sessionId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error adding to AR cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARShoppingResponse.error("Failed to add item to AR cart"));
        }
    }
    
    @PostMapping("/session/{sessionId}/shopping/checkout")
    @Operation(summary = "Checkout AR shopping cart",
              description = "Complete purchase of items in AR shopping cart")
    @ApiResponse(responseCode = "200", description = "Checkout completed")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARCheckoutResponse> checkoutARCart(
            @PathVariable String sessionId,
            @Valid @RequestBody ARCheckoutRequest request) {
        
        log.info("Checking out AR cart for session: {}", sessionId);
        
        try {
            ARCheckoutResponse response = arPaymentService.checkoutARCart(sessionId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error checking out AR cart", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARCheckoutResponse.error("Failed to checkout AR cart"));
        }
    }
    
    @PostMapping("/session/{sessionId}/wallet/visualize")
    @Operation(summary = "Visualize AR wallet",
              description = "Display 3D holographic wallet visualization")
    @ApiResponse(responseCode = "200", description = "Wallet visualization data returned")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARWalletVisualizationResponse> visualizeWallet(
            @PathVariable String sessionId,
            @RequestParam(required = false) String visualizationType) {
        
        log.info("Visualizing AR wallet for session: {}", sessionId);
        
        try {
            ARWalletVisualizationResponse response = arVisualizationService.visualizeWallet(
                    sessionId, visualizationType);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error visualizing AR wallet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARWalletVisualizationResponse.error("Failed to visualize wallet"));
        }
    }
    
    @PostMapping("/session/{sessionId}/social/send")
    @Operation(summary = "Send AR social payment",
              description = "Send payment to friend in AR social space")
    @ApiResponse(responseCode = "200", description = "Social payment sent")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARSocialPaymentResponse> sendSocialPayment(
            @PathVariable String sessionId,
            @Valid @RequestBody ARSocialPaymentRequest request) {
        
        log.info("Sending AR social payment for session: {}", sessionId);
        
        try {
            ARSocialPaymentResponse response = arPaymentService.sendSocialPayment(sessionId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error sending AR social payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARSocialPaymentResponse.error("Failed to send social payment"));
        }
    }
    
    @PostMapping("/session/{sessionId}/confirm")
    @Operation(summary = "Confirm AR payment",
              description = "Confirm payment with biometric or gesture verification")
    @ApiResponse(responseCode = "200", description = "Payment confirmed")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARPaymentConfirmationResponse> confirmPayment(
            @PathVariable String sessionId,
            @Valid @RequestBody ARPaymentConfirmationRequest request) {
        
        log.info("Confirming AR payment for session: {}", sessionId);
        
        try {
            ARPaymentConfirmationResponse response = arPaymentService.confirmPayment(sessionId, request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error confirming AR payment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARPaymentConfirmationResponse.error("Failed to confirm payment"));
        }
    }
    
    @GetMapping("/session/{sessionId}/status")
    @Operation(summary = "Get AR session status",
              description = "Get current status of AR payment session")
    @ApiResponse(responseCode = "200", description = "Session status returned")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARSessionStatusResponse> getSessionStatus(
            @PathVariable String sessionId) {
        
        log.debug("Getting AR session status: {}", sessionId);
        
        try {
            ARSessionStatusResponse response = arSessionService.getSessionStatus(sessionId);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting AR session status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/session/{sessionId}/experiences")
    @Operation(summary = "Get AR payment experiences",
              description = "Get all payment experiences in current session")
    @ApiResponse(responseCode = "200", description = "Payment experiences returned")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ARPaymentExperienceDto>> getSessionExperiences(
            @PathVariable String sessionId) {
        
        log.debug("Getting AR payment experiences for session: {}", sessionId);
        
        try {
            List<ARPaymentExperienceDto> experiences = arPaymentService.getSessionExperiences(sessionId);
            return ResponseEntity.ok(experiences);
            
        } catch (Exception e) {
            log.error("Error getting AR payment experiences", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/session/{sessionId}/end")
    @Operation(summary = "End AR session",
              description = "End the current AR payment session")
    @ApiResponse(responseCode = "200", description = "Session ended successfully")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARSessionEndResponse> endSession(
            @PathVariable String sessionId) {
        
        log.info("Ending AR session: {}", sessionId);
        
        try {
            ARSessionEndResponse response = arSessionService.endSession(sessionId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error ending AR session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARSessionEndResponse.error("Failed to end session"));
        }
    }
    
    @PostMapping(value = "/session/{sessionId}/screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload AR screenshot",
              description = "Upload screenshot from AR payment experience")
    @ApiResponse(responseCode = "200", description = "Screenshot uploaded")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARScreenshotResponse> uploadScreenshot(
            @PathVariable String sessionId,
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(required = false) String experienceId) {
        
        log.info("Uploading AR screenshot for session: {}", sessionId);
        
        try {
            if (imageFile.isEmpty() || !isValidImageFile(imageFile)) {
                return ResponseEntity.badRequest()
                        .body(ARScreenshotResponse.error("Invalid image file"));
            }
            
            ARScreenshotResponse response = arPaymentService.uploadScreenshot(
                    sessionId, imageFile, experienceId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error uploading AR screenshot", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ARScreenshotResponse.error("Failed to upload screenshot"));
        }
    }
    
    @GetMapping("/analytics/usage")
    @Operation(summary = "Get AR payment analytics",
              description = "Get analytics for AR payment usage")
    @ApiResponse(responseCode = "200", description = "Analytics returned")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARAnalyticsResponse> getARAnalytics(
            @RequestParam UUID userId,
            @RequestParam(defaultValue = "30") int days) {
        
        log.debug("Getting AR analytics for user: {}", userId);
        
        try {
            ARAnalyticsResponse response = arPaymentService.getARAnalytics(userId, days);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting AR analytics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/locations/nearby")
    @Operation(summary = "Get nearby AR payment locations",
              description = "Find nearby merchants and payment spots with AR support")
    @ApiResponse(responseCode = "200", description = "Nearby locations returned")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ARLocationDto>> getNearbyARLocations(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1000") double radiusMeters) {
        
        log.debug("Getting nearby AR locations: {}, {}", latitude, longitude);
        
        try {
            List<ARLocationDto> locations = arPaymentService.getNearbyARLocations(
                    latitude, longitude, radiusMeters);
            return ResponseEntity.ok(locations);
            
        } catch (Exception e) {
            log.error("Error getting nearby AR locations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/achievements")
    @Operation(summary = "Get AR payment achievements",
              description = "Get user's AR payment achievements and rewards")
    @ApiResponse(responseCode = "200", description = "Achievements returned")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ARAchievementsResponse> getAchievements(
            @RequestParam UUID userId) {
        
        log.debug("Getting AR achievements for user: {}", userId);
        
        try {
            ARAchievementsResponse response = arPaymentService.getUserAchievements(userId);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting AR achievements", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Helper methods
    
    private boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();
        
        if (contentType == null || originalFilename == null) {
            return false;
        }
        
        List<String> validMimeTypes = List.of(
            "image/jpeg", "image/jpg", "image/png", 
            "image/gif", "image/webp", "image/heic"
        );
        
        List<String> validExtensions = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic"
        );
        
        boolean validMimeType = validMimeTypes.contains(contentType.toLowerCase());
        boolean validExtension = validExtensions.stream()
                .anyMatch(ext -> originalFilename.toLowerCase().endsWith(ext));
        
        return validMimeType || validExtension;
    }
}