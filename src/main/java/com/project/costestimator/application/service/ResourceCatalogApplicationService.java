package com.project.costestimator.application.service;

import com.project.costestimator.application.port.in.ResourceCatalogUseCase;
import com.project.costestimator.application.port.out.ProjectRepositoryPort;
import com.project.costestimator.application.port.out.ResourceRepositoryPort;
import com.project.costestimator.application.service.support.ResourceFinder;
import com.project.costestimator.application.service.support.ResourceViewMapper;
import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.enums.CalculationBasis;
import com.project.costestimator.domain.enums.CostCategory;
import com.project.costestimator.domain.enums.ResourceStatus;
import com.project.costestimator.domain.enums.UnitOfMeasure;
import com.project.costestimator.domain.service.CurrencyConverter;
import com.project.costestimator.domain.service.EquipmentEconomicsPolicy;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.exception.NotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class ResourceCatalogApplicationService implements ResourceCatalogUseCase {
    private final ResourceRepositoryPort resources;
    private final ProjectRepositoryPort projects;
    private final ResourceFinder finder;
    private final ResourceViewMapper views;
    private final EquipmentEconomicsPolicy economics;
    private final CurrencyConverter currencies;

    public ResourceCatalogApplicationService(ResourceRepositoryPort resources,
                                             ProjectRepositoryPort projects,
                                             ResourceFinder finder,
                                             ResourceViewMapper views,
                                             EquipmentEconomicsPolicy economics,
                                             CurrencyConverter currencies) {
        this.resources = resources;
        this.projects = projects;
        this.finder = finder;
        this.views = views;
        this.economics = economics;
        this.currencies = currencies;
    }

    @Override
    public ResourceView createPersonnel(PersonnelRequest request) {
        return createPersonnel(request, null, true);
    }

    @Override
    public ResourceView createPersonnel(PersonnelRequest request, UUID ownerProjectId, boolean shared) {
        PersonnelResource personnel = new PersonnelResource();
        initialize(personnel, request.code(), request.name(), request.description(), ownerProjectId, shared);
        personnel.setProfession(request.profession());
        personnel.setRoleName(request.roleName());
        personnel.setSkillLevel(request.skillLevel());
        personnel.setGenericResource(Boolean.TRUE.equals(request.genericResource()));
        return views.toView(resources.save(personnel));
    }

    @Override
    public ResourceView createEquipment(EquipmentRequest request) {
        return createEquipment(request, null, true);
    }

    @Override
    public ResourceView createEquipment(EquipmentRequest request, UUID ownerProjectId, boolean shared) {
        EquipmentResource equipment = new EquipmentResource();
        initialize(equipment, request.code(), request.name(), request.description(), ownerProjectId, shared);
        equipment.setEquipmentType(request.equipmentType());
        equipment.setManufacturer(request.manufacturer());
        equipment.setModel(request.model());
        equipment.setCapacity(request.capacity());
        equipment.setCapacityUnit(request.capacityUnit());
        equipment.setOwned(Boolean.TRUE.equals(request.owned()));
        return views.toView(resources.save(equipment));
    }

    @Override
    public ResourceView createMaterial(MaterialRequest request) {
        return createMaterial(request, null, true);
    }

    @Override
    public ResourceView createMaterial(MaterialRequest request, UUID ownerProjectId, boolean shared) {
        MaterialResource material = new MaterialResource();
        initialize(material, request.code(), request.name(), request.description(), ownerProjectId, shared);
        material.setMaterialType(request.materialType());
        material.setDefaultUnit(request.defaultUnit());
        return views.toView(resources.save(material));
    }

    @Override
    public List<ResourceView> list(String type, UUID projectId) {
        return resources.findAll().stream()
                .filter(resource -> projectId == null || finder.isAvailable(resource, projectId))
                .filter(resource -> type == null
                        || resource.getClass().getSimpleName().toLowerCase().startsWith(type.toLowerCase()))
                .map(views::toView)
                .toList();
    }

    @Override
    public ResourceView get(UUID resourceId) {
        return views.toView(finder.require(resourceId, Resource.class));
    }

    @Override
    public ResourceView updateSharing(UUID resourceId, ResourceSharingRequest request) {
        Resource resource = finder.require(resourceId, Resource.class);
        if (resource.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("System-wide resources cannot be made project-specific");
        }
        if (!resource.getOwnerProjectId().equals(request.projectId())) {
            throw new IllegalArgumentException("Only the owning project can change resource sharing");
        }
        resource.setShared(Boolean.TRUE.equals(request.shared()));
        return views.toView(resources.save(resource));
    }

    @Override
    public void delete(UUID resourceId, UUID projectId) {
        Resource resource = finder.require(resourceId, Resource.class);
        if (resource.getOwnerProjectId() == null) {
            throw new IllegalArgumentException("System-wide resources cannot be deleted");
        }
        if (!resource.getOwnerProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Only the owning project can delete this resource");
        }
        if (isUsed(resourceId)) {
            throw new IllegalArgumentException(
                    "Resource is in use; remove its activity, crew, and project staff assignments first");
        }
        resources.deleteById(resourceId);
    }

    @Override
    public CostView addCost(UUID resourceId, CostRequest request) {
        Resource resource = finder.require(resourceId, Resource.class);
        CostComponent cost = new CostComponent();
        cost.setId(UUID.randomUUID());
        applyCost(cost, request);
        cost.setResource(resource);
        resource.getCostComponents().add(cost);
        resources.save(resource);
        return views.toCostView(cost);
    }

    @Override
    public CostView updateCost(UUID resourceId, UUID costId, CostRequest request) {
        Resource resource = finder.require(resourceId, Resource.class);
        CostComponent cost = resource.getCostComponents().stream()
                .filter(candidate -> candidate.getId().equals(costId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cost component not found: " + costId));
        applyCost(cost, request);
        resources.save(resource);
        return views.toCostView(cost);
    }

    @Override
    public void deleteCost(UUID resourceId, UUID costId) {
        Resource resource = finder.require(resourceId, Resource.class);
        boolean removed = resource.getCostComponents().removeIf(cost -> cost.getId().equals(costId));
        if (!removed) {
            throw new NotFoundException("Cost component not found: " + costId);
        }
        resources.save(resource);
    }

    @Override
    public EquipmentEconomicsView updateEquipmentEconomics(UUID resourceId,
                                                            EquipmentEconomicsRequest request) {
        EquipmentResource equipment = finder.require(resourceId, EquipmentResource.class);
        equipment.setOwned(Boolean.TRUE.equals(request.owned()));
        equipment.setAcquisitionCost(request.acquisitionCost());
        equipment.setResidualValue(request.residualValue());
        equipment.setUsefulLifeMonths(request.usefulLifeMonths());
        equipment.setMaintenanceRatePercentage(request.maintenanceRatePercentage());
        equipment.setInsuranceRatePercentage(request.insuranceRatePercentage());
        equipment.setEconomicsCurrency(currencies.fromCode(request.currencyCode()));
        equipment.getCostComponents().removeIf(CostComponent::isGenerated);
        addGeneratedCosts(equipment);
        resources.save(equipment);
        return views.toEconomicsView(equipment);
    }

    @Override
    public MaterialProcurementView updateMaterialProcurement(UUID resourceId,
                                                             MaterialProcurementRequest request) {
        MaterialResource material = finder.require(resourceId, MaterialResource.class);
        material.setSupplier(request.supplier());
        material.setLeadTimeDays(request.leadTimeDays());
        material.setMinimumOrderQuantity(request.minimumOrderQuantity());
        material.setDefaultWastePercentage(request.defaultWastePercentage());
        resources.save(material);
        return views.toProcurementView(material);
    }

    @Override
    public FuelView addFuel(UUID resourceId, FuelRequest request) {
        EquipmentResource equipment = finder.require(resourceId, EquipmentResource.class);
        FuelConsumption fuel = new FuelConsumption();
        fuel.setId(UUID.randomUUID());
        fuel.setFuelType(request.fuelType());
        fuel.setConsumptionPerHour(request.consumptionPerHour());
        fuel.setStandbyConsumptionPerHour(request.standbyConsumptionPerHour());
        fuel.setConsumptionUnit(request.consumptionUnit());
        fuel.setEquipmentResource(equipment);
        equipment.getFuelConsumptions().add(fuel);
        resources.save(equipment);
        return views.toFuelView(fuel);
    }

    private void initialize(Resource resource, String code, String name, String description,
                            UUID ownerProjectId, boolean shared) {
        if (!shared && ownerProjectId == null) {
            throw new IllegalArgumentException("A project-specific resource requires an owner project");
        }
        resource.setId(UUID.randomUUID());
        resource.setCode(code);
        resource.setName(name);
        resource.setDescription(description);
        resource.setStatus(ResourceStatus.ACTIVE);
        resource.setOwnerProjectId(ownerProjectId);
        resource.setShared(shared);
    }

    private boolean isUsed(UUID resourceId) {
        return projects.findAll().stream()
                .flatMap(project -> project.getEstimateVersions().stream())
                .anyMatch(estimate -> usedByStaff(estimate, resourceId)
                        || usedByActivity(estimate, resourceId));
    }

    private boolean usedByStaff(EstimateVersion estimate, UUID resourceId) {
        return estimate.getProjectStaffAssignments().stream()
                .anyMatch(staff -> staff.getPersonnelResource().getId().equals(resourceId));
    }

    private boolean usedByActivity(EstimateVersion estimate, UUID resourceId) {
        return estimate.getWbsItems().stream()
                .flatMap(wbs -> wbs.getActivities().stream())
                .flatMap(activity -> activity.getResourceAssignments().stream())
                .anyMatch(assignment -> assignmentUses(assignment, resourceId));
    }

    private boolean assignmentUses(ResourceAssignment assignment, UUID resourceId) {
        return switch (assignment) {
            case ActivityEquipmentAssignment equipment ->
                    equipment.getEquipmentResource().getId().equals(resourceId)
                            || equipment.getCrewAssignments().stream()
                            .anyMatch(crew -> crew.getPersonnelResource().getId().equals(resourceId));
            case ActivityPersonnelAssignment personnel ->
                    personnel.getPersonnelResource().getId().equals(resourceId);
            case ActivityMaterialAssignment material ->
                    material.getMaterialResource().getId().equals(resourceId);
            default -> false;
        };
    }

    private void applyCost(CostComponent cost, CostRequest request) {
        cost.setCategory(request.category());
        cost.setName(request.name());
        cost.setCalculationBasis(request.calculationBasis());
        cost.setUnitPrice(request.unitPrice());
        cost.setUnit(request.unit());
        cost.setCurrency(currencies.fromCode(
                request.currencyCode() == null ? "USD" : request.currencyCode()));
        cost.setTaxable(Boolean.TRUE.equals(request.taxable()));
        cost.setTaxRate(request.taxRate());
        cost.setValidFrom(request.validFrom());
        cost.setValidTo(request.validTo());
    }

    private void addGeneratedCosts(EquipmentResource equipment) {
        if (!equipment.isOwned() || !isPositive(equipment.getAcquisitionCost())
                || equipment.getUsefulLifeMonths() == null) {
            return;
        }
        addGeneratedCost(
                equipment, CostCategory.DEPRECIATION, "Calculated depreciation",
                economics.monthlyDepreciation(equipment));
        addGeneratedCost(
                equipment, CostCategory.MAINTENANCE, "Calculated maintenance",
                economics.monthlyPercentage(
                        equipment.getAcquisitionCost(), equipment.getMaintenanceRatePercentage()));
        addGeneratedCost(
                equipment, CostCategory.INSURANCE, "Calculated insurance",
                economics.monthlyPercentage(
                        equipment.getAcquisitionCost(), equipment.getInsuranceRatePercentage()));
    }

    private void addGeneratedCost(EquipmentResource equipment, CostCategory category,
                                  String name, BigDecimal amount) {
        CostComponent cost = new CostComponent();
        cost.setId(UUID.randomUUID());
        cost.setCategory(category);
        cost.setName(name);
        cost.setCalculationBasis(CalculationBasis.PER_MONTH);
        cost.setUnitPrice(amount);
        cost.setUnit(UnitOfMeasure.MONTH);
        cost.setCurrency(equipment.getEconomicsCurrency());
        cost.setGenerated(true);
        cost.setResource(equipment);
        equipment.getCostComponents().add(cost);
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
