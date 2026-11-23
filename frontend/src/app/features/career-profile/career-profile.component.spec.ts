import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { CareerProfileComponent } from './career-profile.component';
import { SkillCandidate } from '../../core/api/skills.api';

describe('CareerProfileComponent likely-held questions', () => {
  const QUESTION = 'Du har {{held}}. Har du også {{skill}}?';
  const LIKELY: SkillCandidate = {
    name: 'Content marketing', category: null, marketFrequency: 0,
    relatedSkills: ['Kampagneplanlægning'], source: 'LIKELY_HELD',
  };
  let fixture: ComponentFixture<CareerProfileComponent>;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CareerProfileComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('da', { careerProfile: { skills: {
      likelyHeld: QUESTION, yesAdd: 'Ja, tilføj', no: 'Nej' } } });
    translate.use('da');
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CareerProfileComponent);
    fixture.detectChanges();
    backend.expectOne(req => req.url === '/api/v1/skills/candidates').flush([LIKELY]);
    // The page's other loads are not what these cases are about.
    backend.match(() => true);
    fixture.componentInstance.activeSection.set('skills');
    fixture.detectChanges();
  });

  function card(): HTMLElement {
    return fixture.nativeElement.querySelector('[data-testid="likely-held"]');
  }

  function answerSent(): { name: string; decision: string } {
    const confirm = backend.expectOne('/api/v1/skills/candidates/confirm');
    return confirm.request.body.confirmations[0];
  }

  it('asks about the suggestion by the skill the user already has', () => {
    expect(card().textContent).toContain('Du har Kampagneplanlægning. Har du også Content marketing?');
  });

  it('adds the skill on yes', () => {
    (card().querySelector('[data-testid="likely-held-yes"]') as HTMLButtonElement).click();

    expect(answerSent()).toEqual(jasmine.objectContaining({ name: 'Content marketing', decision: 'YES' }));
  });

  it('dismisses the skill on no', () => {
    (card().querySelector('[data-testid="likely-held-no"]') as HTMLButtonElement).click();

    expect(answerSent()).toEqual(jasmine.objectContaining({ name: 'Content marketing', decision: 'NO' }));
  });
});
