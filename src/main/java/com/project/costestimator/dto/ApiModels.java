package com.project.costestimator.dto;

import com.project.costestimator.domain.enums.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}

    public record ProjectRequest(@NotBlank String code, @NotBlank String name, String description,
                                 @NotNull LocalDate plannedStartDate, @NotNull LocalDate plannedEndDate,
                                 @NotBlank String currencyCode, String languageCode, ProjectStatus status,
                                 @Positive BigDecimal usdTryRate, @Positive BigDecimal eurTryRate) {}
    public record ProjectSummary(UUID id, String code, String name, String description, LocalDate plannedStartDate,
                                 LocalDate plannedEndDate, Currency currency, String languageCode,
                                 BigDecimal usdTryRate, BigDecimal eurTryRate, ProjectStatus status) {}
    public record ProjectDetail(ProjectSummary project, List<EstimateView> estimates) {}
    public record EstimateRequest(@NotBlank String name, String description) {}
    public record EstimateView(UUID id, String name, String description, int versionNumber, EstimateStatus status,
                               List<WbsView> wbsItems, List<StaffView> projectStaff) {}
    public record WbsRequest(@NotBlank String code, @NotBlank String name, String description, Integer sequence, UUID parentId) {}
    public record WbsView(UUID id, String code, String name, String description, int sequence, UUID parentId, List<ActivityView> activities) {}
    public record ActivityRequest(@NotBlank String code, @NotBlank String name, String description, ActivityType type,
                                  @PositiveOrZero BigDecimal plannedQuantity, UnitOfMeasure quantityUnit,
                                  @PositiveOrZero BigDecimal plannedDuration, DurationUnit durationUnit,
                                  LocalDate plannedStartDate, LocalDate plannedEndDate) {}
    public record ActivityView(UUID id, String code, String name, ActivityType type, BigDecimal plannedQuantity,
                               UnitOfMeasure quantityUnit, BigDecimal plannedDuration, DurationUnit durationUnit,
                               LocalDate plannedStartDate, LocalDate plannedEndDate, List<AssignmentView> assignments) {}
    public record AssignmentRequest(@NotNull UUID resourceId, @Positive BigDecimal quantity,
                                    @PositiveOrZero BigDecimal plannedWork, WorkUnit workUnit,
                                    @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal utilizationRate,
                                    LocalDate startDate, LocalDate endDate, Boolean overtimeAllowed,
                                    PersonnelAssignmentType personnelAssignmentType,
                                    @PositiveOrZero BigDecimal operatingHoursPerDay,
                                    @PositiveOrZero BigDecimal standbyHoursPerDay,
                                    @PositiveOrZero BigDecimal requiredQuantity,
                                    @PositiveOrZero BigDecimal wastePercentage) {}
    public record AssignmentView(UUID id, UUID resourceId, String resourceName, String resourceType,
                                 BigDecimal quantity, BigDecimal plannedWork, WorkUnit workUnit, BigDecimal utilizationRate) {}
    public record CrewRequest(@NotNull UUID personnelResourceId, @NotBlank String roleName, @Positive BigDecimal quantity,
                              @Positive BigDecimal workingHoursPerDay, Boolean mandatory) {}
    public record CrewView(UUID id, UUID personnelResourceId, String personnelName, String roleName,
                           BigDecimal quantity, BigDecimal workingHoursPerDay, boolean mandatory) {}
    public record StaffRequest(@NotNull UUID personnelResourceId, @NotBlank String projectRole, @Positive BigDecimal quantity,
                               @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal allocationPercentage,
                               LocalDate startDate, LocalDate endDate) {}
    public record StaffView(UUID id, UUID personnelResourceId, String personnelName, String projectRole,
                            BigDecimal quantity, BigDecimal allocationPercentage, LocalDate startDate, LocalDate endDate) {}

    public record PersonnelRequest(@NotBlank String code, @NotBlank String name, String description,
                                   @NotBlank String profession, String roleName, SkillLevel skillLevel, Boolean genericResource) {}
    public record EquipmentRequest(@NotBlank String code, @NotBlank String name, String description,
                                   @NotBlank String equipmentType, String manufacturer, String model,
                                   @PositiveOrZero BigDecimal capacity, UnitOfMeasure capacityUnit, Boolean owned) {}
    public record MaterialRequest(@NotBlank String code, @NotBlank String name, String description,
                                  @NotBlank String materialType, @NotNull UnitOfMeasure defaultUnit) {}
    public record ResourceView(UUID id, String type, String code, String name, String description, ResourceStatus status,
                               String subtype, String roleName, SkillLevel skillLevel, Boolean genericResource,
                               String manufacturer, String model, BigDecimal capacity, UnitOfMeasure capacityUnit, Boolean owned,
                               UnitOfMeasure defaultUnit, List<CostView> costs, List<FuelView> fuelConsumptions) {}
    public record CostRequest(@NotNull CostCategory category, @NotBlank String name, @NotNull CalculationBasis calculationBasis,
                              @PositiveOrZero BigDecimal unitPrice, UnitOfMeasure unit, Boolean taxable,
                              @DecimalMin("0.0") BigDecimal taxRate, LocalDate validFrom, LocalDate validTo) {}
    public record CostView(UUID id, CostCategory category, String name, CalculationBasis calculationBasis,
                           BigDecimal unitPrice, UnitOfMeasure unit, boolean taxable, BigDecimal taxRate) {}
    public record FuelRequest(@NotNull FuelType fuelType, @PositiveOrZero BigDecimal consumptionPerHour,
                              @NotNull UnitOfMeasure consumptionUnit) {}
    public record FuelView(UUID id, FuelType fuelType, BigDecimal consumptionPerHour, UnitOfMeasure consumptionUnit) {}
}
