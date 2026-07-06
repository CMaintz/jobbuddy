import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbToggleComponent } from '../../shared/components/jb-toggle/jb-toggle.component';
import { JbSegmentedComponent } from '../../shared/components/jb-segmented/jb-segmented.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { ThemeService } from '../../core/theme.service';

type Section = 'match' | 'gen' | 'sources' | 'account' | 'privacy';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbToggleComponent, JbSegmentedComponent, TagInputComponent],
  templateUrl: './settings.component.html'
})
export class SettingsComponent {
  theme = inject(ThemeService);
  Math = Math;

  activeSection = signal<Section>('match');

  sections: { key: Section; label: string; icon: string }[] = [
    { key: 'match', label: 'Match preferences', icon: 'target' },
    { key: 'gen', label: 'Generation defaults', icon: 'wand' },
    { key: 'sources', label: 'Sources & feed', icon: 'compass' },
    { key: 'account', label: 'Account', icon: 'user' },
    { key: 'privacy', label: 'Privacy & data', icon: 'key' },
  ];

  // Match settings
  skills = ['React', 'TypeScript', 'Design systems', 'Motion', 'Accessibility'];
  allIndustries = ['AI', 'DevTools', 'Productivity', 'Design tools', 'Fintech', 'Healthtech', 'Climate', 'Gaming', 'E-commerce'];
  industries = new Set(['AI', 'DevTools', 'Design tools']);
  seniority = 'Senior';
  remote = true;
  relocate = false;
  minMatch = 75;
  weeklyGoal = 10;

  // Gen settings
  voice = 'Warm';
  lang = 'Dansk';
  length = 'Standard';
  highlightKw = true;
  autoFollowup = true;

  // Sources
  sources = [
    { label: 'Company career pages', on: true },
    { label: 'Greenhouse', on: true },
    { label: 'Lever', on: true },
    { label: 'Ashby', on: true },
    { label: 'LinkedIn Jobs', on: false },
    { label: 'Browser extension', on: true },
  ];

  // Privacy
  anonymise = false;

  toggleIndustry(ind: string): void {
    if (this.industries.has(ind)) this.industries.delete(ind);
    else this.industries.add(ind);
    this.industries = new Set(this.industries);
  }
}
