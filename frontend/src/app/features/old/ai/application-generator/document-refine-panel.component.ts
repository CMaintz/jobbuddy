import { CommonModule } from '@angular/common';
import { AfterViewChecked, Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { StructuredDocument } from '../../../../core/models/structured-document.model';
import { ChatMessage } from './application-generator.types';

@Component({
  selector: 'app-document-refine-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './document-refine-panel.component.html'
})
export class DocumentRefinePanelComponent implements AfterViewChecked {
  @ViewChild('chatScroll') chatScrollEl!: ElementRef<HTMLDivElement>;

  @Input() structuredDocument: StructuredDocument | null = null;
  @Input() chatHistory: ChatMessage[] = [];
  @Input() refining = false;
  @Input() chatMessage = '';

  @Output() chatMessageChange = new EventEmitter<string>();
  @Output() send = new EventEmitter<void>();
  @Output() moveSection = new EventEmitter<{ index: number; direction: -1 | 1 }>();
  @Output() removeSection = new EventEmitter<number>();
  @Output() addCustomSection = new EventEmitter<{ heading: string; body: string }>();

  private shouldScrollChat = false;
  newCustomHeading = '';
  newCustomBody = '';

  ngAfterViewChecked(): void {
    if (!this.shouldScrollChat || !this.chatScrollEl) return;
    const el = this.chatScrollEl.nativeElement;
    el.scrollTop = el.scrollHeight;
    this.shouldScrollChat = false;
  }

  ngOnChanges(): void {
    this.shouldScrollChat = true;
  }

  addSection(): void {
    const heading = this.newCustomHeading.trim();
    if (!heading) return;
    this.addCustomSection.emit({ heading, body: this.newCustomBody.trim() });
    this.newCustomHeading = '';
    this.newCustomBody = '';
  }
}
