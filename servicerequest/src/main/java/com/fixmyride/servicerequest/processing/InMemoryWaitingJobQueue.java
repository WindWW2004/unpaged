package com.fixmyride.servicerequest.processing;

import com.fixmyride.servicerequest.domain.ServiceJob;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Intentionally single-threaded. A concurrency module can replace this class
 * with a BlockingQueue implementation while preserving the interface.
 */
@Component
public class InMemoryWaitingJobQueue implements WaitingJobQueue {
    private final Deque<ServiceJob> jobs = new ArrayDeque<>();

    @Override
    public void enqueue(ServiceJob job) {
        jobs.offerLast(job);
    }

    @Override
    public Optional<ServiceJob> poll() {
        return Optional.ofNullable(jobs.pollFirst());
    }

    @Override
    public void remove(Long jobId) {
        jobs.removeIf(job -> job.getId().equals(jobId));
    }

    @Override
    public List<ServiceJob> snapshot() {
        return List.copyOf(new ArrayList<>(jobs));
    }

    @Override
    public int size() {
        return jobs.size();
    }
}
