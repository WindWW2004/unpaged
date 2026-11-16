package com.fixmyride.servicerequest.validation;

import com.fixmyride.servicerequest.domain.ServiceType;
import com.fixmyride.servicerequest.web.ServiceRequestForm;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class ServiceRequestFormValidator implements ConstraintValidator<ValidServiceRequest, ServiceRequestForm> {
    @Override
    public boolean isValid(ServiceRequestForm form, ConstraintValidatorContext context) {
        if (form == null || form.getServiceType() == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();
        if (form.getScheduledFor() != null && form.getScheduledFor().isBefore(java.time.LocalDateTime.now())) {
            addViolation(context, "scheduledFor", "Appointment time must be in the future.");
            valid = false;
        }

        if (form.getServiceType() == ServiceType.OIL_CHANGE) {
            valid &= require(context, form.getOilType() != null, "oilType", "Choose an oil type.");
            valid &= require(context, form.getNumberOfQuarts() != null, "numberOfQuarts", "Enter the number of quarts.");
            valid &= require(context, form.getFilterCost() != null, "filterCost", "Enter the filter cost.");
        } else if (form.getServiceType() == ServiceType.TIRE_SERVICE) {
            valid &= require(context, form.getNumberOfTires() != null, "numberOfTires", "Enter the number of tires.");
        } else if (form.getServiceType() == ServiceType.ENGINE_REPAIR) {
            valid &= require(context, positive(form.getDiagnosticHours()), "diagnosticHours", "Diagnostic hours must be positive.");
            valid &= require(context, positive(form.getLaborRate()), "laborRate", "Labor rate must be positive.");
            valid &= require(context, form.getPartsCost() != null && form.getPartsCost().signum() >= 0,
                    "partsCost", "Parts cost cannot be negative.");
            valid &= require(context, form.getComplexity() != null, "complexity", "Choose a repair complexity.");
        }
        return valid;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean require(ConstraintValidatorContext context, boolean condition, String field, String message) {
        if (!condition) {
            addViolation(context, field, message);
        }
        return condition;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
