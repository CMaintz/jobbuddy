import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule } from 'lucide-angular';
import { ResumeStateService } from '../../services/resume-state.service';
import { SkillChipListComponent } from '../../shared/skill-chip-list.component';
import { getSocialIcon, CONTACT_ICONS } from '../../data/social-platforms';
import { getStrengthIcon } from '../../data/strength-icons';

@Component({
  selector: 'app-classic-layout',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, SkillChipListComponent],
  template: `
    <div class="flex h-full" [style.color]="'#333'">
      <!-- Left main column (65%) -->
      <div class="flex-[65] p-8 flex flex-col gap-5">
        <!-- Header -->
        <div class="border-b-2 pb-4" [style.border-color]="themeColor">
          <h1 class="text-3xl font-bold" [style.color]="themeColor">{{ pi.fullName }}</h1>
          <p class="text-lg text-gray-500 mt-0.5">{{ pi.title }}</p>
          <div class="flex flex-wrap gap-3 mt-2 text-xs text-gray-500">
            @if (pi.email) { <span class="flex items-center gap-1"><svg class="w-3 h-3 flex-shrink-0" [style.fill]="themeColor" viewBox="0 0 24 24"><path [attr.d]="contactIcons.email"/></svg>{{ pi.email }}</span> }
            @if (pi.phone) { <span class="flex items-center gap-1"><svg class="w-3 h-3 flex-shrink-0" [style.fill]="themeColor" viewBox="0 0 24 24"><path [attr.d]="contactIcons.phone"/></svg>{{ pi.phone }}</span> }
            @if (pi.location) { <span class="flex items-center gap-1"><svg class="w-3 h-3 flex-shrink-0" [style.fill]="themeColor" viewBox="0 0 24 24"><path [attr.d]="contactIcons.location"/></svg>{{ pi.location }}</span> }
          </div>
        </div>

        <!-- Summary -->
        @if (pi.summary) {
          <section>
            <h2 class="section-heading" [style.color]="themeColor">Summary</h2>
            <p class="text-sm leading-relaxed text-gray-700">{{ pi.summary }}</p>
          </section>
        }

        <!-- Experience -->
        @if (experience.length > 0) {
          <section>
            <h2 class="section-heading" [style.color]="themeColor">Experience</h2>
            <div class="flex flex-col gap-4">
              @for (exp of experience; track exp.id) {
                <div>
                  <div class="flex justify-between items-start">
                    <div>
                      <h3 class="font-semibold text-sm">{{ exp.title }}</h3>
                      <p class="text-xs text-gray-500">{{ exp.company }}@if (exp.location) { · {{ exp.location }} }</p>
                    </div>
                    <span class="text-xs text-gray-400 whitespace-nowrap ml-2">
                      {{ exp.startDate }} – {{ exp.current ? 'Present' : exp.endDate }}
                    </span>
                  </div>
                  @if (exp.description) {
                    <p class="text-xs text-gray-600 mt-1 leading-relaxed">{{ exp.description }}</p>
                  }
                  <app-skill-chip-list [skills]="exp.skills ?? []" />
                </div>
              }
            </div>
          </section>
        }

        <!-- Education -->
        @if (education.length > 0) {
          <section>
            <h2 class="section-heading" [style.color]="themeColor">Education</h2>
            <div class="flex flex-col gap-3">
              @for (edu of education; track edu.id) {
                <div>
                  <div class="flex justify-between items-start">
                    <div>
                      <h3 class="font-semibold text-sm">{{ edu.degree }}</h3>
                      <p class="text-xs text-gray-500">{{ edu.school }}@if (edu.location) { · {{ edu.location }} }</p>
                    </div>
                    <span class="text-xs text-gray-400 whitespace-nowrap ml-2">
                      {{ edu.startDate }} – {{ edu.current ? 'Present' : edu.endDate }}
                    </span>
                  </div>
                  <app-skill-chip-list [skills]="edu.skills ?? []" />
                </div>
              }
            </div>
          </section>
        }

        <!-- Projects -->
        @if (projects.length > 0) {
          <section>
            <h2 class="section-heading" [style.color]="themeColor">Projects</h2>
            <div class="flex flex-col gap-3">
              @for (proj of projects; track proj.id) {
                <div>
                  <div class="flex justify-between items-start">
                    <h3 class="font-semibold text-sm">{{ proj.name }}</h3>
                    @if (proj.date) { <span class="text-xs text-gray-400">{{ proj.date }}</span> }
                  </div>
                  @if (proj.description) {
                    <p class="text-xs text-gray-600 mt-1 leading-relaxed">{{ proj.description }}</p>
                  }
                  <app-skill-chip-list [skills]="proj.skills ?? []" />
                </div>
              }
            </div>
          </section>
        }
      </div>

      <!-- Right sidebar (35%) -->
      <div class="flex-[35] p-6 flex flex-col gap-5" [style.background-color]="themeColor + '15'">
        <!-- Photo -->
        @if (pi.photoUrl) {
          <div class="flex justify-center">
            <img
              [src]="pi.photoUrl"
              alt="Profile photo"
              class="w-24 h-24 object-cover"
              [class.rounded-full]="photoStyle === 'circle'"
              [class.rounded-xl]="photoStyle === 'rounded'"
            />
          </div>
        }

        <!-- Skills -->
        @if (skills.length > 0) {
          <section>
            <h2 class="sidebar-heading" [style.color]="themeColor">Skills</h2>
            <div class="flex flex-col gap-1.5">
              @for (skill of skills; track skill.id) {
                <div class="flex items-center justify-between">
                  <span class="text-xs text-gray-700">{{ skill.name }}</span>
                  @if (showSkillLevel && skill.level) {
                    <div class="flex gap-0.5">
                      @for (dot of [1,2,3,4,5]; track dot) {
                        <div
                          class="w-2 h-2 rounded-full"
                          [style.background-color]="dot <= (skill.level ?? 0) ? themeColor : '#d1d5db'"
                        ></div>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          </section>
        }

        <!-- Languages -->
        @if (languages.length > 0) {
          <section>
            <h2 class="sidebar-heading" [style.color]="themeColor">Languages</h2>
            <div class="flex flex-col gap-1">
              @for (lang of languages; track lang.id) {
                <div class="flex justify-between items-center">
                  <span class="text-xs text-gray-700">{{ lang.name }}</span>
                  <span class="text-xs text-gray-400">{{ lang.proficiency }}</span>
                </div>
              }
            </div>
          </section>
        }

        <!-- Certifications -->
        @if (certifications.length > 0) {
          <section>
            <h2 class="sidebar-heading" [style.color]="themeColor">Certifications</h2>
            <div class="flex flex-col gap-2">
              @for (cert of certifications; track cert.id) {
                <div>
                  <p class="text-xs font-medium text-gray-700">{{ cert.name }}</p>
                  @if (cert.issuer) { <p class="text-xs text-gray-400">{{ cert.issuer }}</p> }
                  @if (cert.date) { <p class="text-xs text-gray-400">{{ cert.date }}</p> }
                </div>
              }
            </div>
          </section>
        }

        <!-- Strengths -->
        @if (strengths.length > 0) {
          <section>
            <h2 class="sidebar-heading" [style.color]="themeColor">Strengths</h2>
            <div class="flex flex-col gap-2">
              @for (s of strengths; track s.id) {
                <div>
                  <p class="text-xs font-semibold text-gray-700 flex items-center gap-1">
                    <lucide-icon [img]="getStrengthIcon(s.iconKey)" [size]="11" [strokeWidth]="1.5" [color]="themeColor" class="flex-shrink-0"></lucide-icon>
                    {{ s.title }}
                  </p>
                  @if (s.description) { <p class="text-xs text-gray-400 leading-relaxed">{{ s.description }}</p> }
                </div>
              }
            </div>
          </section>
        }

        <!-- Socials -->
        @if (socials.length > 0) {
          <section>
            <h2 class="sidebar-heading" [style.color]="themeColor">Links</h2>
            <div class="flex flex-col gap-1.5">
              @for (s of socials; track s.id) {
                <div class="flex items-center gap-1.5">
                  <lucide-icon [img]="getSocialIcon(s.iconKey)" [size]="11" [strokeWidth]="1.5" [color]="themeColor" class="flex-shrink-0"></lucide-icon>
                  <span class="text-xs text-gray-600 truncate">{{ s.username || s.url }}</span>
                </div>
              }
            </div>
          </section>
        }
      </div>
    </div>
  `,
  styles: [`
    .section-heading {
      font-size: 0.8rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 0.5rem;
      border-bottom: 1px solid currentColor;
      padding-bottom: 0.2rem;
    }
    .sidebar-heading {
      font-size: 0.7rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.08em;
      margin-bottom: 0.4rem;
    }
  `],
})
export class ClassicLayoutComponent {
  private stateService = inject(ResumeStateService);

  get pi() { return this.stateService.personalInfo(); }
  get experience() { return this.stateService.experience(); }
  get education() { return this.stateService.education(); }
  get projects() { return this.stateService.projects(); }
  get skills() { return this.stateService.skills(); }
  get languages() { return this.stateService.languages(); }
  get certifications() { return this.stateService.certifications(); }
  get strengths() { return this.stateService.strengths(); }
  get socials() { return this.stateService.socials(); }
  get themeColor() { return this.stateService.settings().themeColor; }
  get photoStyle() { return this.stateService.settings().photoStyle ?? 'circle'; }
  get showSkillLevel() { return this.stateService.settings().showSkillLevel; }

  readonly contactIcons = CONTACT_ICONS;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getSocialIcon(key: string): any { return getSocialIcon(key); }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getStrengthIcon(key: string): any { return getStrengthIcon(key); }
}
