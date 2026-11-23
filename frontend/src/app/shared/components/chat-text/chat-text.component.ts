import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { parseChatMarkdown } from './chat-markdown';

@Component({
  selector: 'jb-chat-text',
  imports: [NgTemplateOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './chat-text.component.html',
})
export class ChatTextComponent {
  readonly text = input('');
  readonly blocks = computed(() => parseChatMarkdown(this.text()));
}
