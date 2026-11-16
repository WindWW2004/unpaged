package com.fixmyride.servicerequest.service;

import com.fixmyride.servicerequest.domain.Customer;
import com.fixmyride.servicerequest.domain.JobStatus;
import com.fixmyride.servicerequest.domain.Mechanic;
import com.fixmyride.servicerequest.domain.ServiceJob;
import com.fixmyride.servicerequest.domain.Vehicle;
import com.fixmyride.servicerequest.exception.JobNotFoundException;
import com.fixmyride.servicerequest.exception.MechanicUnavailableException;
import com.fixmyride.servicerequest.exception.VehicleOwnershipException;
import com.fixmyride.servicerequest.processing.WaitingJobQueue;
import com.fixmyride.servicerequest.repository.CustomerRepository;
import com.fixmyride.servicerequest.repository.MechanicRepository;
import com.fixmyride.servicerequest.repository.ServiceJobRepository;
import com.fixmyride.servicerequest.repository.VehicleRepository;
import com.fixmyride.servicerequest.web.ServiceRequestForm;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceRequestService {
    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final MechanicRepository mechanicRepository;
    private final ServiceJobRepository jobRepository;
    private final ServiceJobFactory jobFactory;
    private final WaitingJobQueue waitingJobQueue;

    public ServiceRequestService(CustomerRepository customerRepository, VehicleRepository vehicleRepository,
                                 MechanicRepository mechanicRepository, ServiceJobRepository jobRepository,
                                 ServiceJobFactory jobFactory, WaitingJobQueue waitingJobQueue) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.mechanicRepository = mechanicRepository;
        this.jobRepository = jobRepository;
        this.jobFactory = jobFactory;
        this.waitingJobQueue = waitingJobQueue;
    }

    @Transactional
    public ServiceJob submit(ServiceRequestForm form) {
        Customer customer = customerRepository.findByEmailIgnoreCase(form.getEmail())
                .map(existing -> {
                    existing.updateContactDetails(form.getCustomerName(), form.getPhone());
                    return existing;
                })
                .orElseGet(() -> new Customer(form.getCustomerName(), form.getEmail(), form.getPhone()));
        customer = customerRepository.save(customer);

        Customer finalCustomer = customer;
        Vehicle vehicle = vehicleRepository.findByVinIgnoreCase(form.getVin())
                .map(existing -> {
                    if (!existing.getOwner().getEmail().equalsIgnoreCase(finalCustomer.getEmail())) {
                        throw new VehicleOwnershipException(form.getVin());
                    }
                    return existing;
                })
                .orElseGet(() -> vehicleRepository.save(new Vehicle(form.getVin(), form.getLicensePlate(),
                        form.getMake(), form.getModel(), form.getModelYear(), finalCustomer)));

        Mechanic mechanic = null;
        if (form.getMechanicId() != null) {
            mechanic = mechanicRepository.findById(form.getMechanicId())
                    .orElseThrow(() -> new IllegalArgumentException("Selected mechanic no longer exists."));
            if (!mechanic.isAvailable()) {
                throw new MechanicUnavailableException(mechanic.getName());
            }
        }

        ServiceJob saved = jobRepository.save(jobFactory.create(form, vehicle, mechanic));
        if (saved.getStatus() == JobStatus.REQUESTED) {
            waitingJobQueue.enqueue(saved);
        }
        return saved;
    }

    public List<ServiceJob> findAll() {
        return jobRepository.findAllByOrderByCreatedAtDesc();
    }

    public ServiceJob findById(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional
    public ServiceJob updateStatus(Long id, JobStatus nextStatus) {
        ServiceJob job = findById(id);
        job.transitionTo(nextStatus);
        if (nextStatus != JobStatus.REQUESTED) {
            waitingJobQueue.remove(id);
        }
        return jobRepository.save(job);
    }
}
