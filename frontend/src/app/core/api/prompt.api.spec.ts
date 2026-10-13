import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PromptApiService } from './prompt.api';

describe('PromptApiService', () => {
  let api: PromptApiService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withXhr()), provideHttpClientTesting()] });
    api = TestBed.inject(PromptApiService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shares one fetch of the library between cached readers', () => {
    api.getAllCached().subscribe();
    api.getAllCached().subscribe();
    backend.expectOne('/api/v1/prompts').flush([]);

    api.getAllCached().subscribe();
    backend.expectNone('/api/v1/prompts');
  });

  it('refetches after a change so the default in use is never stale', () => {
    api.getAllCached().subscribe();
    backend.expectOne('/api/v1/prompts').flush([]);

    api.selectDefault('t1').subscribe();
    backend.expectOne('/api/v1/prompts/t1/default').flush(null);

    api.getAllCached().subscribe();
    backend.expectOne('/api/v1/prompts').flush([]);
  });

  it('retries after a failed fetch instead of replaying the error', () => {
    api.getAllCached().subscribe({ error: () => undefined });
    backend.expectOne('/api/v1/prompts').flush('down', { status: 503, statusText: 'Unavailable' });

    api.getAllCached().subscribe();
    backend.expectOne('/api/v1/prompts').flush([]);
  });
});
