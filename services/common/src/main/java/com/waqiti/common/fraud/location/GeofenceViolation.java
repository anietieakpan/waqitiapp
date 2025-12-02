package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Geofence violation details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceViolation {
    private String ruleName;
    private LocationService.GeofenceType type;
    private LocationService.GeofenceSeverity severity;
    private String message;
}
