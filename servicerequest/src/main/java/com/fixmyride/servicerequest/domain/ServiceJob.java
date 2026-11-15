package com.fixmyride.servicerequest.domain;

import com.fixmyride.servicerequest.exception.InvalidStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "service_kind")
public abstract class ServiceJob implements Billable, Schedulable, Inspectable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 9)
    private String serviceCode;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mechanic_id")
    private Mechanic mechanic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime scheduledFor;

    protected ServiceJob() {
    }

    protected ServiceJob(String serviceCode, Vehicle vehicle, Mechanic mechanic, LocalDateTime scheduledFor) {
        this.serviceCode = serviceCode;
        this.vehicle = vehicle;
        this.mechanic = mechanic;
        this.scheduledFor = scheduledFor;
        this.status = scheduledFor == null ? JobStatus.REQUESTED : JobStatus.SCHEDULED;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Mechanic getMechanic() {
        return mechanic;
    }

    public JobStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }

    @Transient
    public abstract ServiceType getServiceType();

    @Override
    @Transient
    public abstract BigDecimal calculateCost();

    @Override
    @Transient
    public abstract Duration estimateDuration();

    @Override
    @Transient
    public abstract List<String> generateChecklist();

    @Transient
    public long getEstimatedMinutes() {
        return estimateDuration().toMinutes();
    }

    public void transitionTo(JobStatus nextStatus) {
        if (!status.allowedNextStatuses().contains(nextStatus)) {
            throw new InvalidStatusTransitionException(status, nextStatus);
        }
        status = nextStatus;
    }
}
