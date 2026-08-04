import { ChangeDetectionStrategy, Component, effect, input, output, signal } from '@angular/core';
import { ProjectSettings } from '../../core/project-api.service';

@Component({ selector: 'app-project-settings', templateUrl: './project-settings.component.html', styleUrl: './project-settings.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class ProjectSettingsComponent {
  readonly settings = input.required<ProjectSettings>();
  readonly language = input<'en' | 'tr'>('en');
  readonly saving = input(false);
  readonly message = input('');
  readonly settingsSave = output<{ settings: ProjectSettings; language: 'en' | 'tr' }>();
  protected readonly draft = signal<ProjectSettings>({ code: '', name: '', description: '', start: '', end: '', currencyCode: 'USD', status: 'DRAFT' });
  protected readonly draftLanguage = signal<'en' | 'tr'>('en');
  constructor() { effect(() => { this.draft.set({ ...this.settings() }); this.draftLanguage.set(this.language()); }); }
  protected update(field: keyof ProjectSettings, value: string): void { this.draft.update(draft => ({ ...draft, [field]: value } as ProjectSettings)); }
  protected submit(): void { const value = this.draft(); if (!value.code.trim() || !value.name.trim() || !value.start || !value.end || value.end < value.start) return; this.settingsSave.emit({ settings: { ...value, code: value.code.trim(), name: value.name.trim() }, language: this.draftLanguage() }); }
  protected t(en: string, tr: string): string { return this.draftLanguage() === 'tr' ? tr : en; }
}
