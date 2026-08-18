import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CostBreakdownView, EstimateCostReport } from '../../core/project-api.service';

@Component({ selector: 'app-project-report', templateUrl: './project-report.component.html', styleUrl: './project-report.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class ProjectReportComponent {
  readonly projectName = input.required<string>();
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly report = input<EstimateCostReport | null>(null);
  protected indirect(costs: CostBreakdownView): number { return costs.accommodationCost + costs.transportationCost + costs.overheadCost + costs.taxCost; }
  protected hasCost(costs: CostBreakdownView): boolean { return costs.totalCost !== 0; }
  protected money(value: number): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 0 }).format(value); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
