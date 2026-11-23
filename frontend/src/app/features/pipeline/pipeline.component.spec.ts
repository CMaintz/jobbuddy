import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { Application, ApplicationStatus } from '../../core/models/application.model';
import { stageLabelKey } from '../../shared/components/status-chip/status-chip.component';
import { PipelineComponent } from './pipeline.component';

describe('PipelineComponent status change', () => {
  let fixture: ComponentFixture<PipelineComponent>;
  let api: jasmine.SpyObj<ApplicationsApiService>;
  let navigate: jasmine.Spy;

  const saved: Application = {
    id: 'a1', jobId: 'j1', status: 'PREPARING', nextStatuses: ['APPLIED', 'SAVED', 'ARCHIVED'],
    jobTitle: 'Kommunikationsmedarbejder', jobCompanyName: 'Acme A/S', createdAt: '', updatedAt: '',
  };
  const other: Application = { ...saved, id: 'a2', jobTitle: 'Copywriter' };

  beforeEach(() => {
    api = jasmine.createSpyObj<ApplicationsApiService>('ApplicationsApiService', ['getAll', 'updateStatus']);
    api.getAll.and.returnValue(of([saved, other]));
    TestBed.configureTestingModule({
      imports: [PipelineComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: ApplicationsApiService, useValue: api }],
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    fixture = TestBed.createComponent(PipelineComponent);
    fixture.detectChanges();
  });

  function rowSelect(): HTMLSelectElement {
    return (fixture.nativeElement as HTMLElement).querySelector('select')!;
  }

  it('changes the status from the row without opening the application', () => {
    api.updateStatus.and.returnValue(of({ ...saved, status: 'APPLIED', nextStatuses: [] }));
    const select = rowSelect();

    select.click();
    select.value = 'APPLIED';
    select.dispatchEvent(new Event('change'));

    expect(api.updateStatus).toHaveBeenCalledWith('a1', 'APPLIED');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('shows the new status on that row and leaves the others alone', () => {
    api.updateStatus.and.returnValue(of({ ...saved, status: 'APPLIED', nextStatuses: [] }));

    fixture.componentInstance.changeStatus(saved, 'APPLIED');

    expect(fixture.componentInstance.applications().map(a => a.status)).toEqual(['APPLIED', 'PREPARING']);
  });

  it('keeps the old status when the server refuses the move', () => {
    api.updateStatus.and.returnValue(throwError(() => new Error('409')));

    const select = rowSelect();

    select.value = 'APPLIED';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.componentInstance.applications()[0].status).toBe('PREPARING');
    expect(rowSelect().value).toBe('PREPARING');
  });

  it('holds the picked status and blocks a second pick while the server answers', () => {
    const reply = new Subject<Application>();
    api.updateStatus.and.returnValue(reply);

    pick('APPLIED');

    expect(rowSelect().value).toBe('APPLIED');
    expect(rowSelect().disabled).toBeTrue();

    reply.next({ ...saved, status: 'APPLIED', nextStatuses: ['REJECTED', 'ARCHIVED'] });
    reply.complete();
    fixture.detectChanges();

    expect(rowSelect().value).toBe('APPLIED');
    expect(rowSelect().disabled).toBeFalse();
  });

  function pick(status: string): void {
    const select = rowSelect();
    select.value = status;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('still opens the application when the row itself is clicked', () => {
    const row = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="pipeline-row"]') as HTMLElement;

    row.click();

    expect(navigate).toHaveBeenCalledWith(['/applications', 'a1']);
  });

  it('puts an application of every status on the board, under its column, showing its status', () => {
    const columnOf: Record<ApplicationStatus, string> = {
      SAVED: 'pipeline.stage.saved', PREPARING: 'pipeline.stage.saved',
      APPLIED: 'pipeline.stage.applied',
      RECRUITER_CONTACT: 'pipeline.column.inProgress', INTERVIEW: 'pipeline.column.inProgress',
      TECHNICAL_TEST: 'pipeline.column.inProgress', FINAL_ROUND: 'pipeline.column.inProgress',
      OFFER: 'pipeline.stage.offer',
      REJECTED: 'pipeline.column.closed', ARCHIVED: 'pipeline.column.closed',
    };
    const statuses = Object.keys(columnOf) as ApplicationStatus[];
    fixture.componentInstance.applications.set(statuses.map(status => ({
      ...saved, id: status, status, jobTitle: `Job ${status}`,
    })));
    fixture.componentInstance.view.set('kanban');
    fixture.detectChanges();

    const columns = Array.from((fixture.nativeElement as HTMLElement)
      .querySelectorAll<HTMLElement>('[data-testid="kanban-column"]'));
    const cards = columns.flatMap(column => Array.from(column.querySelectorAll<HTMLElement>('a'))
      .map(card => ({ column: column.querySelector('jb-pill')!.textContent!.trim(), card })));

    expect(cards.length).toBe(statuses.length);
    for (const status of statuses) {
      const placed = cards.find(c => c.card.textContent!.includes(`Job ${status}`));
      expect(placed?.column).withContext(status).toBe(columnOf[status]);
      // A column holding one status already names it; in a grouped one the card must say which.
      const statusShown = `${placed?.column} ${placed?.card.textContent}`;
      expect(statusShown).withContext(status).toContain(stageLabelKey(status));
    }
  });

  it('shows an application still in preparation on the board, under saved', () => {
    fixture.componentInstance.view.set('kanban');
    fixture.detectChanges();

    const savedColumn = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="kanban-column"]')!;

    expect(savedColumn.querySelectorAll('a').length).toBe(2);
    expect(savedColumn.textContent).toContain('Kommunikationsmedarbejder');
  });

  function filterChip(labelKey: string): HTMLButtonElement {
    const chips = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button'));
    return chips.find(chip => chip.textContent!.includes(labelKey))!;
  }

  function oneOfEach(...statuses: ApplicationStatus[]): void {
    fixture.componentInstance.applications.set(statuses.map(status => ({
      ...saved, id: status, status, jobTitle: `Job ${status}`,
    })));
    fixture.detectChanges();
  }

  it('counts each filter chip by what its board column holds', () => {
    oneOfEach('SAVED', 'PREPARING', 'APPLIED', 'RECRUITER_CONTACT', 'INTERVIEW', 'TECHNICAL_TEST',
      'FINAL_ROUND', 'OFFER', 'REJECTED', 'ARCHIVED');

    const counts = Object.fromEntries(['pipeline.stage.saved', 'pipeline.stage.applied',
      'pipeline.column.inProgress', 'pipeline.stage.offer', 'pipeline.column.closed']
      .map(key => [key, filterChip(key)?.textContent?.replace(key, '').trim()]));

    expect(counts).toEqual({
      'pipeline.stage.saved': '2', 'pipeline.stage.applied': '1', 'pipeline.column.inProgress': '4',
      'pipeline.stage.offer': '1', 'pipeline.column.closed': '2',
    });
  });

  it('filters the table to every status of the picked column', () => {
    oneOfEach('SAVED', 'PREPARING', 'APPLIED');

    filterChip('pipeline.stage.saved').click();
    fixture.detectChanges();

    const rows = Array.from((fixture.nativeElement as HTMLElement)
      .querySelectorAll<HTMLElement>('[data-testid="pipeline-row"]')).map(row => row.textContent);
    expect(rows.length).toBe(2);
    expect(rows.join()).not.toContain('Job APPLIED');
    expect(filterChip('pipeline.stage.saved').getAttribute('aria-pressed')).toBe('true');
  });

  it('filters the board to the picked column', () => {
    oneOfEach('PREPARING', 'APPLIED', 'OFFER');
    fixture.componentInstance.view.set('kanban');
    fixture.detectChanges();

    filterChip('pipeline.stage.applied').click();
    fixture.detectChanges();

    const columns = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('[data-testid="kanban-column"]');
    expect(columns.length).toBe(1);
    expect(columns[0].textContent).toContain('Job APPLIED');
  });
});
