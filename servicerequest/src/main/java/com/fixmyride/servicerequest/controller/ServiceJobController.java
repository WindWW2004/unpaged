package com.fixmyride.servicerequest.controller;

import com.fixmyride.servicerequest.domain.JobStatus;
import com.fixmyride.servicerequest.domain.OilType;
import com.fixmyride.servicerequest.domain.RepairComplexity;
import com.fixmyride.servicerequest.domain.ServiceJob;
import com.fixmyride.servicerequest.domain.ServiceType;
import com.fixmyride.servicerequest.processing.JobProcessor;
import com.fixmyride.servicerequest.service.MechanicDirectory;
import com.fixmyride.servicerequest.service.ServiceRequestService;
import com.fixmyride.servicerequest.web.ServiceRequestForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Locale;

@Controller
public class ServiceJobController {
    private final ServiceRequestService requestService;
    private final MechanicDirectory mechanicDirectory;
    private final JobProcessor jobProcessor;

    public ServiceJobController(ServiceRequestService requestService, MechanicDirectory mechanicDirectory,
                                JobProcessor jobProcessor) {
        this.requestService = requestService;
        this.mechanicDirectory = mechanicDirectory;
        this.jobProcessor = jobProcessor;
    }

    @ModelAttribute("serviceTypes")
    public ServiceType[] serviceTypes() {
        return ServiceType.values();
    }

    @ModelAttribute("oilTypes")
    public OilType[] oilTypes() {
        return OilType.values();
    }

    @ModelAttribute("complexities")
    public RepairComplexity[] complexities() {
        return RepairComplexity.values();
    }

    @ModelAttribute("mechanics")
    public Object mechanics() {
        return mechanicDirectory.availableMechanics();
    }

    @GetMapping("/jobs")
    public String list(@RequestParam(defaultValue = "") String query, Model model) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        List<ServiceJob> jobs = requestService.findAll().stream()
                .filter(job -> normalized.isBlank()
                        || job.getServiceCode().toLowerCase(Locale.ROOT).contains(normalized)
                        || job.getVehicle().getVin().toLowerCase(Locale.ROOT).contains(normalized)
                        || job.getVehicle().getOwner().getName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
        model.addAttribute("jobs", jobs);
        model.addAttribute("query", query);
        return "service-jobs";
    }

    @GetMapping("/jobs/request")
    public String requestForm(Model model) {
        if (!model.containsAttribute("serviceRequestForm")) {
            model.addAttribute("serviceRequestForm", new ServiceRequestForm());
        }
        return "service-job-form";
    }

    @PostMapping("/jobs")
    public String submit(@Valid @ModelAttribute ServiceRequestForm serviceRequestForm,
                         BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "service-job-form";
        }
        ServiceJob job = requestService.submit(serviceRequestForm);
        redirectAttributes.addFlashAttribute("successMessage",
                "Request " + job.getServiceCode() + " was created successfully.");
        return "redirect:/jobs/" + job.getId();
    }

    @GetMapping("/jobs/{id}")
    public String details(@PathVariable Long id, Model model) {
        ServiceJob job = requestService.findById(id);
        model.addAttribute("job", job);
        model.addAttribute("nextStatuses", job.getStatus().allowedNextStatuses());
        return "job-details";
    }

    @PostMapping("/jobs/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam JobStatus status,
                               RedirectAttributes redirectAttributes) {
        ServiceJob updated = requestService.updateStatus(id, status);
        redirectAttributes.addFlashAttribute("successMessage",
                updated.getServiceCode() + " moved to " + status.getDisplayName() + ".");
        return "redirect:/jobs/" + id;
    }

    @PostMapping("/jobs/process-next")
    public String processNext(RedirectAttributes redirectAttributes) {
        jobProcessor.processNext().ifPresentOrElse(
                job -> redirectAttributes.addFlashAttribute("successMessage",
                        job.getServiceCode() + " entered the service bay in sequential mode."),
                () -> redirectAttributes.addFlashAttribute("infoMessage", "No requested jobs are waiting."));
        return "redirect:/dashboard";
    }
}
