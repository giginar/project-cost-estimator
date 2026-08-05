import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { ActivityResource, NewResource, ResourceType } from '../gantt/gantt.models';

@Component({
  selector: 'app-resource-catalog',
  templateUrl: './resource-catalog.component.html',
  styleUrl: './resource-catalog.component.scss',
  styles: [`
    .table-head, .catalog-table article { grid-template-columns: 105px minmax(190px, 1.2fr) minmax(130px, .7fr) minmax(210px, 1fr) 260px; }
    .resource-scope { display: flex !important; align-items: center; justify-content: space-between; gap: 10px; }
    .resource-scope > b { padding: 7px 10px; border-radius: 999px; background: #f1f4f6; color: #667b88 !important; font-size: 12px !important; white-space: nowrap; }
    .resource-scope.shared > b { background: #e9f7f4; color: #168f86 !important; }
    .scope-actions { display: flex; align-items: center; justify-content: flex-end; gap: 7px; }
    .resource-scope button { min-height: 36px; padding: 7px 10px; border: 1px solid #cfe0e4; border-radius: 6px; background: white; color: #52707f; font-size: 12px; font-weight: 700; cursor: pointer; white-space: nowrap; }
    .resource-scope button.delete-resource { border-color: #eccccc; color: #b94e4e; }
    .resource-scope button:disabled { border-color: #e2e7e9; background: #f4f6f7; color: #9aa7ae; cursor: not-allowed; }
    .resource-action-message { margin: -8px 0 14px; padding: 12px 15px; border: 1px solid #e9d4b3; border-radius: 7px; background: #fff9ef; color: #8a622b; font-size: 13px; }
    .delete-dialog { width: min(430px, 100%); padding: 22px; border-radius: 11px; background: white; box-shadow: 0 20px 60px #102c3b40; }
    .delete-dialog small { color: #bd5656; font-size: 12px; font-weight: 800; letter-spacing: .1em; }.delete-dialog h2 { margin: 6px 0 10px; color: #263e50; font: 700 21px var(--heading-font); }.delete-dialog p { margin: 0; color: #647b89; font-size: 14px; line-height: 1.55; }.delete-dialog footer { display: flex; justify-content: flex-end; gap: 10px; margin-top: 22px; }.delete-dialog button { min-height: 40px; padding: 10px 15px; border-radius: 7px; font-size: 13px; font-weight: 700; cursor: pointer; }.delete-dialog .cancel { border: 1px solid #dce4e8; background: white; }.delete-dialog .danger { border: 1px solid #b94e4e; background: #b94e4e; color: white; }
    .sharing-option { display: flex !important; align-items: center; gap: 10px; padding: 11px; border: 1px solid #dce6e8; border-radius: 7px; background: #f7fbfa; }
    .sharing-option input { width: 17px !important; height: 17px !important; }
    .sharing-option > span { display: grid; gap: 3px; text-transform: none !important; }
    .sharing-option b { color: #365264; font-size: 9px; }.sharing-option small { color: #8797a0; font-size: 8px; font-weight: 400; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResourceCatalogComponent {
  readonly type = input.required<ResourceType>();
  readonly resources = input<ActivityResource[]>([]);
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly readonly = input(false);
  readonly activeProjectId = input<string | null>(null);
  readonly actionMessage = input('');
  readonly resourceCreate = output<NewResource>();
  readonly sharingChange = output<{ resourceId: string; shared: boolean }>();
  readonly resourceDelete = output<string>();
  protected readonly dialogOpen = signal(false);
  protected readonly deleteCandidate = signal<ActivityResource | null>(null);
  protected readonly search = signal('');
  protected readonly draft = signal<NewResource>({ type: 'personnel', code: '', name: '', description: '', subtype: '', defaultUnit: 'PIECE', shared: false, unitPrice: null, calculationBasis: 'PER_DAY', fuelType: 'DIESEL', fuelConsumption: null, standbyFuelConsumption: null, fuelUnitPrice: null });
  protected readonly filteredResources = computed(() => {
    const term = this.search().trim().toLowerCase();
    const resources = this.resources().filter(resource => resource.type === this.type());
    return term ? resources.filter(resource => `${resource.code} ${resource.name} ${resource.subtype}`.toLowerCase().includes(term)) : resources;
  });

  protected title(): string { return this.type() === 'personnel' ? this.t('Personnel', 'Personel') : this.type() === 'equipment' ? this.t('Equipment', 'Ekipman') : this.t('Material', 'Malzeme'); }
  protected subtitle(): string { return this.type() === 'personnel' ? 'Manage people and trades available to project activities.' : this.type() === 'equipment' ? 'Manage machinery and equipment available to project activities.' : 'Manage consumable and permanent materials used by activities.'; }
  protected subtypeLabel(): string { return this.type() === 'personnel' ? this.t('Profession', 'Meslek') : this.type() === 'equipment' ? this.t('Equipment type', 'Ekipman türü') : this.t('Material type', 'Malzeme türü'); }
  protected economicSummary(resource: ActivityResource): string {
    const rate = resource.costs.find(cost => cost.category !== 'FUEL');
    const fuel = resource.fuelConsumptions[0];
    const values: string[] = [];
    if (rate) values.push(`${this.money(rate.unitPrice, rate.currencyCode)} / ${rate.calculationBasis.replace('PER_', '').toLowerCase()}`);
    if (fuel) values.push(`${this.t('work', 'çalışma')} ${fuel.consumptionPerHour} /h · ${this.t('standby', 'bekleme')} ${fuel.standbyConsumptionPerHour ?? 0} /h ${fuel.fuelType.replace('_', ' ').toLowerCase()}`);
    return values.join(' · ') || 'No economic data';
  }
  protected openDialog(): void { this.draft.set({ type: this.type(), code: '', name: '', description: '', subtype: '', defaultUnit: 'PIECE', shared: false, unitPrice: null, calculationBasis: this.type() === 'material' ? 'PER_UNIT' : 'PER_DAY', fuelType: 'DIESEL', fuelConsumption: null, standbyFuelConsumption: null, fuelUnitPrice: null }); this.dialogOpen.set(true); }
  protected updateDraft(field: keyof NewResource, value: string | number | boolean | null): void { this.draft.update(draft => ({ ...draft, [field]: value })); }
  protected canChangeSharing(resource: ActivityResource): boolean { return !this.readonly() && !!resource.ownerProjectId && resource.ownerProjectId === this.activeProjectId(); }
  protected canDelete(resource: ActivityResource): boolean { return this.canChangeSharing(resource) && resource.assignable !== false; }
  protected deleteReason(resource: ActivityResource): string {
    if (resource.assignable === false) return this.t('Historical resources cannot be deleted here.', 'Geçmiş kullanım kaynakları buradan silinemez.');
    if (!resource.ownerProjectId) return this.t('Protected system resource', 'Korumalı sistem kaynağı');
    if (resource.ownerProjectId !== this.activeProjectId()) return this.t('Only the owning project can delete this shared resource.', 'Bu ortak kaynağı yalnızca sahibi olan proje silebilir.');
    return this.t('Delete resource', 'Kaynağı sil');
  }
  protected confirmDelete(): void { const resource = this.deleteCandidate(); if (!resource) return; this.resourceDelete.emit(resource.id); this.deleteCandidate.set(null); }
  protected submit(): void {
    const value = this.draft();
    if (!value.code.trim() || !value.name.trim() || !value.subtype.trim()) return;
    this.resourceCreate.emit({ ...value, code: value.code.trim(), name: value.name.trim(), subtype: value.subtype.trim(), description: value.description.trim() });
    this.dialogOpen.set(false);
  }
  private money(value: number, currency = this.currency()): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(value); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
}
