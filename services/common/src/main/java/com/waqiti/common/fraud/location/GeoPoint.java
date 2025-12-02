package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Geographic point
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {
    private double latitude;
    private double longitude;
}
