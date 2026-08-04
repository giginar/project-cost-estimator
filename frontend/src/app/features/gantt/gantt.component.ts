import { ChangeDetectionStrategy, Component, HostListener, computed, input, output, signal } from '@angular/core';
import { ActivityResource, GanttTask, ResourceSelection } from './gantt.models';

type ResizeEdge = 'start' | 'end';
type ZoomMode = 'month' | 'week' | 'day';
type TaskColumn = 'activity' | 'start' | 'finish' | 'days';
interface DragState { task: GanttTask; edge: ResizeEdge; originX: number; draft: GanttTask; }
interface ColumnResizeState { column: TaskColumn; originX: number; originWidth: number; }
interface TimelineCell { date: Date; label: string; weekend: boolean; }
interface MonthBand { label: string; days: number; }

const DAY_MS = 86_400_000;

@Component({
  selector: 'app-gantt',
  imports: [],
  templateUrl: './gantt.component.html',
  styleUrl: './gantt.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GanttComponent {
  readonly tasks = input.required<GanttTask[]>();
  readonly resources = input<ActivityResource[]>([]);
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly readonly = input(false);
  readonly taskChange = output<GanttTask>();
  readonly resourcesAdd = output<ResourceSelection>();
  protected readonly taskColumns: TaskColumn[] = ['activity', 'start', 'finish', 'days'];
  protected readonly zoomMode = signal<ZoomMode>('week');
  protected readonly dayWidth = computed(() => ({ month: 12, week: 34, day: 50 })[this.zoomMode()]);
  protected readonly drag = signal<DragState | null>(null);
  protected readonly columnResize = signal<ColumnResizeState | null>(null);
  protected readonly columnMenuOpen = signal(false);
  protected readonly visibleColumns = signal<Record<TaskColumn, boolean>>({ activity: true, start: true, finish: true, days: true });
  protected readonly columnWidths = signal<Record<TaskColumn, number>>({ activity: 220, start: 80, finish: 80, days: 60 });
  protected readonly search = signal('');
  protected readonly resourceDialogTask = signal<GanttTask | null>(null);
  protected readonly selectedResourceIds = signal<Set<string>>(new Set());
  protected readonly filteredTasks = computed(() => {
    const term = this.search().trim().toLowerCase();
    return term ? this.tasks().filter(task => `${task.code} ${task.name} ${task.wbs}`.toLowerCase().includes(term)) : this.tasks();
  });
  protected readonly rangeStart = computed(() => this.startOfWeek(this.addDays(new Date(`${this.earliestDate()}T00:00:00`), -5)));
  protected readonly rangeEnd = computed(() => this.addDays(new Date(`${this.latestDate()}T00:00:00`), 10));
  protected readonly days = computed<TimelineCell[]>(() => {
    const values: TimelineCell[] = [];
    for (let date = this.rangeStart(); date <= this.rangeEnd(); date = this.addDays(date, 1)) {
      values.push({ date, label: `${date.getDate()}`, weekend: date.getDay() === 0 || date.getDay() === 6 });
    }
    return values;
  });
  protected readonly months = computed<MonthBand[]>(() => {
    const values: MonthBand[] = [];
    this.days().forEach(day => {
      const label = day.date.toLocaleDateString(this.language() === 'tr' ? 'tr-TR' : 'en-GB', { month: 'long', year: 'numeric' });
      const last = values.at(-1);
      last?.label === label ? last.days++ : values.push({ label, days: 1 });
    });
    return values;
  });
  protected readonly timelineWidth = computed(() => this.days().length * this.dayWidth());
  protected readonly taskPanelWidth = computed(() => this.taskColumns.reduce((total, column) => total + (this.columnVisible(column) ? this.columnWidths()[column] : 0), 0));
  protected readonly taskGridColumns = computed(() => this.taskColumns.filter(column => this.columnVisible(column)).map(column => `${this.columnWidths()[column]}px`).join(' ') || '0px');
  protected readonly groups = computed(() => [...new Set(this.filteredTasks().map(task => task.wbs))]);

  protected tasksInGroup(group: string): GanttTask[] { return this.filteredTasks().filter(task => task.wbs === group); }
  protected displayTask(task: GanttTask): GanttTask { return this.drag()?.task.id === task.id ? this.drag()!.draft : task; }
  protected barLeft(task: GanttTask): number { return this.daysBetween(this.rangeStart(), new Date(`${this.displayTask(task).start}T00:00:00`)) * this.dayWidth(); }
  protected barWidth(task: GanttTask): number { const value = this.displayTask(task); return (this.daysBetween(new Date(`${value.start}T00:00:00`), new Date(`${value.end}T00:00:00`)) + 1) * this.dayWidth(); }
  protected isToday(date: Date): boolean { const now = new Date(); return date.toDateString() === now.toDateString(); }
  protected resourcesByType(type: ActivityResource['type']): ActivityResource[] { return this.resources().filter(resource => resource.type === type); }
  protected resourcesForTask(task: GanttTask): ActivityResource[] { return task.assignments.map(assignment => this.resources().find(resource => resource.id === assignment.resourceId)).filter((resource): resource is ActivityResource => !!resource); }
  protected rateSummary(resource: ActivityResource): string {
    const cost = resource.costs.find(item => item.category !== 'FUEL');
    return cost ? `${this.money(cost.unitPrice)} / ${cost.calculationBasis.replace('PER_', '').toLowerCase()}` : 'No rate entered';
  }
  protected fuelSummary(resource: ActivityResource): string {
    const fuel = resource.fuelConsumptions[0]; const price = resource.costs.find(item => item.category === 'FUEL');
    if (!fuel) return '';
    return `${fuel.fuelType.replace('_', ' ').toLowerCase()} · ${fuel.consumptionPerHour} ${fuel.consumptionUnit.toLowerCase()}/h${price ? ` · ${this.money(price.unitPrice)}/${price.unit?.toLowerCase()}` : ''}`;
  }
  protected estimatedCost(task: GanttTask, resource: ActivityResource): string {
    const days = this.daysBetween(new Date(`${task.start}T00:00:00`), new Date(`${task.end}T00:00:00`)) + 1;
    const assignment = task.assignments.find(item => item.resourceId === resource.id); if (!assignment) return '';
    const base = resource.costs.find(item => item.category !== 'FUEL');
    let total = base ? base.unitPrice * this.costFactor(base.calculationBasis, days, assignment.quantity) : 0;
    const fuel = resource.fuelConsumptions[0]; const fuelPrice = resource.costs.find(item => item.category === 'FUEL');
    if (fuel && fuelPrice) total += fuel.consumptionPerHour * fuelPrice.unitPrice * days * 8 * assignment.quantity;
    return total ? `Est. ${this.money(total)}` : '';
  }
  protected openResourceDialog(event: MouseEvent, task: GanttTask): void { event.preventDefault(); if (this.readonly()) return; this.selectedResourceIds.set(new Set(task.assignments.map(assignment => assignment.resourceId))); this.resourceDialogTask.set(task); }
  protected toggleResource(id: string): void { this.selectedResourceIds.update(current => { const next = new Set(current); next.has(id) ? next.delete(id) : next.add(id); return next; }); }
  protected addResources(): void {
    const task = this.resourceDialogTask(); if (!task) return;
    const currentIds = new Set(task.assignments.map(assignment => assignment.resourceId));
    const addResourceIds = [...this.selectedResourceIds()].filter(id => !currentIds.has(id));
    const removeAssignmentIds = task.assignments.filter(assignment => !this.selectedResourceIds().has(assignment.resourceId)).map(assignment => assignment.id);
    this.resourcesAdd.emit({ task, addResourceIds, removeAssignmentIds }); this.resourceDialogTask.set(null);
  }

  protected beginResize(event: PointerEvent, task: GanttTask, edge: ResizeEdge): void {
    if (this.readonly()) return;
    event.preventDefault(); event.stopPropagation();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.drag.set({ task, edge, originX: event.clientX, draft: { ...task } });
  }

  protected beginColumnResize(event: PointerEvent, column: TaskColumn): void {
    event.preventDefault(); event.stopPropagation();
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.columnResize.set({ column, originX: event.clientX, originWidth: this.columnWidths()[column] });
  }

  @HostListener('window:pointermove', ['$event'])
  protected resize(event: PointerEvent): void {
    const columnState = this.columnResize();
    if (columnState) {
      const minimum = columnState.column === 'activity' ? 130 : columnState.column === 'days' ? 48 : 68;
      const maximum = columnState.column === 'activity' ? 520 : columnState.column === 'days' ? 130 : 190;
      const width = Math.min(maximum, Math.max(minimum, columnState.originWidth + event.clientX - columnState.originX));
      this.columnWidths.update(current => ({ ...current, [columnState.column]: width }));
      return;
    }
    const state = this.drag(); if (!state) return;
    const deltaDays = Math.round((event.clientX - state.originX) / this.dayWidth());
    const start = new Date(`${state.task.start}T00:00:00`); const end = new Date(`${state.task.end}T00:00:00`);
    const draft = { ...state.task };
    if (state.edge === 'start') draft.start = this.iso(this.addDays(start, Math.min(deltaDays, this.daysBetween(start, end))));
    else draft.end = this.iso(this.addDays(end, Math.max(deltaDays, -this.daysBetween(start, end))));
    this.drag.set({ ...state, draft });
  }

  @HostListener('window:pointerup')
  protected finishResize(): void {
    if (this.columnResize()) { this.columnResize.set(null); return; }
    const state = this.drag(); if (!state) return;
    this.drag.set(null);
    if (state.draft.start !== state.task.start || state.draft.end !== state.task.end) this.taskChange.emit(state.draft);
  }

  protected setZoom(mode: ZoomMode): void { this.zoomMode.set(mode); }
  protected toggleColumnMenu(event: MouseEvent): void { event.stopPropagation(); this.columnMenuOpen.update(open => !open); }
  protected toggleColumn(column: TaskColumn): void { this.visibleColumns.update(current => ({ ...current, [column]: !current[column] })); }
  protected columnVisible(column: TaskColumn): boolean { return this.visibleColumns()[column]; }
  protected columnLabel(column: TaskColumn): string {
    const labels = {
      activity: this.t('Activity', 'Aktivite'), start: this.t('Start', 'Başlangıç'),
      finish: this.t('Finish', 'Bitiş'), days: this.t('Days', 'Gün'),
    };
    return labels[column];
  }
  protected shortDate(value: string): string { const [year, month, day] = value.split('-'); return `${day}.${month}.${year.slice(-2)}`; }
  protected weekdayLabel(date: Date): string { return date.toLocaleDateString(this.language() === 'tr' ? 'tr-TR' : 'en-GB', { weekday: 'short' }); }
  @HostListener('document:pointerdown')
  protected closeColumnMenu(): void { this.columnMenuOpen.set(false); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
  private earliestDate(): string { return this.tasks().map(task => task.start).sort()[0] ?? this.iso(new Date()); }
  private latestDate(): string { return this.tasks().map(task => task.end).sort().at(-1) ?? this.iso(new Date()); }
  private addDays(date: Date, count: number): Date { const next = new Date(date); next.setDate(next.getDate() + count); return next; }
  private startOfWeek(date: Date): Date { return this.addDays(date, -((date.getDay() + 6) % 7)); }
  private daysBetween(start: Date, end: Date): number { return Math.round((Date.UTC(end.getFullYear(), end.getMonth(), end.getDate()) - Date.UTC(start.getFullYear(), start.getMonth(), start.getDate())) / DAY_MS); }
  private iso(date: Date): string { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
  private money(value: number): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 2 }).format(value); }
  private costFactor(basis: string, days: number, quantity: number): number { if (basis === 'PER_HOUR') return days * 8 * quantity; if (basis === 'PER_WEEK') return days / 7 * quantity; if (basis === 'PER_MONTH') return days / 30 * quantity; if (basis === 'FIXED') return 1; if (basis === 'PER_UNIT') return quantity; return days * quantity; }
}
