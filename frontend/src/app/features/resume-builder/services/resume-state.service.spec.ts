import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NotifyService } from '../../../shared/services/notify.service';
import { INITIAL_SETTINGS, ResumeDraft, ResumeSettings } from '../models/resume-builder.models';
import { ResumeDraftApiService } from './resume-draft-api.service';
import { ResumeStateService } from './resume-state.service';
import type { Mocked } from 'vitest';
import { spyObj } from '../../../../testing/spy-obj';

const AUTOSAVE_DEBOUNCE_MS = 1500;

describe('ResumeStateService', () => {
  let draftApi: Mocked<ResumeDraftApiService>;
  let notify: Mocked<NotifyService>;
  let state: ResumeStateService;

  afterEach(() => vi.useRealTimers());

  // Called inside each test rather than in beforeEach so a test can install fake timers before
  // the service's effect and autosave debounce create theirs.
  function setup(withDraft = true): void {
    draftApi = spyObj<ResumeDraftApiService>(['getDraft', 'saveDraft']);
    notify = spyObj<NotifyService>(['error']);
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
    if (withDraft) state.loadDraft('d1').subscribe();
    TestBed.tick();
  }

  function edit(fullName: string): void {
    state.updatePersonalInfo({ fullName });
    TestBed.tick();
  }

  function editAndWait(): void {
    edit('Ada');
    vi.advanceTimersByTime(AUTOSAVE_DEBOUNCE_MS);
  }

  it('saves once, a full debounce after the last edit of a burst', () => {
    vi.useFakeTimers();
    setup();
    edit('A');
    vi.advanceTimersByTime(1000);
    edit('Ad');
    vi.advanceTimersByTime(1000);
    edit('Ada');
    vi.advanceTimersByTime(AUTOSAVE_DEBOUNCE_MS - 1);

    expect(draftApi.saveDraft).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);

    expect(draftApi.saveDraft).toHaveBeenCalledTimes(1);
    const [id, body] = draftApi.saveDraft.mock.lastCall!;
    expect(id).toBe('d1');
    expect(body.resumeData?.personalInfo.fullName).toBe('Ada');
  });

  it('does not autosave a freshly loaded draft', () => {
    vi.useFakeTimers();
    setup();
    vi.advanceTimersByTime(AUTOSAVE_DEBOUNCE_MS);

    expect(state.isDirty()).toBe(false);
    expect(draftApi.saveDraft).not.toHaveBeenCalled();
  });

  it('does not autosave while no draft is open', () => {
    vi.useFakeTimers();
    setup(false);
    editAndWait();

    expect(draftApi.saveDraft).not.toHaveBeenCalled();
    expect(state.isDirty()).toBe(true);
  });

  it('reports saving while the request is in flight and clears dirty once it lands', () => {
    vi.useFakeTimers();
    setup();
    const response = new Subject<ResumeDraft>();
    draftApi.saveDraft.mockReturnValue(response);
    editAndWait();

    expect(state.isSaving()).toBe(true);
    expect(state.isDirty()).toBe(true);

    response.next({ id: 'd1' } as ResumeDraft);

    expect(state.isSaving()).toBe(false);
    expect(state.isDirty()).toBe(false);
  });

  it('groups skills by trimmed category in first-seen order, uncategorised last', () => {
    setup();
    state.addSkill({ name: 'Git' });
    state.addSkill({ name: 'Java', category: 'Backend' });
    state.addSkill({ name: 'Jira', category: '   ' });
    state.addSkill({ name: 'Angular', category: 'Frontend' });
    state.addSkill({ name: 'Spring', category: ' Backend ' });

    const groups = state.skillGroups().map(g => [g.category, g.skills.map(s => s.name)]);

    expect(groups).toEqual([
      ['Backend', ['Java', 'Spring']],
      ['Frontend', ['Angular']],
      [null, ['Git', 'Jira']],
    ]);
  });

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

  // A draft saved while Helvetica and Khand were in the picker. Bug caught: the picker shows no
  // font selected, so she cannot see that it prints in Arial or Inter.
  function loadOldDraft(settings: Partial<ResumeSettings>): void {
    setup(false);
    draftApi.getDraft.mockReturnValue(of({ id: 'd1', settings: { ...INITIAL_SETTINGS, ...settings } } as ResumeDraft));
    state.loadDraft('d1').subscribe();
  }

  it('opens a draft naming Helvetica with Arial selected', () => {
    loadOldDraft({ fontFamily: 'Helvetica' });

    expect(state.settings().fontFamily).toBe('Arial');
  });

  it('opens a draft naming Khand with Inter selected', () => {
    loadOldDraft({ fontFamily: 'Khand' });

    expect(state.settings().fontFamily).toBe('Inter');
  });

  it('shows the printed font for a section that names a removed one, keeping its other styling', () => {
    loadOldDraft({ sectionTypography: { summary: { fontFamily: 'Khand', bold: true }, skills: { italic: true } } });

    expect(state.settings().sectionTypography).toEqual({
      summary: { fontFamily: 'Inter', bold: true },
      skills: { italic: true },
    });
  });

  it('keeps a font the picker still offers', () => {
    loadOldDraft({ fontFamily: 'Lora' });

    expect(state.settings().fontFamily).toBe('Lora');
  });
});
