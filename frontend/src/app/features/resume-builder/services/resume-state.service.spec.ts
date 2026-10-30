import type { Mocked } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NotifyService } from '../../../shared/services/notify.service';
import { ResumeDraft } from '../models/resume-builder.models';
import { ResumeDraftApiService } from './resume-draft-api.service';
import { ResumeStateService } from './resume-state.service';

const AUTOSAVE_DEBOUNCE_MS = 1500;

describe('ResumeStateService', () => {
  let draftApi: Mocked<ResumeDraftApiService>;
  let notify: Mocked<NotifyService>;
  let state: ResumeStateService;

  afterEach(() => vi.useRealTimers());

  // Called inside each test rather than in beforeEach so a test can install fake timers
  // before the service creates its debounce timer.
  function setup(): void {
    draftApi = { getDraft: vi.fn(), saveDraft: vi.fn() } as unknown as Mocked<ResumeDraftApiService>;
    notify = { error: vi.fn() } as unknown as Mocked<NotifyService>;
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: ResumeDraftApiService, useValue: draftApi },
        { provide: NotifyService, useValue: notify },
      ],
    });
    state = TestBed.inject(ResumeStateService);
    draftApi.getDraft.mockReturnValue(of({ id: 'd1', jobId: null } as unknown as ResumeDraft));
    draftApi.saveDraft.mockReturnValue(of({ id: 'd1' } as ResumeDraft));
    state.loadDraft('d1').subscribe();
    TestBed.tick();
  }

  function editAndWait(): void {
    state.updatePersonalInfo({ fullName: 'Ada' });
    TestBed.tick();
    vi.advanceTimersByTime(AUTOSAVE_DEBOUNCE_MS);
  }

  it('keeps autosaving after a failed save', () => {
    vi.useFakeTimers();
    setup();
    draftApi.saveDraft.mockReturnValue(throwError(() => new Error('offline')));
    editAndWait();

    expect(notify.error).toHaveBeenCalledWith('notify.autosaveFailed');
    expect(state.isSaving()).toBe(false);
    expect(state.isDirty()).toBe(true);

    draftApi.saveDraft.mockReturnValue(of({ id: 'd1' } as ResumeDraft));
    editAndWait();

    expect(draftApi.saveDraft).toHaveBeenCalledTimes(2);
    expect(state.isDirty()).toBe(false);
  });

  it('re-labels the anonymised name when the language changes', () => {
    setup();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { masterCv: { candidate: 'Candidate' } });
    translate.setTranslation('da', { masterCv: { candidate: 'Kandidat' } });
    translate.use('en');
    state.updateSettings({ anonymise: true });
    expect(state.displayPersonalInfo().fullName).toBe('Candidate');

    translate.use('da');

    expect(state.displayPersonalInfo().fullName).toBe('Kandidat');
  });

  it('empties and detaches the editor when loading another draft fails', () => {
    setup();
    state.updatePersonalInfo({ fullName: 'Ada' });
    draftApi.getDraft.mockReturnValue(throwError(() => new Error('404')));
    let failed = false;

    state.loadDraft('d2').subscribe({ error: () => (failed = true) });

    expect(failed).toBe(true);
    expect(state.draftId()).toBeNull();
    expect(state.personalInfo().fullName).not.toBe('Ada');
  });
});
