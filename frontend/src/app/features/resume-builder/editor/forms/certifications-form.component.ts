import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ResumeStateService } from '../../services/resume-state.service';
import { ResumeCertification } from '../../models/resume-builder.models';

@Component({
  selector: 'app-certifications-form',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './certifications-form.component.html',
})
export class CertificationsFormComponent {
  private state = inject(ResumeStateService);
  get certifications() { return this.state.certifications(); }
  add(): void { this.state.addCertification({ name: '', issuer: '', date: '' }); }
  remove(id: string): void { this.state.removeCertification(id); }
  update(id: string, field: string, value: unknown): void { this.state.updateCertification(id, { [field]: value } as Partial<ResumeCertification>); }
}
