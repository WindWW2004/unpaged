package com.fixmyride.servicerequest.domain;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@DiscriminatorValue("OIL")
public class OilChange extends ServiceJob {
    @Enumerated(EnumType.STRING)
    private OilType oilType;
    private int numberOfQuarts;
    private BigDecimal filterCost;

    protected OilChange() {
    }

    public OilChange(String serviceCode, Vehicle vehicle, Mechanic mechanic, LocalDateTime scheduledFor,
                     OilType oilType, int numberOfQuarts, BigDecimal filterCost) {
        super(serviceCode, vehicle, mechanic, scheduledFor);
        this.oilType = oilType;
        this.numberOfQuarts = numberOfQuarts;
        this.filterCost = filterCost;
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.OIL_CHANGE;
    }

    @Override
    public BigDecimal calculateCost() {
        BigDecimal shopLabor = new BigDecimal("24.00");
        return oilType.getPricePerQuart()
                .multiply(BigDecimal.valueOf(numberOfQuarts))
                .add(filterCost)
                .add(shopLabor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Duration estimateDuration() {
        return Duration.ofMinutes(30L + Math.max(0, numberOfQuarts - 5L) * 3L);
    }

    @Override
    public List<String> generateChecklist() {
        return List.of(
                "Confirm " + oilType.getDisplayName().toLowerCase() + " oil specification",
                "Drain old oil and inspect for contamination",
                "Replace oil filter",
                "Add " + numberOfQuarts + " quarts and verify level",
                "Reset maintenance reminder and check for leaks"
        );
    }

    public OilType getOilType() {
        return oilType;
    }

    public int getNumberOfQuarts() {
        return numberOfQuarts;
    }

    public BigDecimal getFilterCost() {
        return filterCost;
    }
}
