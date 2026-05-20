import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { StructuredDocumentItem } from '../../../core/models/structured-document.model';

@Component({
  selector: 'app-document-item',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="item">
      <div class="item-head">
        <div>
          <h3>{{ item.title }}</h3>
          @if (item.subtitle) {
            <p class="subtitle">{{ item.subtitle }}</p>
          }
        </div>
        @if (item.dateRange) {
          <p class="dates">{{ item.dateRange }}</p>
        }
      </div>
      @if (item.description) {
        <p class="description">{{ item.description }}</p>
      }
      @if ((item.bullets ?? []).length > 0) {
        <ul class="bullets">
          @for (bullet of item.bullets; track bullet) {
            <li>{{ bullet }}</li>
          }
        </ul>
      }
      @if ((item.technologies ?? []).length > 0) {
        <p class="tech">{{ item.technologies?.join(' · ') }}</p>
      }
      @if ((item.links ?? []).length > 0) {
        <p class="links">{{ item.links?.join(' · ') }}</p>
      }
    </div>
  `
})
export class DocumentItemComponent {
  @Input({ required: true }) item!: StructuredDocumentItem;
}
