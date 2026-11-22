import { Injectable, inject, signal } from '@angular/core';
import { ChatApiService } from '../api/chat.api';
import { ChatPlan } from '../models/chat.model';

export type Decision = 'approve' | 'reject';

/**
 * Her decisions on proposed changes while they travel to the server: an item with a decision on
 * its way takes no second one, and one that never arrived stays pending so she can try again.
 */
@Injectable({ providedIn: 'root' })
export class PlanDecisions {
  private api = inject(ChatApiService);
  private readonly inFlight = signal<ReadonlySet<string>>(new Set());
  private readonly notSent = signal<ReadonlySet<string>>(new Set());

  deciding(itemId: string): boolean {
    return this.inFlight().has(itemId);
  }

  failed(itemId: string): boolean {
    return this.notSent().has(itemId);
  }

  send(decision: Decision, planId: string, itemId: string, decided: (plan: ChatPlan) => void): void {
    if (this.deciding(itemId)) return;
    this.inFlight.update(ids => withId(ids, itemId, true));
    this.notSent.update(ids => withId(ids, itemId, false));
    this.api.decide(planId, itemId, decision).subscribe({
      next: plan => {
        this.inFlight.update(ids => withId(ids, itemId, false));
        decided(plan);
      },
      error: () => {
        this.inFlight.update(ids => withId(ids, itemId, false));
        this.notSent.update(ids => withId(ids, itemId, true));
      },
    });
  }
}

function withId(ids: ReadonlySet<string>, id: string, present: boolean): ReadonlySet<string> {
  const next = new Set(ids);
  if (present) next.add(id);
  else next.delete(id);
  return next;
}
