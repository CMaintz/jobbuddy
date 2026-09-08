import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { Profile, ProfilePrivateInfo } from '../../core/models/user.model';
import {
  WorkExperience, Education, Project, Certification,
  SpokenLanguage, ProfileSocial, ProfileStrength
} from '../../core/models/profile-section.model';

/** Read-only paper preview of the master CV, shown beside the section editor. */
@Component({
  selector: 'app-master-cv-preview',
  standalone: true,
  imports: [CommonModule, TranslateModule],
  templateUrl: './master-cv-preview.component.html',
})
export class MasterCvPreviewComponent {
  @Input() profile: Profile = {};
  @Input() privateInfo: ProfilePrivateInfo = {};
  @Input() experience: WorkExperience[] = [];
  @Input() education: Education[] = [];
  @Input() projects: Project[] = [];
  @Input() certifications: Certification[] = [];
  @Input() languages: SpokenLanguage[] = [];
  @Input() socials: ProfileSocial[] = [];
  @Input() strengths: ProfileStrength[] = [];
  @Input() skills: string[] = [];
  @Input() technologies: string[] = [];
}
