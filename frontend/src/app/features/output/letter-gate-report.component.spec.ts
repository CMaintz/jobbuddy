import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LetterGateReportComponent } from './letter-gate-report.component';
import { LetterReview } from '../../core/models/structured-document.model';

/**
 * The gate's verdict is the user's only view of whether the letter still echoes the posting or
 * recaps the CV, so what failed and why must reach the screen.
 */
describe('LetterGateReportComponent', () => {
  let fixture: ComponentFixture<LetterGateReportComponent>;

  const failed: LetterReview = {
    needs: ['680 chauffører uden skærm skal følge reglerne', 'Revisionen må ikke finde huller'],
    gate: {
      verdict: 'FAILED',
      rounds: 3,
      stop: 'OUT_OF_ROUNDS',
      points: [
        { point: 1, passed: true, quotes: [] },
        { point: 13, passed: false, quotes: ['I søger en, der kan X, Y og Z.'], reason: 'Opremser opslaget.' },
      ],
    },
    stale: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [LetterGateReportComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      output: {
        gate: {
          title: 'Gate', passed: 'Passed', failed: 'Failed', noVerdict: 'No verdict', needs: 'Needs',
          stale: 'Edited since', rounds: 'after {{n}} rewrites',
          point: { '1': 'Colour test', '13': 'Posting echo' },
          stop: { OUT_OF_ROUNDS: 'Still failing after the last rewrite' },
        },
      },
    });
    translate.use('en');
    fixture = TestBed.createComponent(LetterGateReportComponent);
  });

  function render(review: LetterReview | null): HTMLElement {
    fixture.componentRef.setInput('structuredContent', JSON.stringify({ letterReview: review }));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the verdict, each failed point with its quotes, why it stopped, and the needs', () => {
    const text = render(failed).textContent ?? '';

    expect(text).toContain('Failed');
    expect(text).toContain('Posting echo');
    expect(text).toContain('I søger en, der kan X, Y og Z.');
    expect(text).toContain('Opremser opslaget.');
    expect(text).toContain('Still failing after the last rewrite');
    expect(text).toContain('680 chauffører uden skærm skal følge reglerne');
    expect(text).not.toContain('Colour test');
  });

  it('warns when the letter was edited after the gate judged it', () => {
    expect(render({ ...failed, stale: true }).textContent).toContain('Edited since');
  });

  it('shows a pass as passed, and no verdict as no verdict rather than a pass', () => {
    const passed = render({ ...failed, gate: { verdict: 'PASSED', rounds: 1, points: [] } }).textContent ?? '';
    expect(passed).toContain('Passed');

    const none = render({ ...failed, gate: { verdict: 'NOT_COMPLETED', rounds: 0, points: [] } }).textContent ?? '';
    expect(none).toContain('No verdict');
    expect(none).not.toContain('Passed');
  });

  it('shows the needs of an ungated letter with no verdict at all', () => {
    const text = render({ needs: ['Revisionen må ikke finde huller'], gate: null, stale: false }).textContent ?? '';

    expect(text).toContain('Needs');
    expect(text).toContain('Revisionen må ikke finde huller');
    expect(text).not.toContain('Gate');
    expect(text).not.toContain('No verdict');
  });

  it('renders nothing for a document without a review or with unreadable content', () => {
    expect(render(null).textContent?.trim()).toBe('');

    fixture.componentRef.setInput('structuredContent', '{not json');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('');
  });
});
