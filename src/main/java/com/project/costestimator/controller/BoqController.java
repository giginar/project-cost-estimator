package com.project.costestimator.controller;

import com.project.costestimator.application.port.in.BoqUseCase;
import com.project.costestimator.dto.ApiModels.BoqRequest;
import com.project.costestimator.dto.ApiModels.BoqTraceabilityReport;
import com.project.costestimator.dto.ApiModels.BoqView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "BOQ", description = "Bill of quantities and schedule traceability")
public class BoqController {
    private final BoqUseCase boq;

    public BoqController(BoqUseCase boq) {
        this.boq = boq;
    }

    @PostMapping("/{projectId}/estimates/{estimateId}/boq-items")
    @ResponseStatus(HttpStatus.CREATED)
    public BoqView add(@PathVariable UUID projectId,
                       @PathVariable UUID estimateId,
                       @Valid @RequestBody BoqRequest request) {
        return boq.addBoqItem(projectId, estimateId, request);
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/boq-items")
    public List<BoqView> list(@PathVariable UUID projectId, @PathVariable UUID estimateId) {
        return boq.listBoqItems(projectId, estimateId);
    }

    @PutMapping("/{projectId}/estimates/{estimateId}/boq-items/{boqId}")
    public BoqView update(@PathVariable UUID projectId,
                          @PathVariable UUID estimateId,
                          @PathVariable UUID boqId,
                          @Valid @RequestBody BoqRequest request) {
        return boq.updateBoqItem(projectId, estimateId, boqId, request);
    }

    @DeleteMapping("/{projectId}/estimates/{estimateId}/boq-items/{boqId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId,
                       @PathVariable UUID estimateId,
                       @PathVariable UUID boqId) {
        boq.deleteBoqItem(projectId, estimateId, boqId);
    }

    @GetMapping("/{projectId}/estimates/{estimateId}/boq-traceability")
    public BoqTraceabilityReport traceability(@PathVariable UUID projectId,
                                              @PathVariable UUID estimateId) {
        return boq.boqTraceability(projectId, estimateId);
    }
}
