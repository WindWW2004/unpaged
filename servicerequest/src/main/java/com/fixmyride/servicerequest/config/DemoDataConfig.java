package com.fixmyride.servicerequest.config;

import com.fixmyride.servicerequest.domain.JobStatus;
import com.fixmyride.servicerequest.domain.Mechanic;
import com.fixmyride.servicerequest.domain.OilType;
import com.fixmyride.servicerequest.domain.RepairComplexity;
import com.fixmyride.servicerequest.domain.ServiceJob;
import com.fixmyride.servicerequest.domain.ServiceType;
import com.fixmyride.servicerequest.repository.MechanicRepository;
import com.fixmyride.servicerequest.repository.ServiceJobRepository;
import com.fixmyride.servicerequest.service.ServiceRequestService;
import com.fixmyride.servicerequest.web.ServiceRequestForm;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DemoDataConfig {
    @Bean
    ApplicationRunner seedClassroomData(MechanicRepository mechanicRepository,
                                        ServiceJobRepository jobRepository,
                                        ServiceRequestService requestService) {
        return args -> {
            if (mechanicRepository.count() == 0) {
                mechanicRepository.saveAll(List.of(
                        new Mechanic("Avery Chen", "Diagnostics and electrical"),
                        new Mechanic("Jordan Rivera", "Tires, steering, and suspension"),
                        new Mechanic("Morgan Patel", "Preventive maintenance")
                ));
            }
            if (jobRepository.count() > 0) {
                return;
            }
            List<Mechanic> mechanics = mechanicRepository.findAllByOrderByNameAsc();

            ServiceRequestForm oil = common("Sam Taylor", "sam@example.edu", "555-010-1100",
                    "1HGCM82633A004352", "EDU-101", "Honda", "Accord", 2021, ServiceType.OIL_CHANGE);
            oil.setMechanicId(mechanics.get(2).getId());
            oil.setOilType(OilType.FULL_SYNTHETIC);
            oil.setNumberOfQuarts(5);
            ServiceJob completed = requestService.submit(oil);
            requestService.updateStatus(completed.getId(), JobStatus.IN_PROGRESS);
            requestService.updateStatus(completed.getId(), JobStatus.COMPLETED);

            ServiceRequestForm tire = common("Riley Brooks", "riley@example.edu", "555-010-2200",
                    "1FTFW1ET4EFA12345", "LAB-204", "Ford", "F-150", 2020, ServiceType.TIRE_SERVICE);
            tire.setMechanicId(mechanics.get(1).getId());
            tire.setNumberOfTires(4);
            tire.setRotation(true);
            tire.setBalancing(true);
            tire.setAlignment(true);
            tire.setScheduledFor(LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0));
            requestService.submit(tire);

            ServiceRequestForm engine = common("Casey Morgan", "casey@example.edu", "555-010-3300",
                    "JH4KA9650MC012345", "JAVA-21", "Acura", "Legend", 1991, ServiceType.ENGINE_REPAIR);
            engine.setMechanicId(mechanics.get(0).getId());
            engine.setDiagnosticHours(new BigDecimal("1.50"));
            engine.setLaborRate(new BigDecimal("135.00"));
            engine.setPartsCost(new BigDecimal("320.00"));
            engine.setComplexity(RepairComplexity.HIGH);
            requestService.submit(engine);
        };
    }

    private ServiceRequestForm common(String name, String email, String phone, String vin, String plate,
                                      String make, String model, int year, ServiceType type) {
        ServiceRequestForm form = new ServiceRequestForm();
        form.setCustomerName(name);
        form.setEmail(email);
        form.setPhone(phone);
        form.setVin(vin);
        form.setLicensePlate(plate);
        form.setMake(make);
        form.setModel(model);
        form.setModelYear(year);
        form.setServiceType(type);
        return form;
    }
}
