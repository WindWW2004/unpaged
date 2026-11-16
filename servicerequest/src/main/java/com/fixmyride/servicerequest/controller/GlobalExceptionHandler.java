package com.fixmyride.servicerequest.controller;

import com.fixmyride.servicerequest.exception.InvalidStatusTransitionException;
import com.fixmyride.servicerequest.exception.JobNotFoundException;
import com.fixmyride.servicerequest.exception.MechanicUnavailableException;
import com.fixmyride.servicerequest.exception.VehicleOwnershipException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(JobNotFoundException.class)
    public String notFound(JobNotFoundException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        model.addAttribute("errorTitle", "Service job not found");
        model.addAttribute("errorMessage", exception.getMessage());
        return "error";
    }

    @ExceptionHandler({InvalidStatusTransitionException.class, MechanicUnavailableException.class,
            VehicleOwnershipException.class, IllegalArgumentException.class})
    public String businessRule(RuntimeException exception, Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        model.addAttribute("errorTitle", "The request could not be completed");
        model.addAttribute("errorMessage", exception.getMessage());
        return "error";
    }
}
