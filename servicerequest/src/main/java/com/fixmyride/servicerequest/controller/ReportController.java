package com.fixmyride.servicerequest.controller;

import com.fixmyride.servicerequest.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("report", reportService.buildReport());
        return "reports";
    }
}
