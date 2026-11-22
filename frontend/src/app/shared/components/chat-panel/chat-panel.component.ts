import {
  ChangeDetectionStrategy, Component, ElementRef, OnInit, computed, effect, inject, signal, viewChild,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { ChatStore, TurnState } from '../../../core/chat/chat.store';
import { PostingQueue } from '../../../core/chat/posting-queue';
import { ChatAction, ChatSummary } from '../../../core/models/chat.model';
import { JbIconComponent } from '../jb-icon/jb-icon.component';
import { ChatTextComponent } from '../chat-text/chat-text.component';
import { BuddyComponent } from '../buddy/buddy.component';
import { BuddyMood } from '../buddy/buddy-sprites';

/** Buddy on the launcher: he thinks while a turn runs, then waves or shakes his head at how it ended. */
const LAUNCHER_MOODS: Record<TurnState, BuddyMood> = {
  none: 'idle',
  running: 'thinking',
  answered: 'cheer',
  failed: 'error',
};

@Component({
  selector: 'jb-chat-panel',
  imports: [DatePipe, FormsModule, TranslateModule, JbIconComponent, ChatTextComponent, BuddyComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-panel.component.html',
})
export class ChatPanelComponent implements OnInit {
  readonly store = inject(ChatStore);
  private translate = inject(TranslateService);
  private router = inject(Router);
  private queue = inject(PostingQueue);
  private scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly open = signal(false);
  readonly draft = signal('');
  readonly renamingId = signal<string | null>(null);
  readonly renameDraft = signal('');
  readonly launcherMood = computed(() => LAUNCHER_MOODS[this.store.turn()]);

  constructor() {
    effect(() => {
      this.store.messages();
      const el = this.scroller()?.nativeElement;
      if (el) queueMicrotask(() => (el.scrollTop = el.scrollHeight));
    });
  }

  ngOnInit(): void {
    this.store.load();
  }

  toggle(): void {
    this.open.update(o => !o);
  }

  send(): void {
    if (!this.draft().trim() || this.store.streaming()) return;
    this.store.send(this.draft());
    this.draft.set('');
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  startRename(chat: ChatSummary): void {
    this.renamingId.set(chat.id);
    this.renameDraft.set(chat.title ?? '');
  }

  saveRename(chatId: string): void {
    const title = this.renameDraft().trim();
    if (title) this.store.rename(chatId, title);
    this.renamingId.set(null);
  }

  act(action: ChatAction): void {
    const first = action.postings[0]?.id;
    switch (action.kind) {
      case 'OPEN_POSTING':
        void this.router.navigate(['/jobs', first]);
        break;
      case 'START_APPLICATION':
        void this.router.navigate(['/apply'], { queryParams: { jobId: first } });
        break;
      case 'SHOW_PIPELINE':
        void this.router.navigate(['/pipeline']);
        break;
      case 'STEP_THROUGH':
        this.queue.start(action.postings);
        break;
    }
  }

  remove(chat: ChatSummary): void {
    if (window.confirm(this.translate.instant('chat.deleteConfirm'))) this.store.remove(chat.id);
  }
}
