import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { AuthService } from './core/auth.service';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render the authentication landing heading', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Plan resources.');
  });

  it('opens cost breakdown and cash flow as separate report pages', () => {
    const auth = TestBed.inject(AuthService);
    auth.user.set({ id: 'engineer-1', fullName: 'Demo Engineer', email: 'engineer@example.com', role: 'ENGINEER', emailVerified: true, active: true });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    const reportLinks = Array.from(fixture.nativeElement.querySelectorAll('.nav-child')) as HTMLAnchorElement[];
    const costReportLink = reportLinks.find(link => link.textContent?.includes('Cost breakdown'));
    const cashFlowLink = reportLinks.find(link => link.textContent?.includes('Cash flow'));
    expect(costReportLink).toBeTruthy();
    expect(cashFlowLink).toBeTruthy();

    costReportLink!.click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.content h1')?.textContent).toContain('Cost breakdown');
    expect(fixture.nativeElement.querySelector('app-project-cash-flow')).toBeNull();

    cashFlowLink!.click(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.content h1')?.textContent).toContain('Planned income and expenses');
    expect(fixture.nativeElement.querySelector('app-project-report')).toBeNull();
  });
});
