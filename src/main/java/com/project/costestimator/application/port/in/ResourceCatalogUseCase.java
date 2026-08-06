package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.*;

import java.util.List;
import java.util.UUID;

public interface ResourceCatalogUseCase {
    ResourceView createPersonnel(PersonnelRequest request);
    ResourceView createPersonnel(PersonnelRequest request, UUID ownerProjectId, boolean shared);
    ResourceView createEquipment(EquipmentRequest request);
    ResourceView createEquipment(EquipmentRequest request, UUID ownerProjectId, boolean shared);
    ResourceView createMaterial(MaterialRequest request);
    ResourceView createMaterial(MaterialRequest request, UUID ownerProjectId, boolean shared);
    List<ResourceView> list(String type, UUID projectId);
    ResourceView get(UUID resourceId);
    ResourceView updateSharing(UUID resourceId, ResourceSharingRequest request);
    void delete(UUID resourceId, UUID projectId);
    CostView addCost(UUID resourceId, CostRequest request);
    CostView updateCost(UUID resourceId, UUID costId, CostRequest request);
    void deleteCost(UUID resourceId, UUID costId);
    EquipmentEconomicsView updateEquipmentEconomics(UUID resourceId, EquipmentEconomicsRequest request);
    MaterialProcurementView updateMaterialProcurement(UUID resourceId, MaterialProcurementRequest request);
    FuelView addFuel(UUID resourceId, FuelRequest request);
}
