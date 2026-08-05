import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ActivityPlanningDraft, BoqDraft, BoqItem, CalendarSettings, DependencyDraft, WbsOption } from '../../core/project-api.service';
import { GanttTask } from '../gantt/gantt.models';

@Component({ selector: 'app-planning', templateUrl: './planning.component.html', styleUrl: './planning.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class PlanningComponent {
  readonly boqItems = input<BoqItem[]>([]); readonly totalBoqValue = input(0); readonly wbsItems = input<WbsOption[]>([]); readonly tasks = input<GanttTask[]>([]);
  readonly calendar = input<CalendarSettings | null>(null); readonly currency = input('USD'); readonly language = input<'en' | 'tr'>('en'); readonly readonly = input(false);
  readonly boqSave = output<BoqDraft>(); readonly boqDelete = output<string>(); readonly planningSave = output<ActivityPlanningDraft>();
  readonly dependencyAdd = output<DependencyDraft>(); readonly dependencyDelete = output<{ activityId: string; dependencyId: string }>(); readonly calendarSave = output<CalendarSettings>();
  protected readonly boqDialogOpen = signal(false); protected readonly dependencyDialogOpen = signal(false);
  protected readonly boqDraft = signal<BoqDraft>({ id: null, code: '', description: '', unit: 'CUBIC_METER', quantity: 0, unitPrice: 0, currencyCode: 'USD', wbsId: '', activityId: null });
  protected readonly planDrafts = signal<Record<string, ActivityPlanningDraft>>({});
  protected readonly dependencyDraft = signal<DependencyDraft>({ activityId: '', predecessorActivityId: '', type: 'FINISH_TO_START', lagDays: 0 });
  protected readonly calendarOverride = signal<CalendarSettings | null>(null);
  protected readonly linkedCount = computed(() => this.boqItems().filter(item => item.activityId).length);
  protected readonly units = ['PIECE', 'KILOGRAM', 'TON', 'LITER', 'METER', 'SQUARE_METER', 'CUBIC_METER'];

  protected openBoq(item?: BoqItem): void { this.boqDraft.set(item ? { id: item.id, code: item.code, description: item.description, unit: item.unit, quantity: item.quantity, unitPrice: item.unitPrice, currencyCode: item.currencyCode, wbsId: item.wbsId, activityId: item.activityId } : { id: null, code: '', description: '', unit: 'CUBIC_METER', quantity: 0, unitPrice: 0, currencyCode: this.currency(), wbsId: this.wbsItems()[0]?.id ?? '', activityId: null }); this.boqDialogOpen.set(true); }
  protected updateBoq(field: keyof BoqDraft, value: string | number | null): void { this.boqDraft.update(draft => ({ ...draft, [field]: value })); }
  protected submitBoq(): void { const draft = this.boqDraft(); if (!draft.code.trim() || !draft.description.trim() || !draft.wbsId || draft.quantity < 0 || draft.unitPrice < 0) return; this.boqSave.emit({ ...draft, code: draft.code.trim(), description: draft.description.trim() }); this.boqDialogOpen.set(false); }
  protected activitiesForWbs(wbsId: string): GanttTask[] { const wbs = this.wbsItems().find(item => item.id === wbsId); return wbs ? this.tasks().filter(task => task.wbs === wbs.name.toUpperCase()) : []; }
  protected plan(task: GanttTask): ActivityPlanningDraft { return this.planDrafts()[task.id] ?? { activityId: task.id, plannedQuantity: task.plannedQuantity ?? 0, quantityUnit: task.quantityUnit ?? 'CUBIC_METER', dailyProductionRate: task.dailyProductionRate ?? 0, autoSchedule: task.autoSchedule ?? false, plannedStartDate: task.start }; }
  protected updatePlan(task: GanttTask, field: keyof ActivityPlanningDraft, value: string | number | boolean): void { this.planDrafts.update(values => ({ ...values, [task.id]: { ...this.plan(task), [field]: value } })); }
  protected savePlan(task: GanttTask): void { const value = this.plan(task); if (value.plannedQuantity < 0 || value.dailyProductionRate <= 0 || !value.plannedStartDate) return; this.planningSave.emit(value); }
  protected openDependency(): void { const successor = this.tasks()[1] ?? this.tasks()[0]; const predecessor = this.tasks().find(task => task.id !== successor?.id); this.dependencyDraft.set({ activityId: successor?.id ?? '', predecessorActivityId: predecessor?.id ?? '', type: 'FINISH_TO_START', lagDays: 0 }); this.dependencyDialogOpen.set(true); }
  protected updateDependency(field: keyof DependencyDraft, value: string | number): void { this.dependencyDraft.update(draft => ({ ...draft, [field]: value })); }
  protected submitDependency(): void { const draft = this.dependencyDraft(); if (!draft.activityId || !draft.predecessorActivityId || draft.activityId === draft.predecessorActivityId || draft.lagDays < 0) return; this.dependencyAdd.emit(draft); this.dependencyDialogOpen.set(false); }
  protected predecessorOptions(): GanttTask[] { return this.tasks().filter(task => task.id !== this.dependencyDraft().activityId); }
  protected calendarDraft(): CalendarSettings { return this.calendarOverride() ?? this.calendar() ?? { name: 'Standard calendar', workingDaysPerWeek: 5, workingHoursPerDay: 8, shifts: [{ name: 'Day shift', startTime: '08:00:00', endTime: '17:00:00', paidHours: 8 }] }; }
  protected updateCalendar(field: keyof CalendarSettings, value: string | number): void { this.calendarOverride.set({ ...this.calendarDraft(), [field]: value }); }
  protected updateShift(index: number, field: 'name' | 'startTime' | 'endTime' | 'paidHours', value: string | number): void { const draft = this.calendarDraft(); this.calendarOverride.set({ ...draft, shifts: draft.shifts.map((shift, position) => position === index ? { ...shift, [field]: value } : shift) }); }
  protected addShift(): void { const draft = this.calendarDraft(); this.calendarOverride.set({ ...draft, shifts: [...draft.shifts, { name: `Shift ${draft.shifts.length + 1}`, startTime: '16:00:00', endTime: '00:00:00', paidHours: 8 }] }); }
  protected removeShift(index: number): void { const draft = this.calendarDraft(); if (draft.shifts.length <= 1) return; this.calendarOverride.set({ ...draft, shifts: draft.shifts.filter((_, position) => position !== index) }); }
  protected submitCalendar(): void { this.calendarSave.emit(this.calendarDraft()); this.calendarOverride.set(null); }
  protected effectiveHours(): number { return this.calendarDraft().shifts.reduce((total, shift) => total + shift.paidHours, 0); }
  protected money(value: number, currency = this.currency()): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(value); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
