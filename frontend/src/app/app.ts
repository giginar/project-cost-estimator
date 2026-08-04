import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { catchError, finalize, of } from 'rxjs';
import { GanttComponent } from './features/gantt/gantt.component';
import { ActivityResource, GanttTask, NewResource, ResourceSelection, ResourceType } from './features/gantt/gantt.models';
import { ResourceCatalogComponent } from './features/resources/resource-catalog.component';
import { ProjectOverviewComponent } from './features/project/project-overview.component';
import { ProjectReportComponent } from './features/project/project-report.component';
import { NewActivity, ProjectApiService, ScheduleData, WbsOption } from './core/project-api.service';

@Component({
  selector: 'app-root',
  imports: [GanttComponent, ResourceCatalogComponent, ProjectOverviewComponent, ProjectReportComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  private readonly api = inject(ProjectApiService);
  protected scheduleContext: ScheduleData | null = null;
  protected readonly sidebarCollapsed = signal(false);
  protected readonly currentPage = signal<'overview' | 'schedule' | 'report' | ResourceType>('schedule');
  protected readonly projectName = signal('Marine Excavation — Phase 1');
  protected readonly projectStart = signal('2026-08-03');
  protected readonly projectEnd = signal('2026-09-26');
  protected readonly currencyCode = signal('USD');
  protected readonly tasks = signal<GanttTask[]>([
    { id: '1', code: '1.1', name: 'Site mobilization', wbs: 'PREPARATION', start: '2026-08-03', end: '2026-08-08', assignments: [] },
    { id: '2', code: '1.2', name: 'Bathymetric survey', wbs: 'PREPARATION', start: '2026-08-06', end: '2026-08-14', assignments: [] },
    { id: '3', code: '2.1', name: 'Dredging area A', wbs: 'MARINE WORKS', start: '2026-08-12', end: '2026-08-28', assignments: [] },
    { id: '4', code: '2.2', name: 'Dredging area B', wbs: 'MARINE WORKS', start: '2026-08-24', end: '2026-09-11', assignments: [] },
    { id: '5', code: '2.3', name: 'Transport dredged material', wbs: 'MARINE WORKS', start: '2026-08-17', end: '2026-09-08', assignments: [] },
    { id: '6', code: '3.1', name: 'Disposal area grading', wbs: 'LAND OPERATIONS', start: '2026-09-02', end: '2026-09-16', assignments: [] },
    { id: '7', code: '4.1', name: 'Final hydrographic survey', wbs: 'CLOSEOUT', start: '2026-09-14', end: '2026-09-21', assignments: [] },
    { id: '8', code: '4.2', name: 'Demobilization', wbs: 'CLOSEOUT', start: '2026-09-21', end: '2026-09-26', assignments: [] },
  ]);
  protected readonly resources = signal<ActivityResource[]>([]);
  protected readonly activityDialogOpen = signal(false);
  protected readonly activitySaving = signal(false);
  protected readonly activityError = signal('');
  protected readonly wbsItems = signal<WbsOption[]>([]);
  protected readonly activityDraft = signal<NewActivity>({ wbsId: '', code: '', name: '', type: 'WORK', start: '', end: '' });

  ngOnInit(): void {
    this.api.loadSchedule().pipe(catchError(() => of(null))).subscribe(schedule => {
      if (!schedule) return;
      this.scheduleContext = schedule; this.projectName.set(schedule.projectName); this.projectStart.set(schedule.projectStart); this.projectEnd.set(schedule.projectEnd); this.currencyCode.set(schedule.currencyCode); this.tasks.set(schedule.tasks); this.wbsItems.set(schedule.wbsItems);
    });
    this.api.listResources().pipe(catchError(() => of([]))).subscribe(resources => this.resources.set(resources));
  }

  protected updateTask(updated: GanttTask): void {
    this.tasks.update(tasks => tasks.map(task => task.id === updated.id ? updated : task));
    if (this.scheduleContext) this.api.updateDates(this.scheduleContext, updated).subscribe();
  }
  protected addResources(selection: ResourceSelection): void {
    if (!this.scheduleContext) return;
    this.resources().filter(resource => selection.addResourceIds.includes(resource.id)).forEach(resource => {
      this.api.assignResource(this.scheduleContext!, selection.task, resource).subscribe(assignment => {
        this.tasks.update(tasks => tasks.map(task => task.id === selection.task.id ? { ...task, assignments: [...task.assignments, assignment] } : task));
      });
    });
    selection.removeAssignmentIds.forEach(assignmentId => {
      this.api.unassignResource(this.scheduleContext!, selection.task.id, assignmentId).subscribe(() => {
        this.tasks.update(tasks => tasks.map(task => task.id === selection.task.id ? { ...task, assignments: task.assignments.filter(item => item.id !== assignmentId) } : task));
      });
    });
  }
  protected createResource(resource: NewResource): void {
    this.api.createResource(resource).subscribe(created => this.resources.update(resources => [...resources, created]));
  }

  protected openActivityDialog(): void {
    const start = this.iso(new Date());
    const endDate = new Date(); endDate.setDate(endDate.getDate() + 6);
    this.activityDraft.set({ wbsId: this.wbsItems()[0]?.id ?? '', code: '', name: '', type: 'WORK', start, end: this.iso(endDate) });
    this.activityError.set(this.scheduleContext ? '' : 'Create a project, estimate and WBS first so the activity has somewhere to be saved.');
    this.activityDialogOpen.set(true);
  }
  protected updateActivityDraft(field: keyof NewActivity, value: string): void { this.activityDraft.update(draft => ({ ...draft, [field]: value })); }
  protected createActivity(): void {
    const draft = this.activityDraft();
    if (!this.scheduleContext || !draft.wbsId || !draft.code.trim() || !draft.name.trim() || !draft.start || !draft.end) { this.activityError.set('Complete all required fields.'); return; }
    if (draft.end < draft.start) { this.activityError.set('Finish date cannot be before start date.'); return; }
    this.activitySaving.set(true); this.activityError.set('');
    this.api.createActivity(this.scheduleContext, { ...draft, code: draft.code.trim(), name: draft.name.trim() }).pipe(
      finalize(() => this.activitySaving.set(false)),
    ).subscribe({
      next: task => { this.tasks.update(tasks => [...tasks, task]); this.activityDialogOpen.set(false); },
      error: () => this.activityError.set('Activity could not be saved. Check the values and try again.'),
    });
  }
  private iso(date: Date): string { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
}
