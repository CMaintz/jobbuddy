import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AiApiService } from '../../../core/api/ai.api';
import { GeneratedDocument } from '../../../core/models/generated-document.model';

@Component({
  selector: 'app-documents-history',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Document History</h1>
        <span class="text-sm text-gray-500">{{ docs.length }} documents</span>
      </div>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (docs.length === 0) {
        <div class="text-center py-12 text-gray-400">No documents generated yet.</div>
      } @else {
        <div class="space-y-4">
          @for (doc of docs; track doc.id) {
            <div class="card space-y-3">
              <div class="flex items-center justify-between">
                <div>
                  <span class="text-xs font-medium uppercase tracking-wide text-blue-600 bg-blue-50 px-2 py-0.5 rounded">
                    {{ docTypeLabel(doc.documentType) }}
                  </span>
                  <span class="text-xs text-gray-400 ml-3">{{ doc.createdAt | date:'medium' }}</span>
                  @if (doc.modelUsed) {
                    <span class="text-xs text-gray-400 ml-2">via {{ doc.modelUsed }}</span>
                  }
                </div>
                <button (click)="toggle(doc.id)"
                        class="text-xs text-gray-500 hover:text-gray-700 border border-gray-200 px-2 py-1 rounded">
                  {{ expanded === doc.id ? 'Collapse' : 'View' }}
                </button>
              </div>

              @if (expanded === doc.id) {
                <div class="bg-gray-50 rounded-lg p-4 text-sm text-gray-700 whitespace-pre-wrap leading-relaxed">
                  {{ doc.content }}
                </div>
                <div class="flex gap-2">
                  <button (click)="copy(doc.content)"
                          class="btn-secondary text-xs">
                    {{ copiedId === doc.id ? 'Copied!' : 'Copy' }}
                  </button>
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `
})
export class DocumentsHistoryComponent implements OnInit {
  private api = inject(AiApiService);

  docs: GeneratedDocument[] = [];
  loading = true;
  expanded: string | null = null;
  copiedId: string | null = null;

  ngOnInit(): void {
    this.api.getDocuments().subscribe({
      next: docs => { this.docs = docs; this.loading = false; },
      error: () => this.loading = false
    });
  }

  toggle(id: string): void {
    this.expanded = this.expanded === id ? null : id;
  }

  copy(content: string): void {
    navigator.clipboard.writeText(content);
  }

  docTypeLabel(type: string): string {
    return type.replace(/_/g, ' ').toLowerCase()
      .replace(/\b\w/g, c => c.toUpperCase());
  }
}
