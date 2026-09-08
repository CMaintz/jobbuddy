import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { DocumentIdentity } from '../../../core/models/structured-document.model';

@Component({
  selector: 'app-document-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './document-header.component.html'
})
export class DocumentHeaderComponent {
  @Input({ required: true }) identity!: DocumentIdentity;
  @Input() showProfileImage = false;

  get contactLines(): string[] {
    return [
      this.identity?.email,
      this.identity?.phone,
      this.identity?.location,
      this.identity?.linkedinUrl,
      this.identity?.githubUrl,
      this.identity?.websiteUrl
    ].filter((value): value is string => !!value);
  }

  initials(name?: string): string {
    return (name ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(part => part[0].toUpperCase())
      .join('');
  }
}
