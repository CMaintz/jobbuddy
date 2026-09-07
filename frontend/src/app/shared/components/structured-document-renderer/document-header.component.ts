import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { DocumentIdentity } from '../../../core/models/structured-document.model';

@Component({
  selector: 'app-document-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <header class="topbar">
      @if (showProfileImage) {
        <div class="avatar">
          @if (identity.profileImageUrl) {
            <img [src]="identity.profileImageUrl" alt="" />
          } @else {
            {{ initials(identity.name) }}
          }
        </div>
      }

      <div class="person">
        <h1>{{ identity.name }}</h1>
        @if (identity.headline) {
          <p>{{ identity.headline }}</p>
        }
      </div>

      <div class="contact">
        @for (contact of contactLines; track contact) {
          <span>{{ contact }}</span>
        }
      </div>
    </header>
  `
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
