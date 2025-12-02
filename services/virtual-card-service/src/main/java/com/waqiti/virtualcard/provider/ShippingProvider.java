package com.waqiti.virtualcard.provider;

import com.waqiti.virtualcard.domain.ShippingAddress;
import com.waqiti.virtualcard.domain.enums.ShippingMethod;
import com.waqiti.virtualcard.dto.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface for shipping provider integration (e.g., FedEx, UPS, DHL, etc.)
 */
public interface ShippingProvider {
    
    /**
     * Create a shipment for a card order
     */
    ShippingCreationResponse createShipment(ShippingCreationRequest request);
    
    /**
     * Get tracking information for a shipment
     */
    ShippingTrackingResponse getTrackingInfo(String trackingNumber);
    
    /**
     * Get real-time tracking updates
     */
    List<ShippingTrackingUpdate> getTrackingUpdates(String trackingNumber, 
                                                     java.time.Instant since);
    
    /**
     * Validate shipping address
     */
    AddressValidationResult validateAddress(ShippingAddress address);
    
    /**
     * Get shipping rates for different methods
     */
    List<ShippingRate> getShippingRates(ShippingRateRequest request);
    
    /**
     * Schedule a pickup for shipments
     */
    PickupScheduleResponse schedulePickup(PickupScheduleRequest request);
    
    /**
     * Cancel a shipment
     */
    boolean cancelShipment(String trackingNumber);
    
    /**
     * Update delivery preferences
     */
    void updateDeliveryPreferences(String trackingNumber, DeliveryPreferences preferences);
    
    /**
     * Get delivery proof (signature, photo)
     */
    DeliveryProof getDeliveryProof(String trackingNumber);
    
    /**
     * Get supported shipping methods for destination
     */
    List<ShippingMethod> getSupportedMethods(String countryCode);
    
    /**
     * Get estimated delivery time
     */
    EstimatedDeliveryResponse getEstimatedDelivery(EstimatedDeliveryRequest request);
    
    /**
     * Track multiple shipments at once
     */
    List<ShippingTrackingResponse> trackMultipleShipments(List<String> trackingNumbers);
    
    /**
     * Get shipping provider capabilities
     */
    ShippingProviderCapabilities getProviderCapabilities();
    
    /**
     * Register for webhook notifications
     */
    void registerWebhook(String webhookUrl, List<String> eventTypes);
    
    /**
     * Get shipping performance metrics
     */
    ShippingPerformanceMetrics getPerformanceMetrics(java.time.Instant fromDate, 
                                                      java.time.Instant toDate);
}