package com.project.costestimator.application.service.support;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.service.EquipmentEconomicsPolicy;
import com.project.costestimator.dto.ApiModels.*;

import java.util.List;

public final class ResourceViewMapper {
    private final EquipmentEconomicsPolicy economics;

    public ResourceViewMapper(EquipmentEconomicsPolicy economics) {
        this.economics = economics;
    }

    public ResourceView toView(Resource resource) {
        PersonnelResource personnel = resource instanceof PersonnelResource value ? value : null;
        EquipmentResource equipment = resource instanceof EquipmentResource value ? value : null;
        MaterialResource material = resource instanceof MaterialResource value ? value : null;

        List<FuelView> fuelConsumptions = equipment == null
                ? List.of()
                : equipment.getFuelConsumptions().stream().map(this::toFuelView).toList();

        return new ResourceView(
                resource.getId(), resourceType(resource), resource.getCode(), resource.getName(),
                resource.getDescription(), resource.getStatus(), resource.isShared(), resource.getOwnerProjectId(),
                subtype(resource), personnel == null ? null : personnel.getRoleName(),
                personnel == null ? null : personnel.getSkillLevel(),
                personnel == null ? null : personnel.isGenericResource(),
                equipment == null ? null : equipment.getManufacturer(),
                equipment == null ? null : equipment.getModel(),
                equipment == null ? null : equipment.getCapacity(),
                equipment == null ? null : equipment.getCapacityUnit(),
                equipment == null ? null : equipment.isOwned(),
                material == null ? null : material.getDefaultUnit(),
                equipment == null ? null : toEconomicsView(equipment),
                material == null ? null : toProcurementView(material),
                resource.getCostComponents().stream().map(this::toCostView).toList(),
                fuelConsumptions);
    }

    public CostView toCostView(CostComponent cost) {
        return new CostView(
                cost.getId(), cost.getCategory(), cost.getName(), cost.getCalculationBasis(),
                cost.getUnitPrice(), cost.getUnit(), cost.isTaxable(), cost.getTaxRate(),
                cost.getValidFrom(), cost.getValidTo(),
                cost.getCurrency() == null ? "USD" : cost.getCurrency().getCurrencyCode(),
                cost.isGenerated());
    }

    public FuelView toFuelView(FuelConsumption fuel) {
        return new FuelView(
                fuel.getId(), fuel.getFuelType(), fuel.getConsumptionPerHour(),
                fuel.getStandbyConsumptionPerHour(), fuel.getConsumptionUnit());
    }

    public EquipmentEconomicsView toEconomicsView(EquipmentResource equipment) {
        String currencyCode = equipment.getEconomicsCurrency() == null
                ? "USD"
                : equipment.getEconomicsCurrency().getCurrencyCode();
        return new EquipmentEconomicsView(
                equipment.isOwned(), equipment.getAcquisitionCost(), equipment.getResidualValue(),
                equipment.getUsefulLifeMonths(), equipment.getMaintenanceRatePercentage(),
                equipment.getInsuranceRatePercentage(), currencyCode,
                economics.monthlyDepreciation(equipment),
                economics.monthlyPercentage(equipment.getAcquisitionCost(), equipment.getMaintenanceRatePercentage()),
                economics.monthlyPercentage(equipment.getAcquisitionCost(), equipment.getInsuranceRatePercentage()));
    }

    public MaterialProcurementView toProcurementView(MaterialResource material) {
        return new MaterialProcurementView(
                material.getSupplier(), material.getLeadTimeDays(), material.getMinimumOrderQuantity(),
                material.getDefaultWastePercentage());
    }

    private String subtype(Resource resource) {
        return switch (resource) {
            case PersonnelResource personnel -> personnel.getProfession();
            case EquipmentResource equipment -> equipment.getEquipmentType();
            case MaterialResource material -> material.getMaterialType();
            default -> "";
        };
    }

    private String resourceType(Resource resource) {
        return resource.getClass().getSimpleName().replace("Resource", "").toUpperCase();
    }
}
