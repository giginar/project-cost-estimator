import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CashFlowReport, PricingSummary } from '../../core/project-api.service';

@Component({
  selector: 'app-project-cash-flow',
  templateUrl: './project-cash-flow.component.html',
  styleUrl: './project-cash-flow.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProjectCashFlowComponent {
  readonly projectName = input.required<string>();
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly cashFlow = input<CashFlowReport | null>(null);
  readonly pricing = input<PricingSummary | null>(null);

  protected money(value: number): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 0 }).format(value); }
  protected monthLabel(value: string): string { return new Date(`${value}-01T00:00:00`).toLocaleDateString(this.language() === 'tr' ? 'tr-TR' : 'en-US', { month: 'short', year: 'numeric' }); }
  protected barWidth(value: number): number { const report = this.cashFlow(); const max = Math.max(1, ...(report?.months.flatMap(month => [month.income, month.expense]) ?? [1])); return Math.max(2, Math.abs(value) / max * 100); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
