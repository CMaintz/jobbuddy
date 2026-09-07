import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { StructuredDocumentSection } from '../../../core/models/structured-document.model';
import { DocumentItemComponent } from './document-item.component';

@Component({
  selector: 'app-document-section',
  standalone: true,
  imports: [CommonModule, DocumentItemComponent],
  template: `
    <section class="section">
      <h2>{{ section.heading }}</h2>
      @if (section.body) {
        <p class="section-body">{{ section.body }}</p>
      }

      @if (section.type === 'skills') {
        <ul class="skills">
          @for (item of section.items ?? []; track item.title) {
            <li>{{ item.title }}</li>
          }
        </ul>
      } @else {
        @for (item of section.items ?? []; track item.sourceId || item.title) {
          <app-document-item [item]="item"></app-document-item>
        }
      }
    </section>
  `
})
export class DocumentSectionComponent {
  @Input({ required: true }) section!: StructuredDocumentSection;
}
