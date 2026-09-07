import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';

@Component({
  selector: 'app-certifications-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="flex flex-col gap-4">
      @for (cert of certifications; track cert.id) {
        <div class="border border-gray-200 rounded-lg p-3 flex flex-col gap-2">
          <div class="flex justify-between items-center">
            <span class="text-xs font-medium text-gray-700">{{ cert.name || 'New Certification' }}</span>
            <button class="text-red-400 hover:text-red-600 text-xs" (click)="remove(cert.id)">Remove</button>
          </div>
          <label class="form-label">Name</label>
          <input class="form-input" [value]="cert.name" (input)="update(cert.id, 'name', $any($event.target).value)" placeholder="AWS Certified Developer" />
          <label class="form-label">Issuer</label>
          <input class="form-input" [value]="cert.issuer" (input)="update(cert.id, 'issuer', $any($event.target).value)" placeholder="Amazon Web Services" />
          <label class="form-label">Date</label>
          <input class="form-input" [value]="cert.date" (input)="update(cert.id, 'date', $any($event.target).value)" placeholder="2024-06" />
          <label class="form-label">Credential URL</label>
          <input class="form-input" [value]="cert.link ?? ''" (input)="update(cert.id, 'link', $any($event.target).value)" placeholder="https://credly.com/..." />
        </div>
      }
      <button class="add-btn" (click)="add()">+ Add Certification</button>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
    .add-btn { @apply w-full py-2 text-sm text-blue-600 border border-dashed border-blue-300 rounded-lg hover:bg-blue-50 transition-colors; }
  `],
})
export class CertificationsFormComponent {
  private state = inject(ResumeStateService);
  get certifications() { return this.state.certifications(); }
  add(): void { this.state.addCertification({ name: '', issuer: '', date: '' }); }
  remove(id: string): void { this.state.removeCertification(id); }
  update(id: string, field: string, value: any): void { this.state.updateCertification(id, { [field]: value } as any); }
}
