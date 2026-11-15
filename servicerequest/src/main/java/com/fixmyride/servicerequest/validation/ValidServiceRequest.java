package com.fixmyride.servicerequest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ServiceRequestFormValidator.class)
public @interface ValidServiceRequest {
    String message() default "The selected service options are incomplete.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
