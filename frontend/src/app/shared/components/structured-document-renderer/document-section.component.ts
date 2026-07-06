import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { StructuredDocumentSection } from '../../../core/models/structured-document.model';
import { DocumentItemComponent } from './document-item.component';

@Component({
  selector: 'app-document-section',
  standalone: true,
  imports: [CommonModule, DocumentItemComponent],
  templateUrl: './document-section.component.html'
})
export class DocumentSectionComponent {
  @Input({ required: true }) section!: StructuredDocumentSection;
}
