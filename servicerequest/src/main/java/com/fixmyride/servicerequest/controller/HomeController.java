package com.fixmyride.servicerequest.controller;

import com.fixmyride.servicerequest.processing.JobProcessor;
import com.fixmyride.servicerequest.processing.WaitingJobQueue;
import com.fixmyride.servicerequest.service.ReportService;
import com.fixmyride.servicerequest.service.ServiceRequestService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    private final ServiceRequestService requestService;
    private final ReportService reportService;
    private final WaitingJobQueue waitingJobQueue;
    private final JobProcessor jobProcessor;

    public HomeController(ServiceRequestService requestService, ReportService reportService,
                          WaitingJobQueue waitingJobQueue, JobProcessor jobProcessor) {
        this.requestService = requestService;
        this.reportService = reportService;
        this.waitingJobQueue = waitingJobQueue;
        this.jobProcessor = jobProcessor;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("report", reportService.buildReport());
        model.addAttribute("recentJobs", requestService.findAll().stream().limit(5).toList());
        model.addAttribute("queue", waitingJobQueue.snapshot());
        model.addAttribute("processorMode", jobProcessor.modeDescription());
        return "index";
    }
}
