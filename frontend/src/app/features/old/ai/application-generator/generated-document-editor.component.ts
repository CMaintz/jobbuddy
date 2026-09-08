import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApplicationStatus } from '../../../../core/models/application.model';
import { StructuredDocument } from '../../../../core/models/structured-document.model';
import { StructuredDocumentRendererComponent } from '../../../../shared/components/structured-document-renderer/structured-document-renderer.component';
import { AtsReportPanelComponent } from '../../cv/components/ats-report-panel.component';
import { ActiveGeneratedDocument } from './application-generator.types';

@Component({
  selector: 'app-generated-document-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, StructuredDocumentRendererComponent, AtsReportPanelComponent],
  styleUrls: ['./generated-document-editor.component.css'],
  templateUrl: './generated-document-editor.component.html'
})
export class GeneratedDocumentEditorComponent {
  @Input() structuredDocument: StructuredDocument | null = null;
  @Input() applicationDocument: StructuredDocument | null = null;
  @Input() pairedCvDocument: StructuredDocument | null = null;
  @Input() activeDocument: ActiveGeneratedDocument = 'single';
  @Input() currentContent = '';
  @Input() editorHtml = '';
  @Input() modelLabel = '';
  @Input() copied = false;
  @Input() downloadingPdf = false;
  @Input() savingApplication = false;
  @Input() applicationSaveMessage = '';

  @Output() currentContentChange = new EventEmitter<string>();
  @Output() bodyTextChange = new EventEmitter<void>();
  @Output() switchDocument = new EventEmitter<'application' | 'cv'>();
  @Output() copy = new EventEmitter<void>();
  @Output() download = new EventEmitter<void>();
  @Output() downloadCv = new EventEmitter<void>();
  @Output() save = new EventEmitter<ApplicationStatus>();

  updateBody(value: string): void {
    this.currentContentChange.emit(value);
    this.bodyTextChange.emit();
  }

  onEditorInput(event: Event): void {
    const el = event.target as HTMLDivElement;
    this.currentContentChange.emit(el.innerText);
  }
}
