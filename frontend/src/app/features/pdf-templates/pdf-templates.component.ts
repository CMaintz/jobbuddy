import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PdfTemplatesApiService } from '../../core/api/pdf-templates.api';
import { PdfTemplate } from '../../core/models/pdf-template.model';

@Component({
  selector: 'app-pdf-templates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="space-y-6 max-w-4xl mx-auto">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">PDF Templates</h1>
        @if (!showForm) {
          <button (click)="openCreateForm()" class="btn-primary">Create Template</button>
        }
      </div>

      @if (errorMessage) {
        <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ errorMessage }}</div>
      }
      @if (successMessage) {
        <div class="bg-green-50 text-green-700 rounded-md p-3 text-sm">{{ successMessage }}</div>
      }

      <!-- Create / Edit Form -->
      @if (showForm) {
        <div class="card space-y-5">
          <h2 class="text-base font-semibold text-gray-900">
            {{ editingTemplate ? 'Edit Template' : 'Create Template' }}
          </h2>

          <div>
            <label class="label">Name <span class="text-red-500">*</span></label>
            <input type="text" [(ngModel)]="formData.name" class="input" placeholder="e.g. Modern Cover Letter" />
          </div>

          <div>
            <label class="label">Description</label>
            <input type="text" [(ngModel)]="formData.description" class="input" placeholder="Optional description" />
          </div>

          <div>
            <label class="label">Document Type</label>
            <select [(ngModel)]="formData.documentType" class="input">
              <option value="COVER_LETTER">Cover Letter</option>
              <option value="APPLICATION_TEXT">Application Text</option>
              <option value="CV">CV</option>
            </select>
          </div>

          <div class="bg-blue-50 border border-blue-200 rounded-md p-3">
            <p class="text-xs font-semibold text-blue-700 mb-1">Available Placeholders</p>
            <p class="text-xs text-blue-600 font-mono">
              &#123;&#123;NAME&#125;&#125;&nbsp;&nbsp;
              &#123;&#123;EMAIL&#125;&#125;&nbsp;&nbsp;
              &#123;&#123;PHONE&#125;&#125;&nbsp;&nbsp;
              &#123;&#123;DATE&#125;&#125;&nbsp;&nbsp;
              &#123;&#123;CONTENT&#125;&#125;
            </p>
          </div>

          <div>
            <label class="label">HTML Template</label>
            <textarea
              [(ngModel)]="formData.htmlTemplate"
              class="input font-mono text-xs"
              rows="15"
              placeholder="<html><body>...</body></html>"
            ></textarea>
          </div>

          <div>
            <label class="label">CSS Styles</label>
            <textarea
              [(ngModel)]="formData.cssStyles"
              class="input font-mono text-xs"
              rows="8"
              placeholder="body { font-family: Arial, sans-serif; } ..."
            ></textarea>
          </div>

          <div class="flex gap-3 flex-wrap">
            <button (click)="save()" [disabled]="saving || !formData.name" class="btn-primary">
              {{ saving ? 'Saving...' : 'Save' }}
            </button>
            <button (click)="preview()" type="button" class="btn-secondary">Preview</button>
            <button (click)="cancelForm()" type="button" class="btn-secondary">Cancel</button>
          </div>
        </div>
      }

      <!-- Template List -->
      @if (!showForm) {
        @if (loading) {
          <div class="card text-center py-10 text-gray-400">Loading templates...</div>
        } @else if (templates.length === 0) {
          <div class="card text-center py-10">
            <p class="text-gray-500">No templates yet.</p>
            <button (click)="openCreateForm()" class="btn-primary mt-4">Create your first template</button>
          </div>
        } @else {
          <div class="grid gap-4 sm:grid-cols-2">
            @for (tpl of templates; track tpl.id) {
              <div class="card space-y-3">
                <div class="flex items-start justify-between gap-2">
                  <div class="flex-1 min-w-0">
                    <h3 class="font-semibold text-gray-900 truncate">{{ tpl.name }}</h3>
                    @if (tpl.description) {
                      <p class="text-sm text-gray-500 mt-0.5">{{ tpl.description }}</p>
                    }
                  </div>
                  <div class="flex items-center gap-2 shrink-0">
                    <span class="text-xs px-2 py-0.5 rounded-full font-medium"
                          [class]="docTypeBadgeClass(tpl.documentType)">
                      {{ docTypeLabel(tpl.documentType) }}
                    </span>
                    @if (tpl.isSystem) {
                      <span class="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-full font-medium">System</span>
                    }
                  </div>
                </div>

                @if (!tpl.isSystem) {
                  <div class="flex gap-2 pt-1">
                    <button (click)="openEditForm(tpl)" class="btn-secondary text-xs">Edit</button>
                    <button (click)="deleteTemplate(tpl)"
                            [disabled]="deleting === tpl.id"
                            class="text-xs px-3 py-1.5 rounded-md border border-red-200 text-red-600 hover:bg-red-50 transition-colors disabled:opacity-50">
                      {{ deleting === tpl.id ? 'Deleting...' : 'Delete' }}
                    </button>
                  </div>
                }
              </div>
            }
          </div>
        }
      }
    </div>
  `
})
export class PdfTemplatesComponent implements OnInit {
  private api = inject(PdfTemplatesApiService);

  templates: PdfTemplate[] = [];
  loading = true;
  saving = false;
  deleting: string | null = null;

  showForm = false;
  editingTemplate: PdfTemplate | null = null;

  errorMessage = '';
  successMessage = '';

  formData: Partial<PdfTemplate> = this.emptyForm();

  ngOnInit(): void {
    this.loadTemplates();
  }

  private emptyForm(): Partial<PdfTemplate> {
    return {
      name: '',
      description: '',
      documentType: 'COVER_LETTER',
      htmlTemplate: '',
      cssStyles: ''
    };
  }

  loadTemplates(): void {
    this.loading = true;
    this.api.getAll().subscribe({
      next: tpls => { this.templates = tpls; this.loading = false; },
      error: () => { this.loading = false; this.errorMessage = 'Failed to load templates.'; }
    });
  }

  openCreateForm(): void {
    this.editingTemplate = null;
    this.formData = this.emptyForm();
    this.clearMessages();
    this.showForm = true;
  }

  openEditForm(tpl: PdfTemplate): void {
    this.editingTemplate = tpl;
    this.formData = {
      name: tpl.name,
      description: tpl.description ?? '',
      documentType: tpl.documentType,
      htmlTemplate: tpl.htmlTemplate,
      cssStyles: tpl.cssStyles ?? ''
    };
    this.clearMessages();
    this.showForm = true;
  }

  cancelForm(): void {
    this.showForm = false;
    this.editingTemplate = null;
    this.formData = this.emptyForm();
    this.clearMessages();
  }

  save(): void {
    if (!this.formData.name) return;
    this.saving = true;
    this.clearMessages();

    const payload = { ...this.formData };
    const op = this.editingTemplate
      ? this.api.update(this.editingTemplate.id, payload)
      : this.api.create(payload);

    op.subscribe({
      next: saved => {
        if (this.editingTemplate) {
          const idx = this.templates.findIndex(t => t.id === saved.id);
          if (idx >= 0) this.templates[idx] = saved;
        } else {
          this.templates.unshift(saved);
        }
        this.saving = false;
        this.showForm = false;
        this.editingTemplate = null;
        this.formData = this.emptyForm();
        this.successMessage = 'Template saved successfully!';
      },
      error: () => {
        this.saving = false;
        this.errorMessage = 'Failed to save template.';
      }
    });
  }

  deleteTemplate(tpl: PdfTemplate): void {
    if (!confirm(`Delete template "${tpl.name}"?`)) return;
    this.deleting = tpl.id;
    this.clearMessages();
    this.api.delete(tpl.id).subscribe({
      next: () => {
        this.templates = this.templates.filter(t => t.id !== tpl.id);
        this.deleting = null;
        this.successMessage = 'Template deleted.';
      },
      error: () => {
        this.deleting = null;
        this.errorMessage = 'Failed to delete template.';
      }
    });
  }

  preview(): void {
    const html = this.formData.htmlTemplate ?? '';
    const css = this.formData.cssStyles ?? '';
    const rendered = html
      .replace(/\{\{NAME\}\}/g, 'Jane Doe')
      .replace(/\{\{EMAIL\}\}/g, 'jane.doe@example.com')
      .replace(/\{\{PHONE\}\}/g, '+1 555-0100')
      .replace(/\{\{DATE\}\}/g, new Date().toLocaleDateString())
      .replace(/\{\{CONTENT\}\}/g, 'This is a sample content paragraph that demonstrates how your document will look when rendered with real data.');

    const doc = `<!DOCTYPE html><html><head><meta charset="utf-8"><style>${css}</style></head><body>${rendered}</body></html>`;
    const win = window.open('', '_blank');
    if (win) {
      win.document.write(doc);
      win.document.close();
    }
  }

  docTypeLabel(type: string): string {
    const map: Record<string, string> = {
      COVER_LETTER: 'Cover Letter',
      APPLICATION_TEXT: 'Application',
      CV: 'CV'
    };
    return map[type] ?? type;
  }

  docTypeBadgeClass(type: string): string {
    const map: Record<string, string> = {
      COVER_LETTER: 'bg-purple-100 text-purple-700',
      APPLICATION_TEXT: 'bg-green-100 text-green-700',
      CV: 'bg-blue-100 text-blue-700'
    };
    return map[type] ?? 'bg-gray-100 text-gray-600';
  }

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }
}
