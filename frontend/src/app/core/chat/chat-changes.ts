import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { ChatPlan, PlanItem } from '../models/chat.model';

type Area = 'applications' | 'savedPostings';

const AREA_OF: Record<PlanItem['kind'], Area> = {
  SAVE_POSTING: 'savedPostings',
  IGNORE_POSTING: 'savedPostings',
  UPDATE_STATUS: 'applications',
  LOG_INTERVIEW: 'applications',
  GENERATE_DOCUMENTS: 'applications',
};

/**
 * Tells the app's other screens when a change she approved in the chat has been made, so an open
 * pipeline or saved list loads again instead of showing what was true before.
 */
@Injectable({ providedIn: 'root' })
export class ChatChanges {
  private readonly applicationsChanged = new Subject<void>();
  private readonly savedPostingsChanged = new Subject<void>();

  readonly applications: Observable<void> = this.applicationsChanged.asObservable();
  readonly savedPostings: Observable<void> = this.savedPostingsChanged.asObservable();

  /** Announces every item that is done now and was not in {@code before}. */
  noteDone(before: ChatPlan | undefined, after: ChatPlan): void {
    const areas = new Set(after.items
      .filter(item => item.state === 'DONE' && before?.items.find(b => b.id === item.id)?.state !== 'DONE')
      .map(item => AREA_OF[item.kind]));
    if (areas.has('applications')) this.applicationsChanged.next();
    if (areas.has('savedPostings')) this.savedPostingsChanged.next();
  }
}
