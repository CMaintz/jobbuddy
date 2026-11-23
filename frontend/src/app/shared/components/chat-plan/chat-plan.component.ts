import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ChatStore } from '../../../core/chat/chat.store';
import { PlanDecisions } from '../../../core/chat/plan-decisions';
import { ChatPlan, PlanItem } from '../../../core/models/chat.model';
import { stageLabelKey } from '../status-chip/status-chip.component';

/**
 * A change the chat proposed, with Godkend and Afvis while it waits for her. Its text comes from
 * the server's checked details, never from the model's answer, so it says what will really happen.
 */
@Component({
  selector: 'jb-chat-plan',
  imports: [DatePipe, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-plan.component.html',
})
export class ChatPlanComponent {
  readonly store = inject(ChatStore);
  readonly decisions = inject(PlanDecisions);
  readonly plan = input.required<ChatPlan>();
  readonly stageLabelKey = stageLabelKey;
  readonly pendingCount = computed(() => this.plan().items.filter(i => i.state === 'PENDING').length);
  private router = inject(Router);

  /** Her documents open in the app's own review, where she edits and exports them herself. */
  openDocuments(item: PlanItem): void {
    void this.router.navigate(['/applications', item.resultApplicationId, 'pack'],
      { queryParams: { jobId: item.details.postingId } });
  }
}
