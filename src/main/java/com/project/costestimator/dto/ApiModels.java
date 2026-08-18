package com.project.costestimator.dto;

import com.project.costestimator.domain.CostBreakdown;
import com.project.costestimator.domain.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
                                  LocalDate plannedStartDate, LocalDate plannedEndDate,
                                  @Positive BigDecimal dailyProductionRate, Boolean autoSchedule) {}
    public record ActivityView(UUID id, String code, String name, ActivityType type, BigDecimal plannedQuantity,
                               UnitOfMeasure quantityUnit, BigDecimal plannedDuration, DurationUnit durationUnit,
                               LocalDate plannedStartDate, LocalDate plannedEndDate, BigDecimal dailyProductionRate,
                               boolean autoSchedule, List<DependencyView> dependencies, List<AssignmentView> assignments) {}
    public record ActivityPlanningRequest(@PositiveOrZero BigDecimal plannedQuantity, UnitOfMeasure quantityUnit,
                                          @Positive BigDecimal dailyProductionRate, Boolean autoSchedule,
                                          @NotNull LocalDate plannedStartDate) {}
    public record DependencyRequest(@NotNull UUID predecessorActivityId, @NotNull DependencyType type,
                                    @PositiveOrZero Integer lagDays) {}
    public record DependencyView(UUID id, UUID predecessorActivityId, String predecessorCode,
                                 String predecessorName, DependencyType type, int lagDays) {}
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
                                 BigDecimal quantity, BigDecimal plannedWork, WorkUnit workUnit, BigDecimal utilizationRate,
                                 LocalDate startDate, LocalDate endDate, boolean overtimeAllowed,
                                 PersonnelAssignmentType personnelAssignmentType,
                                 BigDecimal operatingHoursPerDay, BigDecimal standbyHoursPerDay,
                                 BigDecimal requiredQuantity, BigDecimal wastePercentage) {}
    public record CrewRequest(@NotNull UUID personnelResourceId, @NotBlank String roleName, @Positive BigDecimal quantity,
                              @Positive BigDecimal workingHoursPerDay, Boolean mandatory) {}
    public record CrewView(UUID id, UUID personnelResourceId, String personnelName, String roleName,
                           BigDecimal quantity, BigDecimal workingHoursPerDay, boolean mandatory) {}
    public record StaffRequest(@NotNull UUID personnelResourceId, @NotBlank String projectRole, @Positive BigDecimal quantity,
                               @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal allocationPercentage,
                               LocalDate startDate, LocalDate endDate) {}
    public record StaffView(UUID id, UUID personnelResourceId, String personnelName, String projectRole,
                            BigDecimal quantity, BigDecimal allocationPercentage, LocalDate startDate, LocalDate endDate) {}

    public record EstimateCostReport(CostBreakdown total, CostBreakdown projectLevel,
                                     List<WbsCostReport> wbsItems) {}
    public record WbsCostReport(UUID wbsId, String code, String name, CostBreakdown costs,
                                List<ActivityCostReport> activities) {}
    public record ActivityCostReport(UUID activityId, String code, String name, CostBreakdown costs) {}

    public record GeneralUnitPriceRequest(@NotBlank String code, @NotBlank String name,
                                          @NotNull FuelType fuelType, @NotNull UnitOfMeasure unit,
                                          @NotNull @PositiveOrZero BigDecimal unitPrice, Boolean active) {}
    public record GeneralUnitPriceView(UUID id, String code, String name, FuelType fuelType,
                                      UnitOfMeasure unit, BigDecimal unitPrice,
                                      String currencyCode, boolean active) {}
    public record CostCodeRequest(@NotBlank String code, @NotBlank String name,
                                  @NotNull CostCodeType type, Boolean active) {}
    public record CostCodeView(UUID id, String code, String name, CostCodeType type, boolean active) {}

    public record CostCodeAmount(UUID costCodeId, String code, String name, CostCodeType type,
                                 BigDecimal amount) {}
    public record CashFlowMonth(String month, BigDecimal income, BigDecimal expense,
                                BigDecimal netCashFlow, BigDecimal cumulativeCashFlow,
                                List<CostCodeAmount> costsByCode) {}
    public record CashFlowReport(BigDecimal totalIncome, BigDecimal totalExpense,
                                 BigDecimal netCashFlow, String revenueBasis,
                                 String timingBasis, List<CashFlowMonth> months) {}

    public record PersonnelRequest(@NotBlank String code, @NotBlank String name, String description,
                                   @NotBlank String profession, String roleName, SkillLevel skillLevel, Boolean genericResource) {}
    public record EquipmentRequest(@NotBlank String code, @NotBlank String name, String description,
                                   @NotBlank String equipmentType, String manufacturer, String model,
                                   @PositiveOrZero BigDecimal capacity, UnitOfMeasure capacityUnit, Boolean owned) {}
    public record MaterialRequest(@NotBlank String code, @NotBlank String name, String description,
                                  @NotBlank String materialType, @NotNull UnitOfMeasure defaultUnit) {}
    public record ResourceView(UUID id, String type, String code, String name, String description, ResourceStatus status,
                               boolean shared, UUID ownerProjectId,
                               String subtype, String roleName, SkillLevel skillLevel, Boolean genericResource,
                               String manufacturer, String model, BigDecimal capacity, UnitOfMeasure capacityUnit, Boolean owned,
                               UnitOfMeasure defaultUnit, EquipmentEconomicsView equipmentEconomics,
                               MaterialProcurementView materialProcurement, List<CostView> costs, List<FuelView> fuelConsumptions) {}
    public record ResourceSharingRequest(Boolean shared, @NotNull UUID projectId) {}
    public record CostRequest(@NotNull CostCategory category, @NotBlank String name, @NotNull CalculationBasis calculationBasis,
                               @PositiveOrZero BigDecimal unitPrice, UnitOfMeasure unit, Boolean taxable,
                               @DecimalMin("0.0") BigDecimal taxRate, LocalDate validFrom, LocalDate validTo,
                               String currencyCode) {}
    public record CostView(UUID id, CostCategory category, String name, CalculationBasis calculationBasis,
                           BigDecimal unitPrice, UnitOfMeasure unit, boolean taxable, BigDecimal taxRate,
                           LocalDate validFrom, LocalDate validTo, String currencyCode, boolean generated) {}
    public record EquipmentEconomicsRequest(Boolean owned, @PositiveOrZero BigDecimal acquisitionCost,
                                            @PositiveOrZero BigDecimal residualValue, @Positive Integer usefulLifeMonths,
                                            @PositiveOrZero BigDecimal maintenanceRatePercentage,
                                            @PositiveOrZero BigDecimal insuranceRatePercentage, @NotBlank String currencyCode) {}
    public record EquipmentEconomicsView(boolean owned, BigDecimal acquisitionCost, BigDecimal residualValue,
                                         Integer usefulLifeMonths, BigDecimal maintenanceRatePercentage,
                                         BigDecimal insuranceRatePercentage, String currencyCode,
                                         BigDecimal monthlyDepreciation, BigDecimal monthlyMaintenance, BigDecimal monthlyInsurance) {}
    public record MaterialProcurementRequest(String supplier, @PositiveOrZero Integer leadTimeDays,
                                             @PositiveOrZero BigDecimal minimumOrderQuantity,
                                             @DecimalMin("0.0") BigDecimal defaultWastePercentage) {}
    public record MaterialProcurementView(String supplier, Integer leadTimeDays, BigDecimal minimumOrderQuantity,
                                          BigDecimal defaultWastePercentage) {}
    public record FuelRequest(@NotNull FuelType fuelType, @PositiveOrZero BigDecimal consumptionPerHour,
                              @PositiveOrZero BigDecimal standbyConsumptionPerHour,
                              @NotNull UnitOfMeasure consumptionUnit) {}
    public record FuelView(UUID id, FuelType fuelType, BigDecimal consumptionPerHour,
                           BigDecimal standbyConsumptionPerHour, UnitOfMeasure consumptionUnit) {}
    public record EstimateRateRequest(@NotNull @PositiveOrZero BigDecimal unitPrice) {}
    public record EstimateRateView(UUID id, UUID resourceId, UUID sourceCostComponentId, CostCategory category,
                                   String name, CalculationBasis calculationBasis, BigDecimal unitPrice,
                                   UnitOfMeasure unit, boolean taxable, BigDecimal taxRate,
                                   LocalDate validFrom, LocalDate validTo) {}
    public record BoqRequest(@NotBlank String code, @NotBlank String description, @NotNull UnitOfMeasure unit,
                             @NotNull @PositiveOrZero BigDecimal quantity, @NotNull @PositiveOrZero BigDecimal unitPrice,
                             @NotBlank String currencyCode, @NotNull UUID wbsId, UUID activityId) {}
    public record BoqView(UUID id, String code, String description, UnitOfMeasure unit, BigDecimal quantity,
                          BigDecimal unitPrice, String currencyCode, BigDecimal totalPrice,
                          UUID wbsId, String wbsCode, String wbsName,
                          UUID activityId, String activityCode, String activityName) {}
    public record BoqTraceabilityReport(BigDecimal totalBoqValue, int itemCount, int linkedItemCount,
                                        int unlinkedItemCount, List<BoqView> items) {}
    public record BoqImportIssue(int rowNumber, String message) {}
    public record BoqImportResult(boolean preview, int itemCount, int createdWbsCount,
                                  List<BoqImportIssue> issues) {}
    public record ShiftRequest(@NotBlank String name, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                               @NotNull @Positive BigDecimal paidHours) {}
    public record ShiftView(UUID id, String name, LocalTime startTime, LocalTime endTime, BigDecimal paidHours) {}
    public record CalendarRequest(@NotBlank String name, @Min(1) @Max(7) int workingDaysPerWeek,
                                  @NotNull @Positive BigDecimal workingHoursPerDay,
                                  @NotEmpty List<@Valid ShiftRequest> shifts) {}
    public record CalendarView(UUID id, String name, int workingDaysPerWeek, BigDecimal workingHoursPerDay,
                               List<ShiftView> shifts) {}
    public record PricingRuleRequest(@NotNull PricingRuleType type, @NotBlank String name,
                                     @NotNull @DecimalMin("0.0") BigDecimal percentage,
                                     @NotNull PricingBase base, @PositiveOrZero Integer sequence, Boolean active) {}
    public record PricingRuleView(UUID id, PricingRuleType type, String name, BigDecimal percentage,
                                  PricingBase base, int sequence, boolean active) {}
    public record PricingLineView(UUID ruleId, PricingRuleType type, String name, BigDecimal percentage,
                                  PricingBase base, int sequence, BigDecimal baseAmount, BigDecimal amount) {}
    public record PricingSummaryView(BigDecimal estimatedCost, BigDecimal boqValue, BigDecimal nonProfitAdders,
                                     BigDecimal profit, BigDecimal salesPrice, BigDecimal grossProfit,
                                     BigDecimal netProfit, BigDecimal profitMarginPercentage,
                                     BigDecimal targetProfitMarginPercentage, BigDecimal boqVariance,
                                     String revenueBasis, List<PricingLineView> lines) {}
}
