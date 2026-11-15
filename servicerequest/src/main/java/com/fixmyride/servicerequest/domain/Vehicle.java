package com.fixmyride.servicerequest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class Vehicle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @Column(nullable = false)
    private String licensePlate;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int modelYear;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer owner;

    protected Vehicle() {
    }

    public Vehicle(String vin, String licensePlate, String make, String model, int modelYear, Customer owner) {
        this.vin = vin.toUpperCase();
        this.licensePlate = licensePlate.toUpperCase();
        this.make = make;
        this.model = model;
        this.modelYear = modelYear;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getVin() {
        return vin;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public int getModelYear() {
        return modelYear;
    }

    public Customer getOwner() {
        return owner;
    }

    public String getDisplayName() {
        return modelYear + " " + make + " " + model;
    }
}
