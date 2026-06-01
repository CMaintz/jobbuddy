import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-tag-input',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent],
  template: `
    <div style="display:flex;flex-wrap:wrap;gap:6px;padding:8px 10px;background:var(--jb-surface-2);border:1px solid var(--jb-border);border-radius:6px;min-height:36px;align-items:center;">
      <span *ngFor="let tag of value; let i = index"
        style="display:inline-flex;align-items:center;gap:4px;padding:2px 8px;border-radius:999px;font-size:12px;font-weight:500;
          background:var(--jb-accent-soft);border:1px solid var(--jb-accent-border);color:var(--jb-accent-2);">
        {{ tag }}
        <button (click)="removeTag(i)" style="background:none;border:none;cursor:pointer;padding:0;color:inherit;display:flex;">
          <jb-icon name="x" [size]="9" [strokeWidth]="2.5" />
        </button>
      </span>
      <input [(ngModel)]="inputValue" (keydown.enter)="addTag()" (keydown.backspace)="onBackspace()"
        [placeholder]="value.length === 0 ? placeholder : ''"
        style="background:none;border:none;outline:none;font-size:12px;color:var(--jb-text);flex:1;min-width:60px;padding:0;" />
    </div>
  `,
  styles: [`:host { display: block; }`]
})
export class TagInputComponent {
  @Input() value: string[] = [];
  @Input() placeholder = 'Add...';
  @Input() suggestions: string[] = [];
  @Output() valueChange = new EventEmitter<string[]>();

  inputValue = '';

  addTag(): void {
    const tag = this.inputValue.trim();
    if (tag && !this.value.includes(tag)) {
      this.value = [...this.value, tag];
      this.valueChange.emit(this.value);
    }
    this.inputValue = '';
  }

  removeTag(index: number): void {
    this.value = this.value.filter((_, i) => i !== index);
    this.valueChange.emit(this.value);
  }

  onBackspace(): void {
    if (!this.inputValue && this.value.length > 0) {
      this.removeTag(this.value.length - 1);
    }
  }
}
