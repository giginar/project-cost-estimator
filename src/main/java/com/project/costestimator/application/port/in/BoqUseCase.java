package com.project.costestimator.application.port.in;

import com.project.costestimator.dto.ApiModels.BoqRequest;
import com.project.costestimator.dto.ApiModels.BoqTraceabilityReport;
import com.project.costestimator.dto.ApiModels.BoqView;

import java.util.List;
import java.util.UUID;

public interface BoqUseCase {
    BoqView addBoqItem(UUID projectId, UUID estimateId, BoqRequest request);
    List<BoqView> listBoqItems(UUID projectId, UUID estimateId);
    BoqView updateBoqItem(UUID projectId, UUID estimateId, UUID boqId, BoqRequest request);
    void deleteBoqItem(UUID projectId, UUID estimateId, UUID boqId);
    BoqTraceabilityReport boqTraceability(UUID projectId, UUID estimateId);
}
