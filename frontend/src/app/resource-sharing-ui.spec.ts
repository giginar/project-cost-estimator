import { TestBed } from '@angular/core/testing';
import { ResourceCatalogComponent } from './features/resources/resource-catalog.component';

describe('Shared resource UI', () => {
  beforeEach(async () => TestBed.configureTestingModule({ imports: [ResourceCatalogComponent] }).compileComponents());

  it('offers project-only or shared scope while creating a resource', () => {
    const fixture = TestBed.createComponent(ResourceCatalogComponent);
    fixture.componentRef.setInput('type', 'material'); fixture.componentRef.setInput('activeProjectId', 'project-a'); fixture.detectChanges();
    (fixture.nativeElement.querySelector('.primary-button') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Shared resource');
    expect((fixture.nativeElement.querySelector('.sharing-option input') as HTMLInputElement).checked).toBe(false);
  });

  it('shows scope and lets only the owning project toggle sharing', () => {
    const fixture = TestBed.createComponent(ResourceCatalogComponent);
    fixture.componentRef.setInput('type', 'material'); fixture.componentRef.setInput('activeProjectId', 'project-a');
    fixture.componentRef.setInput('resources', [{ id: 'material-a', type: 'material', code: 'MAT-A', name: 'Aggregate', subtype: 'Aggregate', shared: false, ownerProjectId: 'project-a', costs: [], fuelConsumptions: [] }]);
    let event: { resourceId: string; shared: boolean } | undefined; fixture.componentInstance.sharingChange.subscribe(value => event = value); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Project only');
    (fixture.nativeElement.querySelector('.resource-scope button') as HTMLButtonElement).click();
    expect(event).toEqual({ resourceId: 'material-a', shared: true });
  });

  it('requires confirmation before deleting an owned resource', () => {
    const fixture = TestBed.createComponent(ResourceCatalogComponent);
    fixture.componentRef.setInput('type', 'material'); fixture.componentRef.setInput('activeProjectId', 'project-a');
    fixture.componentRef.setInput('resources', [{ id: 'material-a', type: 'material', code: 'MAT-A', name: 'Aggregate', subtype: 'Aggregate', shared: false, ownerProjectId: 'project-a', costs: [], fuelConsumptions: [] }]);
    let deletedId = ''; fixture.componentInstance.resourceDelete.subscribe(id => deletedId = id); fixture.detectChanges();
    const deleteButton = [...fixture.nativeElement.querySelectorAll('.resource-scope button')].find((button: HTMLButtonElement) => button.textContent?.includes('Delete')) as HTMLButtonElement;
    deleteButton.click(); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('DELETE RESOURCE');
    (fixture.nativeElement.querySelector('.delete-dialog .danger') as HTMLButtonElement).click();
    expect(deletedId).toBe('material-a');
  });

  it('keeps delete visible but explains protected system resources', () => {
    const fixture = TestBed.createComponent(ResourceCatalogComponent);
    fixture.componentRef.setInput('type', 'equipment'); fixture.componentRef.setInput('activeProjectId', 'project-a');
    fixture.componentRef.setInput('resources', [{ id: 'system-equipment', type: 'equipment', code: 'EQ-SYS', name: 'System crane', subtype: 'Crane', shared: true, ownerProjectId: null, costs: [], fuelConsumptions: [] }]);
    fixture.detectChanges();
    const deleteButton = fixture.nativeElement.querySelector('.delete-resource') as HTMLButtonElement;
    expect(deleteButton).toBeTruthy();
    expect(deleteButton.disabled).toBe(true);
    expect(deleteButton.title).toBe('Protected system resource');
  });
});
