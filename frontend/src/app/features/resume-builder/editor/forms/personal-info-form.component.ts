import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { DebouncedTextareaComponent } from '../../shared/debounced-textarea.component';

@Component({
  selector: 'app-personal-info-form',
  standalone: true,
  imports: [CommonModule, FormsModule, DebouncedTextareaComponent],
  template: `
    <div class="flex flex-col gap-3">
      <div class="grid grid-cols-2 gap-2">
        <div class="col-span-2">
          <label class="form-label">Full Name</label>
          <input class="form-input" type="text" [value]="pi.fullName" (input)="update('fullName', $any($event.target).value)" placeholder="Jane Doe" />
        </div>
        <div class="col-span-2">
          <label class="form-label">Job Title</label>
          <input class="form-input" type="text" [value]="pi.title" (input)="update('title', $any($event.target).value)" placeholder="Senior Software Engineer" />
        </div>
        <div>
          <label class="form-label">Email</label>
          <input class="form-input" type="email" [value]="pi.email" (input)="update('email', $any($event.target).value)" placeholder="jane@example.com" />
        </div>
        <div>
          <label class="form-label">Phone</label>
          <input class="form-input" type="tel" [value]="pi.phone" (input)="update('phone', $any($event.target).value)" placeholder="+1 234 567 8900" />
        </div>
        <div class="col-span-2">
          <label class="form-label">Location</label>
          <input class="form-input" type="text" [value]="pi.location" (input)="update('location', $any($event.target).value)" placeholder="New York, NY" />
        </div>
        <div class="col-span-2">
          <label class="form-label">Website</label>
          <input class="form-input" type="url" [value]="pi.website" (input)="update('website', $any($event.target).value)" placeholder="https://yoursite.com" />
        </div>
        <div>
          <label class="form-label">LinkedIn</label>
          <input class="form-input" type="text" [value]="pi.linkedin" (input)="update('linkedin', $any($event.target).value)" placeholder="linkedin.com/in/jane" />
        </div>
        <div>
          <label class="form-label">GitHub</label>
          <input class="form-input" type="text" [value]="pi.github" (input)="update('github', $any($event.target).value)" placeholder="github.com/jane" />
        </div>
        <div class="col-span-2">
          <label class="form-label">Summary</label>
          <app-debounced-textarea
            [value]="pi.summary"
            [rows]="4"
            placeholder="A brief professional summary..."
            (debouncedChange)="update('summary', $event)"
          />
        </div>
      </div>
    </div>
  `,
  styles: [`
    .form-label { @apply block text-xs font-medium text-gray-600 mb-1; }
    .form-input { @apply w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500; }
  `],
})
export class PersonalInfoFormComponent {
  private state = inject(ResumeStateService);
  get pi() { return this.state.personalInfo(); }
  update(field: string, value: string): void {
    this.state.updatePersonalInfo({ [field]: value } as any);
  }
}
