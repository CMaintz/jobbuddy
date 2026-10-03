import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { StructuredDocumentItem } from '../../../core/models/structured-document.model';

@Component({
  selector: 'app-document-item',
  imports: [],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './document-item.component.html'
})
export class DocumentItemComponent {
  @Input({ required: true }) item!: StructuredDocumentItem;
}
