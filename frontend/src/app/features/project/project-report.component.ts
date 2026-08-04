import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ActivityResource, GanttTask } from '../gantt/gantt.models';
import { CostBreakdown, sumCosts, taskCosts } from './project-cost.utils';

interface ReportGroup { name: string; tasks: Array<{ task: GanttTask; costs: CostBreakdown }>; costs: CostBreakdown; }
@Component({ selector: 'app-project-report', templateUrl: './project-report.component.html', styleUrl: './project-report.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class ProjectReportComponent {
  readonly projectName = input.required<string>();
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly tasks = input<GanttTask[]>([]);
  readonly resources = input<ActivityResource[]>([]);
  protected readonly groups = computed<ReportGroup[]>(() => [...new Set(this.tasks().map(task => task.wbs))].map(name => { const tasks = this.tasks().filter(task => task.wbs === name).map(task => ({ task, costs: taskCosts(task, this.resources()) })); return { name, tasks, costs: sumCosts(tasks.map(item => item.costs)) }; }));
  protected readonly total = computed(() => sumCosts(this.groups().map(group => group.costs)));
  protected money(value: number): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 0 }).format(value); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
