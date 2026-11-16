package com.fixmyride.servicerequest.service;

import com.fixmyride.servicerequest.domain.EngineRepair;
import com.fixmyride.servicerequest.domain.Mechanic;
import com.fixmyride.servicerequest.domain.OilChange;
import com.fixmyride.servicerequest.domain.ServiceJob;
import com.fixmyride.servicerequest.domain.TireService;
import com.fixmyride.servicerequest.domain.Vehicle;
import com.fixmyride.servicerequest.web.ServiceRequestForm;
import org.springframework.stereotype.Component;

@Component
public class ServiceJobFactory {
    private final ServiceCodeGenerator codeGenerator;

    public ServiceJobFactory(ServiceCodeGenerator codeGenerator) {
        this.codeGenerator = codeGenerator;
    }

    public ServiceJob create(ServiceRequestForm form, Vehicle vehicle, Mechanic mechanic) {
        String code = codeGenerator.next(form.getServiceType());
        return switch (form.getServiceType()) {
            case OIL_CHANGE -> new OilChange(code, vehicle, mechanic, form.getScheduledFor(),
                    form.getOilType(), form.getNumberOfQuarts(), form.getFilterCost());
            case TIRE_SERVICE -> new TireService(code, vehicle, mechanic, form.getScheduledFor(),
                    form.getNumberOfTires(), form.isRotation(), form.isBalancing(), form.isAlignment());
            case ENGINE_REPAIR -> new EngineRepair(code, vehicle, mechanic, form.getScheduledFor(),
                    form.getDiagnosticHours(), form.getLaborRate(), form.getPartsCost(), form.getComplexity());
        };
    }
}
