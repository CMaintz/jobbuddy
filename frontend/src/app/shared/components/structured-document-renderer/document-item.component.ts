import { Component, Input } from '@angular/core';
import { StructuredDocumentItem } from '../../../core/models/structured-document.model';

@Component({
  selector: 'app-document-item',
  imports: [],
  templateUrl: './document-item.component.html'
})
export class DocumentItemComponent {
  @Input({ required: true }) item!: StructuredDocumentItem;
}
