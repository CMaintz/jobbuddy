import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CompaniesApiService, OutreachContact, OutreachTarget } from './companies.api';

describe('CompaniesApiService', () => {
  let api: CompaniesApiService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(CompaniesApiService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('searches with an empty query and first page by default', () => {
    api.search().subscribe();

    const req = backend.expectOne(r => r.url === '/api/v1/companies');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('q')).toBe('');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush([]);
  });

  it('excludes companies hiring right now from outreach targets unless asked', () => {
    api.outreachTargets().subscribe();
    const byDefault = backend.expectOne(r => r.url === '/api/v1/companies/outreach-targets');
    expect(byDefault.request.params.get('limit')).toBe('20');
    expect(byDefault.request.params.get('includeHiringNow')).toBe('false');
    byDefault.flush([]);

    let targets: OutreachTarget[] | undefined;
    api.outreachTargets(5, true).subscribe(r => (targets = r));
    const included = backend.expectOne(r => r.url === '/api/v1/companies/outreach-targets');
    expect(included.request.params.get('limit')).toBe('5');
    expect(included.request.params.get('includeHiringNow')).toBe('true');
    const target: OutreachTarget = {
      companyId: 'c1', companyName: 'Acme', website: null, score: 0.8,
      reasons: [{ code: 'techMatch', args: { tech: 'Java' } }],
      lastPostedAt: null, matchedTechnologies: ['Java'], hasOpenRole: true,
    };
    included.flush([target]);
    expect(targets).toEqual([target]);
  });

  it('tracks outreach with an explicit null company id for an untracked company', () => {
    api.trackOutreach(null, 'Small Shop ApS').subscribe();

    const req = backend.expectOne('/api/v1/companies/outreach');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ companyId: null, companyName: 'Small Shop ApS', contactName: undefined });
    req.flush({});
  });

  it('patches only the fields being changed', () => {
    let updated: OutreachContact | undefined;
    api.updateOutreach('o1', { status: 'CONTACTED', followUpDue: '2026-10-07' }).subscribe(r => (updated = r));

    const req = backend.expectOne('/api/v1/companies/outreach/o1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'CONTACTED', followUpDue: '2026-10-07' });
    const contact = { id: 'o1', companyName: 'Acme', status: 'CONTACTED' } as OutreachContact;
    req.flush(contact);
    expect(updated).toEqual(contact);
  });

  it('untracks outreach with DELETE', () => {
    api.untrackOutreach('o1').subscribe();
    expect(backend.expectOne('/api/v1/companies/outreach/o1').request.method).toBe('DELETE');
  });

  it('reads and saves research notes on the company resource', () => {
    api.getResearch('c1').subscribe();
    backend.expectOne('/api/v1/companies/c1/research').flush({ notes: null, updatedAt: null });

    api.saveResearch('c1', 'Uses Kotlin; hiring in Aarhus').subscribe();
    const save = backend.expectOne('/api/v1/companies/c1/research');
    expect(save.request.method).toBe('PUT');
    expect(save.request.body).toEqual({ notes: 'Uses Kotlin; hiring in Aarhus' });
    save.flush({ notes: 'Uses Kotlin; hiring in Aarhus', updatedAt: '2026-09-30T08:00:00Z' });
  });
});
