import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { EquipmentEconomicsDraft, EstimateResourceRate, MaterialProcurementDraft, NewCostRate } from '../../core/project-api.service';
import { ActivityResource, ResourceCost } from '../gantt/gantt.models';

interface CostRow { resource: ActivityResource; cost: ResourceCost; projectRate: EstimateResourceRate | null; }
interface CostGroup { resource: ActivityResource; rows: CostRow[]; catalogTotal: number; projectTotal: number; catalogCurrency: string; syncedCount: number; }

@Component({ selector: 'app-cost-library', templateUrl: './cost-library.component.html', styleUrl: './cost-library.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class CostLibraryComponent {
  readonly resources = input<ActivityResource[]>([]);
  readonly projectRates = input<EstimateResourceRate[]>([]);
  readonly projectCost = input<number | null>(null);
  readonly currency = input('USD');
  readonly language = input<'en' | 'tr'>('en');
  readonly costSave = output<NewCostRate>();
  readonly projectRateSync = output<{ resourceId: string; replaceExisting: boolean }>();
  readonly projectRateUpdate = output<{ sourceCostComponentId: string; unitPrice: number }>();
  readonly equipmentEconomicsSave = output<EquipmentEconomicsDraft>();
  readonly materialProcurementSave = output<MaterialProcurementDraft>();
  protected readonly dialogOpen = signal(false);
  protected readonly profileDialog = signal<'equipment' | 'material' | null>(null);
  protected readonly editingName = signal('');
  protected readonly search = signal('');
  protected readonly typeFilter = signal('all');
  protected readonly expandedGroups = signal<Set<string>>(new Set());
  protected readonly draft = signal<NewCostRate>(this.emptyRate());
  protected readonly equipmentDraft = signal<EquipmentEconomicsDraft>({ resourceId: '', owned: false, acquisitionCost: 0, residualValue: 0, usefulLifeMonths: 60, maintenanceRatePercentage: 0, insuranceRatePercentage: 0, currencyCode: 'USD' });
  protected readonly materialDraft = signal<MaterialProcurementDraft>({ resourceId: '', supplier: '', leadTimeDays: 0, minimumOrderQuantity: 0, defaultWastePercentage: 0 });
  protected readonly rows = computed<CostRow[]>(() => {
    const term = this.search().trim().toLowerCase(); const type = this.typeFilter();
    return this.resources().filter(resource => type === 'all' || resource.type === type)
      .flatMap(resource => resource.costs.map(cost => ({ resource, cost, projectRate: this.projectRates().find(rate => rate.sourceCostComponentId === cost.id) ?? null })))
      .filter(row => !term || `${row.resource.code} ${row.resource.name} ${row.cost.name} ${row.cost.category}`.toLowerCase().includes(term));
  });
  protected readonly groups = computed<CostGroup[]>(() => {
    const grouped = new Map<string, CostRow[]>();
    this.rows().forEach(row => grouped.set(row.resource.id, [...(grouped.get(row.resource.id) ?? []), row]));
    return [...grouped.values()].map(rows => ({
      resource: rows[0].resource,
      rows,
      catalogTotal: rows.reduce((total, row) => total + row.cost.unitPrice, 0),
      projectTotal: rows.reduce((total, row) => total + (row.projectRate?.unitPrice ?? 0), 0),
      catalogCurrency: rows.every(row => row.cost.currencyCode === rows[0].cost.currencyCode) ? rows[0].cost.currencyCode : this.currency(),
      syncedCount: rows.filter(row => row.projectRate).length,
    }));
  });
  protected readonly equipment = computed(() => this.resources().filter(resource => resource.type === 'equipment'));
  protected readonly materials = computed(() => this.resources().filter(resource => resource.type === 'material'));
  protected readonly averageRate = computed(() => { const rows = this.rows().filter(row => row.cost.currencyCode === this.currency()); return rows.length ? rows.reduce((sum, row) => sum + row.cost.unitPrice, 0) / rows.length : 0; });

  protected openRateDialog(row?: CostRow): void {
    this.editingName.set(row ? `${row.resource.code} · ${row.cost.name}` : '');
    this.draft.set(row ? { id: row.cost.id, resourceId: row.resource.id, category: row.cost.category, name: row.cost.name, calculationBasis: row.cost.calculationBasis, unitPrice: row.cost.unitPrice, unit: row.cost.unit ?? 'DAY', currencyCode: row.cost.currencyCode, taxable: row.cost.taxable ?? false, taxRate: row.cost.taxRate ?? 0, validFrom: row.cost.validFrom ?? null, validTo: row.cost.validTo ?? null } : this.emptyRate());
    this.dialogOpen.set(true);
  }
  protected editEquipment(resource: ActivityResource): void { const value = resource.equipmentEconomics; this.equipmentDraft.set({ resourceId: resource.id, owned: value?.owned ?? resource.owned ?? false, acquisitionCost: value?.acquisitionCost ?? 0, residualValue: value?.residualValue ?? 0, usefulLifeMonths: value?.usefulLifeMonths ?? 60, maintenanceRatePercentage: value?.maintenanceRatePercentage ?? 0, insuranceRatePercentage: value?.insuranceRatePercentage ?? 0, currencyCode: value?.currencyCode ?? this.currency() }); this.editingName.set(resource.name); this.profileDialog.set('equipment'); }
  protected editMaterial(resource: ActivityResource): void { const value = resource.materialProcurement; this.materialDraft.set({ resourceId: resource.id, supplier: value?.supplier ?? '', leadTimeDays: value?.leadTimeDays ?? 0, minimumOrderQuantity: value?.minimumOrderQuantity ?? 0, defaultWastePercentage: value?.defaultWastePercentage ?? 0 }); this.editingName.set(resource.name); this.profileDialog.set('material'); }
  protected updateDraft(field: keyof NewCostRate, value: string | number | boolean | null): void { this.draft.update(draft => ({ ...draft, [field]: value })); }
  protected updateEquipment(field: keyof EquipmentEconomicsDraft, value: string | number | boolean): void { this.equipmentDraft.update(draft => ({ ...draft, [field]: value })); }
  protected updateMaterial(field: keyof MaterialProcurementDraft, value: string | number): void { this.materialDraft.update(draft => ({ ...draft, [field]: value })); }
  protected submitRate(): void { const value = this.draft(); if (!value.resourceId || !value.name.trim() || value.unitPrice < 0 || (!!value.validFrom && !!value.validTo && value.validTo < value.validFrom)) return; this.costSave.emit({ ...value, name: value.name.trim() }); this.dialogOpen.set(false); }
  protected submitProfile(): void { if (this.profileDialog() === 'equipment') this.equipmentEconomicsSave.emit(this.equipmentDraft()); else if (this.profileDialog() === 'material') this.materialProcurementSave.emit(this.materialDraft()); this.profileDialog.set(null); }
  protected toggleGroup(resourceId: string): void { this.expandedGroups.update(current => { const next = new Set(current); next.has(resourceId) ? next.delete(resourceId) : next.add(resourceId); return next; }); }
  protected isExpanded(resourceId: string): boolean { return this.expandedGroups().has(resourceId); }
  protected typeName(type: ActivityResource['type']): string { return type === 'equipment' ? this.t('Equipment', 'Ekipman') : type === 'material' ? this.t('Material', 'Malzeme') : this.t('Personnel', 'Personel'); }
  protected saveProjectRate(row: CostRow, value: number): void { if (!row.projectRate || !Number.isFinite(value) || value < 0 || value === row.projectRate.unitPrice) return; this.projectRateUpdate.emit({ sourceCostComponentId: row.cost.id, unitPrice: value }); }
  protected money(value: number, currency = this.currency()): string { return new Intl.NumberFormat(this.language() === 'tr' ? 'tr-TR' : 'en-US', { style: 'currency', currency, maximumFractionDigits: 2 }).format(value); }
  protected basis(value: string): string { return value.replace('PER_', '').replace('_', ' ').toLowerCase(); }
  protected t(en: string, tr: string): string { return this.language() === 'tr' ? tr : en; }
  private emptyRate(): NewCostRate { return { id: null, resourceId: this.resources()[0]?.id ?? '', category: 'OTHER', name: '', calculationBasis: 'PER_DAY', unitPrice: 0, unit: 'DAY', currencyCode: this.currency(), taxable: false, taxRate: 0, validFrom: null, validTo: null }; }
}
