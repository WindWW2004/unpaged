package com.fixmyride.servicerequest.service;

import com.fixmyride.servicerequest.domain.ServiceType;
import com.fixmyride.servicerequest.repository.ServiceJobRepository;
import org.springframework.stereotype.Component;

@Component
public class ServiceCodeGenerator {
    private final ServiceJobRepository jobRepository;
    private int nextNumber = 1000;

    public ServiceCodeGenerator(ServiceJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public String next(ServiceType type) {
        String prefix = switch (type) {
            case OIL_CHANGE -> "OIL";
            case TIRE_SERVICE -> "TIRE";
            case ENGINE_REPAIR -> "ENG";
        };
        String candidate;
        do {
            candidate = "%s-%04d".formatted(prefix, nextNumber++);
        } while (jobRepository.existsByServiceCode(candidate));
        return candidate;
    }
}
