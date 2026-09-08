import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-tag-input',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbIconComponent],
  templateUrl: './tag-input.component.html',
  styleUrls: ['./tag-input.component.css']
})
export class TagInputComponent {
  @Input() value: string[] = [];
  @Input() placeholder = 'shared.addTag';
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
