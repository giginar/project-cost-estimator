import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { ProjectSettings } from '../../core/project-api.service';

@Component({ selector: 'app-project-settings', templateUrl: './project-settings.component.html', styleUrl: './project-settings.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class ProjectSettingsComponent {
  readonly settings = input.required<ProjectSettings>();
  readonly language = input<'en' | 'tr'>('en');
  readonly saving = input(false);
  readonly message = input('');
  readonly settingsSave = output<{ settings: ProjectSettings; language: 'en' | 'tr' }>();
  protected readonly draft = signal<ProjectSettings>({ code: '', name: '', description: '', start: '', end: '', currencyCode: 'USD', usdTryRate: null, eurTryRate: null, status: 'DRAFT' });
  protected readonly draftLanguage = signal<'en' | 'tr'>('en');
  protected readonly validationError = signal('');
  protected readonly currencyChanged = computed(() => this.draft().currencyCode !== this.settings().currencyCode);

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

  protected t(en: string, tr: string): string { return this.draftLanguage() === 'tr' ? tr : en; }
}
