import { TestBed } from '@angular/core/testing';
import { CostLibraryComponent } from './features/cost-library/cost-library.component';
import { PricingComponent } from './features/pricing/pricing.component';
import { ProjectOverviewComponent } from './features/project/project-overview.component';
import { ActivityResource } from './features/gantt/gantt.models';

const equipment: ActivityResource = { id: 'eq-1', type: 'equipment', code: 'EQ-1', name: 'Crane', subtype: 'Crane', owned: true, equipmentEconomics: { owned: true, acquisitionCost: 120000, residualValue: 24000, usefulLifeMonths: 48, maintenanceRatePercentage: 6, insuranceRatePercentage: 1.2, currencyCode: 'USD', monthlyDepreciation: 2000, monthlyMaintenance: 600, monthlyInsurance: 120 }, costs: [], fuelConsumptions: [] };
const material: ActivityResource = { id: 'mat-1', type: 'material', code: 'MAT-1', name: 'Rock', subtype: 'Rock', defaultUnit: 'TON', materialProcurement: { supplier: 'Quarry A', leadTimeDays: 14, minimumOrderQuantity: 25, defaultWastePercentage: 10 }, costs: [{ id: 'cost-1', category: 'MATERIAL', name: 'Quote', calculationBasis: 'PER_UNIT', unitPrice: 120, unit: 'TON', currencyCode: 'USD', validFrom: '2026-01-01', validTo: '2027-12-31' }], fuelConsumptions: [] };
const summary = { estimatedCost: 1320, boqValue: 1500, nonProfitAdders: 132, profit: 290.4, salesPrice: 1742.4, grossProfit: 422.4, netProfit: 290.4, profitMarginPercentage: 16.67, boqVariance: -242.4, lines: [{ ruleId: 'rule-1', id: 'rule-1', type: 'PROFIT' as const, name: 'Target profit', percentage: 20, base: 'RUNNING_TOTAL' as const, sequence: 1, active: true, baseAmount: 1452, amount: 290.4 }] };

describe('Phase three and four UI', () => {
  beforeEach(async () => TestBed.configureTestingModule({ imports: [CostLibraryComponent, PricingComponent, ProjectOverviewComponent] }).compileComponents());

  it('shows editable equipment economics, procurement and global validity data', () => {
    const fixture = TestBed.createComponent(CostLibraryComponent); fixture.componentRef.setInput('resources', [equipment, material]); fixture.detectChanges();
    (fixture.nativeElement.querySelector('.group-summary') as HTMLButtonElement).click(); fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('EQUIPMENT ECONOMICS'); expect(text).toContain('MATERIAL PROCUREMENT'); expect(text).toContain('Quarry A'); expect(text).toContain('2026-01-01');
  });

  it('shows sequential pricing and its commercial summary', () => {
    const fixture = TestBed.createComponent(PricingComponent); fixture.componentRef.setInput('rules', [{ id: 'rule-1', type: 'PROFIT', name: 'Target profit', percentage: 20, base: 'RUNNING_TOTAL', sequence: 1, active: true }]); fixture.componentRef.setInput('summary', summary); fixture.detectChanges();
    const text = fixture.nativeElement.textContent; expect(text).toContain('SALES PRICE'); expect(text).toContain('NET PROFIT'); expect(text).toContain('Target profit'); expect(text).toContain('Running total');
  });

  it('shows cost, sales, profit, margin and BOQ on overview', () => {
    const fixture = TestBed.createComponent(ProjectOverviewComponent); fixture.componentRef.setInput('projectName', 'Bid'); fixture.componentRef.setInput('projectStart', '2026-08-01'); fixture.componentRef.setInput('projectEnd', '2026-08-31'); fixture.componentRef.setInput('pricing', summary); fixture.detectChanges();
    const text = fixture.nativeElement.textContent; expect(text).toContain('SALES PRICE'); expect(text).toContain('NET PROFIT'); expect(text).toContain('BOQ'); expect(text).toContain('16.67');
  });
});
