import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NotifyService } from '../../../shared/services/notify.service';
import { ResumeDraft } from '../models/resume-builder.models';
import { ResumeDraftApiService } from './resume-draft-api.service';
import { ResumeStateService } from './resume-state.service';

const AUTOSAVE_DEBOUNCE_MS = 1500;

describe('ResumeStateService', () => {
  let draftApi: jasmine.SpyObj<ResumeDraftApiService>;
  let notify: jasmine.SpyObj<NotifyService>;
  let state: ResumeStateService;

  // Called inside each test rather than in beforeEach so the service's effect and debounce
  // timer are created in the test's fakeAsync zone, where tick() controls them.
  function setup(withDraft = true): void {
    draftApi = jasmine.createSpyObj<ResumeDraftApiService>('ResumeDraftApiService', ['getDraft', 'saveDraft']);
    notify = jasmine.createSpyObj<NotifyService>('NotifyService', ['error']);
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        { provide: ResumeDraftApiService, useValue: draftApi },
        { provide: NotifyService, useValue: notify },
      ],
    });
    state = TestBed.inject(ResumeStateService);
    draftApi.getDraft.and.returnValue(of({ id: 'd1', jobId: null } as unknown as ResumeDraft));
    draftApi.saveDraft.and.returnValue(of({ id: 'd1' } as ResumeDraft));
    if (withDraft) state.loadDraft('d1').subscribe();
    TestBed.tick();
  }

  function edit(fullName: string): void {
    state.updatePersonalInfo({ fullName });
    TestBed.tick();
  }

  function editAndWait(): void {
    edit('Ada');
    tick(AUTOSAVE_DEBOUNCE_MS);
  }

  it('saves once, a full debounce after the last edit of a burst', fakeAsync(() => {
    setup();
    edit('A');
    tick(1000);
    edit('Ad');
    tick(1000);
    edit('Ada');
    tick(AUTOSAVE_DEBOUNCE_MS - 1);

    expect(draftApi.saveDraft).not.toHaveBeenCalled();

    tick(1);

    expect(draftApi.saveDraft).toHaveBeenCalledTimes(1);
    const [id, body] = draftApi.saveDraft.calls.mostRecent().args;
    expect(id).toBe('d1');
    expect(body.resumeData?.personalInfo.fullName).toBe('Ada');
  }));

  it('does not autosave a freshly loaded draft', fakeAsync(() => {
    setup();
    tick(AUTOSAVE_DEBOUNCE_MS);

    expect(state.isDirty()).toBeFalse();
    expect(draftApi.saveDraft).not.toHaveBeenCalled();
  }));

  it('does not autosave while no draft is open', fakeAsync(() => {
    setup(false);
    editAndWait();

    expect(draftApi.saveDraft).not.toHaveBeenCalled();
    expect(state.isDirty()).toBeTrue();
  }));

  it('reports saving while the request is in flight and clears dirty once it lands', fakeAsync(() => {
    setup();
    const response = new Subject<ResumeDraft>();
    draftApi.saveDraft.and.returnValue(response);
    editAndWait();

    expect(state.isSaving()).toBeTrue();
    expect(state.isDirty()).toBeTrue();

    response.next({ id: 'd1' } as ResumeDraft);

    expect(state.isSaving()).toBeFalse();
    expect(state.isDirty()).toBeFalse();
  }));

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

  it('keeps autosaving after a failed save', fakeAsync(() => {
    setup();
    draftApi.saveDraft.and.returnValue(throwError(() => new Error('offline')));
    editAndWait();

    expect(notify.error).toHaveBeenCalledWith('notify.autosaveFailed');
    expect(state.isSaving()).toBeFalse();
    expect(state.isDirty()).toBeTrue();

    draftApi.saveDraft.and.returnValue(of({ id: 'd1' } as ResumeDraft));
    editAndWait();

    expect(draftApi.saveDraft).toHaveBeenCalledTimes(2);
    expect(state.isDirty()).toBeFalse();
  }));

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
    draftApi.getDraft.and.returnValue(throwError(() => new Error('404')));
    let failed = false;

    state.loadDraft('d2').subscribe({ error: () => (failed = true) });

    expect(failed).toBeTrue();
    expect(state.draftId()).toBeNull();
    expect(state.personalInfo().fullName).not.toBe('Ada');
  });
});
