import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ResumeStateService } from '../../services/resume-state.service';
import { SkillChipListComponent } from '../../shared/skill-chip-list.component';
import { CONTACT_ICONS } from '../../data/social-platforms';

@Component({
  selector: 'app-minimal-layout',
  standalone: true,
  imports: [CommonModule, SkillChipListComponent],
  template: `
    <div class="p-12 flex flex-col gap-7 bg-white text-gray-800">
      <div>
        <h1 class="text-4xl font-light tracking-tight">{{ pi.fullName }}</h1>
        <p class="text-base text-gray-400 mt-1 font-light">{{ pi.title }}</p>
        <div class="flex flex-wrap gap-4 mt-3 text-xs text-gray-400">
          @if (pi.email) { <span class="flex items-center gap-1"><svg class="w-3 h-3 flex-shrink-0 fill-gray-400" viewBox="0 0 24 24"><path [attr.d]="contactIcons.email"/></svg>{{ pi.email }}</span> }
          @if (pi.phone) { <span class="flex items-center gap-1"><svg class="w-3 h-3 flex-shrink-0 fill-gray-400" viewBox="0 0 24 24"><path [attr.d]="contactIcons.phone"/></svg>{{ pi.phone }}</span> }
          @if (pi.location) { <span class="flex items-center gap-1"><svg class="w-3 h-3 flex-shrink-0 fill-gray-400" viewBox="0 0 24 24"><path [attr.d]="contactIcons.location"/></svg>{{ pi.location }}</span> }
        </div>
      </div>
      @if (pi.summary) {
        <p class="text-sm text-gray-600 leading-relaxed font-light border-l-2 pl-4" [style.border-color]="themeColor">{{ pi.summary }}</p>
      }
      @if (experience.length > 0) {
        <section>
          <h2 class="text-xs uppercase tracking-[0.15em] text-gray-400 mb-4">Experience</h2>
          <div class="flex flex-col gap-4">
            @for (exp of experience; track exp.id) {
              <div class="flex gap-4">
                <div class="text-xs text-gray-300 whitespace-nowrap pt-0.5 w-24 text-right">{{ exp.startDate }}</div>
                <div class="flex-1">
                  <p class="font-medium text-sm">{{ exp.title }}</p>
                  <p class="text-xs text-gray-400">{{ exp.company }}</p>
                  @if (exp.description) { <p class="text-xs text-gray-500 mt-1">{{ exp.description }}</p> }
                  <app-skill-chip-list [skills]="exp.skills ?? []" />
                </div>
              </div>
            }
          </div>
        </section>
      }
      @if (education.length > 0) {
        <section>
          <h2 class="text-xs uppercase tracking-[0.15em] text-gray-400 mb-4">Education</h2>
          @for (edu of education; track edu.id) {
            <div class="flex gap-4 mb-2">
              <div class="text-xs text-gray-300 whitespace-nowrap w-24 text-right pt-0.5">{{ edu.startDate }}</div>
              <div><p class="font-medium text-sm">{{ edu.degree }}</p><p class="text-xs text-gray-400">{{ edu.school }}</p></div>
            </div>
          }
        </section>
      }
      @if (skills.length > 0) {
        <section>
          <h2 class="text-xs uppercase tracking-[0.15em] text-gray-400 mb-3">Skills</h2>
          <div class="flex flex-wrap gap-2">
            @for (s of skills; track s.id) {
              <span class="text-xs text-gray-600 bg-gray-50 border border-gray-200 px-2.5 py-1 rounded">{{ s.name }}</span>
            }
          </div>
        </section>
      }
    </div>
  `,
})
export class MinimalLayoutComponent {
  private svc = inject(ResumeStateService);
  get pi() { return this.svc.personalInfo(); }
  get experience() { return this.svc.experience(); }
  get education() { return this.svc.education(); }
  get skills() { return this.svc.skills(); }
  get themeColor() { return this.svc.settings().themeColor; }
  readonly contactIcons = CONTACT_ICONS;
}
