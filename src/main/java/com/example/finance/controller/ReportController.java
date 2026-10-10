package com.example.finance.controller;

import com.example.finance.dto.report.MonthlyReportResponse;
import com.example.finance.dto.report.YearlyReportResponse;
import com.example.finance.security.CustomUserDetails;
import com.example.finance.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Income and expense reports for the authenticated user.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    /**
     * @param reportService report aggregation
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * @param user  authenticated user
     * @param year  calendar year
     * @param month 1-12
     * @return monthly totals by category
     */
    @GetMapping("/monthly/{year}/{month}")
    public MonthlyReportResponse monthly(@AuthenticationPrincipal CustomUserDetails user, @PathVariable int year,
                                         @PathVariable int month) {
        return reportService.monthly(user.getId(), year, month);
    }

    /**
     * @param user authenticated user
     * @param year calendar year
     * @return yearly totals by category
     */
    @GetMapping("/yearly/{year}")
    public YearlyReportResponse yearly(@AuthenticationPrincipal CustomUserDetails user, @PathVariable int year) {
        return reportService.yearly(user.getId(), year);
    }
}
