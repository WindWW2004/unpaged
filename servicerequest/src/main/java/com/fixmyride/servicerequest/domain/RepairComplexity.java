package com.fixmyride.servicerequest.domain;

import java.math.BigDecimal;

public enum RepairComplexity {
    LOW("Low", new BigDecimal("1.00")),
    MEDIUM("Medium", new BigDecimal("1.15")),
    HIGH("High", new BigDecimal("1.35"));

    private final String displayName;
    private final BigDecimal laborMultiplier;

    RepairComplexity(String displayName, BigDecimal laborMultiplier) {
        this.displayName = displayName;
        this.laborMultiplier = laborMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getLaborMultiplier() {
        return laborMultiplier;
    }
}
