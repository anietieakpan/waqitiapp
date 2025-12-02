package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List; /**
 * Geofence rule
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceRule {
    private String id;
    private String name;
    private LocationService.GeofenceType type;
    private LocationService.GeofenceShape shape;
    private LocationService.GeofenceSeverity severity;
    private double centerLatitude;
    private double centerLongitude;
    private double radiusKm;
    private List<GeoPoint> polygonPoints;
    private List<String> applicableUserIds;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    
    public boolean appliesTo(String userId) {
        return applicableUserIds == null || applicableUserIds.isEmpty() || 
               applicableUserIds.contains(userId);
    }
}
