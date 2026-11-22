import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ApplicationOutputComponent } from './application-output.component';
import { AiApiService } from '../../core/api/ai.api';
import { ApplicationsApiService } from '../../core/api/applications.api';
import { JobsApiService } from '../../core/api/jobs.api';
import { DocumentApiService } from '../../core/api/document.api';
import { PdfExportService } from '../resume-builder/services/pdf-export.service';
import { AtsPdfService } from '../../shared/services/ats-pdf.service';
import { NotifyService } from '../../shared/services/notify.service';
import { GeneratedDocument } from '../../core/models/generated-document.model';
import { Job } from '../../core/models/job.model';
import { Application } from '../../core/models/application.model';
import { LetterReview } from '../../core/models/structured-document.model';

/**
 * The export warning as the output page drives it: which text counts as checked, and what a
 * re-check that cannot help tells the user.
 */
describe('ApplicationOutputComponent export check', () => {
  const POSTING = 'Vi søger en compliance-specialist.';
  let ai: jasmine.SpyObj<AiApiService>;
  let ats: jasmine.SpyObj<AtsPdfService>;

  const passed: LetterReview = { needs: [], gate: { verdict: 'PASSED', points: [], rounds: 0 }, stale: false };
  const failed: LetterReview = {
    needs: [],
    gate: { verdict: 'FAILED', rounds: 3, points: [{ point: 13, passed: false, quotes: ['X, Y og Z.'] }] },
    stale: false,
  };

  function letter(review: LetterReview | null): GeneratedDocument {
    return {
      id: 'doc-1', userId: 'u', jobId: 'job-1', documentType: 'APPLICATION_TEXT', content: 'Jeres behov.',
      structuredContent: review ? JSON.stringify({ bodyContent: 'Jeres behov.', letterReview: review }) : undefined,
      createdAt: '2026-11-20T10:00:00Z',
    };
  }

  function open(doc: GeneratedDocument, description: string | null = POSTING): ApplicationOutputComponent {
    const jobs = jasmine.createSpyObj<JobsApiService>('JobsApiService', ['getDocumentsForJob', 'getById']);
    jobs.getDocumentsForJob.and.returnValue(of([doc]));
    jobs.getById.and.returnValue(of({ id: 'job-1', descriptionClean: description } as unknown as Job));
    const apps = jasmine.createSpyObj<ApplicationsApiService>('ApplicationsApiService', ['getById']);
    apps.getById.and.returnValue(of({ id: 'app-1', jobId: 'job-1' } as unknown as Application));
    const documents = jasmine.createSpyObj<DocumentApiService>('DocumentApiService', ['getQualityScores']);
    documents.getQualityScores.and.returnValue(of([]));
    TestBed.configureTestingModule({
      imports: [ApplicationOutputComponent, TranslateModule.forRoot()],
      providers: [
        { provide: ActivatedRoute, useValue: { snapshot: {
          paramMap: convertToParamMap({ id: 'app-1' }), queryParamMap: convertToParamMap({}) } } },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) },
        { provide: AiApiService, useValue: ai },
        { provide: ApplicationsApiService, useValue: apps },
        { provide: JobsApiService, useValue: jobs },
        { provide: DocumentApiService, useValue: documents },
        { provide: PdfExportService, useValue: jasmine.createSpyObj('PdfExportService', ['download']) },
        { provide: AtsPdfService, useValue: ats },
      ],
    });
    const component = TestBed.createComponent(ApplicationOutputComponent).componentInstance;
    component.format.set('app');
    component.ngOnInit();
    return component;
  }

  beforeEach(() => {
    ai = jasmine.createSpyObj<AiApiService>('AiApiService', ['saveStructuredDocument', 'recheckLetter']);
    ats = jasmine.createSpyObj<AtsPdfService>('AtsPdfService', ['downloadLetter']);
    ats.downloadLetter.and.returnValue(Promise.resolve(1));
  });

  it('holds the download of a passed letter once an AI refinement replaced its text', () => {
    // Bug: the refined text is only local, the stored review still says passed, so it exports
    // unchecked; and when the save fails that lasts for the rest of the visit.
    ai.saveStructuredDocument.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const page = open(letter(passed));
    page.refinedText.set('En helt ny tekst, som porten aldrig har set.');

    page.acceptRefinement();
    page.downloadAtsPdf();

    expect(ats.downloadLetter).not.toHaveBeenCalled();
    expect(page.exportGuard.pending()).toBe('ats');
    expect(page.exportCheck()).toBe('stale');
  });

  it('keeps the failed points and says the check failed when a re-check gets no verdict', () => {
    // Bug: a provider outage replaces the failed verdict with "no verdict" and the user loses the
    // quotes she was fixing, or sees no sign that the re-check did nothing.
    ai.recheckLetter.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    const page = open(letter(failed));
    page.downloadAtsPdf();

    page.exportGuard.recheck();

    expect(TestBed.inject(NotifyService).notice()?.message).toBe('output.exportCheck.recheckFailed');
    expect(page.exportCheck()).toBe('failed');
    expect(page.exportGuard.pending()).toBe('ats');
  });

  it('offers no re-check for a legacy letter without structure, or a job without a description', () => {
    // Bug: the button is offered on the job id alone, and the server can only answer 409.
    expect(open(letter(null)).canRecheck()).toBeFalse();
    TestBed.resetTestingModule();
    expect(open(letter(failed), null).canRecheck()).toBeFalse();
    TestBed.resetTestingModule();
    expect(open(letter(failed)).canRecheck()).toBeTrue();
  });

  it('explains a re-check the server cannot run instead of asking to try again', () => {
    // Bug: a 409 shows "try again in a moment" for a letter that can never be checked.
    ai.recheckLetter.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    const page = open(letter(failed));
    page.downloadAtsPdf();

    page.exportGuard.recheck();

    expect(TestBed.inject(NotifyService).notice()?.message).toBe('output.exportCheck.cannotRecheck');
  });

  it('says the letter changed during the check instead of calling it uncheckable', () => {
    // Bug: every 409 reads as "not tied to a posting", so a letter edited while the gate ran
    // tells the user it can never be checked when checking again would work.
    ai.recheckLetter.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { status: 409, code: 'LETTER_CHANGED_DURING_CHECK' },
    })));
    const page = open(letter(failed));
    page.downloadAtsPdf();

    page.exportGuard.recheck();

    expect(TestBed.inject(NotifyService).notice()?.message).toBe('output.exportCheck.changedDuringCheck');
  });
});
