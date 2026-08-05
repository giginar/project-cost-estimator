import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, forkJoin, of, switchMap, map, tap } from 'rxjs';
import { ActivityAssignment, ActivityDependency, ActivityResource, AssignmentDraft, GanttTask, NewResource, ResourceCost } from '../features/gantt/gantt.models';

interface ProjectSummary { id: string; code: string; name: string; description: string | null; plannedStartDate: string; plannedEndDate: string; currency: string; languageCode: 'en' | 'tr'; usdTryRate: number | null; eurTryRate: number | null; status: string; }
interface ActivityView { id: string; code: string; name: string; type: string; plannedQuantity: number | null; quantityUnit: string | null; plannedDuration: number | null; durationUnit: string | null; plannedStartDate: string | null; plannedEndDate: string | null; dailyProductionRate: number | null; autoSchedule: boolean; dependencies: ActivityDependency[]; assignments: ActivityAssignment[]; }
interface WbsView { id: string; code: string; name: string; activities: ActivityView[]; }
interface EstimateView { id: string; wbsItems: WbsView[]; }
interface ProjectDetail { project: ProjectSummary; estimates: EstimateView[]; }
export interface WbsOption { id: string; code: string; name: string; }
export interface ProjectOption { id: string; code: string; name: string; }
export interface NewProject { code: string; name: string; description: string; start: string; end: string; currencyCode: 'USD' | 'TRY' | 'EUR'; }
export interface NewWbs { code: string; name: string; description: string; }
export interface NewActivity { wbsId: string; code: string; name: string; type: string; start: string; end: string; plannedQuantity: number; quantityUnit: string; dailyProductionRate: number | null; autoSchedule: boolean; }
export interface ProjectSettings { code: string; name: string; description: string; start: string; end: string; currencyCode: 'USD' | 'TRY' | 'EUR'; usdTryRate: number | null; eurTryRate: number | null; status: string; }
export interface NewCostRate { id: string | null; resourceId: string; category: string; name: string; calculationBasis: string; unitPrice: number; unit: string; currencyCode: string; taxable: boolean; taxRate: number; validFrom: string | null; validTo: string | null; }
export interface EquipmentEconomicsDraft { resourceId: string; owned: boolean; acquisitionCost: number; residualValue: number; usefulLifeMonths: number; maintenanceRatePercentage: number; insuranceRatePercentage: number; currencyCode: string; }
export interface MaterialProcurementDraft { resourceId: string; supplier: string; leadTimeDays: number; minimumOrderQuantity: number; defaultWastePercentage: number; }
export interface ScheduleData { projectId: string; estimateId: string; projectCode: string; projectName: string; projectDescription: string; projectStatus: string; projectStart: string; projectEnd: string; currencyCode: string; languageCode: 'en' | 'tr'; usdTryRate: number | null; eurTryRate: number | null; tasks: GanttTask[]; activities: Map<string, ActivityView>; wbsItems: WbsOption[]; }
export interface CostBreakdownView { personnelCost: number; equipmentCost: number; fuelCost: number; materialCost: number; accommodationCost: number; transportationCost: number; overheadCost: number; taxCost: number; totalCost: number; }
export interface ActivityCostReport { activityId: string; code: string; name: string; costs: CostBreakdownView; }
export interface WbsCostReport { wbsId: string; code: string; name: string; costs: CostBreakdownView; activities: ActivityCostReport[]; }
export interface EstimateCostReport { total: CostBreakdownView; projectLevel: CostBreakdownView; wbsItems: WbsCostReport[]; }
export interface EstimateResourceRate { id: string; resourceId: string; sourceCostComponentId: string; category: string; name: string; calculationBasis: string; unitPrice: number; unit: string | null; taxable: boolean; taxRate: number; validFrom: string | null; validTo: string | null; }
export interface BoqItem { id: string; code: string; description: string; unit: string; quantity: number; unitPrice: number; currencyCode: string; totalPrice: number; wbsId: string; wbsCode: string; wbsName: string; activityId: string | null; activityCode: string | null; activityName: string | null; }
export interface BoqDraft { id: string | null; code: string; description: string; unit: string; quantity: number; unitPrice: number; currencyCode: string; wbsId: string; activityId: string | null; }
export interface BoqTraceabilityReport { totalBoqValue: number; itemCount: number; linkedItemCount: number; unlinkedItemCount: number; items: BoqItem[]; }
export interface ShiftSettings { id?: string; name: string; startTime: string; endTime: string; paidHours: number; }
export interface CalendarSettings { id?: string; name: string; workingDaysPerWeek: number; workingHoursPerDay: number; shifts: ShiftSettings[]; }
export interface ActivityPlanningDraft { activityId: string; plannedQuantity: number; quantityUnit: string; dailyProductionRate: number; autoSchedule: boolean; plannedStartDate: string; }
export interface DependencyDraft { activityId: string; predecessorActivityId: string; type: string; lagDays: number; }
export type PricingRuleType = 'OVERHEAD' | 'RISK' | 'CONTINGENCY' | 'GUARANTEE' | 'FINANCING' | 'TAX' | 'PROFIT';
export type PricingBase = 'ESTIMATED_COST' | 'RUNNING_TOTAL';
export interface PricingRuleDraft { id: string | null; type: PricingRuleType; name: string; percentage: number; base: PricingBase; sequence: number; active: boolean; }
export interface PricingRule extends Omit<PricingRuleDraft, 'id'> { id: string; }
export interface PricingLine extends PricingRule { ruleId: string; baseAmount: number; amount: number; }
export interface PricingSummary { estimatedCost: number; boqValue: number; nonProfitAdders: number; profit: number; salesPrice: number; grossProfit: number; netProfit: number; profitMarginPercentage: number; boqVariance: number; lines: PricingLine[]; }

@Injectable({ providedIn: 'root' })
export class ProjectApiService {
  constructor(private readonly http: HttpClient) {}

  listProjects(): Observable<ProjectOption[]> { return this.http.get<ProjectSummary[]>('/api/v1/projects').pipe(map(projects => projects.map(({ id, code, name }) => ({ id, code, name })))); }

  loadCostReport(context: ScheduleData): Observable<EstimateCostReport> {
    return this.http.get<EstimateCostReport>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/cost-report`);
  }

  listResourceRates(context: ScheduleData): Observable<EstimateResourceRate[]> {
    return this.http.get<EstimateResourceRate[]>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/resource-rates`);
  }

  syncResourceRates(context: ScheduleData, resourceId: string, replaceExisting = false): Observable<EstimateResourceRate[]> {
    return this.http.post<EstimateResourceRate[]>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/resource-rates/${resourceId}/sync?replaceExisting=${replaceExisting}`, {});
  }

  updateResourceRate(context: ScheduleData, sourceCostComponentId: string, unitPrice: number): Observable<EstimateResourceRate> {
    return this.http.put<EstimateResourceRate>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/resource-rates/${sourceCostComponentId}`, { unitPrice });
  }

  listBoq(context: ScheduleData): Observable<BoqTraceabilityReport> { return this.http.get<BoqTraceabilityReport>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/boq-traceability`); }
  saveBoq(context: ScheduleData, draft: BoqDraft): Observable<BoqItem> {
    const url = `/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/boq-items${draft.id ? `/${draft.id}` : ''}`;
    return draft.id ? this.http.put<BoqItem>(url, draft) : this.http.post<BoqItem>(url, draft);
  }
  deleteBoq(context: ScheduleData, id: string): Observable<void> { return this.http.delete<void>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/boq-items/${id}`); }
  getCalendar(context: ScheduleData): Observable<CalendarSettings> { return this.http.get<CalendarSettings>(`/api/v1/projects/${context.projectId}/calendar`); }
  updateCalendar(context: ScheduleData, calendar: CalendarSettings): Observable<CalendarSettings> { return this.http.put<CalendarSettings>(`/api/v1/projects/${context.projectId}/calendar`, calendar); }
  updateActivityPlanning(context: ScheduleData, draft: ActivityPlanningDraft, wbsName: string): Observable<GanttTask> {
    return this.http.put<ActivityView>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${draft.activityId}/planning`, draft).pipe(map(activity => this.toTask(activity, wbsName)));
  }
  addDependency(context: ScheduleData, draft: DependencyDraft): Observable<ActivityDependency> { return this.http.post<ActivityDependency>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${draft.activityId}/dependencies`, draft); }
  deleteDependency(context: ScheduleData, activityId: string, dependencyId: string): Observable<void> { return this.http.delete<void>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${activityId}/dependencies/${dependencyId}`); }
  listPricingRules(context: ScheduleData): Observable<PricingRule[]> { return this.http.get<PricingRule[]>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/pricing-rules`); }
  loadPricingSummary(context: ScheduleData): Observable<PricingSummary> { return this.http.get<PricingSummary>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/pricing-summary`); }
  savePricingRule(context: ScheduleData, draft: PricingRuleDraft): Observable<PricingRule> { const url = `/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/pricing-rules${draft.id ? `/${draft.id}` : ''}`; return draft.id ? this.http.put<PricingRule>(url, draft) : this.http.post<PricingRule>(url, draft); }
  deletePricingRule(context: ScheduleData, id: string): Observable<void> { return this.http.delete<void>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/pricing-rules/${id}`); }

  loadSchedule(projectId?: string): Observable<ScheduleData | null> {
    return this.http.get<ProjectSummary[]>('/api/v1/projects').pipe(
      switchMap(projects => {
        const project = projectId ? projects.find(item => item.id === projectId) : projects[0];
        return project ? this.http.get<ProjectDetail>(`/api/v1/projects/${project.id}`) : of(null);
      }),
      map(detail => {
        if (!detail) return null;
        const estimate = detail.estimates.at(-1); if (!estimate) return null;
        const activities = new Map<string, ActivityView>();
        const tasks = estimate.wbsItems.flatMap(wbs => wbs.activities
          .filter(activity => activity.plannedStartDate && activity.plannedEndDate)
          .map(activity => {
            activities.set(activity.id, activity);
            return this.toTask(activity, wbs.name.toUpperCase());
          }));
        const wbsItems = estimate.wbsItems.map(({ id, code, name }) => ({ id, code, name }));
        return { projectId: detail.project.id, estimateId: estimate.id, projectCode: detail.project.code, projectName: detail.project.name, projectDescription: detail.project.description ?? '', projectStatus: detail.project.status, projectStart: detail.project.plannedStartDate, projectEnd: detail.project.plannedEndDate, currencyCode: detail.project.currency || 'USD', languageCode: detail.project.languageCode || 'en', usdTryRate: detail.project.usdTryRate, eurTryRate: detail.project.eurTryRate, tasks, activities, wbsItems };
      }),
    );
  }

  createProject(input: NewProject, languageCode: 'en' | 'tr'): Observable<ScheduleData> {
    return this.http.post<ProjectDetail>('/api/v1/projects', {
      code: input.code, name: input.name, description: input.description, plannedStartDate: input.start, plannedEndDate: input.end,
      currencyCode: input.currencyCode, languageCode, status: 'DRAFT', usdTryRate: null, eurTryRate: null,
    }).pipe(
      switchMap(detail => this.http.post<EstimateView>(`/api/v1/projects/${detail.project.id}/estimates`, { name: 'Baseline Estimate', description: 'Initial project estimate' }).pipe(map(estimate => ({ projectId: detail.project.id, estimateId: estimate.id })))),
      switchMap(context => this.loadSchedule(context.projectId)),
      map(schedule => { if (!schedule) throw new Error('Created project could not be loaded'); return schedule; }),
    );
  }

  addWbs(context: ScheduleData, input: NewWbs): Observable<WbsOption> {
    return this.http.post<WbsView>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/wbs-items`, {
      code: input.code, name: input.name, description: input.description, sequence: context.wbsItems.length + 1, parentId: null,
    }).pipe(map(({ id, code, name }) => ({ id, code, name })));
  }

  updateDates(context: ScheduleData, task: GanttTask): Observable<ActivityView> {
    const activity = context.activities.get(task.id)!;
    return this.http.put<ActivityView>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${task.id}`, {
      code: activity.code, name: activity.name, type: activity.type,
      plannedQuantity: activity.plannedQuantity, quantityUnit: activity.quantityUnit,
      plannedDuration: this.duration(task.start, task.end), durationUnit: 'DAY',
      plannedStartDate: task.start, plannedEndDate: task.end,
      dailyProductionRate: activity.dailyProductionRate, autoSchedule: false,
    });
  }

  createActivity(context: ScheduleData, input: NewActivity): Observable<GanttTask> {
    const wbs = context.wbsItems.find(item => item.id === input.wbsId)!;
    return this.http.post<ActivityView>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/wbs-items/${input.wbsId}/activities`, {
      code: input.code, name: input.name, type: input.type,
      plannedQuantity: input.plannedQuantity, quantityUnit: input.quantityUnit, plannedDuration: this.duration(input.start, input.end), durationUnit: 'DAY',
      plannedStartDate: input.start, plannedEndDate: input.end,
      dailyProductionRate: input.dailyProductionRate, autoSchedule: input.autoSchedule,
    }).pipe(
      tap(activity => context.activities.set(activity.id, activity)),
      map(activity => this.toTask(activity, wbs.name.toUpperCase())),
    );
  }

  listResources(projectId?: string): Observable<ActivityResource[]> {
    const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
    return this.http.get<Array<Omit<ActivityResource, 'type'> & { type: string }>>(`/api/v1/resources${query}`).pipe(
      map(resources => resources
        .map(resource => ({ ...resource, costs: resource.costs ?? [], fuelConsumptions: resource.fuelConsumptions ?? [], assignable: true, type: resource.type.toLowerCase() as ActivityResource['type'] } as ActivityResource))
        .filter(resource => resource.type === 'equipment' || resource.type === 'personnel' || resource.type === 'material')),
    );
  }
  getResource(resourceId: string, assignable = true): Observable<ActivityResource> { return this.http.get<Omit<ActivityResource, 'type'> & { type: string }>(`/api/v1/resources/${resourceId}`).pipe(map(resource => ({ ...resource, costs: resource.costs ?? [], fuelConsumptions: resource.fuelConsumptions ?? [], assignable, type: resource.type.toLowerCase() as ActivityResource['type'] }))); }

  createResource(input: NewResource, currencyCode: string, context?: ScheduleData | null): Observable<ActivityResource> {
    const body = input.type === 'personnel'
      ? { code: input.code, name: input.name, description: input.description, profession: input.subtype, genericResource: true }
      : input.type === 'equipment'
        ? { code: input.code, name: input.name, description: input.description, equipmentType: input.subtype, owned: false }
        : { code: input.code, name: input.name, description: input.description, materialType: input.subtype, defaultUnit: input.defaultUnit };
    const path = input.type === 'material' ? 'materials' : input.type;
    const scope = `?shared=${input.shared}${context ? `&projectId=${encodeURIComponent(context.projectId)}` : ''}`;
    return this.http.post<Omit<ActivityResource, 'type'> & { type: string }>(`/api/v1/resources/${path}${scope}`, body).pipe(
      switchMap(created => {
        const requests: Observable<unknown>[] = [];
        if (input.unitPrice != null && input.unitPrice >= 0) requests.push(this.http.post(`/api/v1/resources/${created.id}/cost-components`, {
          category: input.type === 'personnel' ? 'SALARY' : input.type === 'equipment' ? 'RENTAL' : 'MATERIAL',
          name: input.type === 'personnel' ? 'Base salary' : input.type === 'equipment' ? 'Equipment rate' : 'Unit price',
          calculationBasis: input.calculationBasis, unitPrice: input.unitPrice,
          unit: this.costUnit(input), taxable: false, taxRate: 0, currencyCode,
        }));
        if (input.type === 'equipment' && input.fuelConsumption != null && input.fuelConsumption > 0) requests.push(this.http.post(`/api/v1/resources/${created.id}/fuel-consumptions`, {
          fuelType: input.fuelType, consumptionPerHour: input.fuelConsumption, standbyConsumptionPerHour: input.standbyFuelConsumption ?? 0,
          consumptionUnit: input.fuelType === 'ELECTRICITY' ? 'KILOWATT_HOUR' : 'LITER',
        }));
        if (input.type === 'equipment' && input.fuelUnitPrice != null && input.fuelUnitPrice >= 0) requests.push(this.http.post(`/api/v1/resources/${created.id}/cost-components`, {
          category: 'FUEL', name: 'Fuel unit price', calculationBasis: 'PER_UNIT', unitPrice: input.fuelUnitPrice,
          unit: input.fuelType === 'ELECTRICITY' ? 'KILOWATT_HOUR' : 'LITER', taxable: false, taxRate: 0, currencyCode,
        }));
        return (requests.length ? forkJoin(requests) : of([])).pipe(switchMap(() => this.http.get<Omit<ActivityResource, 'type'> & { type: string }>(`/api/v1/resources/${created.id}`)));
      }),
      map(resource => ({ ...resource, costs: resource.costs ?? [], fuelConsumptions: resource.fuelConsumptions ?? [], type: resource.type.toLowerCase() as ActivityResource['type'] })),
    );
  }

  addResourceCost(input: NewCostRate, currencyCode: string, context?: ScheduleData | null): Observable<ResourceCost> {
    const body = {
      category: input.category, name: input.name, calculationBasis: input.calculationBasis,
      unitPrice: input.unitPrice, unit: input.unit, taxable: input.taxable, taxRate: input.taxRate,
      validFrom: input.validFrom, validTo: input.validTo, currencyCode: input.currencyCode || currencyCode,
    };
    const request = input.id ? this.http.put<ResourceCost>(`/api/v1/resources/${input.resourceId}/cost-components/${input.id}`, body) : this.http.post<ResourceCost>(`/api/v1/resources/${input.resourceId}/cost-components`, body);
    return request.pipe(switchMap(cost => context ? this.syncResourceRates(context, input.resourceId, true).pipe(map(() => cost)) : of(cost)));
  }

  updateEquipmentEconomics(input: EquipmentEconomicsDraft, context?: ScheduleData | null): Observable<ActivityResource> { return this.http.put(`/api/v1/resources/${input.resourceId}/equipment-economics`, input).pipe(switchMap(() => context ? this.syncResourceRates(context, input.resourceId, true) : of([])), switchMap(() => this.http.get<ActivityResource>(`/api/v1/resources/${input.resourceId}`)), map(resource => ({ ...resource, costs: resource.costs ?? [], fuelConsumptions: resource.fuelConsumptions ?? [], type: resource.type.toLowerCase() as ActivityResource['type'] }))); }
  updateMaterialProcurement(input: MaterialProcurementDraft): Observable<ActivityResource> { return this.http.put(`/api/v1/resources/${input.resourceId}/material-procurement`, input).pipe(switchMap(() => this.http.get<ActivityResource>(`/api/v1/resources/${input.resourceId}`)), map(resource => ({ ...resource, type: resource.type.toLowerCase() as ActivityResource['type'] }))); }
  updateResourceSharing(resourceId: string, projectId: string, shared: boolean): Observable<ActivityResource> { return this.http.put<ActivityResource>(`/api/v1/resources/${resourceId}/sharing`, { projectId, shared }).pipe(map(resource => ({ ...resource, type: resource.type.toLowerCase() as ActivityResource['type'] }))); }
  deleteResource(resourceId: string, projectId: string): Observable<void> { return this.http.delete<void>(`/api/v1/resources/${resourceId}?projectId=${encodeURIComponent(projectId)}`); }

  updateProjectSettings(context: ScheduleData, settings: ProjectSettings, languageCode: 'en' | 'tr'): Observable<ProjectSettings> {
    return this.http.put<ProjectDetail>(`/api/v1/projects/${context.projectId}`, {
      code: settings.code, name: settings.name, description: settings.description, plannedStartDate: settings.start,
      plannedEndDate: settings.end, currencyCode: settings.currencyCode, languageCode, status: settings.status,
      usdTryRate: settings.usdTryRate, eurTryRate: settings.eurTryRate,
    }).pipe(map(detail => ({
      code: detail.project.code, name: detail.project.name, description: detail.project.description ?? '', start: detail.project.plannedStartDate,
      end: detail.project.plannedEndDate, currencyCode: (detail.project.currency || settings.currencyCode) as 'USD' | 'TRY' | 'EUR', usdTryRate: detail.project.usdTryRate, eurTryRate: detail.project.eurTryRate, status: detail.project.status,
    })));
  }

  saveAssignment(context: ScheduleData, task: GanttTask, assignment: AssignmentDraft): Observable<ActivityAssignment> {
    const url = `/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${task.id}/assignments${assignment.id ? `/${assignment.id}` : ''}`;
    const request = assignment.id ? this.http.put<ActivityAssignment>(url, assignment) : this.http.post<ActivityAssignment>(url, assignment);
    return request;
  }

  unassignResource(context: ScheduleData, activityId: string, assignmentId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${activityId}/assignments/${assignmentId}`);
  }

  private costUnit(input: NewResource): string {
    if (input.type === 'material') return input.defaultUnit;
    return input.calculationBasis === 'PER_HOUR' ? 'HOUR' : input.calculationBasis === 'PER_MONTH' ? 'MONTH' : 'DAY';
  }

  private toTask(activity: ActivityView, wbs: string): GanttTask { return { id: activity.id, code: activity.code, name: activity.name, wbs, start: activity.plannedStartDate!, end: activity.plannedEndDate!, plannedQuantity: activity.plannedQuantity, quantityUnit: activity.quantityUnit, dailyProductionRate: activity.dailyProductionRate, autoSchedule: activity.autoSchedule, dependencies: activity.dependencies ?? [], assignments: activity.assignments ?? [] }; }

  private duration(start: string, end: string): number { return Math.round((new Date(`${end}T00:00:00`).getTime() - new Date(`${start}T00:00:00`).getTime()) / 86_400_000) + 1; }
}
