package com.fixmyride.servicerequest.exception;

import com.fixmyride.servicerequest.domain.JobStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(JobStatus current, JobStatus requested) {
        super("A job cannot move from " + current.getDisplayName() + " to " + requested.getDisplayName() + ".");
    }
}
