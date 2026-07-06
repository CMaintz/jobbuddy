import { Injectable } from '@angular/core';

export interface WizardState {
  step: 1 | 2 | 3 | 4;
  jobId: string;
  jobTitle: string;
  draftId: string | null;
  applicationId: string | null;
  coverLetterContent: string | null;
  coverLetterDocId: string | null;
}

const STORAGE_KEY = 'aa_apply_wizard';

@Injectable({ providedIn: 'root' })
export class ApplyWizardStateService {

  load(jobId: string): WizardState {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as WizardState;
        if (parsed.jobId === jobId) return parsed;
      }
    } catch { /* ignore */ }
    return {
      step: 1, jobId, jobTitle: '',
      draftId: null, applicationId: null,
      coverLetterContent: null, coverLetterDocId: null,
    };
  }

  save(state: WizardState): void {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }

  clear(): void {
    sessionStorage.removeItem(STORAGE_KEY);
  }
}
