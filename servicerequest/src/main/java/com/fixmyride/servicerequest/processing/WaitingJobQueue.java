package com.fixmyride.servicerequest.processing;

import com.fixmyride.servicerequest.domain.ServiceJob;

import java.util.List;
import java.util.Optional;

public interface WaitingJobQueue {
    void enqueue(ServiceJob job);
    Optional<ServiceJob> poll();
    void remove(Long jobId);
    List<ServiceJob> snapshot();
    int size();
}
