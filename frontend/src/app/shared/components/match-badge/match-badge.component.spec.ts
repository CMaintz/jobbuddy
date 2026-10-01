import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatchBadgeComponent, matchColor, matchLabelKey } from './match-badge.component';

/**
 * The grade comes from the backend; the badge only colours and names it. It must never
 * re-derive a grade from the score, or the two drift when a threshold moves.
 */
describe('match badge helpers', () => {
  it('names each grade by its translation key', () => {
    expect(matchLabelKey('EXCELLENT')).toBe('match.grade.excellent');
    expect(matchLabelKey('MODERATE')).toBe('match.grade.moderate');
  });

  it('treats a missing grade as weak', () => {
    expect(matchLabelKey(undefined)).toBe('match.grade.weak');
    expect(matchColor(undefined)).toEqual(matchColor('WEAK'));
  });

  it('gives the top grades distinct colours', () => {
    expect(matchColor('EXCELLENT').fg).toBe('var(--jb-success)');
    expect(matchColor('STRONG').fg).toBe('var(--jb-accent-2)');
    expect(matchColor('EXCELLENT')).not.toEqual(matchColor('STRONG'));
  });
});

describe('MatchBadgeComponent', () => {
  let fixture: ComponentFixture<MatchBadgeComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MatchBadgeComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      match: { grade: { strong: 'Strong', weak: 'Weak' }, scoreTooltip: 'Match score {{score}}' },
    });
    translate.use('en');
    fixture = TestBed.createComponent(MatchBadgeComponent);
  });

  function badge(): HTMLElement {
    return fixture.nativeElement.querySelector('span');
  }

  it('shows the grade, not the number, with the score on hover', () => {
    fixture.componentRef.setInput('label', 'STRONG');
    fixture.componentRef.setInput('score', 74);
    fixture.detectChanges();

    expect(badge().textContent?.trim()).toBe('Strong');
    expect(badge().title).toBe('Match score 74');
    expect(badge().style.color).toBe('var(--jb-accent-2)');
  });

  it('does not upgrade a high score whose grade is weak', () => {
    fixture.componentRef.setInput('label', 'WEAK');
    fixture.componentRef.setInput('score', 95);
    fixture.detectChanges();

    expect(badge().textContent?.trim()).toBe('Weak');
    expect(badge().style.color).toBe('var(--jb-text-dim)');
  });
});
