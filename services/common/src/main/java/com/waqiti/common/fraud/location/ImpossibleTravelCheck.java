package com.waqiti.common.fraud.location;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Impossible travel check result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpossibleTravelCheck {
    private boolean isPossible;
    private double distance;
    private long timeDifferenceMinutes;
    private double calculatedSpeed;
    private double confidence;
    private String reason;
}
