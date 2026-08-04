import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ActivityResource, GanttTask } from '../gantt/gantt.models';
import { sumCosts, taskCosts } from './project-cost.utils';

@Component({ selector: 'app-project-overview', templateUrl: './project-overview.component.html', styleUrl: './project-overview.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class ProjectOverviewComponent {
  readonly projectName = input.required<string>();
  readonly projectStart = input('');
  readonly projectEnd = input('');
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly tasks = input<GanttTask[]>([]);
  readonly resources = input<ActivityResource[]>([]);
  protected readonly groups = computed(() => [...new Set(this.tasks().map(task => task.wbs))].map(name => ({ name, tasks: this.tasks().filter(task => task.wbs === name) })));
  protected readonly assignmentCount = computed(() => this.tasks().reduce((sum, task) => sum + task.assignments.length, 0));
  protected readonly totalCost = computed(() => sumCosts(this.tasks().map(task => taskCosts(task, this.resources()))).total);
  protected resourcesFor(task: GanttTask): Array<{ resource: ActivityResource; quantity: number }> { return task.assignments.map(assignment => ({ resource: this.resources().find(item => item.id === assignment.resourceId), quantity: assignment.quantity })).filter((item): item is { resource: ActivityResource; quantity: number } => !!item.resource); }
  protected taskTotal(task: GanttTask): string { return this.money(taskCosts(task, this.resources()).total); }
  protected formatDate(value: string): string { return new Date(`${value}T00:00:00`).toLocaleDateString(this.language() === 'tr' ? 'tr-TR' : 'en-GB', { day: '2-digit', month: 'long', year: 'numeric' }); }
  protected wbsNumber(index: number): string { return String(index + 1).padStart(2, '0'); }
  protected money(value: number): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 0 }).format(value); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
