package com.waqiti.arpayment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single point in a gesture
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GesturePoint {
    private double x;
    private double y;
    private double z;
    private Instant timestamp;
    private double pressure;
    private double touchRadius;
    private String handType; // LEFT, RIGHT
    private String fingerType; // THUMB, INDEX, MIDDLE, RING, PINKY
    private Map<String, Double> velocity;
    private Map<String, Double> acceleration;
    private boolean isContact;
}