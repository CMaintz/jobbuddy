import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'applicationStatus', standalone: true })
export class ApplicationStatusPipe implements PipeTransform {
  transform(status: string): string {
    const map: Record<string, string> = {
      SAVED: 'Saved',
      PREPARING: 'Preparing',
      APPLIED: 'Applied',
      RECRUITER_CONTACT: 'Recruiter Contact',
      INTERVIEW: 'Interview',
      TECHNICAL_TEST: 'Technical Test',
      FINAL_ROUND: 'Final Round',
      OFFER: 'Offer Received',
      REJECTED: 'Rejected',
      ARCHIVED: 'Archived'
    };
    return map[status] ?? status;
  }
}
