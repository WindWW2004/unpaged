package com.fixmyride.servicerequest.processing;

import com.fixmyride.servicerequest.domain.JobStatus;
import com.fixmyride.servicerequest.domain.ServiceJob;
import com.fixmyride.servicerequest.repository.ServiceJobRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SequentialJobProcessor implements JobProcessor {
    private final WaitingJobQueue waitingJobQueue;
    private final ServiceJobRepository jobRepository;

    public SequentialJobProcessor(WaitingJobQueue waitingJobQueue, ServiceJobRepository jobRepository) {
        this.waitingJobQueue = waitingJobQueue;
        this.jobRepository = jobRepository;
    }

    @Override
    @Transactional
    public Optional<ServiceJob> processNext() {
        while (waitingJobQueue.size() > 0) {
            Optional<ServiceJob> queued = waitingJobQueue.poll();
            if (queued.isEmpty()) {
                return Optional.empty();
            }
            ServiceJob current = jobRepository.findById(queued.get().getId()).orElse(null);
            if (current != null && current.getStatus() == JobStatus.REQUESTED) {
                current.transitionTo(JobStatus.IN_PROGRESS);
                return Optional.of(jobRepository.save(current));
            }
        }
        return Optional.empty();
    }

    @Override
    public String modeDescription() {
        return "Sequential classroom mode";
    }
}
