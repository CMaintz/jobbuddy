import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationTimelineEntry, ApplicationsApiService } from './applications.api';
import { Application } from '../models/application.model';

describe('ApplicationsApiService', () => {
  let api: ApplicationsApiService;
  let backend: HttpTestingController;

  const application = { id: 'a1', jobId: 'j1', status: 'APPLIED' } as Application;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withXhr()), provideHttpClientTesting()] });
    api = TestBed.inject(ApplicationsApiService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists applications', () => {
    let result: Application[] | undefined;
    api.getAll().subscribe(r => (result = r));

    const req = backend.expectOne('/api/v1/applications');
    expect(req.request.method).toBe('GET');
    req.flush([application]);
    expect(result).toEqual([application]);
  });

  describe('create', () => {
    it('builds the payload from positional arguments', () => {
      api.create('j1', 'cv-1', 'referred by Anna').subscribe();

      const req = backend.expectOne('/api/v1/applications');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ jobId: 'j1', cvVersionId: 'cv-1', notes: 'referred by Anna' });
      req.flush(application);
    });

    it('sends a payload object unchanged', () => {
      const payload = { jobId: 'j1', status: 'PREPARING' as const, matchScore: 72, coverLetterText: 'Hej' };
      let created: Application | undefined;
      api.create(payload).subscribe(r => (created = r));

      const req = backend.expectOne('/api/v1/applications');
      expect(req.request.body).toBe(payload);
      req.flush(application);
      expect(created).toEqual(application);
    });
  });

  it('reads the timeline of one application', () => {
    const entries: ApplicationTimelineEntry[] = [
      { at: '2026-01-01T10:00:00Z', fromStatus: null, toStatus: 'SAVED', employerResponse: false, notes: null },
      { at: '2026-01-03T10:00:00Z', fromStatus: 'SAVED', toStatus: 'APPLIED', employerResponse: false, notes: null },
    ];
    let result: ApplicationTimelineEntry[] | undefined;
    api.timeline('a1').subscribe(r => (result = r));

    backend.expectOne('/api/v1/applications/a1/timeline').flush(entries);
    expect(result).toEqual(entries);
  });

  it('patches the status with its notes', () => {
    api.updateStatus('a1', 'INTERVIEW', 'first round Tuesday').subscribe();

    const req = backend.expectOne('/api/v1/applications/a1/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'INTERVIEW', notes: 'first round Tuesday' });
    req.flush(application);
  });

  it('patches recruiter info and outcome on their own sub-resources', () => {
    api.updateRecruiterInfo('a1', { recruiterName: 'Kim' }).subscribe();
    const recruiter = backend.expectOne('/api/v1/applications/a1/recruiter');
    expect(recruiter.request.method).toBe('PATCH');
    expect(recruiter.request.body).toEqual({ recruiterName: 'Kim' });
    recruiter.flush(application);

    api.updateOutcome('a1', { outcomeLessons: 'show more system design' }).subscribe();
    const outcome = backend.expectOne('/api/v1/applications/a1/outcome');
    expect(outcome.request.method).toBe('PATCH');
    expect(outcome.request.body).toEqual({ outcomeLessons: 'show more system design' });
    outcome.flush(application);
  });

  it('attaches a generated document by id, with content and status in the body', () => {
    api.attachGeneratedDocument('a1', 'doc-9', '<p>letter</p>', 'APPLIED').subscribe();

    const req = backend.expectOne('/api/v1/applications/a1/generated-documents/doc-9');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      generatedContent: '<p>letter</p>', status: 'APPLIED', notes: undefined,
    });
    req.flush(application);
  });
});
