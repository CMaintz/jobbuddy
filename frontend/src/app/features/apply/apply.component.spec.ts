import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { PromptApiService } from '../../core/api/prompt.api';
import { Job } from '../../core/models/job.model';
import { NotifyService } from '../../shared/services/notify.service';
import { ApplyComponent } from './apply.component';

// Written as a literal on purpose: the browser extension (extension/capture-bridge.js) writes
// this key, and its own test pins the same string. Renaming it on one side alone breaks the
// handoff silently, so each side's test fails instead.
const CAPTURE_KEY = 'jb-captured-job';
const DRAFT_KEY = 'jb-apply-draft';
const ELEVEN_MINUTES_MS = 11 * 60_000;

/** The extension hands a scraped posting to the apply screen through localStorage. */
describe('ApplyComponent captured-job handoff', () => {
  beforeEach(() => {
    localStorage.removeItem(CAPTURE_KEY);
    localStorage.removeItem(DRAFT_KEY);
  });

  afterEach(() => {
    localStorage.removeItem(CAPTURE_KEY);
    localStorage.removeItem(DRAFT_KEY);
  });

  function capture(savedAt = Date.now()): void {
    localStorage.setItem(CAPTURE_KEY, JSON.stringify({
      savedAt,
      title: 'Backend-udvikler',
      company: 'Netcompany',
      location: 'Aarhus',
      description: 'Vi søger en backend-udvikler.',
      url: 'https://jobs.example.dk/opslag/123',
    }));
  }

  function create(queryParams: Params = {}): ApplyComponent {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
        { provide: AiApiService, useValue: {} },
        { provide: JobsApiService, useValue: { getById: () => of({ id: 'j1', title: 'Preset role' } as Job) } },
        { provide: ApplicationsApiService, useValue: { getAll: () => of([]) } },
        { provide: PromptApiService, useValue: { getAll: () => of([]) } },
        { provide: NotifyService, useValue: { onError: () => () => undefined } },
      ],
    });
    // Only the capture logic is under test; the real template pulls in icons and translations.
    TestBed.overrideComponent(ApplyComponent, { set: { template: '', imports: [], styleUrls: [] } });
    const fixture = TestBed.createComponent(ApplyComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('prefills the form from a fresh capture and consumes it', () => {
    capture();
    const apply = create();

    expect(apply.role).toBe('Backend-udvikler');
    expect(apply.company).toBe('Netcompany');
    expect(apply.location).toBe('Aarhus');
    expect(apply.jd).toBe('Vi søger en backend-udvikler.');
    expect(apply.jobUrl).toBe('https://jobs.example.dk/opslag/123');
    expect(apply.mode()).toBe('paste');
    expect(apply.capturedFrom()).toBe('jobs.example.dk');
    expect(localStorage.getItem(CAPTURE_KEY)).toBeNull();
  });

  it('drops a capture older than ten minutes without prefilling', () => {
    capture(Date.now() - ELEVEN_MINUTES_MS);
    const apply = create();

    expect(apply.role).toBe('');
    expect(apply.capturedFrom()).toBe('');
    expect(localStorage.getItem(CAPTURE_KEY)).toBeNull();
  });

  it('lets a fresh capture win over the job fields of a saved draft', () => {
    localStorage.setItem(DRAFT_KEY, JSON.stringify({
      savedAt: Date.now(), mode: 'manual', company: 'Old Co', role: 'Old role', location: '', jd: '', jobUrl: '',
    }));
    capture();
    const apply = create();

    expect(apply.company).toBe('Netcompany');
    expect(apply.role).toBe('Backend-udvikler');
  });

  it('leaves the capture parked when a job is preset via ?jobId', () => {
    capture();
    const apply = create({ jobId: 'j1' });

    expect(apply.role).toBe('Preset role');
    expect(localStorage.getItem(CAPTURE_KEY)).not.toBeNull();
  });

  it('picks up a capture the extension writes after the screen has loaded', () => {
    const apply = create();
    capture();

    window.dispatchEvent(new CustomEvent('jb-capture-ready'));

    expect(apply.role).toBe('Backend-udvikler');
    expect(localStorage.getItem(CAPTURE_KEY)).toBeNull();
  });
});
