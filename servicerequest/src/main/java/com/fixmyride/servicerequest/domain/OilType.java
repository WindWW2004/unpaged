package com.fixmyride.servicerequest.domain;

import java.math.BigDecimal;

public enum OilType {
    CONVENTIONAL("Conventional", new BigDecimal("5.50")),
    SYNTHETIC_BLEND("Synthetic blend", new BigDecimal("7.25")),
    FULL_SYNTHETIC("Full synthetic", new BigDecimal("10.00"));

    private final String displayName;
    private final BigDecimal pricePerQuart;

    OilType(String displayName, BigDecimal pricePerQuart) {
        this.displayName = displayName;
        this.pricePerQuart = pricePerQuart;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getPricePerQuart() {
        return pricePerQuart;
    }
}
