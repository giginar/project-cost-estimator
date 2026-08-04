import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { catchError, finalize, map, of, retry, switchMap } from 'rxjs';
import { GanttComponent } from './features/gantt/gantt.component';
import { ActivityResource, GanttTask, NewResource, ResourceSelection, ResourceType } from './features/gantt/gantt.models';
import { ResourceCatalogComponent } from './features/resources/resource-catalog.component';
import { ProjectOverviewComponent } from './features/project/project-overview.component';
import { ProjectReportComponent } from './features/project/project-report.component';
import { CostLibraryComponent } from './features/cost-library/cost-library.component';
import { ProjectSettingsComponent } from './features/settings/project-settings.component';
import { NewActivity, NewCostRate, NewProject, NewWbs, ProjectApiService, ProjectOption, ProjectSettings, ScheduleData, WbsOption } from './core/project-api.service';
import { AuthService, AuthUser } from './core/auth.service';
import { AuthComponent } from './features/auth/auth.component';
import { UserAdminComponent } from './features/admin/user-admin.component';

@Component({
  selector: 'app-root',
  imports: [GanttComponent, ResourceCatalogComponent, ProjectOverviewComponent, ProjectReportComponent, CostLibraryComponent, ProjectSettingsComponent, AuthComponent, UserAdminComponent],
  templateUrl: './app.html',
  styleUrls: ['./app.scss', './app-extra.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  private readonly api = inject(ProjectApiService);
  protected readonly auth = inject(AuthService);
  protected scheduleContext: ScheduleData | null = null;
  protected readonly sidebarCollapsed = signal(false);
  protected readonly currentPage = signal<'overview' | 'schedule' | 'cost-library' | 'report' | 'settings' | ResourceType>('schedule');
  protected readonly projects = signal<ProjectOption[]>([]);
  protected readonly projectMenuOpen = signal(false);
  protected readonly newProjectDialogOpen = signal(false);
  protected readonly wbsDialogOpen = signal(false);
  protected readonly projectCreating = signal(false);
  protected readonly wbsCreating = signal(false);
  protected readonly projectActionError = signal('');
  protected readonly newProjectDraft = signal<NewProject>({ code: '', name: '', description: '', start: '', end: '', currencyCode: 'USD', wbsCode: '1', wbsName: '' });
  protected readonly newWbsDraft = signal<NewWbs>({ code: '', name: '', description: '' });
  protected readonly language = signal<'en' | 'tr'>('en');
  protected readonly projectCode = signal('MAR-001');
  protected readonly projectStatus = signal('DRAFT');
  protected readonly projectName = signal('Marine Excavation — Phase 1');
  protected readonly projectDescription = signal('Demo marine excavation project');
  protected readonly projectStart = signal('2026-08-03');
  protected readonly projectEnd = signal('2026-09-26');
  protected readonly currencyCode = signal('USD');
  protected readonly usdTryRate = signal<number | null>(null);
  protected readonly eurTryRate = signal<number | null>(null);
  protected readonly settingsSaving = signal(false);
  protected readonly settingsMessage = signal('');
  protected readonly settings = computed<ProjectSettings>(() => ({ code: this.projectCode(), name: this.projectName(), description: this.projectDescription(), start: this.projectStart(), end: this.projectEnd(), currencyCode: this.currencyCode() as 'USD' | 'TRY' | 'EUR', usdTryRate: this.usdTryRate(), eurTryRate: this.eurTryRate(), status: this.projectStatus() }));
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
    const authParameters = new URLSearchParams(location.search);
    if (authParameters.has('verify') || authParameters.has('reset')) { this.auth.logout(); return; }
    this.auth.restore().subscribe(user => { if (user && user.role !== 'ADMIN') this.loadProjectData(); });
  }

  protected onAuthenticated(user: AuthUser): void {
    this.currentPage.set(user.role === 'MANAGER' ? 'overview' : 'schedule');
    if (user.role !== 'ADMIN') this.loadProjectData();
  }

  protected logout(): void { this.auth.logout(); this.scheduleContext = null; this.resources.set([]); }
  protected isEngineer(): boolean { return this.auth.user()?.role === 'ENGINEER'; }
  protected initials(): string { return this.auth.user()?.fullName.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase() ?? ''; }

  private loadProjectData(projectId?: string): void {
    this.api.listProjects().pipe(retry({ count: 15, delay: 1000 }), catchError(() => of([]))).subscribe(projects => this.projects.set(projects));
    this.api.loadSchedule(projectId).pipe(retry({ count: 15, delay: 1000 }), catchError(() => of(null))).subscribe(schedule => { if (schedule) this.applySchedule(schedule); });
    this.api.listResources().pipe(retry({ count: 15, delay: 1000 }), catchError(() => of([]))).subscribe(resources => this.resources.set(resources));
  }

  protected switchProject(projectId: string): void { if (projectId === this.scheduleContext?.projectId) { this.projectMenuOpen.set(false); return; } this.projectMenuOpen.set(false); this.tasks.set([]); this.wbsItems.set([]); this.loadProjectData(projectId); }
  protected openNewProjectDialog(): void { const start = this.iso(new Date()); const end = new Date(); end.setMonth(end.getMonth() + 1); this.newProjectDraft.set({ code: '', name: '', description: '', start, end: this.iso(end), currencyCode: this.currencyCode() as NewProject['currencyCode'], wbsCode: '1', wbsName: '' }); this.projectActionError.set(''); this.projectMenuOpen.set(false); this.newProjectDialogOpen.set(true); }
  protected openWbsDialog(): void { this.newWbsDraft.set({ code: String(this.wbsItems().length + 1), name: '', description: '' }); this.projectActionError.set(''); this.projectMenuOpen.set(false); this.wbsDialogOpen.set(true); }
  protected updateNewProject(field: keyof NewProject, value: string): void { this.newProjectDraft.update(draft => ({ ...draft, [field]: value })); }
  protected updateNewWbs(field: keyof NewWbs, value: string): void { this.newWbsDraft.update(draft => ({ ...draft, [field]: value })); }
  protected createProject(): void {
    const draft = this.newProjectDraft(); this.projectActionError.set('');
    if (!draft.code.trim() || !draft.name.trim() || !draft.start || !draft.end || draft.end < draft.start || !draft.wbsCode.trim() || !draft.wbsName.trim()) { this.projectActionError.set('Complete the project dates and the initial WBS fields.'); return; }
    this.projectCreating.set(true);
    this.api.createProject(draft, this.language()).pipe(finalize(() => this.projectCreating.set(false))).subscribe({ next: schedule => { this.applySchedule(schedule); this.api.listProjects().subscribe(projects => this.projects.set(projects)); this.newProjectDialogOpen.set(false); this.currentPage.set('schedule'); }, error: error => this.projectActionError.set(error.error?.detail ?? 'Project could not be created.') });
  }
  protected createWbs(): void {
    const draft = this.newWbsDraft(); this.projectActionError.set('');
    if (!this.scheduleContext || !draft.code.trim() || !draft.name.trim()) { this.projectActionError.set('Enter a WBS code and name.'); return; }
    this.wbsCreating.set(true);
    this.api.addWbs(this.scheduleContext, draft).pipe(finalize(() => this.wbsCreating.set(false))).subscribe({ next: wbs => { this.wbsItems.update(items => [...items, wbs]); this.scheduleContext!.wbsItems.push(wbs); this.wbsDialogOpen.set(false); }, error: error => this.projectActionError.set(error.error?.detail ?? 'WBS could not be added.') });
  }

  private applySchedule(schedule: ScheduleData): void { this.scheduleContext = schedule; this.projectCode.set(schedule.projectCode); this.projectName.set(schedule.projectName); this.projectDescription.set(schedule.projectDescription); this.projectStatus.set(schedule.projectStatus); this.projectStart.set(schedule.projectStart); this.projectEnd.set(schedule.projectEnd); this.currencyCode.set(schedule.currencyCode); this.usdTryRate.set(schedule.usdTryRate); this.eurTryRate.set(schedule.eurTryRate); this.language.set(schedule.languageCode); this.tasks.set(schedule.tasks); this.wbsItems.set(schedule.wbsItems); }

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
  protected createCost(rate: NewCostRate): void {
    this.api.addResourceCost(rate).subscribe(cost => this.resources.update(resources => resources.map(resource => resource.id === rate.resourceId ? { ...resource, costs: [...resource.costs, cost] } : resource)));
  }
  protected saveSettings(event: { settings: ProjectSettings; language: 'en' | 'tr' }): void {
    if (!this.scheduleContext) { this.settingsMessage.set(event.language === 'tr' ? 'Proje verisi henüz yüklenmedi. Backend bağlantısını kontrol edip sayfayı yenileyin.' : 'Project data has not loaded yet. Check the backend connection and refresh.'); return; }
    this.settingsSaving.set(true); this.settingsMessage.set('');
    this.api.updateProjectSettings(this.scheduleContext, event.settings, event.language).pipe(
      switchMap(settings => this.api.listResources().pipe(map(resources => ({ settings, resources })))),
      finalize(() => this.settingsSaving.set(false)),
    ).subscribe({
      next: ({ settings, resources }) => {
        this.projectCode.set(settings.code); this.projectName.set(settings.name); this.projectDescription.set(settings.description); this.projectStart.set(settings.start); this.projectEnd.set(settings.end); this.currencyCode.set(settings.currencyCode); this.usdTryRate.set(settings.usdTryRate); this.eurTryRate.set(settings.eurTryRate); this.projectStatus.set(settings.status); this.language.set(event.language); this.resources.set(resources);
        this.scheduleContext!.projectCode = settings.code; this.scheduleContext!.projectName = settings.name; this.scheduleContext!.projectDescription = settings.description; this.scheduleContext!.projectStatus = settings.status; this.scheduleContext!.projectStart = settings.start; this.scheduleContext!.projectEnd = settings.end; this.scheduleContext!.currencyCode = settings.currencyCode; this.scheduleContext!.languageCode = event.language; this.scheduleContext!.usdTryRate = settings.usdTryRate; this.scheduleContext!.eurTryRate = settings.eurTryRate;
        this.settingsMessage.set(event.language === 'tr' ? 'Proje ayarları, kurlar ve dönüştürülmüş fiyatlar kaydedildi.' : 'Project settings, exchange rates and converted prices saved.');
      },
      error: () => this.settingsMessage.set(event.language === 'tr' ? 'Ayarlar kaydedilemedi.' : 'Settings could not be saved.'),
    });
  }

  protected openActivityDialog(): void {
    const start = this.iso(new Date());
    const endDate = new Date(); endDate.setDate(endDate.getDate() + 6);
    this.activityDraft.set({ wbsId: this.wbsItems()[0]?.id ?? '', code: '', name: '', type: 'WORK', start, end: this.iso(endDate) });
    this.activityError.set(this.scheduleContext ? '' : 'Create a project, estimate and WBS first so the activity has somewhere to be saved.');
    this.activityDialogOpen.set(true);
  }
  protected updateActivityDraft(field: keyof NewActivity, value: string): void { this.activityDraft.update(draft => ({ ...draft, [field]: value })); }
  protected formatDate(value: string): string { return new Date(`${value}T00:00:00`).toLocaleDateString(this.language() === 'tr' ? 'tr-TR' : 'en-GB', { day: '2-digit', month: 'long', year: 'numeric' }); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
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
