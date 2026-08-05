package com.project.costestimator.service;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.enums.*;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.exception.NotFoundException;
import com.project.costestimator.repository.ResourceRepository;
import com.project.costestimator.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.Currency;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ResourceService {
    private final ResourceRepository repository;
    private final ProjectRepository projects;

    public ResourceView createPersonnel(PersonnelRequest request) {
        return createPersonnel(request, null, true);
    }
    public ResourceView createPersonnel(PersonnelRequest request, UUID ownerProjectId, boolean shared) {
        var value = new PersonnelResource();
        initialize(value, request.code(), request.name(), request.description(), ownerProjectId, shared);
        value.setProfession(request.profession()); value.setRoleName(request.roleName()); value.setSkillLevel(request.skillLevel());
        value.setGenericResource(Boolean.TRUE.equals(request.genericResource()));
        return view(repository.save(value));
    }

    public ResourceView createEquipment(EquipmentRequest request) {
        return createEquipment(request, null, true);
    }
    public ResourceView createEquipment(EquipmentRequest request, UUID ownerProjectId, boolean shared) {
        var value = new EquipmentResource();
        initialize(value, request.code(), request.name(), request.description(), ownerProjectId, shared);
        value.setEquipmentType(request.equipmentType()); value.setManufacturer(request.manufacturer()); value.setModel(request.model());
        value.setCapacity(request.capacity()); value.setCapacityUnit(request.capacityUnit()); value.setOwned(Boolean.TRUE.equals(request.owned()));
        return view(repository.save(value));
    }

    public ResourceView createMaterial(MaterialRequest request) {
        return createMaterial(request, null, true);
    }
    public ResourceView createMaterial(MaterialRequest request, UUID ownerProjectId, boolean shared) {
        var value = new MaterialResource();
        initialize(value, request.code(), request.name(), request.description(), ownerProjectId, shared);
        value.setMaterialType(request.materialType()); value.setDefaultUnit(request.defaultUnit());
        return view(repository.save(value));
    }

    public List<ResourceView> list(String type, UUID projectId) {
        return repository.findAll().stream()
                .filter(resource -> projectId == null || isAvailable(resource, projectId))
                .filter(resource -> type == null || resource.getClass().getSimpleName().toLowerCase().startsWith(type.toLowerCase()))
                .map(this::view).toList();
    }
    public List<ResourceView> list(String type) { return list(type, null); }
    public ResourceView get(UUID id) { return view(require(id, Resource.class)); }
    public ResourceView updateSharing(UUID resourceId, ResourceSharingRequest request) {
        Resource resource = require(resourceId, Resource.class);
        if (resource.getOwnerProjectId() == null) throw new IllegalArgumentException("System-wide resources cannot be made project-specific");
        if (!resource.getOwnerProjectId().equals(request.projectId())) throw new IllegalArgumentException("Only the owning project can change resource sharing");
        resource.setShared(Boolean.TRUE.equals(request.shared()));
        return view(repository.save(resource));
    }
    public <T extends Resource> T requireAvailable(UUID id, UUID projectId, Class<T> expectedType) {
        T resource = require(id, expectedType);
        if (!isAvailable(resource, projectId)) throw new IllegalArgumentException("Resource is not available to this project");
        return resource;
    }
    public void delete(UUID id, UUID projectId) {
        Resource resource = require(id, Resource.class);
        if (resource.getOwnerProjectId() == null) throw new IllegalArgumentException("System-wide resources cannot be deleted");
        if (!resource.getOwnerProjectId().equals(projectId)) throw new IllegalArgumentException("Only the owning project can delete this resource");
        if (isUsed(id)) throw new IllegalArgumentException("Resource is in use; remove its activity, crew, and project staff assignments first");
        repository.deleteById(id);
    }
    public CostView addCost(UUID resourceId, CostRequest request) {
        Resource resource = require(resourceId, Resource.class);
        var cost = new CostComponent();
        cost.setId(UUID.randomUUID()); applyCost(cost, request); cost.setResource(resource);
        resource.getCostComponents().add(cost);
        return costView(cost);
    }

    public CostView updateCost(UUID resourceId, UUID costId, CostRequest request) {
        Resource resource = require(resourceId, Resource.class); CostComponent cost = resource.getCostComponents().stream().filter(value -> value.getId().equals(costId)).findFirst().orElseThrow(() -> new NotFoundException("Cost component not found: " + costId));
        applyCost(cost, request); repository.save(resource); return costView(cost);
    }

    public void deleteCost(UUID resourceId, UUID costId) {
        Resource resource = require(resourceId, Resource.class); if (!resource.getCostComponents().removeIf(value -> value.getId().equals(costId))) throw new NotFoundException("Cost component not found: " + costId); repository.save(resource);
    }

    public EquipmentEconomicsView updateEquipmentEconomics(UUID resourceId, EquipmentEconomicsRequest request) {
        EquipmentResource equipment = require(resourceId, EquipmentResource.class); equipment.setOwned(Boolean.TRUE.equals(request.owned()));
        equipment.setAcquisitionCost(request.acquisitionCost()); equipment.setResidualValue(request.residualValue()); equipment.setUsefulLifeMonths(request.usefulLifeMonths());
        equipment.setMaintenanceRatePercentage(request.maintenanceRatePercentage()); equipment.setInsuranceRatePercentage(request.insuranceRatePercentage()); equipment.setEconomicsCurrency(Currency.getInstance(request.currencyCode().toUpperCase()));
        equipment.getCostComponents().removeIf(cost -> cost.isGenerated());
        if (equipment.isOwned() && positive(equipment.getAcquisitionCost()) && equipment.getUsefulLifeMonths() != null) {
            addGeneratedCost(equipment, CostCategory.DEPRECIATION, "Calculated depreciation", monthlyDepreciation(equipment));
            addGeneratedCost(equipment, CostCategory.MAINTENANCE, "Calculated maintenance", monthlyPercentage(equipment.getAcquisitionCost(), equipment.getMaintenanceRatePercentage()));
            addGeneratedCost(equipment, CostCategory.INSURANCE, "Calculated insurance", monthlyPercentage(equipment.getAcquisitionCost(), equipment.getInsuranceRatePercentage()));
        }
        repository.save(equipment); return equipmentEconomics(equipment);
    }

    public MaterialProcurementView updateMaterialProcurement(UUID resourceId, MaterialProcurementRequest request) {
        MaterialResource material = require(resourceId, MaterialResource.class); material.setSupplier(request.supplier()); material.setLeadTimeDays(request.leadTimeDays());
        material.setMinimumOrderQuantity(request.minimumOrderQuantity()); material.setDefaultWastePercentage(request.defaultWastePercentage()); repository.save(material); return materialProcurement(material);
    }

    public FuelView addFuel(UUID resourceId, FuelRequest request) {
        EquipmentResource equipment = require(resourceId, EquipmentResource.class);
        var fuel = new FuelConsumption();
        fuel.setId(UUID.randomUUID()); fuel.setFuelType(request.fuelType()); fuel.setConsumptionPerHour(request.consumptionPerHour());
        fuel.setStandbyConsumptionPerHour(request.standbyConsumptionPerHour());
        fuel.setConsumptionUnit(request.consumptionUnit()); fuel.setEquipmentResource(equipment); equipment.getFuelConsumptions().add(fuel);
        return new FuelView(fuel.getId(), fuel.getFuelType(), fuel.getConsumptionPerHour(), fuel.getStandbyConsumptionPerHour(), fuel.getConsumptionUnit());
    }

    public <T extends Resource> T require(UUID id, Class<T> expectedType) {
        Resource resource = repository.findById(id).orElseThrow(() -> new NotFoundException("Resource not found: " + id));
        if (!expectedType.isInstance(resource)) throw new IllegalArgumentException("Resource " + id + " must be " + expectedType.getSimpleName());
        return expectedType.cast(resource);
    }

    private void initialize(Resource value, String code, String name, String description, UUID ownerProjectId, boolean shared) {
        if (!shared && ownerProjectId == null) throw new IllegalArgumentException("A project-specific resource requires an owner project");
        value.setId(UUID.randomUUID()); value.setCode(code); value.setName(name); value.setDescription(description); value.setStatus(ResourceStatus.ACTIVE);
        value.setOwnerProjectId(ownerProjectId); value.setShared(shared);
    }
    private boolean isAvailable(Resource resource, UUID projectId) { return resource.isShared() || projectId.equals(resource.getOwnerProjectId()); }
    private boolean isUsed(UUID resourceId) {
        return projects.findAll().stream().flatMap(project -> project.getEstimateVersions().stream()).anyMatch(estimate ->
                estimate.getProjectStaffAssignments().stream().anyMatch(staff -> staff.getPersonnelResource().getId().equals(resourceId))
                || estimate.getWbsItems().stream().flatMap(wbs -> wbs.getActivities().stream()).flatMap(activity -> activity.getResourceAssignments().stream()).anyMatch(assignment -> switch (assignment) {
                    case ActivityEquipmentAssignment equipment -> equipment.getEquipmentResource().getId().equals(resourceId) || equipment.getCrewAssignments().stream().anyMatch(crew -> crew.getPersonnelResource().getId().equals(resourceId));
                    case ActivityPersonnelAssignment personnel -> personnel.getPersonnelResource().getId().equals(resourceId);
                    case ActivityMaterialAssignment material -> material.getMaterialResource().getId().equals(resourceId);
                    default -> false;
                }));
    }
    private void applyCost(CostComponent cost, CostRequest request) { cost.setCategory(request.category()); cost.setName(request.name()); cost.setCalculationBasis(request.calculationBasis()); cost.setUnitPrice(request.unitPrice()); cost.setUnit(request.unit()); cost.setCurrency(Currency.getInstance(request.currencyCode() == null ? "USD" : request.currencyCode().toUpperCase())); cost.setTaxable(Boolean.TRUE.equals(request.taxable())); cost.setTaxRate(request.taxRate()); cost.setValidFrom(request.validFrom()); cost.setValidTo(request.validTo()); }
    private void addGeneratedCost(EquipmentResource equipment, CostCategory category, String name, BigDecimal amount) { var cost = new CostComponent(); cost.setId(UUID.randomUUID()); cost.setCategory(category); cost.setName(name); cost.setCalculationBasis(CalculationBasis.PER_MONTH); cost.setUnitPrice(amount); cost.setUnit(UnitOfMeasure.MONTH); cost.setCurrency(equipment.getEconomicsCurrency()); cost.setGenerated(true); cost.setResource(equipment); equipment.getCostComponents().add(cost); }
    private EquipmentEconomicsView equipmentEconomics(EquipmentResource equipment) { String currency = equipment.getEconomicsCurrency() == null ? "USD" : equipment.getEconomicsCurrency().getCurrencyCode(); return new EquipmentEconomicsView(equipment.isOwned(), equipment.getAcquisitionCost(), equipment.getResidualValue(), equipment.getUsefulLifeMonths(), equipment.getMaintenanceRatePercentage(), equipment.getInsuranceRatePercentage(), currency, monthlyDepreciation(equipment), monthlyPercentage(equipment.getAcquisitionCost(), equipment.getMaintenanceRatePercentage()), monthlyPercentage(equipment.getAcquisitionCost(), equipment.getInsuranceRatePercentage())); }
    private MaterialProcurementView materialProcurement(MaterialResource material) { return new MaterialProcurementView(material.getSupplier(), material.getLeadTimeDays(), material.getMinimumOrderQuantity(), material.getDefaultWastePercentage()); }
    private BigDecimal monthlyDepreciation(EquipmentResource equipment) { if (!positive(equipment.getAcquisitionCost()) || equipment.getUsefulLifeMonths() == null || equipment.getUsefulLifeMonths() <= 0) return BigDecimal.ZERO; return equipment.getAcquisitionCost().subtract(zero(equipment.getResidualValue())).max(BigDecimal.ZERO).divide(BigDecimal.valueOf(equipment.getUsefulLifeMonths()), 4, java.math.RoundingMode.HALF_UP); }
    private BigDecimal monthlyPercentage(BigDecimal value, BigDecimal annualRate) { if (!positive(value) || !positive(annualRate)) return BigDecimal.ZERO; return value.multiply(annualRate).divide(BigDecimal.valueOf(1200), 4, java.math.RoundingMode.HALF_UP); }
    private boolean positive(BigDecimal value) { return value != null && value.signum() > 0; }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private ResourceView view(Resource resource) {
        String subtype = switch (resource) {
            case PersonnelResource p -> p.getProfession();
            case EquipmentResource e -> e.getEquipmentType();
            case MaterialResource m -> m.getMaterialType();
            default -> "";
        };
        String type = resource.getClass().getSimpleName().replace("Resource", "").toUpperCase();
        String roleName = resource instanceof PersonnelResource personnel ? personnel.getRoleName() : null;
        com.project.costestimator.domain.enums.SkillLevel skillLevel = resource instanceof PersonnelResource personnel ? personnel.getSkillLevel() : null;
        Boolean genericResource = resource instanceof PersonnelResource personnel ? personnel.isGenericResource() : null;
        String manufacturer = resource instanceof EquipmentResource equipment ? equipment.getManufacturer() : null;
        String model = resource instanceof EquipmentResource equipment ? equipment.getModel() : null;
        java.math.BigDecimal capacity = resource instanceof EquipmentResource equipment ? equipment.getCapacity() : null;
        com.project.costestimator.domain.enums.UnitOfMeasure capacityUnit = resource instanceof EquipmentResource equipment ? equipment.getCapacityUnit() : null;
        Boolean owned = resource instanceof EquipmentResource equipment ? equipment.isOwned() : null;
        com.project.costestimator.domain.enums.UnitOfMeasure defaultUnit = resource instanceof MaterialResource material ? material.getDefaultUnit() : null;
        List<FuelView> fuelConsumptions = resource instanceof EquipmentResource equipment
                ? equipment.getFuelConsumptions().stream().map(fuel -> new FuelView(fuel.getId(), fuel.getFuelType(), fuel.getConsumptionPerHour(), fuel.getStandbyConsumptionPerHour(), fuel.getConsumptionUnit())).toList()
                : List.of();
        EquipmentEconomicsView economics = resource instanceof EquipmentResource equipment ? equipmentEconomics(equipment) : null;
        MaterialProcurementView procurement = resource instanceof MaterialResource material ? materialProcurement(material) : null;
        return new ResourceView(resource.getId(), type, resource.getCode(), resource.getName(), resource.getDescription(),
                resource.getStatus(), resource.isShared(), resource.getOwnerProjectId(), subtype, roleName, skillLevel, genericResource, manufacturer, model, capacity, capacityUnit, owned,
                defaultUnit, economics, procurement, resource.getCostComponents().stream().map(this::costView).toList(), fuelConsumptions);
    }
    private CostView costView(CostComponent c) { return new CostView(c.getId(), c.getCategory(), c.getName(), c.getCalculationBasis(), c.getUnitPrice(), c.getUnit(), c.isTaxable(), c.getTaxRate(), c.getValidFrom(), c.getValidTo(), c.getCurrency() == null ? "USD" : c.getCurrency().getCurrencyCode(), c.isGenerated()); }
}
