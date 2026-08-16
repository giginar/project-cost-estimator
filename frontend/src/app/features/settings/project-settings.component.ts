import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { CostCode, CostCodeDraft, CostCodeType, GeneralUnitPrice, GeneralUnitPriceDraft, ProjectSettings } from '../../core/project-api.service';

@Component({ selector: 'app-project-settings', templateUrl: './project-settings.component.html', styleUrls: ['./project-settings.component.scss', './project-master-data.component.scss'], changeDetection: ChangeDetectionStrategy.OnPush })
export class ProjectSettingsComponent {
  readonly settings = input.required<ProjectSettings>();
  readonly language = input<'en' | 'tr'>('en');
  readonly saving = input(false);
  readonly message = input('');
  readonly configurationMessage = input('');
  readonly generalUnitPrices = input<GeneralUnitPrice[]>([]);
  readonly costCodes = input<CostCode[]>([]);
  readonly readonly = input(false);
  readonly settingsSave = output<{ settings: ProjectSettings; language: 'en' | 'tr' }>();
  readonly unitPriceSave = output<GeneralUnitPriceDraft>();
  readonly unitPriceDelete = output<string>();
  readonly costCodeSave = output<CostCodeDraft>();
  readonly costCodeDelete = output<string>();
  protected readonly draft = signal<ProjectSettings>({ code: '', name: '', description: '', start: '', end: '', currencyCode: 'USD', usdTryRate: null, eurTryRate: null, status: 'DRAFT' });
  protected readonly draftLanguage = signal<'en' | 'tr'>('en');
  protected readonly validationError = signal('');
  protected readonly currencyChanged = computed(() => this.draft().currencyCode !== this.settings().currencyCode);
  protected readonly unitPriceDraft = signal<GeneralUnitPriceDraft>({ id: null, code: '', name: '', fuelType: 'DIESEL', unit: 'LITER', unitPrice: 0, active: true });
  protected readonly costCodeDraft = signal<CostCodeDraft>({ id: null, code: '', name: '', type: 'PERSONNEL', active: true });
  protected readonly fuelTypes = ['DIESEL', 'GASOLINE', 'MARINE_DIESEL', 'ELECTRICITY'];
  protected readonly costCodeTypes: CostCodeType[] = ['PERSONNEL', 'EQUIPMENT', 'FUEL', 'MATERIAL', 'ACCOMMODATION', 'TRANSPORTATION', 'OVERHEAD', 'TAX'];

  constructor() {
    effect(() => {
      this.draft.set({ ...this.settings() });
      this.draftLanguage.set(this.language());
    });
  }

  protected update(field: keyof ProjectSettings, value: string | number | null): void {
    this.draft.update(draft => ({ ...draft, [field]: value } as ProjectSettings));
  }

  protected submit(): void {
    const value = this.draft();
    this.validationError.set('');
    if (!value.code.trim() || !value.name.trim() || !value.start || !value.end || value.end < value.start) {
      this.validationError.set(this.t('Complete the required project fields and check the dates.', 'Zorunlu proje alanlarını doldurun ve tarihleri kontrol edin.'));
      return;
    }
    if (this.currencyChanged() && (!value.usdTryRate || value.usdTryRate <= 0 || !value.eurTryRate || value.eurTryRate <= 0)) {
      this.validationError.set(this.t('Enter positive USD/TRY and EUR/TRY rates to convert all prices.', 'Tüm fiyatları dönüştürmek için pozitif USD/TRY ve EUR/TRY kurları girin.'));
      return;
    }
    this.settingsSave.emit({ settings: { ...value, code: value.code.trim(), name: value.name.trim() }, language: this.draftLanguage() });
  }

  protected eurUsdRate(): string {
    const { usdTryRate, eurTryRate } = this.draft();
    return usdTryRate && eurTryRate ? (eurTryRate / usdTryRate).toFixed(4) : '—';
  }

  protected editUnitPrice(price?: GeneralUnitPrice): void { this.unitPriceDraft.set(price ? { id: price.id, code: price.code, name: price.name, fuelType: price.fuelType, unit: price.unit, unitPrice: price.unitPrice, active: price.active } : { id: null, code: '', name: '', fuelType: 'DIESEL', unit: 'LITER', unitPrice: 0, active: true }); }
  protected updateUnitPrice(field: keyof GeneralUnitPriceDraft, value: string | number | boolean): void { this.unitPriceDraft.update(draft => ({ ...draft, [field]: value })); }
  protected submitUnitPrice(): void { const draft = this.unitPriceDraft(); if (this.readonly() || !draft.code.trim() || !draft.name.trim() || draft.unitPrice < 0) return; this.unitPriceSave.emit({ ...draft, code: draft.code.trim(), name: draft.name.trim(), unit: draft.fuelType === 'ELECTRICITY' ? 'KILOWATT_HOUR' : 'LITER' }); this.editUnitPrice(); }
  protected editCostCode(code?: CostCode): void { this.costCodeDraft.set(code ? { id: code.id, code: code.code, name: code.name, type: code.type, active: code.active } : { id: null, code: '', name: '', type: 'PERSONNEL', active: true }); }
  protected updateCostCode(field: keyof CostCodeDraft, value: string | boolean): void { this.costCodeDraft.update(draft => ({ ...draft, [field]: value } as CostCodeDraft)); }
  protected submitCostCode(): void { const draft = this.costCodeDraft(); if (this.readonly() || !draft.code.trim() || !draft.name.trim()) return; this.costCodeSave.emit({ ...draft, code: draft.code.trim(), name: draft.name.trim() }); this.editCostCode(); }

  protected t(en: string, tr: string): string { return this.draftLanguage() === 'tr' ? tr : en; }
}
