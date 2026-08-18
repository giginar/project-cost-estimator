import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { catchError, finalize, forkJoin, map, of, retry, switchMap, tap } from 'rxjs';
import { GanttComponent } from './features/gantt/gantt.component';
import { ActivityResource, GanttTask, NewResource, ResourceSelection, ResourceType } from './features/gantt/gantt.models';
import { ResourceCatalogComponent } from './features/resources/resource-catalog.component';
import { ProjectOverviewComponent } from './features/project/project-overview.component';
import { ProjectCashFlowComponent } from './features/project/project-cash-flow.component';
import { ProjectReportComponent } from './features/project/project-report.component';
import { CostLibraryComponent } from './features/cost-library/cost-library.component';
import { ProjectSettingsComponent } from './features/settings/project-settings.component';
import { EstimateCostReport, EstimateResourceRate, NewActivity, NewCostRate, NewProject, NewWbs, ProjectApiService, ProjectOption, ProjectSettings, ScheduleData, WbsOption } from './core/project-api.service';
import { AuthService, AuthUser } from './core/auth.service';
import { AuthComponent } from './features/auth/auth.component';
import { UserAdminComponent } from './features/admin/user-admin.component';
import { PlanningComponent } from './features/planning/planning.component';
import { PricingComponent } from './features/pricing/pricing.component';
import { ActivityPlanningDraft, BoqDraft, BoqImportResult, BoqTraceabilityReport, CalendarSettings, CashFlowReport, CostCode, CostCodeDraft, DependencyDraft, EquipmentEconomicsDraft, GeneralUnitPrice, GeneralUnitPriceDraft, MaterialProcurementDraft, PricingRule, PricingRuleDraft, PricingSummary } from './core/project-api.service';

@Component({
  selector: 'app-root',
  imports: [GanttComponent, ResourceCatalogComponent, ProjectOverviewComponent, ProjectCashFlowComponent, ProjectReportComponent, CostLibraryComponent, ProjectSettingsComponent, PlanningComponent, PricingComponent, AuthComponent, UserAdminComponent],
  templateUrl: './app.html',
  styleUrls: ['./app.scss', './app-extra.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App implements OnInit {
  private readonly api = inject(ProjectApiService);
  protected readonly auth = inject(AuthService);
  protected scheduleContext: ScheduleData | null = null;
  protected readonly sidebarCollapsed = signal(false);
  protected readonly currentPage = signal<'overview' | 'schedule' | 'planning' | 'cost-library' | 'pricing' | 'cost-report' | 'cash-flow' | 'settings' | ResourceType>('schedule');
  protected readonly projects = signal<ProjectOption[]>([]);
  protected readonly projectMenuOpen = signal(false);
  protected readonly newProjectDialogOpen = signal(false);
  protected readonly wbsDialogOpen = signal(false);
  protected readonly projectCreating = signal(false);
  protected readonly wbsCreating = signal(false);
  protected readonly projectActionError = signal('');
  protected readonly newProjectDraft = signal<NewProject>({ code: '', name: '', description: '', start: '', end: '', currencyCode: 'USD' });
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
  // Demo data must come from the backend. Keeping this empty prevents a
  // hard-coded schedule from masking API/network failures.
  protected readonly tasks = signal<GanttTask[]>([]);
  protected readonly resources = signal<ActivityResource[]>([]);
  protected readonly resourceActionMessage = signal('');
  protected readonly costReport = signal<EstimateCostReport | null>(null);
  protected readonly cashFlow = signal<CashFlowReport | null>(null);
  protected readonly projectRates = signal<EstimateResourceRate[]>([]);
  protected readonly boqReport = signal<BoqTraceabilityReport | null>(null);
  protected readonly boqImporting = signal(false);
  protected readonly boqImportResult = signal<BoqImportResult | null>(null);
  protected readonly generalUnitPrices = signal<GeneralUnitPrice[]>([]);
  protected readonly costCodes = signal<CostCode[]>([]);
  protected readonly configurationMessage = signal('');
  protected readonly workCalendar = signal<CalendarSettings | null>(null);
  protected readonly pricingRules = signal<PricingRule[]>([]);
  protected readonly pricingSummary = signal<PricingSummary | null>(null);
  protected readonly activityDialogOpen = signal(false);
  protected readonly activitySaving = signal(false);
  protected readonly activityError = signal('');
  protected readonly wbsItems = signal<WbsOption[]>([]);
  protected readonly activityDraft = signal<NewActivity>({ wbsId: '', code: '', name: '', type: 'WORK', start: '', end: '', plannedQuantity: 0, quantityUnit: 'CUBIC_METER', dailyProductionRate: null, autoSchedule: false });

  ngOnInit(): void {
    const authParameters = new URLSearchParams(location.search);
    if (authParameters.has('verify') || authParameters.has('reset')) { this.auth.logout(); return; }
    this.auth.restore().subscribe(user => { if (user && user.role !== 'ADMIN') this.loadProjectData(); });
  }

  protected onAuthenticated(user: AuthUser): void {
    this.currentPage.set(user.role === 'MANAGER' ? 'overview' : 'schedule');
    if (user.role !== 'ADMIN') this.loadProjectData();
  }

  protected logout(): void { this.auth.logout(); this.scheduleContext = null; this.resources.set([]); this.costReport.set(null); this.cashFlow.set(null); this.projectRates.set([]); this.boqReport.set(null); this.generalUnitPrices.set([]); this.costCodes.set([]); this.workCalendar.set(null); this.pricingRules.set([]); this.pricingSummary.set(null); }
  protected isEngineer(): boolean { return this.auth.user()?.role === 'ENGINEER'; }
  protected initials(): string { return this.auth.user()?.fullName.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase() ?? ''; }

  private loadProjectData(projectId?: string): void {
    this.api.listProjects().pipe(
      retry({ count: 15, delay: 1000 }),
      tap(projects => this.projects.set(projects)),
      switchMap(projects => {
        const selectedId = projectId ?? projects.find(project => project.code === 'PORT-2027')?.id ?? projects[0]?.id;
        return selectedId ? this.api.loadSchedule(selectedId) : of(null);
      }),
      retry({ count: 3, delay: 1000 }),
      catchError(() => of(null)),
    ).subscribe(schedule => { if (schedule) this.applySchedule(schedule); });
  }

  protected switchProject(projectId: string): void { if (projectId === this.scheduleContext?.projectId) { this.projectMenuOpen.set(false); return; } this.projectMenuOpen.set(false); this.tasks.set([]); this.wbsItems.set([]); this.resources.set([]); this.resourceActionMessage.set(''); this.costReport.set(null); this.cashFlow.set(null); this.projectRates.set([]); this.boqReport.set(null); this.boqImportResult.set(null); this.generalUnitPrices.set([]); this.costCodes.set([]); this.workCalendar.set(null); this.pricingRules.set([]); this.pricingSummary.set(null); this.loadProjectData(projectId); }
  protected openNewProjectDialog(): void { const start = this.iso(new Date()); const end = new Date(); end.setMonth(end.getMonth() + 1); this.newProjectDraft.set({ code: '', name: '', description: '', start, end: this.iso(end), currencyCode: this.currencyCode() as NewProject['currencyCode'] }); this.projectActionError.set(''); this.projectMenuOpen.set(false); this.newProjectDialogOpen.set(true); }
  protected openWbsDialog(): void { this.newWbsDraft.set({ code: String(this.wbsItems().length + 1), name: '', description: '' }); this.projectActionError.set(''); this.projectMenuOpen.set(false); this.wbsDialogOpen.set(true); }
  protected updateNewProject(field: keyof NewProject, value: string): void { this.newProjectDraft.update(draft => ({ ...draft, [field]: value })); }
  protected updateNewWbs(field: keyof NewWbs, value: string): void { this.newWbsDraft.update(draft => ({ ...draft, [field]: value })); }
  protected createProject(): void {
    const draft = this.newProjectDraft(); this.projectActionError.set('');
    if (!draft.code.trim() || !draft.name.trim() || !draft.start || !draft.end || draft.end < draft.start) { this.projectActionError.set('Complete the required project fields and date range.'); return; }
    this.projectCreating.set(true);
    this.api.createProject(draft, this.language()).pipe(finalize(() => this.projectCreating.set(false))).subscribe({ next: schedule => { this.applySchedule(schedule); this.api.listProjects().subscribe(projects => this.projects.set(projects)); this.newProjectDialogOpen.set(false); this.currentPage.set('schedule'); }, error: error => this.projectActionError.set(error.error?.detail ?? 'Project could not be created.') });
  }
  protected createWbs(): void {
    const draft = this.newWbsDraft(); this.projectActionError.set('');
    if (!this.scheduleContext || !draft.code.trim() || !draft.name.trim()) { this.projectActionError.set('Enter a WBS code and name.'); return; }
    this.wbsCreating.set(true);
    this.api.addWbs(this.scheduleContext, draft).pipe(finalize(() => this.wbsCreating.set(false))).subscribe({ next: wbs => { this.wbsItems.update(items => [...items, wbs]); this.scheduleContext!.wbsItems.push(wbs); this.wbsDialogOpen.set(false); }, error: error => this.projectActionError.set(error.error?.detail ?? 'WBS could not be added.') });
  }

  private applySchedule(schedule: ScheduleData): void { this.scheduleContext = schedule; this.projectCode.set(schedule.projectCode); this.projectName.set(schedule.projectName); this.projectDescription.set(schedule.projectDescription); this.projectStatus.set(schedule.projectStatus); this.projectStart.set(schedule.projectStart); this.projectEnd.set(schedule.projectEnd); this.currencyCode.set(schedule.currencyCode); this.usdTryRate.set(schedule.usdTryRate); this.eurTryRate.set(schedule.eurTryRate); this.language.set(schedule.languageCode); this.tasks.set(schedule.tasks); this.wbsItems.set(schedule.wbsItems); this.refreshResources(); this.refreshCostReport(); this.refreshResourceRates(); this.refreshPhaseOne(); this.refreshPricing(); this.refreshProjectConfiguration(); }
  private refreshCostReport(): void { if (!this.scheduleContext) { this.costReport.set(null); this.cashFlow.set(null); return; } this.refreshCashFlow(); this.api.loadCostReport(this.scheduleContext).pipe(retry({ count: 3, delay: 1000 }), catchError(() => of(null))).subscribe(report => { this.costReport.set(report); this.refreshPricingSummary(); }); }
  private refreshCashFlow(): void { if (!this.scheduleContext) { this.cashFlow.set(null); return; } this.api.loadCashFlow(this.scheduleContext).pipe(retry({ count: 3, delay: 1000 }), catchError(() => of(null))).subscribe(report => this.cashFlow.set(report)); }
  private refreshProjectConfiguration(): void { if (!this.scheduleContext) return; forkJoin({ prices: this.api.listGeneralUnitPrices(this.scheduleContext.projectId), codes: this.api.listCostCodes(this.scheduleContext.projectId) }).pipe(catchError(() => of({ prices: [], codes: [] }))).subscribe(result => { this.generalUnitPrices.set(result.prices); this.costCodes.set(result.codes); }); }
  private refreshResourceRates(): void { if (!this.scheduleContext) { this.projectRates.set([]); return; } this.api.listResourceRates(this.scheduleContext).pipe(retry({ count: 3, delay: 1000 }), catchError(() => of([]))).subscribe(rates => this.projectRates.set(rates)); }
  private refreshResources(): void {
    if (!this.scheduleContext) { this.resources.set([]); return; }
    this.api.listResources(this.scheduleContext.projectId).pipe(
      switchMap(available => {
        const assignedIds = [...new Set(this.tasks().flatMap(task => task.assignments.map(assignment => assignment.resourceId)))];
        const missingIds = assignedIds.filter(id => !available.some(resource => resource.id === id));
        return missingIds.length ? forkJoin(missingIds.map(id => this.api.getResource(id, false).pipe(catchError(() => of(null))))).pipe(map(history => [...available, ...history.filter((resource): resource is ActivityResource => !!resource)])) : of(available);
      }), retry({ count: 3, delay: 1000 }), catchError(() => of([])),
    ).subscribe(resources => this.resources.set(resources));
  }
  private refreshPhaseOne(): void { if (!this.scheduleContext) { this.boqReport.set(null); this.workCalendar.set(null); return; } this.api.listBoq(this.scheduleContext).pipe(retry({ count: 3, delay: 1000 }), catchError(() => of(null))).subscribe(report => this.boqReport.set(report)); this.api.getCalendar(this.scheduleContext).pipe(retry({ count: 3, delay: 1000 }), catchError(() => of(null))).subscribe(calendar => this.workCalendar.set(calendar)); }
  private refreshPricing(): void { if (!this.scheduleContext) return; this.api.listPricingRules(this.scheduleContext).pipe(retry({ count: 3, delay: 1000 }), catchError(() => of([]))).subscribe(rules => this.pricingRules.set(rules)); this.refreshPricingSummary(); }
  private refreshPricingSummary(): void { if (!this.scheduleContext) { this.pricingSummary.set(null); return; } this.api.loadPricingSummary(this.scheduleContext).pipe(retry({ count: 3, delay: 1000 }), catchError(() => of(null))).subscribe(summary => this.pricingSummary.set(summary)); }
  private reloadSchedule(): void { if (!this.scheduleContext) return; this.api.loadSchedule(this.scheduleContext.projectId).subscribe(schedule => { if (schedule) this.applySchedule(schedule); }); }

  protected updateTask(updated: GanttTask): void {
    this.tasks.update(tasks => tasks.map(task => task.id === updated.id ? { ...updated, autoSchedule: false } : task));
    if (this.scheduleContext) this.api.updateDates(this.scheduleContext, updated).subscribe(() => this.reloadSchedule());
  }
  protected addResources(selection: ResourceSelection): void {
    if (!this.scheduleContext) return;
    selection.assignments.forEach(draft => {
      this.api.saveAssignment(this.scheduleContext!, selection.task, draft).subscribe(assignment => {
        this.tasks.update(tasks => tasks.map(task => task.id === selection.task.id ? { ...task, assignments: draft.id ? task.assignments.map(item => item.id === draft.id ? assignment : item) : [...task.assignments, assignment] } : task));
        this.refreshCostReport(); this.refreshResourceRates();
      });
    });
    selection.removeAssignmentIds.forEach(assignmentId => {
      this.api.unassignResource(this.scheduleContext!, selection.task.id, assignmentId).subscribe(() => {
        this.tasks.update(tasks => tasks.map(task => task.id === selection.task.id ? { ...task, assignments: task.assignments.filter(item => item.id !== assignmentId) } : task));
        this.refreshCostReport();
      });
    });
  }
  protected createResource(resource: NewResource): void {
    this.resourceActionMessage.set(''); this.api.createResource(resource, this.currencyCode(), this.scheduleContext).subscribe({ next: created => this.resources.update(resources => [...resources, created]), error: error => this.resourceActionMessage.set(error.error?.detail ?? this.t('Resource could not be created.', 'Kaynak oluşturulamadı.')) });
  }
  protected updateResourceSharing(event: { resourceId: string; shared: boolean }): void { if (!this.scheduleContext) return; this.resourceActionMessage.set(''); this.api.updateResourceSharing(event.resourceId, this.scheduleContext.projectId, event.shared).subscribe({ next: updated => this.replaceResource(updated), error: error => this.resourceActionMessage.set(error.error?.detail ?? this.t('Resource scope could not be changed.', 'Kaynak kapsamı değiştirilemedi.')) }); }
  protected deleteResource(resourceId: string): void { if (!this.scheduleContext) return; this.resourceActionMessage.set(''); this.api.deleteResource(resourceId, this.scheduleContext.projectId).subscribe({ next: () => { this.resources.update(resources => resources.filter(resource => resource.id !== resourceId)); this.resourceActionMessage.set(this.t('Resource deleted.', 'Kaynak silindi.')); }, error: error => this.resourceActionMessage.set(error.error?.detail ?? this.t('Resource could not be deleted.', 'Kaynak silinemedi.')) }); }
  protected saveCost(rate: NewCostRate): void {
    this.api.addResourceCost(rate, this.currencyCode(), this.scheduleContext).subscribe(cost => { this.resources.update(resources => resources.map(resource => resource.id === rate.resourceId ? { ...resource, costs: rate.id ? resource.costs.map(value => value.id === cost.id ? cost : value) : [...resource.costs, cost] } : resource)); this.refreshCostReport(); this.refreshResourceRates(); });
  }
  protected syncProjectRates(event: { resourceId: string; replaceExisting: boolean }): void { if (!this.scheduleContext) return; this.api.syncResourceRates(this.scheduleContext, event.resourceId, event.replaceExisting).subscribe(() => { this.refreshResourceRates(); this.refreshCostReport(); }); }
  protected updateProjectRate(event: { sourceCostComponentId: string; unitPrice: number }): void { if (!this.scheduleContext) return; this.api.updateResourceRate(this.scheduleContext, event.sourceCostComponentId, event.unitPrice).subscribe(updated => { this.projectRates.update(rates => rates.map(rate => rate.id === updated.id ? updated : rate)); this.refreshCostReport(); }); }
  protected saveEquipmentEconomics(draft: EquipmentEconomicsDraft): void { this.api.updateEquipmentEconomics(draft, this.scheduleContext).subscribe(updated => { this.replaceResource(updated); this.refreshCostReport(); this.refreshResourceRates(); }); }
  protected saveMaterialProcurement(draft: MaterialProcurementDraft): void { this.api.updateMaterialProcurement(draft).subscribe(updated => this.replaceResource(updated)); }
  protected savePricingRule(draft: PricingRuleDraft): void { if (!this.scheduleContext) return; this.api.savePricingRule(this.scheduleContext, draft).subscribe(() => this.refreshPricing()); }
  protected deletePricingRule(id: string): void { if (!this.scheduleContext) return; this.api.deletePricingRule(this.scheduleContext, id).subscribe(() => this.refreshPricing()); }
  private replaceResource(updated: ActivityResource): void { this.resources.update(resources => resources.map(resource => resource.id === updated.id ? updated : resource)); }
  protected saveBoq(draft: BoqDraft): void { if (!this.scheduleContext) return; this.api.saveBoq(this.scheduleContext, draft).subscribe(() => this.reloadSchedule()); }
  protected deleteBoq(id: string): void { if (!this.scheduleContext) return; this.api.deleteBoq(this.scheduleContext, id).subscribe(() => { this.refreshPhaseOne(); this.refreshCashFlow(); }); }
  protected importBoq(file: File): void { if (!this.scheduleContext) return; this.boqImporting.set(true); this.boqImportResult.set(null); this.api.importBoq(this.scheduleContext, file).pipe(finalize(() => this.boqImporting.set(false))).subscribe({ next: result => { this.boqImportResult.set(result); if (!result.issues.length) this.reloadSchedule(); }, error: error => this.boqImportResult.set({ preview: true, itemCount: 0, createdWbsCount: 0, issues: [{ rowNumber: 0, message: error.error?.detail ?? this.t('Spreadsheet could not be imported.', 'Excel dosyası içe aktarılamadı.') }] }) }); }
  protected saveActivityPlanning(draft: ActivityPlanningDraft): void { if (!this.scheduleContext) return; const task = this.tasks().find(value => value.id === draft.activityId); if (!task) return; this.api.updateActivityPlanning(this.scheduleContext, draft, task.wbs).subscribe(() => this.reloadSchedule()); }
  protected addDependency(draft: DependencyDraft): void { if (!this.scheduleContext) return; this.api.addDependency(this.scheduleContext, draft).subscribe(() => this.reloadSchedule()); }
  protected deleteDependency(event: { activityId: string; dependencyId: string }): void { if (!this.scheduleContext) return; this.api.deleteDependency(this.scheduleContext, event.activityId, event.dependencyId).subscribe(() => this.reloadSchedule()); }
  protected saveCalendar(calendar: CalendarSettings): void { if (!this.scheduleContext) return; this.api.updateCalendar(this.scheduleContext, calendar).subscribe(() => this.reloadSchedule()); }
  protected saveGeneralUnitPrice(draft: GeneralUnitPriceDraft): void { if (!this.scheduleContext) return; this.configurationMessage.set(''); this.api.saveGeneralUnitPrice(this.scheduleContext.projectId, draft).subscribe({ next: () => { this.refreshProjectConfiguration(); this.refreshCostReport(); this.configurationMessage.set(this.t('General unit price saved.', 'Genel birim fiyatı kaydedildi.')); }, error: error => this.configurationMessage.set(error.error?.detail ?? this.t('General unit price could not be saved.', 'Genel birim fiyatı kaydedilemedi.')) }); }
  protected deleteGeneralUnitPrice(id: string): void { if (!this.scheduleContext) return; this.api.deleteGeneralUnitPrice(this.scheduleContext.projectId, id).subscribe({ next: () => { this.refreshProjectConfiguration(); this.refreshCostReport(); }, error: error => this.configurationMessage.set(error.error?.detail ?? this.t('General unit price could not be deleted.', 'Genel birim fiyatı silinemedi.')) }); }
  protected saveCostCode(draft: CostCodeDraft): void { if (!this.scheduleContext) return; this.configurationMessage.set(''); this.api.saveCostCode(this.scheduleContext.projectId, draft).subscribe({ next: () => { this.refreshProjectConfiguration(); this.refreshCashFlow(); this.configurationMessage.set(this.t('Cost code saved.', 'Maliyet kodu kaydedildi.')); }, error: error => this.configurationMessage.set(error.error?.detail ?? this.t('Cost code could not be saved.', 'Maliyet kodu kaydedilemedi.')) }); }
  protected deleteCostCode(id: string): void { if (!this.scheduleContext) return; this.api.deleteCostCode(this.scheduleContext.projectId, id).subscribe({ next: () => { this.refreshProjectConfiguration(); this.refreshCashFlow(); }, error: error => this.configurationMessage.set(error.error?.detail ?? this.t('Cost code could not be deleted.', 'Maliyet kodu silinemedi.')) }); }
  protected saveSettings(event: { settings: ProjectSettings; language: 'en' | 'tr' }): void {
    if (!this.scheduleContext) { this.settingsMessage.set(event.language === 'tr' ? 'Proje verisi henüz yüklenmedi. Backend bağlantısını kontrol edip sayfayı yenileyin.' : 'Project data has not loaded yet. Check the backend connection and refresh.'); return; }
    this.settingsSaving.set(true); this.settingsMessage.set('');
    this.api.updateProjectSettings(this.scheduleContext, event.settings, event.language).pipe(
      switchMap(settings => this.api.listResources(this.scheduleContext!.projectId).pipe(map(resources => ({ settings, resources })))),
      finalize(() => this.settingsSaving.set(false)),
    ).subscribe({
      next: ({ settings, resources }) => {
        this.projectCode.set(settings.code); this.projectName.set(settings.name); this.projectDescription.set(settings.description); this.projectStart.set(settings.start); this.projectEnd.set(settings.end); this.currencyCode.set(settings.currencyCode); this.usdTryRate.set(settings.usdTryRate); this.eurTryRate.set(settings.eurTryRate); this.projectStatus.set(settings.status); this.language.set(event.language); this.resources.set(resources); this.refreshCostReport(); this.refreshResourceRates();
        this.scheduleContext!.projectCode = settings.code; this.scheduleContext!.projectName = settings.name; this.scheduleContext!.projectDescription = settings.description; this.scheduleContext!.projectStatus = settings.status; this.scheduleContext!.projectStart = settings.start; this.scheduleContext!.projectEnd = settings.end; this.scheduleContext!.currencyCode = settings.currencyCode; this.scheduleContext!.languageCode = event.language; this.scheduleContext!.usdTryRate = settings.usdTryRate; this.scheduleContext!.eurTryRate = settings.eurTryRate;
        this.settingsMessage.set(event.language === 'tr' ? 'Proje ayarları, kurlar ve dönüştürülmüş fiyatlar kaydedildi.' : 'Project settings, exchange rates and converted prices saved.');
      },
      error: () => this.settingsMessage.set(event.language === 'tr' ? 'Ayarlar kaydedilemedi.' : 'Settings could not be saved.'),
    });
  }

  protected openActivityDialog(): void {
    if (!this.wbsItems().length) { this.projectActionError.set(this.t('Add a WBS before creating an activity.', 'Aktivite oluşturmadan önce bir WBS ekleyin.')); this.openWbsDialog(); return; }
    const start = this.iso(new Date());
    const endDate = new Date(); endDate.setDate(endDate.getDate() + 6);
    this.activityDraft.set({ wbsId: this.wbsItems()[0]?.id ?? '', code: '', name: '', type: 'WORK', start, end: this.iso(endDate), plannedQuantity: 0, quantityUnit: 'CUBIC_METER', dailyProductionRate: null, autoSchedule: false });
    this.activityError.set(this.scheduleContext ? '' : 'Create a project, estimate and WBS first so the activity has somewhere to be saved.');
    this.activityDialogOpen.set(true);
  }
  protected updateActivityDraft(field: keyof NewActivity, value: string | number | boolean | null): void { this.activityDraft.update(draft => ({ ...draft, [field]: value })); }
  protected formatDate(value: string): string { return new Date(`${value}T00:00:00`).toLocaleDateString(this.language() === 'tr' ? 'tr-TR' : 'en-GB', { day: '2-digit', month: 'long', year: 'numeric' }); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
  protected createActivity(): void {
    const draft = this.activityDraft();
    if (!this.scheduleContext || !draft.wbsId || !draft.code.trim() || !draft.name.trim() || !draft.start || !draft.end) { this.activityError.set('Complete all required fields.'); return; }
    if (draft.end < draft.start) { this.activityError.set('Finish date cannot be before start date.'); return; }
    if (draft.autoSchedule && (draft.plannedQuantity <= 0 || !draft.dailyProductionRate || draft.dailyProductionRate <= 0)) { this.activityError.set(this.t('Automatic scheduling requires a positive quantity and daily production capacity.', 'Otomatik planlama için pozitif miktar ve günlük üretim kapasitesi gereklidir.')); return; }
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
