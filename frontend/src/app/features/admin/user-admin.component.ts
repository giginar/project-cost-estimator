import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { AdminUserInput, AuthService, AuthUser, MailOutboxEntry, UserRole } from '../../core/auth.service';

@Component({ selector: 'app-user-admin', templateUrl: './user-admin.component.html', styleUrl: './user-admin.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class UserAdminComponent implements OnInit {
  protected readonly auth = inject(AuthService);
  protected readonly users = signal<AuthUser[]>([]);
  protected readonly verifiedCount = computed(() => this.users().filter(user => user.emailVerified).length);
  protected readonly outbox = signal<MailOutboxEntry[]>([]);
  protected readonly formOpen = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');
  protected readonly draft = signal<AdminUserInput>({ fullName: '', email: '', password: '', role: 'ENGINEER' });

  ngOnInit(): void { this.reload(); }
  protected update(field: keyof AdminUserInput, value: string): void { this.draft.update(draft => ({ ...draft, [field]: value } as AdminUserInput)); }
  protected create(): void {
    this.error.set(''); this.message.set(''); this.saving.set(true);
    this.auth.createUser(this.draft()).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: user => { this.users.update(users => [...users, user]); this.message.set(`Verification email prepared for ${user.email}.`); this.formOpen.set(false); this.draft.set({ fullName: '', email: '', password: '', role: 'ENGINEER' }); this.loadOutbox(); },
      error: error => this.error.set(error.error?.detail ?? 'User could not be created.'),
    });
  }
  protected logout(): void { this.auth.logout(); }
  protected initials(name: string): string { return name.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase(); }
  protected roleLabel(role: UserRole): string { return role[0] + role.slice(1).toLowerCase(); }
  private reload(): void { this.auth.listUsers().subscribe(users => this.users.set(users)); this.loadOutbox(); }
  private loadOutbox(): void { this.auth.mailOutbox().subscribe(outbox => this.outbox.set(outbox)); }
}
