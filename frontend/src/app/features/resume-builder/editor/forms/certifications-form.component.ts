import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';

@Component({
  selector: 'app-certifications-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './certifications-form.component.html',
  styleUrls: ['./certifications-form.component.css'],
})
export class CertificationsFormComponent {
  private state = inject(ResumeStateService);
  get certifications() { return this.state.certifications(); }
  add(): void { this.state.addCertification({ name: '', issuer: '', date: '' }); }
  remove(id: string): void { this.state.removeCertification(id); }
  update(id: string, field: string, value: any): void { this.state.updateCertification(id, { [field]: value } as any); }
}
