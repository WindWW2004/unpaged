package com.fixmyride.servicerequest.service;

import com.fixmyride.servicerequest.domain.JobStatus;
import com.fixmyride.servicerequest.domain.ServiceJob;
import com.fixmyride.servicerequest.domain.ServiceType;
import com.fixmyride.servicerequest.repository.ServiceJobRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportService {
    private final ServiceJobRepository jobRepository;

    public ReportService(ServiceJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public ReportSnapshot buildReport() {
        List<ServiceJob> jobs = jobRepository.findAll();

        BigDecimal expectedRevenue = jobs.stream()
                .filter(job -> job.getStatus() != JobStatus.CANCELLED)
                .map(ServiceJob::calculateCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        double averageMinutes = jobs.stream()
                .mapToLong(ServiceJob::getEstimatedMinutes)
                .average()
                .orElse(0.0);

        Map<JobStatus, Long> byStatus = new EnumMap<>(JobStatus.class);
        for (JobStatus status : JobStatus.values()) {
            byStatus.put(status, 0L);
        }
        jobs.stream().collect(Collectors.groupingBy(ServiceJob::getStatus, Collectors.counting()))
                .forEach(byStatus::put);

        Map<ServiceType, Long> byType = new EnumMap<>(ServiceType.class);
        for (ServiceType type : ServiceType.values()) {
            byType.put(type, 0L);
        }
        jobs.stream().collect(Collectors.groupingBy(ServiceJob::getServiceType, Collectors.counting()))
                .forEach(byType::put);

        Map<String, Long> mechanicWorkload = jobs.stream()
                .filter(job -> job.getMechanic() != null)
                .filter(job -> job.getStatus() != JobStatus.COMPLETED && job.getStatus() != JobStatus.CANCELLED)
                .collect(Collectors.groupingBy(job -> job.getMechanic().getName(), LinkedHashMap::new,
                        Collectors.counting()));

        return new ReportSnapshot(jobs.size(), expectedRevenue, averageMinutes, byStatus, byType, mechanicWorkload);
    }

    public record ReportSnapshot(long totalJobs, BigDecimal expectedRevenue, double averageMinutes,
                                 Map<JobStatus, Long> jobsByStatus, Map<ServiceType, Long> jobsByType,
                                 Map<String, Long> mechanicWorkload) {
    }
}
