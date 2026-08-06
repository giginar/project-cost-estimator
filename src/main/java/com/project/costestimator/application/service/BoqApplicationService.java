package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.BoqUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.application.service.support.ProjectViewMapper;
import com.project.costestimator.domain.Activity;
import com.project.costestimator.domain.BoqItem;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.WbsItem;
import com.project.costestimator.domain.service.ActivitySchedulingPolicy;
import com.project.costestimator.domain.service.CurrencyConverter;
import com.project.costestimator.dto.ApiModels.BoqRequest;
import com.project.costestimator.dto.ApiModels.BoqTraceabilityReport;
import com.project.costestimator.dto.ApiModels.BoqView;
import com.project.costestimator.exception.NotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class BoqApplicationService implements BoqUseCase {
    private final ProjectRepositoryPort projects;
    private final ProjectFinder finder;
    private final ProjectViewMapper views;
    private final CurrencyConverter currencies;
    private final ActivitySchedulingPolicy scheduling;

    public BoqApplicationService(ProjectRepositoryPort projects,
                                ProjectFinder finder,
                                ProjectViewMapper views,
                                CurrencyConverter currencies,
                                ActivitySchedulingPolicy scheduling) {
        this.projects = projects;
        this.finder = finder;
        this.views = views;
        this.currencies = currencies;
        this.scheduling = scheduling;
    }

    @Override
    public BoqView addBoqItem(UUID projectId, UUID estimateId, BoqRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        ensureUniqueCode(estimate, null, request.code());

        BoqItem item = new BoqItem();
        item.setId(UUID.randomUUID());
        item.setEstimateVersion(estimate);
        apply(item, estimate, request);
        estimate.getBoqItems().add(item);
        projects.save(estimate.getProject());
        return views.toBoqView(item);
    }

    @Override
    public List<BoqView> listBoqItems(UUID projectId, UUID estimateId) {
        return finder.requireEstimate(projectId, estimateId).getBoqItems().stream()
                .map(views::toBoqView)
                .toList();
    }

    @Override
    public BoqView updateBoqItem(UUID projectId, UUID estimateId, UUID boqId, BoqRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        BoqItem item = finder.requireBoqItem(estimate, boqId);
        ensureUniqueCode(estimate, item, request.code());
        apply(item, estimate, request);
        projects.save(estimate.getProject());
        return views.toBoqView(item);
    }

    @Override
    public void deleteBoqItem(UUID projectId, UUID estimateId, UUID boqId) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        boolean removed = estimate.getBoqItems().removeIf(item -> item.getId().equals(boqId));
        if (!removed) {
            throw new NotFoundException("BOQ item not found: " + boqId);
        }
        projects.save(estimate.getProject());
    }

    @Override
    public BoqTraceabilityReport boqTraceability(UUID projectId, UUID estimateId) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        List<BoqView> items = estimate.getBoqItems().stream().map(views::toBoqView).toList();
        int linkedItems = (int) estimate.getBoqItems().stream()
                .filter(item -> item.getActivity() != null)
                .count();
        BigDecimal total = estimate.getBoqItems().stream()
                .map(item -> projectCurrencyValue(estimate, item))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BoqTraceabilityReport(
                total, items.size(), linkedItems, items.size() - linkedItems, items);
    }

    private void ensureUniqueCode(EstimateVersion estimate, BoqItem currentItem, String code) {
        boolean duplicate = estimate.getBoqItems().stream()
                .anyMatch(item -> item != currentItem && item.getCode().equalsIgnoreCase(code));
        if (duplicate) {
            throw new IllegalArgumentException("BOQ code already exists: " + code);
        }
    }

    private void apply(BoqItem item, EstimateVersion estimate, BoqRequest request) {
        WbsItem wbs = finder.requireWbs(estimate, request.wbsId());
        Activity activity = request.activityId() == null
                ? null
                : finder.requireActivity(estimate, request.activityId());
        if (activity != null && activity.getWbsItem() != wbs) {
            throw new IllegalArgumentException("BOQ activity must belong to the selected WBS");
        }

        item.setCode(request.code().trim());
        item.setDescription(request.description().trim());
        item.setUnit(request.unit());
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());
        item.setCurrency(currencies.fromCode(request.currencyCode()));
        currencies.conversionRate(
                item.getCurrency(), estimate.getProject().getCurrency(),
                estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
        item.setWbsItem(wbs);
        item.setActivity(activity);

        if (activity != null) {
            activity.setPlannedQuantity(request.quantity());
            activity.setQuantityUnit(request.unit());
            if (activity.isAutoSchedule()) {
                scheduling.scheduleFromProduction(activity);
            }
            scheduling.applyDependencyConstraints(activity);
            scheduling.synchronizeAssignmentSchedule(activity);
            scheduling.rescheduleDependents(estimate, activity);
        }
    }

    private BigDecimal projectCurrencyValue(EstimateVersion estimate, BoqItem item) {
        BigDecimal value = item.getQuantity().multiply(item.getUnitPrice());
        BigDecimal conversionRate = currencies.conversionRate(
                item.getCurrency(), estimate.getProject().getCurrency(),
                estimate.getProject().getUsdTryRate(), estimate.getProject().getEurTryRate());
        return currencies.convert(value, conversionRate);
    }
}
