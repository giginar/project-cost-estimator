import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, forkJoin, of, switchMap, map, tap } from 'rxjs';
import { ActivityAssignment, ActivityResource, GanttTask, NewResource } from '../features/gantt/gantt.models';

interface ProjectSummary { id: string; name: string; plannedStartDate: string; plannedEndDate: string; currency: string; }
interface ActivityView { id: string; code: string; name: string; type: string; plannedQuantity: number | null; quantityUnit: string | null; plannedDuration: number | null; durationUnit: string | null; plannedStartDate: string | null; plannedEndDate: string | null; assignments: ActivityAssignment[]; }
interface WbsView { id: string; code: string; name: string; activities: ActivityView[]; }
interface EstimateView { id: string; wbsItems: WbsView[]; }
interface ProjectDetail { project: ProjectSummary; estimates: EstimateView[]; }
export interface WbsOption { id: string; code: string; name: string; }
export interface NewActivity { wbsId: string; code: string; name: string; type: string; start: string; end: string; }
export interface ScheduleData { projectId: string; estimateId: string; projectName: string; projectStart: string; projectEnd: string; currencyCode: string; tasks: GanttTask[]; activities: Map<string, ActivityView>; wbsItems: WbsOption[]; }

@Injectable({ providedIn: 'root' })
export class ProjectApiService {
  constructor(private readonly http: HttpClient) {}

  loadSchedule(): Observable<ScheduleData | null> {
    return this.http.get<ProjectSummary[]>('/api/v1/projects').pipe(
      switchMap(projects => projects.length ? this.http.get<ProjectDetail>(`/api/v1/projects/${projects[0].id}`) : of(null)),
      map(detail => {
        if (!detail) return null;
        const estimate = detail.estimates.at(-1); if (!estimate) return null;
        const activities = new Map<string, ActivityView>();
        const tasks = estimate.wbsItems.flatMap(wbs => wbs.activities
          .filter(activity => activity.plannedStartDate && activity.plannedEndDate)
          .map(activity => {
            activities.set(activity.id, activity);
            return { id: activity.id, code: activity.code, name: activity.name, wbs: wbs.name.toUpperCase(), start: activity.plannedStartDate!, end: activity.plannedEndDate!, assignments: activity.assignments ?? [] };
          }));
        const wbsItems = estimate.wbsItems.map(({ id, code, name }) => ({ id, code, name }));
        return { projectId: detail.project.id, estimateId: estimate.id, projectName: detail.project.name, projectStart: detail.project.plannedStartDate, projectEnd: detail.project.plannedEndDate, currencyCode: detail.project.currency || 'USD', tasks, activities, wbsItems };
      }),
    );
  }

  updateDates(context: ScheduleData, task: GanttTask): Observable<ActivityView> {
    const activity = context.activities.get(task.id)!;
    return this.http.put<ActivityView>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${task.id}`, {
      code: activity.code, name: activity.name, type: activity.type,
      plannedQuantity: activity.plannedQuantity, quantityUnit: activity.quantityUnit,
      plannedDuration: this.duration(task.start, task.end), durationUnit: 'DAY',
      plannedStartDate: task.start, plannedEndDate: task.end,
    });
  }

  createActivity(context: ScheduleData, input: NewActivity): Observable<GanttTask> {
    const wbs = context.wbsItems.find(item => item.id === input.wbsId)!;
    return this.http.post<ActivityView>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/wbs-items/${input.wbsId}/activities`, {
      code: input.code, name: input.name, type: input.type,
      plannedQuantity: 0, plannedDuration: this.duration(input.start, input.end), durationUnit: 'DAY',
      plannedStartDate: input.start, plannedEndDate: input.end,
    }).pipe(
      tap(activity => context.activities.set(activity.id, activity)),
      map(activity => ({ id: activity.id, code: activity.code, name: activity.name, wbs: wbs.name.toUpperCase(), start: activity.plannedStartDate!, end: activity.plannedEndDate!, assignments: activity.assignments ?? [] })),
    );
  }

  listResources(): Observable<ActivityResource[]> {
    return this.http.get<Array<Omit<ActivityResource, 'type'> & { type: string }>>('/api/v1/resources').pipe(
      map(resources => resources
        .map(resource => ({ ...resource, costs: resource.costs ?? [], fuelConsumptions: resource.fuelConsumptions ?? [], type: resource.type.toLowerCase() }))
        .filter((resource): resource is ActivityResource => resource.type === 'equipment' || resource.type === 'personnel' || resource.type === 'material')),
    );
  }

  createResource(input: NewResource): Observable<ActivityResource> {
    const body = input.type === 'personnel'
      ? { code: input.code, name: input.name, description: input.description, profession: input.subtype, genericResource: true }
      : input.type === 'equipment'
        ? { code: input.code, name: input.name, description: input.description, equipmentType: input.subtype, owned: false }
        : { code: input.code, name: input.name, description: input.description, materialType: input.subtype, defaultUnit: input.defaultUnit };
    const path = input.type === 'material' ? 'materials' : input.type;
    return this.http.post<Omit<ActivityResource, 'type'> & { type: string }>(`/api/v1/resources/${path}`, body).pipe(
      switchMap(created => {
        const requests: Observable<unknown>[] = [];
        if (input.unitPrice != null && input.unitPrice >= 0) requests.push(this.http.post(`/api/v1/resources/${created.id}/cost-components`, {
          category: input.type === 'personnel' ? 'SALARY' : input.type === 'equipment' ? 'RENTAL' : 'MATERIAL',
          name: input.type === 'personnel' ? 'Base salary' : input.type === 'equipment' ? 'Equipment rate' : 'Unit price',
          calculationBasis: input.calculationBasis, unitPrice: input.unitPrice,
          unit: this.costUnit(input), taxable: false, taxRate: 0,
        }));
        if (input.type === 'equipment' && input.fuelConsumption != null && input.fuelConsumption > 0) requests.push(this.http.post(`/api/v1/resources/${created.id}/fuel-consumptions`, {
          fuelType: input.fuelType, consumptionPerHour: input.fuelConsumption,
          consumptionUnit: input.fuelType === 'ELECTRICITY' ? 'KILOWATT_HOUR' : 'LITER',
        }));
        if (input.type === 'equipment' && input.fuelUnitPrice != null && input.fuelUnitPrice >= 0) requests.push(this.http.post(`/api/v1/resources/${created.id}/cost-components`, {
          category: 'FUEL', name: 'Fuel unit price', calculationBasis: 'PER_UNIT', unitPrice: input.fuelUnitPrice,
          unit: input.fuelType === 'ELECTRICITY' ? 'KILOWATT_HOUR' : 'LITER', taxable: false, taxRate: 0,
        }));
        return (requests.length ? forkJoin(requests) : of([])).pipe(switchMap(() => this.http.get<Omit<ActivityResource, 'type'> & { type: string }>(`/api/v1/resources/${created.id}`)));
      }),
      map(resource => ({ ...resource, costs: resource.costs ?? [], fuelConsumptions: resource.fuelConsumptions ?? [], type: resource.type.toLowerCase() as ActivityResource['type'] })),
    );
  }

  assignResource(context: ScheduleData, task: GanttTask, resource: ActivityResource): Observable<ActivityAssignment> {
    const days = this.duration(task.start, task.end);
    return this.http.post<ActivityAssignment>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${task.id}/assignments`, {
      resourceId: resource.id, quantity: 1, plannedWork: resource.type === 'material' ? 0 : days * 8,
      workUnit: resource.type === 'personnel' ? 'PERSON_HOUR' : resource.type === 'equipment' ? 'EQUIPMENT_HOUR' : null,
      utilizationRate: 100,
      overtimeAllowed: false, personnelAssignmentType: 'DIRECT_LABOR', operatingHoursPerDay: 0,
      standbyHoursPerDay: 0, requiredQuantity: 1, wastePercentage: 0,
    });
  }

  unassignResource(context: ScheduleData, activityId: string, assignmentId: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/projects/${context.projectId}/estimates/${context.estimateId}/activities/${activityId}/assignments/${assignmentId}`);
  }

  private costUnit(input: NewResource): string {
    if (input.type === 'material') return input.defaultUnit;
    return input.calculationBasis === 'PER_HOUR' ? 'HOUR' : input.calculationBasis === 'PER_MONTH' ? 'MONTH' : 'DAY';
  }

  private duration(start: string, end: string): number { return Math.round((new Date(`${end}T00:00:00`).getTime() - new Date(`${start}T00:00:00`).getTime()) / 86_400_000) + 1; }
}
