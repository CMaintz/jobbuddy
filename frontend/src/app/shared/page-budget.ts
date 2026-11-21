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
