package com.fixmyride.servicerequest.exception;

public class VehicleOwnershipException extends RuntimeException {
    public VehicleOwnershipException(String vin) {
        super("Vehicle " + vin + " is already registered to a different customer.");
    }
}
