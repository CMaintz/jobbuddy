import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ChatStore } from '../../../core/chat/chat.store';
import { PlanDecisions } from '../../../core/chat/plan-decisions';
import { ChatPlan } from '../../../core/models/chat.model';
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
}
