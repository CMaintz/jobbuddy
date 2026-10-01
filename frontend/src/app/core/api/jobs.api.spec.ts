import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { JobsApiService } from './jobs.api';
import { Job, JobSearchResult, MatchResult } from '../models/job.model';

describe('JobsApiService', () => {
  let api: JobsApiService;
  let backend: HttpTestingController;

  const job = { id: 'j1', url: 'https://example.com/j1', title: 'Backend Engineer' } as Job;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(JobsApiService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('pages the job list with default page and size', () => {
    let result: { content: Job[]; totalElements: number } | undefined;
    api.getJobs().subscribe(r => (result = r));

    const req = backend.expectOne(r => r.url === '/api/v1/jobs');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ content: [job], totalElements: 1 });

    expect(result).toEqual({ content: [job], totalElements: 1 });
  });

  it('searches without a categories param when none are chosen', () => {
    api.search('java', 2, 10).subscribe();

    const req = backend.expectOne(r => r.url === '/api/v1/jobs/search');
    expect(req.request.params.get('q')).toBe('java');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('categories')).toBeFalse();
    req.flush({ jobs: [], total: 0, page: 2, size: 10 } satisfies JobSearchResult);
  });

  it('repeats the categories param once per chosen category', () => {
    let result: JobSearchResult | undefined;
    api.search('java', 0, 20, ['BACKEND', 'DEVOPS']).subscribe(r => (result = r));

    const req = backend.expectOne(r => r.url === '/api/v1/jobs/search');
    expect(req.request.params.getAll('categories')).toEqual(['BACKEND', 'DEVOPS']);
    req.flush({ jobs: [job], total: 1, page: 0, size: 20 });

    expect(result?.jobs).toEqual([job]);
  });

  it('fetches similar jobs and recommendations with a limit', () => {
    api.getSimilar('j1').subscribe();
    const similar = backend.expectOne(r => r.url === '/api/v1/jobs/j1/similar');
    expect(similar.request.params.get('limit')).toBe('5');
    similar.flush([]);

    let recs: MatchResult[] | undefined;
    api.getRecommendations(3).subscribe(r => (recs = r));
    const rec = backend.expectOne(r => r.url === '/api/v1/jobs/recommendations');
    expect(rec.request.params.get('limit')).toBe('3');
    const match = { jobId: 'j1', job, totalScore: 81, matchLabel: 'STRONG' } as MatchResult;
    rec.flush([match]);
    expect(recs).toEqual([match]);
  });

  it('sends the ignore reason only when one is given', () => {
    api.ignore('j1', 'too far').subscribe();
    const withReason = backend.expectOne(r => r.url === '/api/v1/jobs/j1/ignore');
    expect(withReason.request.method).toBe('POST');
    expect(withReason.request.params.get('reason')).toBe('too far');
    withReason.flush(null);

    api.ignore('j2').subscribe();
    const withoutReason = backend.expectOne(r => r.url === '/api/v1/jobs/j2/ignore');
    expect(withoutReason.request.params.keys()).toEqual([]);
    withoutReason.flush(null);
  });

  it('saves with POST and unsaves with DELETE on the same resource', () => {
    api.save('j1').subscribe();
    const save = backend.expectOne('/api/v1/jobs/j1/save');
    expect(save.request.method).toBe('POST');
    expect(save.request.body).toEqual({});
    save.flush(null);

    api.unsave('j1').subscribe();
    expect(backend.expectOne('/api/v1/jobs/j1/save').request.method).toBe('DELETE');
  });

  it('submits feedback type as a query param with an empty body', () => {
    api.submitFeedback('j1', 'FEWER_LIKE_THIS').subscribe();

    const req = backend.expectOne(r => r.url === '/api/v1/jobs/j1/feedback');
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('type')).toBe('FEWER_LIKE_THIS');
    expect(req.request.body).toEqual({});
    req.flush({});
  });

  it('looks a posting up by its URL', () => {
    api.lookupByUrl('https://jobs.example.com/42?ref=a').subscribe();

    const req = backend.expectOne(r => r.url === '/api/v1/jobs/lookup');
    expect(req.request.params.get('url')).toBe('https://jobs.example.com/42?ref=a');
    req.flush(job);
  });

  it('posts a manually added job as the body', () => {
    const payload = { title: 'Dev', companyName: 'Acme', description: 'Build things', salaryMin: 50000 };
    let created: Job | undefined;
    api.addManual(payload).subscribe(r => (created = r));

    const req = backend.expectOne('/api/v1/jobs/manual');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush(job);
    expect(created).toEqual(job);
  });
});
