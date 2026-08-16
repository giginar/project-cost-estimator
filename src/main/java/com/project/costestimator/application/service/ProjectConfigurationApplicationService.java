package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.ProjectConfigurationUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.domain.CostCode;
import com.project.costestimator.domain.GeneralUnitPrice;
import com.project.costestimator.domain.Project;
import com.project.costestimator.dto.ApiModels.CostCodeRequest;
import com.project.costestimator.dto.ApiModels.CostCodeView;
import com.project.costestimator.dto.ApiModels.GeneralUnitPriceRequest;
import com.project.costestimator.dto.ApiModels.GeneralUnitPriceView;
import com.project.costestimator.exception.NotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ProjectConfigurationApplicationService implements ProjectConfigurationUseCase {
    private final ProjectRepositoryPort projects;
    private final ProjectFinder finder;

    public ProjectConfigurationApplicationService(ProjectRepositoryPort projects, ProjectFinder finder) {
        this.projects = projects;
        this.finder = finder;
    }

    @Override
    public List<GeneralUnitPriceView> listGeneralUnitPrices(UUID projectId) {
        return finder.requireProject(projectId).getGeneralUnitPrices().stream()
                .sorted(Comparator.comparing(GeneralUnitPrice::getCode))
                .map(this::toView)
                .toList();
    }

    @Override
    public GeneralUnitPriceView addGeneralUnitPrice(UUID projectId, GeneralUnitPriceRequest request) {
        Project project = finder.requireProject(projectId);
        ensureUniquePrice(project, null, request);
        GeneralUnitPrice price = new GeneralUnitPrice();
        price.setId(UUID.randomUUID());
        price.setProject(project);
        apply(price, request);
        project.getGeneralUnitPrices().add(price);
        projects.save(project);
        return toView(price);
    }

    @Override
    public GeneralUnitPriceView updateGeneralUnitPrice(UUID projectId, UUID priceId,
                                                       GeneralUnitPriceRequest request) {
        Project project = finder.requireProject(projectId);
        GeneralUnitPrice price = requirePrice(project, priceId);
        ensureUniquePrice(project, price, request);
        apply(price, request);
        projects.save(project);
        return toView(price);
    }

    @Override
    public void deleteGeneralUnitPrice(UUID projectId, UUID priceId) {
        Project project = finder.requireProject(projectId);
        if (!project.getGeneralUnitPrices().removeIf(price -> price.getId().equals(priceId))) {
            throw new NotFoundException("General unit price not found: " + priceId);
        }
        projects.save(project);
    }

    @Override
    public List<CostCodeView> listCostCodes(UUID projectId) {
        return finder.requireProject(projectId).getCostCodes().stream()
                .sorted(Comparator.comparing(CostCode::getCode))
                .map(this::toView)
                .toList();
    }

    @Override
    public CostCodeView addCostCode(UUID projectId, CostCodeRequest request) {
        Project project = finder.requireProject(projectId);
        ensureUniqueCostCode(project, null, request);
        CostCode code = new CostCode();
        code.setId(UUID.randomUUID());
        code.setProject(project);
        apply(code, request);
        project.getCostCodes().add(code);
        projects.save(project);
        return toView(code);
    }

    @Override
    public CostCodeView updateCostCode(UUID projectId, UUID costCodeId, CostCodeRequest request) {
        Project project = finder.requireProject(projectId);
        CostCode code = requireCostCode(project, costCodeId);
        ensureUniqueCostCode(project, code, request);
        apply(code, request);
        projects.save(project);
        return toView(code);
    }

    @Override
    public void deleteCostCode(UUID projectId, UUID costCodeId) {
        Project project = finder.requireProject(projectId);
        if (!project.getCostCodes().removeIf(code -> code.getId().equals(costCodeId))) {
            throw new NotFoundException("Cost code not found: " + costCodeId);
        }
        projects.save(project);
    }

    private void ensureUniquePrice(Project project, GeneralUnitPrice current,
                                   GeneralUnitPriceRequest request) {
        boolean duplicateCode = project.getGeneralUnitPrices().stream()
                .anyMatch(price -> price != current && price.getCode().equalsIgnoreCase(request.code().trim()));
        if (duplicateCode) {
            throw new IllegalArgumentException("General unit price code already exists: " + request.code());
        }
        boolean duplicateFuelType = project.getGeneralUnitPrices().stream()
                .anyMatch(price -> price != current && price.getFuelType() == request.fuelType());
        if (duplicateFuelType) {
            throw new IllegalArgumentException("A general unit price already exists for " + request.fuelType());
        }
    }

    private void ensureUniqueCostCode(Project project, CostCode current, CostCodeRequest request) {
        boolean duplicateCode = project.getCostCodes().stream()
                .anyMatch(code -> code != current && code.getCode().equalsIgnoreCase(request.code().trim()));
        if (duplicateCode) {
            throw new IllegalArgumentException("Cost code already exists: " + request.code());
        }
        boolean duplicateType = project.getCostCodes().stream()
                .anyMatch(code -> code != current && code.getType() == request.type());
        if (duplicateType) {
            throw new IllegalArgumentException("A cost code already exists for " + request.type());
        }
    }

    private GeneralUnitPrice requirePrice(Project project, UUID priceId) {
        return project.getGeneralUnitPrices().stream()
                .filter(price -> price.getId().equals(priceId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("General unit price not found: " + priceId));
    }

    private CostCode requireCostCode(Project project, UUID costCodeId) {
        return project.getCostCodes().stream()
                .filter(code -> code.getId().equals(costCodeId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cost code not found: " + costCodeId));
    }

    private void apply(GeneralUnitPrice price, GeneralUnitPriceRequest request) {
        price.setCode(request.code().trim());
        price.setName(request.name().trim());
        price.setFuelType(request.fuelType());
        price.setUnit(request.unit());
        price.setUnitPrice(request.unitPrice());
        price.setActive(request.active() == null || request.active());
    }

    private void apply(CostCode code, CostCodeRequest request) {
        code.setCode(request.code().trim());
        code.setName(request.name().trim());
        code.setType(request.type());
        code.setActive(request.active() == null || request.active());
    }

    private GeneralUnitPriceView toView(GeneralUnitPrice price) {
        return new GeneralUnitPriceView(
                price.getId(), price.getCode(), price.getName(), price.getFuelType(), price.getUnit(),
                price.getUnitPrice(), price.getProject().getCurrency().getCurrencyCode(), price.isActive());
    }

    private CostCodeView toView(CostCode code) {
        return new CostCodeView(code.getId(), code.getCode(), code.getName(), code.getType(), code.isActive());
    }
}
