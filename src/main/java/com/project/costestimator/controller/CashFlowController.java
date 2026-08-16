package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.CashFlowQuery;
import com.project.costestimator.dto.ApiModels.CashFlowReport;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Cash flow", description = "Monthly planned income and expense report")
public class CashFlowController {
    private final CashFlowQuery cashFlows;

    public CashFlowController(CashFlowQuery cashFlows) {
        this.cashFlows = cashFlows;
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/cash-flow")
    public CashFlowReport cashFlow(@PathVariable UUID projectId, @PathVariable UUID estimateId) {
        return cashFlows.cashFlow(projectId, estimateId);
    }
}
