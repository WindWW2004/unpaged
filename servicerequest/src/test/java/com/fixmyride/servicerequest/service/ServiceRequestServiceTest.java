package com.fixmyride.servicerequest.service;

import com.fixmyride.servicerequest.domain.EngineRepair;
import com.fixmyride.servicerequest.domain.RepairComplexity;
import com.fixmyride.servicerequest.domain.ServiceJob;
import com.fixmyride.servicerequest.domain.ServiceType;
import com.fixmyride.servicerequest.processing.WaitingJobQueue;
import com.fixmyride.servicerequest.web.ServiceRequestForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ServiceRequestServiceTest {
    @Autowired
    private ServiceRequestService requestService;

    @Autowired
    private WaitingJobQueue waitingJobQueue;

    @Test
    void factoryCreatesTheSelectedSubtypeAndQueuesAnUnscheduledJob() {
        int queueSizeBefore = waitingJobQueue.size();
        ServiceRequestForm form = new ServiceRequestForm();
        form.setCustomerName("Grace Hopper");
        form.setEmail("grace@example.edu");
        form.setPhone("555-010-4400");
        form.setVin("2C3KA53G76H123456");
        form.setLicensePlate("CS-101");
        form.setMake("Chrysler");
        form.setModel("300");
        form.setModelYear(2006);
        form.setServiceType(ServiceType.ENGINE_REPAIR);
        form.setDiagnosticHours(new BigDecimal("2.00"));
        form.setLaborRate(new BigDecimal("120.00"));
        form.setPartsCost(new BigDecimal("80.00"));
        form.setComplexity(RepairComplexity.MEDIUM);

        ServiceJob saved = requestService.submit(form);

        assertThat(saved).isInstanceOf(EngineRepair.class);
        assertThat(saved.getServiceCode()).matches("ENG-\\d{4}");
        assertThat(saved.calculateCost()).isEqualByComparingTo("356.00");
        assertThat(waitingJobQueue.size()).isEqualTo(queueSizeBefore + 1);
    }
}
