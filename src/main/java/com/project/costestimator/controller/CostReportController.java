package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.CostReportQuery;
import com.project.costestimator.domain.CostBreakdown;
import com.project.costestimator.dto.ApiModels.EstimateCostReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Cost reports", description = "Authoritative estimate cost calculations")
public class CostReportController {
    private final CostReportQuery costs;

    public CostReportController(CostReportQuery costs) {
        this.costs = costs;
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/cost")
    public CostBreakdown cost(@PathVariable UUID projectId, @PathVariable UUID estimateId) {
        return costs.projectCost(projectId, estimateId);
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/cost-report")
    public EstimateCostReport costReport(@PathVariable UUID projectId, @PathVariable UUID estimateId) {
        return costs.costReport(projectId, estimateId);
    }
}
