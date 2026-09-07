import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredDocumentTemplatesApiService } from '../../core/api/structured-document-templates.api';
import {
  DocumentTemplateOption,
  STRUCTURED_DOCUMENT_TEMPLATES,
  StructuredDocument,
  StructuredDocumentType
} from '../../core/models/structured-document.model';
import { StructuredDocumentRendererComponent } from '../../shared/components/structured-document-renderer/structured-document-renderer.component';

@Component({
  selector: 'app-pdf-templates',
  standalone: true,
  imports: [CommonModule, StructuredDocumentRendererComponent],
  template: `
    <div class="space-y-6 max-w-6xl mx-auto">
      <div class="flex items-center justify-between">
        <div>
          <h1 class="text-3xl font-bold text-gray-900">Document Templates</h1>
          <p class="text-sm text-gray-500 mt-1">Structured CV and application templates, grouped into matching visual families.</p>
        </div>
      </div>

      @if (errorMessage) {
        <div class="bg-red-50 text-red-700 rounded-md p-3 text-sm">{{ errorMessage }}</div>
      }

      @if (loading) {
        <div class="card text-center py-10 text-gray-400">Loading templates...</div>
      } @else {
        <div class="grid gap-6 lg:grid-cols-[330px_1fr]">
          <div class="space-y-4">
            @for (family of templateFamilies; track family.familyId) {
              <div class="card space-y-3">
                <div>
                  <h2 class="text-base font-semibold text-gray-900">{{ family.familyName }}</h2>
                  <p class="text-xs text-gray-500 mt-1">{{ family.templates.length }} structured templates</p>
                </div>
                <div class="space-y-2">
                  @for (tpl of family.templates; track tpl.id) {
                    <button
                      type="button"
                      (click)="selectTemplate(tpl)"
                      class="w-full text-left rounded-md border px-3 py-2 transition-colors"
                      [class.border-blue-500]="selectedTemplate?.id === tpl.id"
                      [class.bg-blue-50]="selectedTemplate?.id === tpl.id"
                      [class.border-gray-200]="selectedTemplate?.id !== tpl.id">
                      <div class="flex items-start justify-between gap-2">
                        <span class="text-sm font-medium text-gray-900">{{ tpl.label }}</span>
                        <span class="text-[11px] px-2 py-0.5 rounded-full" [class]="tpl.atsSafe ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-600'">
                          {{ tpl.exportMode }}
                        </span>
                      </div>
                      <p class="text-xs text-gray-500 mt-1">{{ docTypeList(tpl.documentTypes) }}</p>
                    </button>
                  }
                </div>
              </div>
            }
          </div>

          <div class="space-y-4">
            @if (selectedTemplate) {
              <div class="card">
                <div class="flex items-start justify-between gap-4">
                  <div>
                    <h2 class="text-xl font-semibold text-gray-900">{{ selectedTemplate.label }}</h2>
                    <p class="text-sm text-gray-500 mt-1">{{ selectedTemplate.description }}</p>
                  </div>
                  <div class="flex gap-2 shrink-0">
                    @if (selectedTemplate.supportsProfileImage) {
                      <span class="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded-full">Image-ready</span>
                    }
                    @if (selectedTemplate.atsSafe) {
                      <span class="text-xs bg-green-100 text-green-700 px-2 py-1 rounded-full">ATS-safe</span>
                    }
                  </div>
                </div>
                <dl class="grid gap-3 sm:grid-cols-4 mt-5 text-sm">
                  <div>
                    <dt class="text-gray-400">Family</dt>
                    <dd class="font-medium text-gray-800">{{ selectedTemplate.familyName }}</dd>
                  </div>
                  <div>
                    <dt class="text-gray-400">Layout</dt>
                    <dd class="font-medium text-gray-800">{{ selectedTemplate.layoutType }}</dd>
                  </div>
                  <div>
                    <dt class="text-gray-400">Font</dt>
                    <dd class="font-medium text-gray-800">{{ selectedTemplate.defaultTheme?.fontFamily }}</dd>
                  </div>
                  <div>
                    <dt class="text-gray-400">Font Size</dt>
                    <dd class="font-medium text-gray-800">{{ selectedTemplate.defaultTheme?.fontScale }}</dd>
                  </div>
                </dl>
              </div>

              <app-structured-document-renderer [document]="previewDocument"></app-structured-document-renderer>
            }
          </div>
        </div>
      }
    </div>
  `
})
export class PdfTemplatesComponent implements OnInit {
  private api = inject(StructuredDocumentTemplatesApiService);

  templates: DocumentTemplateOption[] = [];
  selectedTemplate: DocumentTemplateOption | null = null;
  loading = true;

  errorMessage = '';

  ngOnInit(): void {
    this.loadTemplates();
  }

  loadTemplates(): void {
    this.loading = true;
    this.api.getActive().subscribe({
      next: tpls => {
        this.templates = tpls.length > 0 ? tpls : STRUCTURED_DOCUMENT_TEMPLATES;
        this.selectedTemplate = this.templates[0] ?? null;
        this.loading = false;
      },
      error: () => {
        this.templates = STRUCTURED_DOCUMENT_TEMPLATES;
        this.selectedTemplate = this.templates[0] ?? null;
        this.loading = false;
        this.errorMessage = 'Loaded local template defaults because the backend catalogue was unavailable.';
      }
    });
  }

  get templateFamilies(): { familyId: string; familyName: string; templates: DocumentTemplateOption[] }[] {
    const families = new Map<string, { familyId: string; familyName: string; templates: DocumentTemplateOption[] }>();
    for (const template of this.templates) {
      const familyId = template.familyId ?? template.id;
      if (!families.has(familyId)) {
        families.set(familyId, {
          familyId,
          familyName: template.familyName ?? template.label,
          templates: []
        });
      }
      families.get(familyId)?.templates.push(template);
    }
    return [...families.values()];
  }

  selectTemplate(template: DocumentTemplateOption): void {
    this.selectedTemplate = template;
  }

  docTypeList(types: StructuredDocumentType[]): string {
    return types.map(type => type === 'APPLICATION_TEXT' ? 'Application' : type === 'COVER_LETTER' ? 'Cover Letter' : type).join(', ');
  }

  get previewDocument(): StructuredDocument {
    const template = this.selectedTemplate ?? STRUCTURED_DOCUMENT_TEMPLATES[0];
    const isCv = template.documentTypes.includes('CV');
    return {
      documentType: isCv ? 'CV' : 'COVER_LETTER',
      exportMode: template.exportMode ?? 'DESIGNED',
      templateId: template.id,
      identity: {
        name: 'Jane Doe',
        headline: 'Senior Software Engineer',
        email: 'jane.doe@example.com',
        phone: '+45 12 34 56 78',
        location: 'Copenhagen, Denmark',
        linkedinUrl: 'linkedin.com/in/janedoe',
        githubUrl: 'github.com/janedoe'
      },
      options: {
        showProfileImage: !!template.supportsProfileImage,
        theme: template.defaultTheme
      },
      sections: isCv ? [
        { id: 'profile', type: 'profile', heading: 'Profile', body: 'Backend-focused engineer with strong product sense, pragmatic architecture habits, and experience turning messy business needs into maintainable systems.', items: [] },
        { id: 'skills', type: 'skills', heading: 'Skills', items: [
          { title: 'Java' }, { title: 'Spring Boot' }, { title: 'Angular' }, { title: 'PostgreSQL' }, { title: 'Docker' }
        ] },
        { id: 'experience', type: 'experience', heading: 'Experience', items: [
          {
            title: 'Senior Software Engineer',
            subtitle: 'Acme Systems',
            dateRange: '2021 - Present',
            description: 'Led backend and frontend delivery for customer-facing workflow products.',
            bullets: ['Reduced manual processing time by 35% through automated document generation.', 'Designed API contracts used across three product teams.'],
            technologies: ['Java', 'Spring Boot', 'Angular', 'PostgreSQL']
          }
        ] }
      ] : [],
      bodyContent: isCv ? undefined : 'Dear Hiring Team,\n\nI am excited to apply for this role because it combines product-minded engineering with practical delivery. My recent work has focused on building maintainable systems, improving document workflows, and translating complex requirements into software that teams can rely on.\n\nI would welcome the chance to discuss how that experience can support your team.'
    };
  }
}
