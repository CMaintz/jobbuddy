import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { STAGE_LABEL_KEY, STAGE_TONE, StatusChipComponent, stageLabelKey, stageTone } from './status-chip.component';

/**
 * Every list, board and detail view renders a stage through this chip, so the mapping is
 * the single source of a stage's colour and label.
 */
describe('stage mapping', () => {
  it('gives every stage both a label and a tone', () => {
    expect(Object.keys(STAGE_TONE).sort()).toEqual(Object.keys(STAGE_LABEL_KEY).sort());
  });

  it('maps known stages', () => {
    expect(stageLabelKey('RECRUITER_CONTACT')).toBe('pipeline.stage.screen');
    expect(stageTone('OFFER')).toBe('success');
    expect(stageTone('REJECTED')).toBe('danger');
  });

  it('falls back to the raw status and a neutral tone for an unknown stage', () => {
    expect(stageLabelKey('GHOSTED')).toBe('GHOSTED');
    expect(stageTone('GHOSTED')).toBe('neutral');
  });
});

describe('StatusChipComponent', () => {
  let fixture: ComponentFixture<StatusChipComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [StatusChipComponent, TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { pipeline: { stage: { interview: 'Interview', offer: 'Offer' } } });
    translate.use('en');
    fixture = TestBed.createComponent(StatusChipComponent);
  });

  function pill(): HTMLElement {
    return fixture.nativeElement.querySelector('jb-pill span');
  }

  it('renders the translated stage in a pill of its tone', () => {
    fixture.componentRef.setInput('status', 'INTERVIEW');
    fixture.detectChanges();

    expect(pill().textContent?.trim()).toBe('Interview');
    expect(pill().className).toBe('pill pill-accent');
  });

  it('follows a status change', () => {
    fixture.componentRef.setInput('status', 'INTERVIEW');
    fixture.detectChanges();
    fixture.componentRef.setInput('status', 'OFFER');
    fixture.detectChanges();

    expect(pill().textContent?.trim()).toBe('Offer');
    expect(pill().className).toBe('pill pill-success');
  });
});
