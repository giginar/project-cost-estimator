package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.ProjectConfigurationUseCase;
import com.project.costestimator.dto.ApiModels.CostCodeRequest;
import com.project.costestimator.dto.ApiModels.CostCodeView;
import com.project.costestimator.dto.ApiModels.GeneralUnitPriceRequest;
import com.project.costestimator.dto.ApiModels.GeneralUnitPriceView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/settings")
@Tag(name = "Project configuration", description = "General unit prices and cost code master data")
public class ProjectConfigurationController {
    private final ProjectConfigurationUseCase configuration;

    public ProjectConfigurationController(ProjectConfigurationUseCase configuration) {
        this.configuration = configuration;
    }

    @GetMapping("/unit-prices")
    public List<GeneralUnitPriceView> listPrices(@PathVariable UUID projectId) {
        return configuration.listGeneralUnitPrices(projectId);
    }

    @PostMapping("/unit-prices")
    @ResponseStatus(HttpStatus.CREATED)
    public GeneralUnitPriceView addPrice(@PathVariable UUID projectId,
                                         @Valid @RequestBody GeneralUnitPriceRequest request) {
        return configuration.addGeneralUnitPrice(projectId, request);
    }

    @PutMapping("/unit-prices/{priceId}")
    public GeneralUnitPriceView updatePrice(@PathVariable UUID projectId, @PathVariable UUID priceId,
                                            @Valid @RequestBody GeneralUnitPriceRequest request) {
        return configuration.updateGeneralUnitPrice(projectId, priceId, request);
    }

    @DeleteMapping("/unit-prices/{priceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrice(@PathVariable UUID projectId, @PathVariable UUID priceId) {
        configuration.deleteGeneralUnitPrice(projectId, priceId);
    }

    @GetMapping("/cost-codes")
    public List<CostCodeView> listCostCodes(@PathVariable UUID projectId) {
        return configuration.listCostCodes(projectId);
    }

    @PostMapping("/cost-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public CostCodeView addCostCode(@PathVariable UUID projectId,
                                    @Valid @RequestBody CostCodeRequest request) {
        return configuration.addCostCode(projectId, request);
    }

    @PutMapping("/cost-codes/{costCodeId}")
    public CostCodeView updateCostCode(@PathVariable UUID projectId, @PathVariable UUID costCodeId,
                                       @Valid @RequestBody CostCodeRequest request) {
        return configuration.updateCostCode(projectId, costCodeId, request);
    }

    @DeleteMapping("/cost-codes/{costCodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCostCode(@PathVariable UUID projectId, @PathVariable UUID costCodeId) {
        configuration.deleteCostCode(projectId, costCodeId);
    }
}
