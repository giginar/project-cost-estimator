import { HttpClient, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

export type UserRole = 'ENGINEER' | 'MANAGER' | 'ADMIN';
export interface AuthUser { id: string; fullName: string; email: string; role: UserRole; emailVerified: boolean; active: boolean; }
export interface AdminUserInput { fullName: string; email: string; password: string; role: UserRole; }
export interface MailOutboxEntry { recipient: string; subject: string; verificationUrl: string; }
interface AuthResponse { accessToken: string; user: AuthUser; }

const TOKEN_KEY = 'cost-estimator-access-token';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = localStorage.getItem(TOKEN_KEY);
  return next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request);
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly user = signal<AuthUser | null>(null);
  constructor(private readonly http: HttpClient) {}

  restore(): Observable<AuthUser | null> {
    if (!localStorage.getItem(TOKEN_KEY)) return of(null);
    return this.http.get<AuthUser>('/api/v1/auth/me').pipe(
      tap(user => this.user.set(user)),
      catchError(() => { this.clear(); return of(null); }),
    );
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http.post<AuthResponse>('/api/v1/auth/login', { email, password }).pipe(tap(response => {
      localStorage.setItem(TOKEN_KEY, response.accessToken); this.user.set(response.user);
    }), map(response => response.user));
  }

  register(fullName: string, email: string, password: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>('/api/v1/auth/register', { fullName, email, password });
  }

  forgotPassword(email: string): Observable<{ message: string }> { return this.http.post<{ message: string }>('/api/v1/auth/forgot-password', { email }); }
  resetPassword(token: string, newPassword: string): Observable<{ message: string }> { return this.http.post<{ message: string }>('/api/v1/auth/reset-password', { token, newPassword }); }

  verify(token: string): Observable<{ message: string }> { return this.http.get<{ message: string }>('/api/v1/auth/verify', { params: { token } }); }
  logout(): void { this.http.post('/api/v1/auth/logout', {}).pipe(catchError(() => of(null))).subscribe(); this.clear(); }
  listUsers(): Observable<AuthUser[]> { return this.http.get<{ users: AuthUser[] }>('/api/v1/admin/users').pipe(map(response => response.users)); }
  createUser(input: AdminUserInput): Observable<AuthUser> { return this.http.post<AuthUser>('/api/v1/admin/users', input); }
  mailOutbox(): Observable<MailOutboxEntry[]> { return this.http.get<MailOutboxEntry[]>('/api/v1/admin/users/mail-outbox'); }
  private clear(): void { localStorage.removeItem(TOKEN_KEY); this.user.set(null); }
}
