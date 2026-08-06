package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.ResourceCatalogUseCase;
import com.project.costestimator.dto.ApiModels.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources")
@Tag(name = "Resources", description = "Personnel, equipment, material, cost, and fuel catalog operations")
public class ResourceController {
    private final ResourceCatalogUseCase resources;

    public ResourceController(ResourceCatalogUseCase resources) {
        this.resources = resources;
    }

    @GetMapping
    public List<ResourceView> list(@RequestParam(required = false) String type,
                                   @RequestParam(required = false) UUID projectId) {
        return resources.list(type, projectId);
    }

    @GetMapping("/{resourceId}")
    public ResourceView get(@PathVariable UUID resourceId) {
        return resources.get(resourceId);
    }

    @PostMapping("/personnel")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceView createPersonnel(@Valid @RequestBody PersonnelRequest request,
                                        @RequestParam(required = false) UUID projectId,
                                        @RequestParam(defaultValue = "true") boolean shared) {
        return resources.createPersonnel(request, projectId, shared);
    }

    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceView createEquipment(@Valid @RequestBody EquipmentRequest request,
                                        @RequestParam(required = false) UUID projectId,
                                        @RequestParam(defaultValue = "true") boolean shared) {
        return resources.createEquipment(request, projectId, shared);
    }

    @PostMapping("/materials")
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceView createMaterial(@Valid @RequestBody MaterialRequest request,
                                       @RequestParam(required = false) UUID projectId,
                                       @RequestParam(defaultValue = "true") boolean shared) {
        return resources.createMaterial(request, projectId, shared);
    }

    @PutMapping("/{resourceId}/sharing")
    public ResourceView updateSharing(@PathVariable UUID resourceId,
                                      @Valid @RequestBody ResourceSharingRequest request) {
        return resources.updateSharing(resourceId, request);
    }

    @PostMapping("/{resourceId}/cost-components")
    @ResponseStatus(HttpStatus.CREATED)
    public CostView addCost(@PathVariable UUID resourceId,
                            @Valid @RequestBody CostRequest request) {
        return resources.addCost(resourceId, request);
    }

    @PutMapping("/{resourceId}/cost-components/{costId}")
    public CostView updateCost(@PathVariable UUID resourceId,
                               @PathVariable UUID costId,
                               @Valid @RequestBody CostRequest request) {
        return resources.updateCost(resourceId, costId, request);
    }

    @DeleteMapping("/{resourceId}/cost-components/{costId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCost(@PathVariable UUID resourceId, @PathVariable UUID costId) {
        resources.deleteCost(resourceId, costId);
    }

    @PutMapping("/{resourceId}/equipment-economics")
    public EquipmentEconomicsView updateEquipmentEconomics(
            @PathVariable UUID resourceId,
            @Valid @RequestBody EquipmentEconomicsRequest request) {
        return resources.updateEquipmentEconomics(resourceId, request);
    }

    @PutMapping("/{resourceId}/material-procurement")
    public MaterialProcurementView updateMaterialProcurement(
            @PathVariable UUID resourceId,
            @Valid @RequestBody MaterialProcurementRequest request) {
        return resources.updateMaterialProcurement(resourceId, request);
    }

    @PostMapping("/{resourceId}/fuel-consumptions")
    @ResponseStatus(HttpStatus.CREATED)
    public FuelView addFuel(@PathVariable UUID resourceId,
                            @Valid @RequestBody FuelRequest request) {
        return resources.addFuel(resourceId, request);
    }

    @DeleteMapping("/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID resourceId, @RequestParam UUID projectId) {
        resources.delete(resourceId, projectId);
    }
}
