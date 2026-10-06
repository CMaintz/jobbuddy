import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { StructuredDocumentSection } from '../../../core/models/structured-document.model';
import { DocumentItemComponent } from './document-item.component';

@Component({
  selector: 'app-document-section',
  imports: [DocumentItemComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './document-section.component.html'
})
export class DocumentSectionComponent {
  @Input({ required: true }) section!: StructuredDocumentSection;
}
