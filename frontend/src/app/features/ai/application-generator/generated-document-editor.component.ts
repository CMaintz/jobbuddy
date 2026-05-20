import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApplicationStatus } from '../../../core/models/application.model';
import { StructuredDocument } from '../../../core/models/structured-document.model';
import { StructuredDocumentRendererComponent } from '../../../shared/components/structured-document-renderer/structured-document-renderer.component';
import { AtsReportPanelComponent } from '../../cv/components/ats-report-panel.component';
import { ActiveGeneratedDocument } from './application-generator.types';

@Component({
  selector: 'app-generated-document-editor',
  standalone: true,
  imports: [CommonModule, FormsModule, StructuredDocumentRendererComponent, AtsReportPanelComponent],
  styles: [`
    .editor {
      min-height: 300px;
      outline: none;
      line-height: 1.75;
      white-space: pre-wrap;
      word-break: break-word;
    }
    .editor:empty:before {
      content: attr(data-placeholder);
      color: #9ca3af;
      pointer-events: none;
    }
    @media print {
      body * { visibility: hidden; }
      .print-area, .print-area * { visibility: visible; }
      .print-area {
        position: fixed; top: 0; left: 0;
        width: 210mm; padding: 20mm;
        font-family: Georgia, serif;
        font-size: 11pt;
        line-height: 1.6;
        color: #111;
      }
    }
  `],
  template: `
    <div class="card space-y-3 print-area">
      <div class="flex items-center justify-between">
        <h2 class="text-base font-semibold text-gray-900">Document</h2>
        <div class="flex gap-2">
          <button (click)="copy.emit()" class="btn-secondary text-xs">
            {{ copied ? 'Copied!' : 'Copy' }}
          </button>
          <button (click)="download.emit()" [disabled]="downloadingPdf" class="btn-secondary text-xs">
            {{ downloadingPdf ? 'Generating PDF...' : 'Download PDF' }}
          </button>
          @if (pairedCvDocument) {
            <button (click)="downloadCv.emit()" [disabled]="downloadingPdf" class="btn-secondary text-xs">
              Download CV PDF
            </button>
          }
          <button (click)="save.emit('PREPARING')" [disabled]="savingApplication" class="btn-secondary text-xs">
            {{ savingApplication ? 'Saving...' : 'Save Draft' }}
          </button>
          <button (click)="save.emit('APPLIED')" [disabled]="savingApplication" class="btn-primary text-xs">
            Mark Applied
          </button>
        </div>
      </div>

      @if (applicationSaveMessage) {
        <div class="rounded-md bg-green-50 px-3 py-2 text-xs text-green-700">{{ applicationSaveMessage }}</div>
      }

      @if (applicationDocument && pairedCvDocument) {
        <div class="rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-700 space-y-2">
          <div>Application and angled CV are separate documents. Switch between them to edit, customize, download, or save.</div>
          <div class="flex gap-2">
            <button type="button"
                    [class]="activeDocument === 'application' ? 'btn-primary text-xs' : 'btn-secondary text-xs'"
                    (click)="switchDocument.emit('application')">
              Application
            </button>
            <button type="button"
                    [class]="activeDocument === 'cv' ? 'btn-primary text-xs' : 'btn-secondary text-xs'"
                    (click)="switchDocument.emit('cv')">
              Angled CV
            </button>
          </div>
        </div>
      }

      @if (structuredDocument?.atsReport; as atsReport) {
        <app-ats-report-panel [report]="atsReport"></app-ats-report-panel>
      }

      @if (structuredDocument) {
        <app-structured-document-renderer [document]="structuredDocument"></app-structured-document-renderer>
      } @else {
        <div class="editor border border-gray-200 rounded-lg p-4 text-sm text-gray-800 bg-white"
             contenteditable="true"
             data-placeholder="Generated content will appear here..."
             (input)="onEditorInput($event)"
             [innerHTML]="editorHtml">
        </div>
      }

      @if (structuredDocument && structuredDocument.documentType !== 'CV') {
        <div>
          <label class="label">Body Text</label>
          <textarea class="input text-sm" rows="8"
                    [ngModel]="currentContent"
                    [ngModelOptions]="{standalone: true}"
                    (ngModelChange)="updateBody($event)"></textarea>
        </div>
      }

      @if (modelLabel) {
        <div class="text-xs text-gray-400 text-right">
          {{ modelLabel }}
        </div>
      }
    </div>
  `
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
