import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
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
  function setup(): void {
    draftApi = jasmine.createSpyObj<ResumeDraftApiService>('ResumeDraftApiService', ['getDraft', 'saveDraft']);
    notify = jasmine.createSpyObj<NotifyService>('NotifyService', ['error']);
    TestBed.configureTestingModule({
      providers: [
        { provide: ResumeDraftApiService, useValue: draftApi },
        { provide: NotifyService, useValue: notify },
      ],
    });
    state = TestBed.inject(ResumeStateService);
    draftApi.getDraft.and.returnValue(of({ id: 'd1', jobId: null } as unknown as ResumeDraft));
    draftApi.saveDraft.and.returnValue(of({ id: 'd1' } as ResumeDraft));
    state.loadDraft('d1').subscribe();
    TestBed.tick();
  }

  function editAndWait(): void {
    state.updatePersonalInfo({ fullName: 'Ada' });
    TestBed.tick();
    tick(AUTOSAVE_DEBOUNCE_MS);
  }

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
