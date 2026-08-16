package com.project.costestimator.config;

import com.project.costestimator.application.port.in.*;
import com.project.costestimator.application.port.out.*;
import com.project.costestimator.application.service.*;
import com.project.costestimator.application.service.support.*;
import com.project.costestimator.domain.service.*;
import com.project.costestimator.domain.service.CostCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationServiceConfig {
    @Bean
    Clock applicationClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    DateRangePolicy dateRangePolicy() {
        return new DateRangePolicy();
    }

    @Bean
    CurrencyConverter currencyConverter() {
        return new CurrencyConverter();
    }

    @Bean
    WorkCalendarPolicy workCalendarPolicy() {
        return new WorkCalendarPolicy();
    }

    @Bean
    ActivitySchedulingPolicy activitySchedulingPolicy(WorkCalendarPolicy calendars) {
        return new ActivitySchedulingPolicy(calendars);
    }

    @Bean
    EquipmentEconomicsPolicy equipmentEconomicsPolicy() {
        return new EquipmentEconomicsPolicy();
    }

    @Bean
    ProjectFinder projectFinder(ProjectRepositoryPort projects) {
        return new ProjectFinder(projects);
    }

    @Bean
    ResourceFinder resourceFinder(ResourceRepositoryPort resources) {
        return new ResourceFinder(resources);
    }

    @Bean
    ProjectViewMapper projectViewMapper() {
        return new ProjectViewMapper();
    }

    @Bean
    ResourceViewMapper resourceViewMapper(EquipmentEconomicsPolicy economics) {
        return new ResourceViewMapper(economics);
    }

    @Bean
    ResourceRateSynchronizer resourceRateSynchronizer(CurrencyConverter currencies) {
        return new ResourceRateSynchronizer(currencies);
    }

    @Bean
    ProjectCurrencyUpdater projectCurrencyUpdater(CurrencyConverter currencies, Clock clock) {
        return new ProjectCurrencyUpdater(currencies, clock);
    }

    @Bean
    CostCalculator costCalculator() {
        return new com.project.costestimator.service.CostCalculator();
    }

    @Bean
    ProjectUseCase projectUseCase(ProjectRepositoryPort projects,
                                  ProjectFinder finder,
                                  ProjectViewMapper views,
                                  DateRangePolicy dateRanges,
                                  CurrencyConverter currencies,
                                  ProjectCurrencyUpdater currencyUpdater,
                                  Clock clock) {
        return new ProjectApplicationService(
                projects, finder, views, dateRanges, currencies, currencyUpdater, clock);
    }

    @Bean
    PlanningUseCase planningUseCase(ProjectRepositoryPort projects,
                                    ProjectFinder finder,
                                    ProjectViewMapper views,
                                    DateRangePolicy dateRanges,
                                    ActivitySchedulingPolicy scheduling) {
        return new PlanningApplicationService(projects, finder, views, dateRanges, scheduling);
    }

    @Bean
    AssignmentUseCase assignmentUseCase(ProjectRepositoryPort projects,
                                        ProjectFinder projectFinder,
                                        ResourceFinder resourceFinder,
                                        ProjectViewMapper views,
                                        ResourceRateSynchronizer rates,
                                        DateRangePolicy dateRanges,
                                        ActivitySchedulingPolicy scheduling) {
        return new AssignmentApplicationService(
                projects, projectFinder, resourceFinder, views, rates, dateRanges, scheduling);
    }

    @Bean
    BoqUseCase boqUseCase(ProjectRepositoryPort projects,
                          ProjectFinder finder,
                          ProjectViewMapper views,
                          CurrencyConverter currencies,
                          ActivitySchedulingPolicy scheduling) {
        return new BoqApplicationService(projects, finder, views, currencies, scheduling);
    }

    @Bean
    ResourceRateUseCase resourceRateUseCase(ProjectRepositoryPort projects,
                                            ProjectFinder projectFinder,
                                            ResourceFinder resourceFinder,
                                            ResourceRateSynchronizer rates,
                                            ProjectViewMapper views) {
        return new ResourceRateApplicationService(
                projects, projectFinder, resourceFinder, rates, views);
    }

    @Bean
    CostReportQuery costReportQuery(ProjectFinder projects, CostCalculator calculator) {
        return new CostReportApplicationService(projects, calculator);
    }

    @Bean
    CashFlowQuery cashFlowQuery(ProjectFinder projects, CostCalculator calculator,
                                CurrencyConverter currencies) {
        return new CashFlowApplicationService(projects, calculator, currencies);
    }

    @Bean
    ProjectConfigurationUseCase projectConfigurationUseCase(ProjectRepositoryPort projects,
                                                             ProjectFinder finder) {
        return new ProjectConfigurationApplicationService(projects, finder);
    }

    @Bean
    PricingUseCase pricingUseCase(ProjectRepositoryPort projects,
                                  ProjectFinder finder,
                                  ProjectViewMapper views,
                                  CostCalculator calculator,
                                  CurrencyConverter currencies) {
        return new PricingApplicationService(projects, finder, views, calculator, currencies);
    }

    @Bean
    ResourceCatalogUseCase resourceCatalogUseCase(ResourceRepositoryPort resources,
                                                   ProjectRepositoryPort projects,
                                                   ResourceFinder finder,
                                                   ResourceViewMapper views,
                                                   EquipmentEconomicsPolicy economics,
                                                   CurrencyConverter currencies) {
        return new ResourceCatalogApplicationService(
                resources, projects, finder, views, economics, currencies);
    }

    @Bean
    AuthenticationApplicationService authenticationApplicationService(
            UserRepositoryPort users,
            PasswordHashPort passwords,
            SecureTokenPort secureTokens,
            AuthenticationTokenStore tokenStore,
            MailDeliveryPort mail,
            Clock clock) {
        return new AuthenticationApplicationService(
                users, passwords, secureTokens, tokenStore, mail, clock);
    }

    @Bean
    MailOutboxQuery mailOutboxQuery(MailDeliveryPort mail) {
        return new MailOutboxApplicationService(mail);
    }
}
