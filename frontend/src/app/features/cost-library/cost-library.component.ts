import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { NewCostRate } from '../../core/project-api.service';
import { ActivityResource, ResourceCost } from '../gantt/gantt.models';

interface CostRow { resource: ActivityResource; cost: ResourceCost; }
@Component({ selector: 'app-cost-library', templateUrl: './cost-library.component.html', styleUrl: './cost-library.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class CostLibraryComponent {
  readonly resources = input<ActivityResource[]>([]);
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly costCreate = output<NewCostRate>();
  protected readonly dialogOpen = signal(false);
  protected readonly search = signal('');
  protected readonly typeFilter = signal('all');
  protected readonly draft = signal<NewCostRate>({ resourceId: '', category: 'OTHER', name: '', calculationBasis: 'PER_DAY', unitPrice: 0, unit: 'DAY' });
  protected readonly rows = computed<CostRow[]>(() => {
    const term = this.search().trim().toLowerCase(); const type = this.typeFilter();
    return this.resources().filter(resource => type === 'all' || resource.type === type)
      .flatMap(resource => resource.costs.map(cost => ({ resource, cost })))
      .filter(row => !term || `${row.resource.code} ${row.resource.name} ${row.cost.name} ${row.cost.category}`.toLowerCase().includes(term));
  });
  protected readonly averageRate = computed(() => this.rows().length ? this.rows().reduce((sum, row) => sum + row.cost.unitPrice, 0) / this.rows().length : 0);
  protected openDialog(): void { this.draft.set({ resourceId: this.resources()[0]?.id ?? '', category: 'OTHER', name: '', calculationBasis: 'PER_DAY', unitPrice: 0, unit: 'DAY' }); this.dialogOpen.set(true); }
  protected updateDraft(field: keyof NewCostRate, value: string | number): void { this.draft.update(draft => ({ ...draft, [field]: value })); }
  protected submit(): void { const value = this.draft(); if (!value.resourceId || !value.name.trim() || value.unitPrice < 0) return; this.costCreate.emit({ ...value, name: value.name.trim() }); this.dialogOpen.set(false); }
  protected money(value: number): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 2 }).format(value); }
  protected basis(value: string): string { return value.replace('PER_', '').replace('_', ' ').toLowerCase(); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
