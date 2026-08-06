package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.ProjectUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.service.support.ProjectCurrencyUpdater;
import com.project.costestimator.application.service.support.ProjectFinder;
import com.project.costestimator.application.service.support.ProjectViewMapper;
import com.project.costestimator.domain.EstimateVersion;
import com.project.costestimator.domain.Project;
import com.project.costestimator.domain.Shift;
import com.project.costestimator.domain.WbsItem;
import com.project.costestimator.domain.WorkCalendar;
import com.project.costestimator.domain.enums.EstimateStatus;
import com.project.costestimator.domain.enums.ProjectStatus;
import com.project.costestimator.domain.service.CurrencyConverter;
import com.project.costestimator.domain.service.DateRangePolicy;
import com.project.costestimator.dto.ApiModels.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public final class ProjectApplicationService implements ProjectUseCase {
    private static final String DEFAULT_CALENDAR_NAME = "Standard 5-day calendar";
    private static final String DEFAULT_SHIFT_NAME = "Day shift";

    private final ProjectRepositoryPort projects;
    private final ProjectFinder finder;
    private final ProjectViewMapper views;
    private final DateRangePolicy dateRanges;
    private final CurrencyConverter currencies;
    private final ProjectCurrencyUpdater currencyUpdater;
    private final Clock clock;

    public ProjectApplicationService(ProjectRepositoryPort projects,
                                     ProjectFinder finder,
                                     ProjectViewMapper views,
                                     DateRangePolicy dateRanges,
                                     CurrencyConverter currencies,
                                     ProjectCurrencyUpdater currencyUpdater,
                                     Clock clock) {
        this.projects = projects;
        this.finder = finder;
        this.views = views;
        this.dateRanges = dateRanges;
        this.currencies = currencies;
        this.currencyUpdater = currencyUpdater;
        this.clock = clock;
    }

    @Override
    public ProjectDetail create(ProjectRequest request) {
        dateRanges.validate(request.plannedStartDate(), request.plannedEndDate());
        Project project = new Project();
        project.setId(UUID.randomUUID());
        apply(project, request);
        project.setWorkCalendar(defaultCalendar(project));
        projects.save(project);
        return views.toDetail(project);
    }

    @Override
    public List<ProjectSummary> list() {
        return projects.findAll().stream().map(views::toSummary).toList();
    }

    @Override
    public ProjectDetail get(UUID projectId) {
        return views.toDetail(finder.requireProject(projectId));
    }

    @Override
    public ProjectDetail update(UUID projectId, ProjectRequest request) {
        dateRanges.validate(request.plannedStartDate(), request.plannedEndDate());
        Project project = finder.requireProject(projectId);
        Currency targetCurrency = currencies.fromCode(request.currencyCode());
        BigDecimal usdTryRate = request.usdTryRate() != null
                ? request.usdTryRate()
                : project.getUsdTryRate();
        BigDecimal eurTryRate = request.eurTryRate() != null
                ? request.eurTryRate()
                : project.getEurTryRate();

        if (!targetCurrency.equals(project.getCurrency())) {
            if (usdTryRate == null || eurTryRate == null) {
                throw new IllegalArgumentException(
                        "USD/TRY and EUR/TRY rates are required when changing currency");
            }
            currencyUpdater.convertProjectPrices(project, targetCurrency, usdTryRate, eurTryRate);
        }

        apply(project, request);
        project.setUsdTryRate(usdTryRate);
        project.setEurTryRate(eurTryRate);
        projects.save(project);
        return views.toDetail(project);
    }

    @Override
    public void delete(UUID projectId) {
        finder.requireProject(projectId);
        projects.deleteById(projectId);
    }

    @Override
    public EstimateView addEstimate(UUID projectId, EstimateRequest request) {
        Project project = finder.requireProject(projectId);
        EstimateVersion estimate = new EstimateVersion();
        estimate.setId(UUID.randomUUID());
        estimate.setName(request.name());
        estimate.setDescription(request.description());
        estimate.setVersionNumber(project.getEstimateVersions().size() + 1);
        estimate.setStatus(EstimateStatus.DRAFT);
        estimate.setCreatedAt(LocalDateTime.now(clock));
        estimate.setProject(project);
        project.getEstimateVersions().add(estimate);
        projects.save(project);
        return views.toEstimateView(estimate);
    }

    @Override
    public WbsView addWbs(UUID projectId, UUID estimateId, WbsRequest request) {
        EstimateVersion estimate = finder.requireEstimate(projectId, estimateId);
        WbsItem wbs = new WbsItem();
        wbs.setId(UUID.randomUUID());
        wbs.setCode(request.code());
        wbs.setName(request.name());
        wbs.setDescription(request.description());
        wbs.setSequence(request.sequence() == null
                ? estimate.getWbsItems().size() + 1
                : request.sequence());
        wbs.setEstimateVersion(estimate);

        if (request.parentId() != null) {
            WbsItem parent = finder.requireWbs(estimate, request.parentId());
            wbs.setParent(parent);
            parent.getChildren().add(wbs);
        }
        estimate.getWbsItems().add(wbs);
        projects.save(estimate.getProject());
        return views.toWbsView(wbs);
    }

    private WorkCalendar defaultCalendar(Project project) {
        WorkCalendar calendar = new WorkCalendar();
        calendar.setId(UUID.randomUUID());
        calendar.setName(DEFAULT_CALENDAR_NAME);
        calendar.setWorkingDaysPerWeek(5);
        calendar.setWorkingHoursPerDay(BigDecimal.valueOf(8));
        calendar.setProject(project);

        Shift shift = new Shift();
        shift.setId(UUID.randomUUID());
        shift.setName(DEFAULT_SHIFT_NAME);
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(17, 0));
        shift.setPaidHours(BigDecimal.valueOf(8));
        shift.setWorkCalendar(calendar);
        calendar.getShifts().add(shift);
        return calendar;
    }

    private void apply(Project project, ProjectRequest request) {
        project.setCode(request.code());
        project.setName(request.name());
        project.setDescription(request.description());
        project.setPlannedStartDate(request.plannedStartDate());
        project.setPlannedEndDate(request.plannedEndDate());
        project.setCurrency(currencies.fromCode(request.currencyCode()));
        project.setLanguageCode("tr".equalsIgnoreCase(request.languageCode()) ? "tr" : "en");
        if (request.usdTryRate() != null) {
            project.setUsdTryRate(request.usdTryRate());
        }
        if (request.eurTryRate() != null) {
            project.setEurTryRate(request.eurTryRate());
        }
        project.setStatus(request.status() == null ? ProjectStatus.DRAFT : request.status());
    }
}
