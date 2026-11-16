package com.fixmyride.servicerequest.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceJobPolymorphismTest {
    private final Customer customer = new Customer("Ada Student", "ada@example.edu", "555-010-1234");
    private final Vehicle vehicle = new Vehicle("1HGCM82633A004352", "JAVA21", "Honda", "Accord", 2021, customer);

    @Test
    void calculatesRevenueWithoutKnowingConcreteJobTypes() {
        List<ServiceJob> jobs = List.of(
                new OilChange("OIL-1000", vehicle, null, null,
                        OilType.FULL_SYNTHETIC, 5, new BigDecimal("12.00")),
                new TireService("TIRE-1001", vehicle, null, null, 4, true, true, true),
                new EngineRepair("ENG-1002", vehicle, null, null,
                        new BigDecimal("1.50"), new BigDecimal("135.00"), new BigDecimal("320.00"),
                        RepairComplexity.HIGH)
        );

        BigDecimal revenue = jobs.stream()
                .map(ServiceJob::calculateCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<ServiceType, Long> byType = jobs.stream()
                .collect(Collectors.groupingBy(ServiceJob::getServiceType, Collectors.counting()));

        assertThat(revenue).isEqualByComparingTo("937.38");
        assertThat(byType).containsEntry(ServiceType.OIL_CHANGE, 1L)
                .containsEntry(ServiceType.TIRE_SERVICE, 1L)
                .containsEntry(ServiceType.ENGINE_REPAIR, 1L);
        assertThat(jobs).allSatisfy(job -> {
            assertThat(job.generateChecklist()).isNotEmpty();
            assertThat(job.estimateDuration()).isPositive();
        });
    }

    @Test
    void enforcesTheStatusStateMachine() {
        ServiceJob job = new OilChange("OIL-1003", vehicle, null, LocalDateTime.now().plusDays(1),
                OilType.CONVENTIONAL, 5, new BigDecimal("8.00"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.SCHEDULED);
        job.transitionTo(JobStatus.IN_PROGRESS);
        job.transitionTo(JobStatus.COMPLETED);

        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThatThrownByTransition(job);
    }

    private void assertThatThrownByTransition(ServiceJob job) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> job.transitionTo(JobStatus.REQUESTED))
                .isInstanceOf(com.fixmyride.servicerequest.exception.InvalidStatusTransitionException.class)
                .hasMessageContaining("cannot move");
    }
}
