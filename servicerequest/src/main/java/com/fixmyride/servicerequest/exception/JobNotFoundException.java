package com.fixmyride.servicerequest.exception;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(Long id) {
        super("No service job exists with id " + id + ".");
    }
}
