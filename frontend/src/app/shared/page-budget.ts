import { Signal, computed, signal } from '@angular/core';

/**
 * Page budgets as Danish hiring reads them: a letter that runs to a second page is a defect, and
 * a CV runs to 2 pages at most. Mirrors the backend's PageBudget for the PDFs built in the browser.
 */
export type BudgetedDocument = 'letter' | 'cv';

export interface PageBudget {
  kind: BudgetedDocument;
  pageCount: number;
  budget: number;
  overBudget: boolean;
}

const BUDGETS: Record<BudgetedDocument, number> = { letter: 1, cv: 2 };

export function pageBudget(kind: BudgetedDocument, pageCount: number): PageBudget {
  const budget = BUDGETS[kind];
  return { kind, pageCount, budget, overBudget: pageCount > budget };
}

/**
 * The page count of the last export, for as long as the document still matches what was
 * exported. A count measured on an older version of the text describes a file the user has
 * since changed, so it is hidden until she exports again rather than shown as current.
 *
 * `source` must change identity whenever anything that affects the page count changes: the
 * comparison is by reference, so pass a computed signal over immutable state.
 */
export class ExportedPageBudget {
  private readonly measured = signal<{ budget: PageBudget; source: unknown } | null>(null);

  /** The budget of the last export, or null when there is none or the document changed since. */
  readonly current = computed(() => {
    const m = this.measured();
    return m && m.source === this.source() ? m.budget : null;
  });

  constructor(private readonly kind: BudgetedDocument, private readonly source: Signal<unknown>) {}

  /**
   * Records the page count once `download` resolves, against the document as it was when the
   * export started, so an edit made while the PDF is being built still counts as a change.
   */
  track(download: Promise<number>): Promise<void> {
    const source = this.source();
    return download.then(pages => this.measured.set({ budget: pageBudget(this.kind, pages), source }));
  }
}
