import { Injectable, inject, signal } from '@angular/core';
import { ChatApiService, ApproveSettings } from '../api/chat.api';
import { ChatPlan } from '../models/chat.model';
import { loadGenDefaults } from '../../features/settings/settings.component';

export type Decision = 'approve' | 'reject';

/** Documents take minutes; a card waiting on them asks again this often. */
const RUNNING_POLL_MS = 5000;

/**
 * Her decisions on proposed changes while they travel to the server: an item with a decision on
 * its way takes no second one, and one that never arrived stays pending so she can try again.
 * Approvals carry her generation settings, so documents follow them as quick apply does.
 */
@Injectable({ providedIn: 'root' })
export class PlanDecisions {
  private api = inject(ChatApiService);
  private readonly inFlight = signal<ReadonlySet<string>>(new Set());
  private readonly notSent = signal<ReadonlySet<string>>(new Set());
  private readonly watched = new Set<string>();

  deciding(id: string): boolean {
    return this.inFlight().has(id);
  }

  failed(id: string): boolean {
    return this.notSent().has(id);
  }

  /** One item; a null item decides every item still waiting in the plan. */
  send(decision: Decision, planId: string, itemId: string | null, decided: (plan: ChatPlan) => void): void {
    const key = itemId ?? planId;
    if (this.deciding(key)) return;
    this.inFlight.update(ids => withId(ids, key, true));
    this.notSent.update(ids => withId(ids, key, false));
    const settings = decision === 'approve' ? genSettings() : undefined;
    this.api.decide(planId, itemId, decision, settings).subscribe({
      next: plan => {
        this.inFlight.update(ids => withId(ids, key, false));
        decided(plan);
      },
      error: () => {
        this.inFlight.update(ids => withId(ids, key, false));
        this.notSent.update(ids => withId(ids, key, true));
      },
    });
  }

  /**
   * Asks again until no item is running, so a card shows when its documents are written. A
   * failed ask is tried again on the same schedule, or the card would wait until she reopens it.
   */
  watch(plan: ChatPlan, changed: (plan: ChatPlan) => void): void {
    if (!isRunning(plan) || this.watched.has(plan.id)) return;
    this.watched.add(plan.id);
    setTimeout(() => this.api.plan(plan.id).subscribe({
      next: fresh => {
        this.watched.delete(plan.id);
        changed(fresh);
      },
      error: () => {
        this.watched.delete(plan.id);
        this.watch(plan, changed);
      },
    }), RUNNING_POLL_MS);
  }
}

function isRunning(plan: ChatPlan): boolean {
  return plan.items.some(i => i.state === 'RUNNING');
}

function genSettings(): ApproveSettings {
  const defaults = loadGenDefaults();
  return { lengthPreference: defaults.length?.toUpperCase(), cvTemplate: defaults.cvTemplate, voice: defaults.voice };
}

function withId(ids: ReadonlySet<string>, id: string, present: boolean): ReadonlySet<string> {
  const next = new Set(ids);
  if (present) next.add(id);
  else next.delete(id);
  return next;
}
