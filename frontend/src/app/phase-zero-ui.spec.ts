import { TestBed } from '@angular/core/testing';
import { CostLibraryComponent } from './features/cost-library/cost-library.component';
import { GanttComponent } from './features/gantt/gantt.component';
import { ActivityResource } from './features/gantt/gantt.models';
import { ResourceCatalogComponent } from './features/resources/resource-catalog.component';

const equipment: ActivityResource = {
  id: 'equipment-1', type: 'equipment', code: 'EQ-1', name: 'Excavator', description: '', subtype: 'Excavator',
  costs: [{ id: 'cost-1', category: 'RENTAL', name: 'Daily rental', calculationBasis: 'PER_DAY', unitPrice: 1000, unit: 'DAY', currencyCode: 'USD' }],
  fuelConsumptions: [{ id: 'fuel-1', fuelType: 'DIESEL', consumptionPerHour: 10, standbyConsumptionPerHour: 2, consumptionUnit: 'LITER' }],
};

const material: ActivityResource = {
  id: 'material-1', type: 'material', code: 'MAT-1', name: 'Concrete', description: '', subtype: 'Concrete', defaultUnit: 'CUBIC_METER',
  costs: [{ id: 'cost-2', category: 'MATERIAL', name: 'Unit price', calculationBasis: 'PER_UNIT', unitPrice: 50, unit: 'CUBIC_METER', currencyCode: 'USD' }], fuelConsumptions: [],
};

describe('Phase zero UI', () => {
  beforeEach(async () => TestBed.configureTestingModule({ imports: [ResourceCatalogComponent, GanttComponent, CostLibraryComponent] }).compileComponents());

  it('shows separate operating and standby fuel inputs', () => {
    const fixture = TestBed.createComponent(ResourceCatalogComponent);
    fixture.componentRef.setInput('type', 'equipment'); fixture.componentRef.setInput('resources', []); fixture.detectChanges();
    (fixture.nativeElement.querySelector('.primary-button') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Operating consumption / hour');
    expect(fixture.nativeElement.textContent).toContain('Standby consumption / hour');
  });

  it('shows required quantity and waste fields for a material assignment', () => {
    const fixture = TestBed.createComponent(GanttComponent);
    fixture.componentRef.setInput('tasks', [{ id: 'task-1', code: 'A-1', name: 'Pour concrete', wbs: 'WORKS', start: '2026-08-01', end: '2026-08-02', assignments: [] }]);
    fixture.componentRef.setInput('resources', [material]); fixture.detectChanges();
    fixture.nativeElement.querySelector('.task-bar').dispatchEvent(new MouseEvent('contextmenu', { bubbles: true })); fixture.detectChanges();
    (fixture.nativeElement.querySelector('.resource-group input') as HTMLInputElement).click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Required quantity');
    expect(fixture.nativeElement.textContent).toContain('Waste %');
  });

  it('shows catalog and active-project rates separately', () => {
    const fixture = TestBed.createComponent(CostLibraryComponent);
    fixture.componentRef.setInput('resources', [equipment]);
    fixture.componentRef.setInput('projectRates', [{ id: 'rate-1', resourceId: equipment.id, sourceCostComponentId: 'cost-1', category: 'RENTAL', name: 'Daily rental', calculationBasis: 'PER_DAY', unitPrice: 900, unit: 'DAY', taxable: false, taxRate: 0, validFrom: null, validTo: null }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('CATALOG RATE');
    expect(fixture.nativeElement.textContent).toContain('PROJECT RATE');
    expect(fixture.nativeElement.textContent).toContain('$900.00');
    (fixture.nativeElement.querySelector('.group-summary') as HTMLButtonElement).click(); fixture.detectChanges();
    expect((fixture.nativeElement.querySelector('.project-rate input') as HTMLInputElement).value).toBe('900');
  });
});
