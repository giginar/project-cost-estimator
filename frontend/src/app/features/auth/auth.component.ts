import { ChangeDetectionStrategy, Component, OnInit, inject, output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { AuthService, AuthUser } from '../../core/auth.service';

@Component({ selector: 'app-auth', templateUrl: './auth.component.html', styleUrls: ['./auth.component.scss', './auth-extra.scss'], changeDetection: ChangeDetectionStrategy.OnPush })
export class AuthComponent implements OnInit {
  private readonly auth = inject(AuthService);
  readonly authenticated = output<AuthUser>();
  protected readonly mode = signal<'login' | 'register' | 'forgot' | 'reset'>('login');
  protected readonly fullName = signal('');
  protected readonly email = signal('');
  protected readonly password = signal('');
  protected readonly passwordConfirmation = signal('');
  protected readonly resetToken = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly message = signal('');

  ngOnInit(): void {
    const parameters = new URLSearchParams(location.search);
    const resetToken = parameters.get('reset');
    if (resetToken) {
      this.resetToken.set(resetToken); this.mode.set('reset'); history.replaceState({}, '', location.pathname); return;
    }
    const token = parameters.get('verify');
    if (!token) return;
    history.replaceState({}, '', location.pathname);
    this.loading.set(true);
    this.auth.verify(token).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: response => { this.mode.set('login'); this.message.set(response.message); },
      error: error => this.error.set(error.error?.detail ?? 'Verification link is invalid or expired.'),
    });
  }

  protected submit(): void {
    this.error.set(''); this.message.set(''); this.loading.set(true);
    if (this.mode() === 'login') {
      this.auth.login(this.email(), this.password()).pipe(finalize(() => this.loading.set(false))).subscribe({
        next: user => this.authenticated.emit(user),
        error: error => this.error.set(error.error?.detail ?? 'Sign in failed.'),
      });
      return;
    }
    if (this.mode() === 'forgot') {
      this.auth.forgotPassword(this.email()).pipe(finalize(() => this.loading.set(false))).subscribe({
        next: response => this.message.set(response.message),
        error: () => this.message.set('If an active verified account exists for that email, a password reset link has been sent.'),
      });
      return;
    }
    if (this.mode() === 'reset') {
      if (this.password() !== this.passwordConfirmation()) { this.loading.set(false); this.error.set('Passwords do not match.'); return; }
      this.auth.resetPassword(this.resetToken(), this.password()).pipe(finalize(() => this.loading.set(false))).subscribe({
        next: response => { this.message.set(response.message); this.password.set(''); this.passwordConfirmation.set(''); this.mode.set('login'); },
        error: error => this.error.set(error.error?.detail ?? 'Password reset link is invalid or expired.'),
      });
      return;
    }
    this.auth.register(this.fullName(), this.email(), this.password()).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: response => { this.message.set(response.message); this.mode.set('login'); this.password.set(''); },
      error: error => this.error.set(error.error?.detail ?? 'Registration failed.'),
    });
  }

  protected switchMode(mode: 'login' | 'register' | 'forgot'): void { this.mode.set(mode); this.error.set(''); this.message.set(''); }
}
