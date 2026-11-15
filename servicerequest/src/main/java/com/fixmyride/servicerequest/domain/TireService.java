package com.fixmyride.servicerequest.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("TIRE")
public class TireService extends ServiceJob {
    private int numberOfTires;
    private boolean rotation;
    private boolean balancing;
    private boolean alignment;

    protected TireService() {
    }

    public TireService(String serviceCode, Vehicle vehicle, Mechanic mechanic, LocalDateTime scheduledFor,
                       int numberOfTires, boolean rotation, boolean balancing, boolean alignment) {
        super(serviceCode, vehicle, mechanic, scheduledFor);
        this.numberOfTires = numberOfTires;
        this.rotation = rotation;
        this.balancing = balancing;
        this.alignment = alignment;
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.TIRE_SERVICE;
    }

    @Override
    public BigDecimal calculateCost() {
        BigDecimal cost = BigDecimal.valueOf(numberOfTires).multiply(new BigDecimal("22.50"));
        if (rotation) {
            cost = cost.add(new BigDecimal("25.00"));
        }
        if (balancing) {
            cost = cost.add(BigDecimal.valueOf(numberOfTires).multiply(new BigDecimal("12.00")));
        }
        if (alignment) {
            cost = cost.add(new BigDecimal("95.00"));
        }
        return cost.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Duration estimateDuration() {
        long minutes = 20L + numberOfTires * 10L;
        minutes += rotation ? 15L : 0L;
        minutes += balancing ? numberOfTires * 5L : 0L;
        minutes += alignment ? 45L : 0L;
        return Duration.ofMinutes(minutes);
    }

    @Override
    public List<String> generateChecklist() {
        List<String> checklist = new ArrayList<>();
        checklist.add("Inspect tread depth and sidewalls on all tires");
        checklist.add("Set tire pressure to manufacturer specification");
        if (rotation) {
            checklist.add("Rotate tires using the correct vehicle pattern");
        }
        if (balancing) {
            checklist.add("Dynamically balance " + numberOfTires + " tire(s)");
        }
        if (alignment) {
            checklist.add("Measure and correct four-wheel alignment");
        }
        checklist.add("Torque wheel fasteners and document measurements");
        return List.copyOf(checklist);
    }

    public int getNumberOfTires() {
        return numberOfTires;
    }

    public boolean isRotation() {
        return rotation;
    }

    public boolean isBalancing() {
        return balancing;
    }

    public boolean isAlignment() {
        return alignment;
    }
}
