package com.fixmyride.servicerequest.repository;

import com.fixmyride.servicerequest.domain.JobStatus;
import com.fixmyride.servicerequest.domain.ServiceJob;

import java.util.List;

public interface ServiceJobRepository extends ServiceRepository<ServiceJob> {
    List<ServiceJob> findAllByOrderByCreatedAtDesc();
    List<ServiceJob> findByStatusOrderByCreatedAtAsc(JobStatus status);
    boolean existsByServiceCode(String serviceCode);
}
