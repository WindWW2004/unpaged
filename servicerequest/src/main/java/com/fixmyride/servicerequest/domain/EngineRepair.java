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
@DiscriminatorValue("ENGINE")
public class EngineRepair extends ServiceJob {
    private BigDecimal diagnosticHours;
    private BigDecimal laborRate;
    private BigDecimal partsCost;

    @Enumerated(EnumType.STRING)
    private RepairComplexity complexity;

    protected EngineRepair() {
    }

    public EngineRepair(String serviceCode, Vehicle vehicle, Mechanic mechanic, LocalDateTime scheduledFor,
                        BigDecimal diagnosticHours, BigDecimal laborRate, BigDecimal partsCost,
                        RepairComplexity complexity) {
        super(serviceCode, vehicle, mechanic, scheduledFor);
        this.diagnosticHours = diagnosticHours;
        this.laborRate = laborRate;
        this.partsCost = partsCost;
        this.complexity = complexity;
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.ENGINE_REPAIR;
    }

    @Override
    public BigDecimal calculateCost() {
        BigDecimal labor = diagnosticHours.multiply(laborRate).multiply(complexity.getLaborMultiplier());
        return labor.add(partsCost).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public Duration estimateDuration() {
        long diagnosticMinutes = diagnosticHours.multiply(BigDecimal.valueOf(60)).longValue();
        long repairMinutes = switch (complexity) {
            case LOW -> 60L;
            case MEDIUM -> 150L;
            case HIGH -> 300L;
        };
        return Duration.ofMinutes(diagnosticMinutes + repairMinutes);
    }

    @Override
    public List<String> generateChecklist() {
        return List.of(
                "Interview customer and reproduce reported symptoms",
                "Scan diagnostic codes and preserve freeze-frame data",
                "Perform " + diagnosticHours.stripTrailingZeros().toPlainString() + " diagnostic hour(s)",
                "Obtain approval before installing $" + partsCost.setScale(2, RoundingMode.HALF_UP) + " in parts",
                "Complete " + complexity.getDisplayName().toLowerCase() + "-complexity repair procedure",
                "Clear codes, road test, and verify repair"
        );
    }

    public BigDecimal getDiagnosticHours() {
        return diagnosticHours;
    }

    public BigDecimal getLaborRate() {
        return laborRate;
    }

    public BigDecimal getPartsCost() {
        return partsCost;
    }

    public RepairComplexity getComplexity() {
        return complexity;
    }
}
