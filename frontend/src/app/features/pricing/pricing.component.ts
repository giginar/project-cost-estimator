import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { PricingRule, PricingRuleDraft, PricingSummary } from '../../core/project-api.service';

@Component({ selector: 'app-pricing', templateUrl: './pricing.component.html', styleUrl: './pricing.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class PricingComponent {
  readonly rules = input<PricingRule[]>([]); readonly summary = input<PricingSummary | null>(null); readonly currency = input('USD'); readonly language = input<'en' | 'tr'>('en'); readonly readonly = input(false);
  readonly ruleSave = output<PricingRuleDraft>(); readonly ruleDelete = output<string>();
  protected readonly dialogOpen = signal(false); protected readonly draft = signal<PricingRuleDraft>(this.empty());
  protected open(rule?: PricingRule): void { this.draft.set(rule ? { ...rule } : { ...this.empty(), sequence: this.rules().length + 1 }); this.dialogOpen.set(true); }
  protected update(field: keyof PricingRuleDraft, value: string | number | boolean): void { this.draft.update(draft => ({ ...draft, [field]: value })); }
  protected submit(): void { const value = this.draft(); if (!value.name.trim() || value.percentage < 0) return; this.ruleSave.emit({ ...value, name: value.name.trim() }); this.dialogOpen.set(false); }
  protected money(value = 0): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 0 }).format(value); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
  private empty(): PricingRuleDraft { return { id: null, type: 'OVERHEAD', name: '', percentage: 0, base: 'ESTIMATED_COST', sequence: 1, active: true }; }
}
