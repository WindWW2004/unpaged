package com.fixmyride.servicerequest.domain;

public enum ServiceType {
    OIL_CHANGE("Oil change"),
    TIRE_SERVICE("Tire service"),
    ENGINE_REPAIR("Engine repair");

    private final String displayName;

    ServiceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
