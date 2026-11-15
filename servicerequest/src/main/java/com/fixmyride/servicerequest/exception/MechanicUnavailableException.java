package com.fixmyride.servicerequest.exception;

public class MechanicUnavailableException extends RuntimeException {
    public MechanicUnavailableException(String mechanicName) {
        super(mechanicName + " is currently unavailable.");
    }
}
