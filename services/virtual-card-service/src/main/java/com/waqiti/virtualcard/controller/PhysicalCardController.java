package com.waqiti.virtualcard.controller;

import com.waqiti.virtualcard.dto.*;
import com.waqiti.virtualcard.service.PhysicalCardService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.ValidUUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * REST API controller for physical card management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/physical-cards")
@RequiredArgsConstructor
@Validated
@Tag(name = "Physical Cards", description = "Physical card ordering, shipping, and management")
public class PhysicalCardController {

    private final PhysicalCardService physicalCardService;
    private final SecurityContext securityContext;

    @Operation(
        summary = "Order physical card",
        description = "Order a new physical card with shipping details"
    )
    @ApiResponse(responseCode = "201", description = "Physical card ordered successfully")
    @ApiResponse(responseCode = "403", description = "Full KYC verification required")
    @ApiResponse(responseCode = "409", description = "Card limit exceeded or pending order exists")
    @PostMapping("/order")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardOrderDto> orderPhysicalCard(
            @Valid @RequestBody OrderCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Ordering physical card for user: {}, brand: {}, shipping: {}", 
            userId, request.getBrand(), request.getShippingMethod());
        
        CardOrderDto order = physicalCardService.orderPhysicalCard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @Operation(
        summary = "Get user's physical cards",
        description = "Get all physical cards for the authenticated user"
    )
    @ApiResponse(responseCode = "200", description = "Physical cards retrieved successfully")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<PhysicalCardDto>> getUserPhysicalCards() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting physical cards for user: {}", userId);
        
        List<PhysicalCardDto> cards = physicalCardService.getUserPhysicalCards(userId);
        return ResponseEntity.ok(cards);
    }

    @Operation(
        summary = "Get physical card details",
        description = "Get detailed information about a specific physical card"
    )
    @ApiResponse(responseCode = "200", description = "Card details retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Card not found")
    @GetMapping("/{cardId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PhysicalCardDto> getPhysicalCard(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting physical card details: {}, user: {}", cardId, userId);
        
        PhysicalCardDto card = physicalCardService.getPhysicalCard(cardId);
        return ResponseEntity.ok(card);
    }

    @Operation(
        summary = "Activate physical card",
        description = "Activate a delivered physical card with activation code and PIN"
    )
    @ApiResponse(responseCode = "200", description = "Card activated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid activation code or card not delivered")
    @PostMapping("/{cardId}/activate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PhysicalCardDto> activateCard(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody ActivateCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Activating physical card: {} for user: {}", cardId, userId);
        
        PhysicalCardDto card = physicalCardService.activateCard(cardId, request);
        return ResponseEntity.ok(card);
    }

    @Operation(
        summary = "Track card shipment",
        description = "Get real-time shipping tracking information for a physical card"
    )
    @ApiResponse(responseCode = "200", description = "Tracking information retrieved successfully")
    @GetMapping("/{cardId}/tracking")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ShippingTrackingDto> trackShipment(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Tracking shipment for card: {}, user: {}", cardId, userId);
        
        ShippingTrackingDto tracking = physicalCardService.trackShipment(cardId);
        return ResponseEntity.ok(tracking);
    }

    @Operation(
        summary = "Replace physical card",
        description = "Order a replacement card for lost, stolen, or damaged cards"
    )
    @ApiResponse(responseCode = "201", description = "Replacement card ordered successfully")
    @PostMapping("/{cardId}/replace")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardOrderDto> replaceCard(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody ReplaceCardRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Replacing physical card: {} for user: {}, reason: {}", 
            cardId, userId, request.getReason());
        
        CardOrderDto order = physicalCardService.replaceCard(cardId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @Operation(
        summary = "Report card lost or stolen",
        description = "Immediately block a card reported as lost or stolen"
    )
    @ApiResponse(responseCode = "204", description = "Card blocked successfully")
    @PostMapping("/{cardId}/report-lost-stolen")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> reportCardLostStolen(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody ReportLostStolenRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.warn("Card {} reported {} by user {}", cardId, request.getReason(), userId);
        
        physicalCardService.reportCardLostStolen(cardId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Get available card designs",
        description = "Get list of available card designs and customization options"
    )
    @ApiResponse(responseCode = "200", description = "Card designs retrieved successfully")
    @GetMapping("/designs")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CardDesignDto>> getAvailableDesigns() {
        log.debug("Getting available card designs");
        
        List<CardDesignDto> designs = physicalCardService.getAvailableDesigns();
        return ResponseEntity.ok(designs);
    }

    @Operation(
        summary = "Get card order history",
        description = "Get history of physical card orders for the user"
    )
    @ApiResponse(responseCode = "200", description = "Order history retrieved successfully")
    @GetMapping("/orders")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CardOrderDto>> getOrderHistory() {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting order history for user: {}", userId);
        
        List<CardOrderDto> orders = physicalCardService.getOrderHistory(userId);
        return ResponseEntity.ok(orders);
    }

    @Operation(
        summary = "Get order details",
        description = "Get detailed information about a specific card order"
    )
    @ApiResponse(responseCode = "200", description = "Order details retrieved successfully")
    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CardOrderDto> getOrderDetails(
            @Parameter(description = "Order ID") @PathVariable @ValidUUID String orderId) {
        String userId = securityContext.getCurrentUserId();
        log.debug("Getting order details: {}, user: {}", orderId, userId);
        
        CardOrderDto order = physicalCardService.getOrderDetails(orderId);
        return ResponseEntity.ok(order);
    }

    @Operation(
        summary = "Change card PIN",
        description = "Change the PIN for an active physical card"
    )
    @ApiResponse(responseCode = "204", description = "PIN changed successfully")
    @ApiResponse(responseCode = "400", description = "Invalid current PIN or card not active")
    @PutMapping("/{cardId}/pin")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> changeCardPin(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody ChangePinRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Changing PIN for card: {}, user: {}", cardId, userId);
        
        physicalCardService.changeCardPin(cardId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Set travel notice",
        description = "Set travel notice to prevent card blocks while traveling"
    )
    @ApiResponse(responseCode = "204", description = "Travel notice set successfully")
    @PostMapping("/{cardId}/travel-notice")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> setTravelNotice(
            @Parameter(description = "Card ID") @PathVariable @ValidUUID String cardId,
            @Valid @RequestBody TravelNoticeRequest request) {
        String userId = securityContext.getCurrentUserId();
        log.info("Setting travel notice for card: {}, user: {}, destinations: {}", 
            cardId, userId, request.getDestinations());
        
        physicalCardService.setTravelNotice(cardId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
        summary = "Webhook: Shipping status update",
        description = "Receive shipping status updates from shipping providers"
    )
    @ApiResponse(responseCode = "200", description = "Shipping status updated successfully")
    @PostMapping("/webhook/shipping")
    @PreAuthorize("hasRole('SHIPPING_PROVIDER')")
    public ResponseEntity<Void> updateShippingStatus(
            @Valid @RequestBody ShippingUpdateWebhook webhook) {
        log.info("Received shipping update for tracking: {}, status: {}", 
            webhook.getTrackingNumber(), webhook.getStatus());
        
        physicalCardService.updateShippingStatus(webhook);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Webhook: Card production update",
        description = "Receive production status updates from card manufacturers"
    )
    @ApiResponse(responseCode = "200", description = "Production status updated successfully")
    @PostMapping("/webhook/production")
    @PreAuthorize("hasRole('CARD_MANUFACTURER')")
    public ResponseEntity<Void> updateProductionStatus(
            @Valid @RequestBody ProductionUpdateWebhook webhook) {
        log.info("Received production update for order: {}, status: {}", 
            webhook.getOrderId(), webhook.getStatus());
        
        physicalCardService.updateProductionStatus(webhook);
        return ResponseEntity.ok().build();
    }

    @Operation(
        summary = "Get shipping options",
        description = "Get available shipping methods and estimated delivery times"
    )
    @ApiResponse(responseCode = "200", description = "Shipping options retrieved successfully")
    @PostMapping("/shipping-options")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ShippingOptionDto>> getShippingOptions(
            @Valid @RequestBody ShippingOptionsRequest request) {
        log.debug("Getting shipping options for address: {}", request.getCountry());
        
        List<ShippingOptionDto> options = physicalCardService.getShippingOptions(request);
        return ResponseEntity.ok(options);
    }

    @Operation(
        summary = "Admin: Get all card orders",
        description = "Get all physical card orders in the system (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "All orders retrieved successfully")
    @GetMapping("/admin/orders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CardOrderDto>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String status) {
        log.info("Admin getting all card orders, page: {}, size: {}, status: {}", 
            page, size, status);
        
        List<CardOrderDto> orders = physicalCardService.getAllOrders(page, size, status);
        return ResponseEntity.ok(orders);
    }

    @Operation(
        summary = "Admin: Update order status",
        description = "Manually update order status (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "Order status updated successfully")
    @PutMapping("/admin/orders/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CardOrderDto> updateOrderStatus(
            @Parameter(description = "Order ID") @PathVariable @ValidUUID String orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        String adminUserId = securityContext.getCurrentUserId();
        log.warn("Admin {} updating order {} status to {}", 
            adminUserId, orderId, request.getStatus());
        
        CardOrderDto order = physicalCardService.updateOrderStatus(orderId, request, adminUserId);
        return ResponseEntity.ok(order);
    }

    @Operation(
        summary = "Admin: Get system metrics",
        description = "Get system-wide physical card metrics (admin only)"
    )
    @ApiResponse(responseCode = "200", description = "System metrics retrieved successfully")
    @GetMapping("/admin/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PhysicalCardMetricsDto> getSystemMetrics() {
        log.info("Getting physical card system metrics");
        
        PhysicalCardMetricsDto metrics = physicalCardService.getSystemMetrics();
        return ResponseEntity.ok(metrics);
    }
}