import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { PageBudgetWarningComponent } from './page-budget-warning.component';
import { BudgetedDocument, pageBudget } from '../../page-budget';

describe('PageBudgetWarningComponent', () => {
  function render(kind: BudgetedDocument, pages: number): string {
    const fixture = TestBed.createComponent(PageBudgetWarningComponent);
    fixture.componentRef.setInput('budget', pageBudget(kind, pages));
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent?.trim() ?? '';
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PageBudgetWarningComponent, TranslateModule.forRoot()],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', {
      pageBudget: {
        letter: { over: 'Letter is {{pages}} pages.', shorten: 'Shorten the letter.' },
        cv: { over: 'CV is {{pages}} pages.', shorten: 'Shorten the CV.' },
      },
    });
    translate.use('en');
  });

  it('warns with the page count when a letter runs to two pages', () => {
    const text = render('letter', 2);

    expect(text).toContain('Letter is 2 pages.');
    expect(text).toContain('Shorten the letter.');
  });

  it('stays silent for a one-page letter', () => {
    expect(render('letter', 1)).toBe('');
  });

  it('stays silent for a two-page CV', () => {
    expect(render('cv', 2)).toBe('');
  });

  it('warns when a CV runs to three pages', () => {
    expect(render('cv', 3)).toContain('CV is 3 pages.');
  });
});
