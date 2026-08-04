import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ActivityResource, NewResource, ResourceType } from '../gantt/gantt.models';

@Component({
  selector: 'app-resource-catalog',
  templateUrl: './resource-catalog.component.html',
  styleUrl: './resource-catalog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResourceCatalogComponent {
  readonly type = input.required<ResourceType>();
  readonly resources = input<ActivityResource[]>([]);
  readonly currency = input('USD');
  readonly resourceCreate = output<NewResource>();
  protected readonly dialogOpen = signal(false);
  protected readonly search = signal('');
  protected readonly draft = signal<NewResource>({ type: 'personnel', code: '', name: '', description: '', subtype: '', defaultUnit: 'PIECE', unitPrice: null, calculationBasis: 'PER_DAY', fuelType: 'DIESEL', fuelConsumption: null, fuelUnitPrice: null });
  protected readonly filteredResources = computed(() => {
    const term = this.search().trim().toLowerCase();
    const resources = this.resources().filter(resource => resource.type === this.type());
    return term ? resources.filter(resource => `${resource.code} ${resource.name} ${resource.subtype}`.toLowerCase().includes(term)) : resources;
  });

  protected title(): string { return this.type() === 'personnel' ? 'Personnel' : this.type() === 'equipment' ? 'Equipment' : 'Material'; }
  protected subtitle(): string { return this.type() === 'personnel' ? 'Manage people and trades available to project activities.' : this.type() === 'equipment' ? 'Manage machinery and equipment available to project activities.' : 'Manage consumable and permanent materials used by activities.'; }
  protected subtypeLabel(): string { return this.type() === 'personnel' ? 'Profession' : this.type() === 'equipment' ? 'Equipment type' : 'Material type'; }
  protected economicSummary(resource: ActivityResource): string {
    const rate = resource.costs.find(cost => cost.category !== 'FUEL');
    const fuel = resource.fuelConsumptions[0];
    const values: string[] = [];
    if (rate) values.push(`${this.money(rate.unitPrice)} / ${rate.calculationBasis.replace('PER_', '').toLowerCase()}`);
    if (fuel) values.push(`${fuel.consumptionPerHour} ${fuel.consumptionUnit.toLowerCase()}/h ${fuel.fuelType.replace('_', ' ').toLowerCase()}`);
    return values.join(' · ') || 'No economic data';
  }
  protected openDialog(): void { this.draft.set({ type: this.type(), code: '', name: '', description: '', subtype: '', defaultUnit: 'PIECE', unitPrice: null, calculationBasis: this.type() === 'material' ? 'PER_UNIT' : 'PER_DAY', fuelType: 'DIESEL', fuelConsumption: null, fuelUnitPrice: null }); this.dialogOpen.set(true); }
  protected updateDraft(field: keyof NewResource, value: string | number | null): void { this.draft.update(draft => ({ ...draft, [field]: value })); }
  protected submit(): void {
    const value = this.draft();
    if (!value.code.trim() || !value.name.trim() || !value.subtype.trim()) return;
    this.resourceCreate.emit({ ...value, code: value.code.trim(), name: value.name.trim(), subtype: value.subtype.trim(), description: value.description.trim() });
    this.dialogOpen.set(false);
  }
  private money(value: number): string { return new Intl.NumberFormat('en-US', { style: 'currency', currency: this.currency(), maximumFractionDigits: 2 }).format(value); }
}
