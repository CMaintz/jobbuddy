import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StructuredDocumentTemplatesApiService } from '../../../core/api/structured-document-templates.api';
import {
  DocumentTemplateOption,
  STRUCTURED_DOCUMENT_TEMPLATES,
  StructuredDocument,
  StructuredDocumentType
} from '../../../core/models/structured-document.model';
import { StructuredDocumentRendererComponent } from '../../../shared/components/structured-document-renderer/structured-document-renderer.component';

@Component({
  selector: 'app-pdf-templates',
  standalone: true,
  imports: [CommonModule, StructuredDocumentRendererComponent],
  templateUrl: './pdf-templates.component.html'
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
