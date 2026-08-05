package com.project.costestimator.controller;

import com.project.costestimator.dto.ApiModels.*;
import com.project.costestimator.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Tag(name = "Resources", description = "Personnel, equipment, material, and cost component catalog")
public class ResourceController {
    private final ResourceService service;

    @Operation(summary = "List resources", description = "The optional type parameter accepts personnel, equipment, or material.")
    @GetMapping public List<ResourceView> list(@RequestParam(required = false) String type, @RequestParam(required = false) UUID projectId) { return service.list(type, projectId); }
    @Operation(summary = "Get resource details")
    @GetMapping("/{id}") public ResourceView get(@PathVariable UUID id) { return service.get(id); }
    @Operation(summary = "Create a personnel resource")
    @PostMapping("/personnel") @ResponseStatus(HttpStatus.CREATED) public ResourceView personnel(@Valid @RequestBody PersonnelRequest request, @RequestParam(required = false) UUID projectId, @RequestParam(defaultValue = "true") boolean shared) { return service.createPersonnel(request, projectId, shared); }
    @Operation(summary = "Create an equipment resource")
    @PostMapping("/equipment") @ResponseStatus(HttpStatus.CREATED) public ResourceView equipment(@Valid @RequestBody EquipmentRequest request, @RequestParam(required = false) UUID projectId, @RequestParam(defaultValue = "true") boolean shared) { return service.createEquipment(request, projectId, shared); }
    @Operation(summary = "Create a material resource")
    @PostMapping("/materials") @ResponseStatus(HttpStatus.CREATED) public ResourceView material(@Valid @RequestBody MaterialRequest request, @RequestParam(required = false) UUID projectId, @RequestParam(defaultValue = "true") boolean shared) { return service.createMaterial(request, projectId, shared); }
    @Operation(summary = "Share or unshare a resource owned by the active project")
    @PutMapping("/{id}/sharing") public ResourceView sharing(@PathVariable UUID id, @Valid @RequestBody ResourceSharingRequest request) { return service.updateSharing(id, request); }
    @Operation(summary = "Add a cost component to a resource")
    @PostMapping("/{id}/cost-components") @ResponseStatus(HttpStatus.CREATED) public CostView cost(@PathVariable UUID id, @Valid @RequestBody CostRequest request) { return service.addCost(id, request); }
    @PutMapping("/{id}/cost-components/{costId}") public CostView updateCost(@PathVariable UUID id, @PathVariable UUID costId, @Valid @RequestBody CostRequest request) { return service.updateCost(id, costId, request); }
    @DeleteMapping("/{id}/cost-components/{costId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCost(@PathVariable UUID id, @PathVariable UUID costId) { service.deleteCost(id, costId); }
    @Operation(summary = "Update owned/rented equipment economics and generated monthly costs")
    @PutMapping("/{id}/equipment-economics") public EquipmentEconomicsView equipmentEconomics(@PathVariable UUID id, @Valid @RequestBody EquipmentEconomicsRequest request) { return service.updateEquipmentEconomics(id, request); }
    @Operation(summary = "Update material procurement, lead time, minimum order and default waste")
    @PutMapping("/{id}/material-procurement") public MaterialProcurementView materialProcurement(@PathVariable UUID id, @Valid @RequestBody MaterialProcurementRequest request) { return service.updateMaterialProcurement(id, request); }
    @Operation(summary = "Add fuel consumption to equipment")
    @PostMapping("/{id}/fuel-consumptions") @ResponseStatus(HttpStatus.CREATED) public FuelView fuel(@PathVariable UUID id, @Valid @RequestBody FuelRequest request) { return service.addFuel(id, request); }
    @Operation(summary = "Delete a resource")
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id, @RequestParam UUID projectId) { service.delete(id, projectId); }
}
