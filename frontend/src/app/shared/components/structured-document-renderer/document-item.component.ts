import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { StructuredDocumentItem } from '../../../core/models/structured-document.model';

@Component({
  selector: 'app-document-item',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './document-item.component.html'
})
export class DocumentItemComponent {
  @Input({ required: true }) item!: StructuredDocumentItem;
}
