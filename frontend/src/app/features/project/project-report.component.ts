import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CashFlowReport, CostBreakdownView, EstimateCostReport } from '../../core/project-api.service';

@Component({ selector: 'app-project-report', templateUrl: './project-report.component.html', styleUrls: ['./project-report.component.scss', './project-cash-flow.component.scss'], changeDetection: ChangeDetectionStrategy.OnPush })
export class ProjectReportComponent {
  readonly projectName = input.required<string>();
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly report = input<EstimateCostReport | null>(null);
  readonly cashFlow = input<CashFlowReport | null>(null);
  protected indirect(costs: CostBreakdownView): number { return costs.accommodationCost + costs.transportationCost + costs.overheadCost + costs.taxCost; }
  protected hasCost(costs: CostBreakdownView): boolean { return costs.totalCost !== 0; }
  protected money(value: number): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 0 }).format(value); }
  protected monthLabel(value: string): string { return new Date(`${value}-01T00:00:00`).toLocaleDateString(this.language() === 'tr' ? 'tr-TR' : 'en-US', { month: 'short', year: 'numeric' }); }
  protected barWidth(value: number): number { const report = this.cashFlow(); const max = Math.max(1, ...(report?.months.flatMap(month => [month.income, month.expense]) ?? [1])); return Math.max(2, Math.abs(value) / max * 100); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
