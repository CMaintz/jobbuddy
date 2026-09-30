import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AiApiService, AiCredentialStatus, AnalysisResponse, GenerateDocumentRequest } from './ai.api';
import { StructuredDocument } from '../models/structured-document.model';

describe('AiApiService', () => {
  let api: AiApiService;
  let backend: HttpTestingController;

  const doc = { documentType: 'COVER_LETTER' } as unknown as StructuredDocument;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AiApiService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('omits templateId from the render-model request when none is chosen', () => {
    api.getCvRenderModel().subscribe();
    const bare = backend.expectOne(r => r.url === '/api/v1/ai/cv/render-model');
    expect(bare.request.params.keys()).toEqual([]);
    bare.flush(doc);

    api.getCvRenderModel('modern').subscribe();
    const withTemplate = backend.expectOne(r => r.url === '/api/v1/ai/cv/render-model');
    expect(withTemplate.request.params.get('templateId')).toBe('modern');
    withTemplate.flush(doc);
  });

  it('posts a document generation request as-is and returns the structured document', () => {
    const request: GenerateDocumentRequest = {
      jobId: 'j1', documentType: 'COVER_LETTER', targetLanguage: 'da', lengthPreference: 'SHORT',
    };
    let result: StructuredDocument | undefined;
    api.generateDocument(request).subscribe(r => (result = r));

    const req = backend.expectOne('/api/v1/ai/generate-document');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(doc);
    expect(result).toEqual(doc);
  });

  it('wraps a structured document with its job when saving', () => {
    api.saveStructuredDocument(doc, 'j1').subscribe();

    const req = backend.expectOne('/api/v1/ai/documents/structured');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ document: doc, jobId: 'j1' });
    req.flush(doc);
  });

  it('analyzes a CV version and the master profile through the same endpoint', () => {
    const analysis = { suggestions: ['quantify impact'], score: 64, rawResponse: '' } as AnalysisResponse;
    let result: AnalysisResponse | undefined;

    api.analyzeCv('cv-1', 'j1').subscribe(r => (result = r));
    const byVersion = backend.expectOne('/api/v1/ai/analyze');
    expect(byVersion.request.body).toEqual({ cvVersionId: 'cv-1', jobId: 'j1' });
    byVersion.flush(analysis);
    expect(result).toEqual(analysis);

    api.analyze({ jobId: 'j1' }).subscribe();
    const byProfile = backend.expectOne('/api/v1/ai/analyze');
    expect(byProfile.request.body).toEqual({ jobId: 'j1' });
    byProfile.flush(analysis);
  });

  it('stores a credential with PUT and clears it with DELETE', () => {
    const status: AiCredentialStatus = {
      configured: true, provider: 'OPENAI', hint: 'abcd', model: 'gpt-4o', updatedAt: null, storageAvailable: true,
    };
    let result: AiCredentialStatus | undefined;
    api.setCredential('OPENAI', 'sk-secret', 'gpt-4o').subscribe(r => (result = r));

    const put = backend.expectOne('/api/v1/ai/credentials');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ provider: 'OPENAI', apiKey: 'sk-secret', model: 'gpt-4o' });
    put.flush(status);
    expect(result).toEqual(status);

    api.clearCredential().subscribe();
    expect(backend.expectOne('/api/v1/ai/credentials').request.method).toBe('DELETE');
  });

  it('sends raw CV text for parsing and a refine request for refinement', () => {
    api.parseCv('Jane Doe\nEngineer').subscribe();
    const parse = backend.expectOne('/api/v1/ai/parse-cv');
    expect(parse.request.body).toEqual({ rawCvText: 'Jane Doe\nEngineer' });
    parse.flush({});

    const refine = { currentContent: 'old', userMessage: 'shorter', sectionKey: 'profile' };
    api.refine(refine).subscribe();
    const req = backend.expectOne('/api/v1/ai/refine');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(refine);
    req.flush({ refinedContent: 'new', modelUsed: 'gpt-4o' });
  });

  it('triggers the skill-gap analysis with an empty POST body', () => {
    api.analyzeSkillGaps().subscribe();

    const req = backend.expectOne('/api/v1/ai/skill-gaps');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ gaps: [], jobsAnalyzed: 0 });
  });
});
