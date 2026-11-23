import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LetterExportWarningComponent } from './letter-export-warning.component';
import { LetterCheckState } from './letter-export-check';
import { LetterReview } from '../../core/models/structured-document.model';

/** The warning has to say why, show what failed, and only offer a re-check that can work. */
describe('LetterExportWarningComponent', () => {
  let fixture: ComponentFixture<LetterExportWarningComponent>;

  const failed: LetterReview = {
    needs: [],
    gate: {
      verdict: 'FAILED', rounds: 3, points: [
        { point: 13, passed: false, quotes: ['I søger en, der kan X, Y og Z.'], reason: 'Opremser opslaget.' },
      ],
    },
    stale: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [LetterExportWarningComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      common: { cancel: 'Cancel' },
      output: {
        gate: { title: 'Gate', failed: 'Failed', point: { '13': 'Posting echo' } },
        exportCheck: {
          title: 'Before you download',
          failed: 'The letter failed the check.',
          noVerdict: 'The letter has no verdict.',
          stale: 'Edited since the check.',
          recheck: 'Check again',
          exportAnyway: 'Download anyway',
        },
      },
    });
    translate.use('en');
    fixture = TestBed.createComponent(LetterExportWarningComponent);
  });

  function render(state: LetterCheckState, canRecheck: boolean): HTMLElement {
    fixture.componentRef.setInput('state', state);
    fixture.componentRef.setInput('structuredContent', JSON.stringify({ letterReview: failed }));
    fixture.componentRef.setInput('canRecheck', canRecheck);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says the letter failed and shows each failed point with its quote', () => {
    const text = render('failed', true).textContent ?? '';

    expect(text).toContain('The letter failed the check.');
    expect(text).toContain('Posting echo');
    expect(text).toContain('I søger en, der kan X, Y og Z.');
    expect(text).toContain('Download anyway');
    expect(text).toContain('Check again');
  });

  it('names the reason for a stale letter rather than calling it failed', () => {
    // Bug: one message for every state tells the user an edited, passed letter failed.
    const text = render('stale', true).textContent ?? '';

    expect(text).toContain('Edited since the check.');
    expect(text).not.toContain('The letter failed the check.');
  });

  it('does not offer a re-check that cannot run', () => {
    // Bug: a letter with unsaved edits or no posting offers a re-check that judges the wrong
    // text or always errors.
    expect(render('stale', false).textContent).not.toContain('Check again');
  });
});
