package com.project.costestimator.service;

import com.project.costestimator.domain.*;
import com.project.costestimator.domain.Resource;
import com.project.costestimator.domain.enums.ResourceStatus;
import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.exception.NotFoundException;
import com.project.costestimator.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResourceService {
    private final ResourceRepository repository;

    public ResourceView createPersonnel(PersonnelRequest request) {
        var value = new PersonnelResource();
        initialize(value, request.code(), request.name(), request.description());
        value.setProfession(request.profession()); value.setRoleName(request.roleName()); value.setSkillLevel(request.skillLevel());
        value.setGenericResource(Boolean.TRUE.equals(request.genericResource()));
        return view(repository.save(value));
    }

    public ResourceView createEquipment(EquipmentRequest request) {
        var value = new EquipmentResource();
        initialize(value, request.code(), request.name(), request.description());
        value.setEquipmentType(request.equipmentType()); value.setManufacturer(request.manufacturer()); value.setModel(request.model());
        value.setCapacity(request.capacity()); value.setCapacityUnit(request.capacityUnit()); value.setOwned(Boolean.TRUE.equals(request.owned()));
        return view(repository.save(value));
    }

    public ResourceView createMaterial(MaterialRequest request) {
        var value = new MaterialResource();
        initialize(value, request.code(), request.name(), request.description());
        value.setMaterialType(request.materialType()); value.setDefaultUnit(request.defaultUnit());
        return view(repository.save(value));
    }

    public List<ResourceView> list(String type) {
        return repository.findAll().stream().filter(r -> type == null || r.getClass().getSimpleName().toLowerCase().startsWith(type.toLowerCase())).map(this::view).toList();
    }
    public ResourceView get(UUID id) { return view(require(id, Resource.class)); }
    public void delete(UUID id) { require(id, Resource.class); repository.deleteById(id); }

    public CostView addCost(UUID resourceId, CostRequest request) {
        Resource resource = require(resourceId, Resource.class);
        var cost = new CostComponent();
        cost.setId(UUID.randomUUID()); cost.setCategory(request.category()); cost.setName(request.name());
        cost.setCalculationBasis(request.calculationBasis()); cost.setUnitPrice(request.unitPrice()); cost.setUnit(request.unit());
        cost.setTaxable(Boolean.TRUE.equals(request.taxable())); cost.setTaxRate(request.taxRate());
        cost.setValidFrom(request.validFrom()); cost.setValidTo(request.validTo()); cost.setResource(resource);
        resource.getCostComponents().add(cost);
        return costView(cost);
    }

    public FuelView addFuel(UUID resourceId, FuelRequest request) {
        EquipmentResource equipment = require(resourceId, EquipmentResource.class);
        var fuel = new FuelConsumption();
        fuel.setId(UUID.randomUUID()); fuel.setFuelType(request.fuelType()); fuel.setConsumptionPerHour(request.consumptionPerHour());
        fuel.setConsumptionUnit(request.consumptionUnit()); fuel.setEquipmentResource(equipment); equipment.getFuelConsumptions().add(fuel);
        return new FuelView(fuel.getId(), fuel.getFuelType(), fuel.getConsumptionPerHour(), fuel.getConsumptionUnit());
    }

    public <T extends Resource> T require(UUID id, Class<T> expectedType) {
        Resource resource = repository.findById(id).orElseThrow(() -> new NotFoundException("Resource not found: " + id));
        if (!expectedType.isInstance(resource)) throw new IllegalArgumentException("Resource " + id + " must be " + expectedType.getSimpleName());
        return expectedType.cast(resource);
    }

    private void initialize(Resource value, String code, String name, String description) {
        value.setId(UUID.randomUUID()); value.setCode(code); value.setName(name); value.setDescription(description); value.setStatus(ResourceStatus.ACTIVE);
    }
    private ResourceView view(Resource resource) {
        String subtype = switch (resource) {
            case PersonnelResource p -> p.getProfession();
            case EquipmentResource e -> e.getEquipmentType();
            case MaterialResource m -> m.getMaterialType();
            default -> "";
        };
        String type = resource.getClass().getSimpleName().replace("Resource", "").toUpperCase();
        List<FuelView> fuelConsumptions = resource instanceof EquipmentResource equipment
                ? equipment.getFuelConsumptions().stream().map(fuel -> new FuelView(fuel.getId(), fuel.getFuelType(), fuel.getConsumptionPerHour(), fuel.getConsumptionUnit())).toList()
                : List.of();
        return new ResourceView(resource.getId(), type, resource.getCode(), resource.getName(), resource.getDescription(),
                resource.getStatus(), subtype, resource.getCostComponents().stream().map(this::costView).toList(), fuelConsumptions);
    }
    private CostView costView(CostComponent c) { return new CostView(c.getId(), c.getCategory(), c.getName(), c.getCalculationBasis(), c.getUnitPrice(), c.getUnit(), c.isTaxable(), c.getTaxRate()); }
}
