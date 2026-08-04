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
    @GetMapping public List<ResourceView> list(@RequestParam(required = false) String type) { return service.list(type); }
    @Operation(summary = "Get resource details")
    @GetMapping("/{id}") public ResourceView get(@PathVariable UUID id) { return service.get(id); }
    @Operation(summary = "Create a personnel resource")
    @PostMapping("/personnel") @ResponseStatus(HttpStatus.CREATED) public ResourceView personnel(@Valid @RequestBody PersonnelRequest request) { return service.createPersonnel(request); }
    @Operation(summary = "Create an equipment resource")
    @PostMapping("/equipment") @ResponseStatus(HttpStatus.CREATED) public ResourceView equipment(@Valid @RequestBody EquipmentRequest request) { return service.createEquipment(request); }
    @Operation(summary = "Create a material resource")
    @PostMapping("/materials") @ResponseStatus(HttpStatus.CREATED) public ResourceView material(@Valid @RequestBody MaterialRequest request) { return service.createMaterial(request); }
    @Operation(summary = "Add a cost component to a resource")
    @PostMapping("/{id}/cost-components") @ResponseStatus(HttpStatus.CREATED) public CostView cost(@PathVariable UUID id, @Valid @RequestBody CostRequest request) { return service.addCost(id, request); }
    @Operation(summary = "Add fuel consumption to equipment")
    @PostMapping("/{id}/fuel-consumptions") @ResponseStatus(HttpStatus.CREATED) public FuelView fuel(@PathVariable UUID id, @Valid @RequestBody FuelRequest request) { return service.addFuel(id, request); }
    @Operation(summary = "Delete a resource")
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
}
