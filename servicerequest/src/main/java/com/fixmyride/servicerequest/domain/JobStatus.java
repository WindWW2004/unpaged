package com.fixmyride.servicerequest.domain;

import java.util.EnumSet;
import java.util.Set;

public enum JobStatus {
    REQUESTED("Requested"),
    SCHEDULED("Scheduled"),
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled");

    private final String displayName;


    JobStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<JobStatus> allowedNextStatuses() {
        return switch (this) {
            case REQUESTED -> EnumSet.of(SCHEDULED, IN_PROGRESS, CANCELLED);
            case SCHEDULED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(JobStatus.class);
        };
    }
}
