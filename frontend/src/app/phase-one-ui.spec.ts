import { TestBed } from '@angular/core/testing';
import { PlanningComponent } from './features/planning/planning.component';

describe('Phase one planning UI', () => {
  beforeEach(async () => TestBed.configureTestingModule({ imports: [PlanningComponent] }).compileComponents());

  it('shows BOQ traceability, productivity, dependencies and shift calendar', () => {
    const fixture = TestBed.createComponent(PlanningComponent);
    fixture.componentRef.setInput('wbsItems', [{ id: 'wbs-1', code: '1', name: 'Earthworks' }]);
    fixture.componentRef.setInput('boqItems', [{ id: 'boq-1', code: 'BOQ-1', description: 'Excavation', unit: 'CUBIC_METER', quantity: 100, unitPrice: 20, currencyCode: 'USD', totalPrice: 2000, wbsId: 'wbs-1', wbsCode: '1', wbsName: 'Earthworks', activityId: 'activity-1', activityCode: 'A-1', activityName: 'Excavate' }]);
    fixture.componentRef.setInput('tasks', [{ id: 'activity-1', code: 'A-1', name: 'Excavate', wbs: 'EARTHWORKS', start: '2026-08-07', end: '2026-08-12', plannedQuantity: 100, quantityUnit: 'CUBIC_METER', dailyProductionRate: 30, autoSchedule: true, dependencies: [{ id: 'dep-1', predecessorActivityId: 'activity-0', predecessorCode: 'A-0', predecessorName: 'Survey', type: 'FINISH_TO_START', lagDays: 0 }], assignments: [] }]);
    fixture.componentRef.setInput('calendar', { name: 'Two shift', workingDaysPerWeek: 5, workingHoursPerDay: 16, shifts: [{ name: 'Day', startTime: '08:00:00', endTime: '16:00:00', paidHours: 8 }, { name: 'Night', startTime: '16:00:00', endTime: '00:00:00', paidHours: 8 }] });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('BOQ → WBS → ACTIVITY TRACEABILITY');
    expect(text).toContain('DAILY CAPACITY');
    expect(text).toContain('FINISH_TO_START');
    expect([...fixture.nativeElement.querySelectorAll('.shift-list input')].some((input: HTMLInputElement) => input.value === 'Night')).toBe(true);
  });

  it('opens the complete BOQ CRUD form', () => {
    const fixture = TestBed.createComponent(PlanningComponent);
    fixture.componentRef.setInput('wbsItems', [{ id: 'wbs-1', code: '1', name: 'Earthworks' }]); fixture.detectChanges();
    (fixture.nativeElement.querySelector('.planning-bar button') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Add BOQ item');
    expect(fixture.nativeElement.textContent).toContain('Unit price');
    expect(fixture.nativeElement.textContent).toContain('Currency');
    expect(fixture.nativeElement.textContent).toContain('Activity');
  });
});
