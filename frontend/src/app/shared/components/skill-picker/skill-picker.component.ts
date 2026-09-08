import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { SkillsApiService } from '../../../core/api/skills.api';
import { SkillTaxonomy } from '../../../core/models/skill-taxonomy.model';

@Component({
  selector: 'app-skill-picker',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule],
  templateUrl: './skill-picker.component.html'
})
export class SkillPickerComponent {
  private skillsApi = inject(SkillsApiService);
  private search$ = new Subject<string>();

  @Input() selected: SkillTaxonomy[] = [];
  @Input() label = 'skillPicker.label';
  @Input() placeholder = 'skillPicker.placeholder';
  @Output() selectedChange = new EventEmitter<SkillTaxonomy[]>();
  @Output() createRequested = new EventEmitter<string>();

  query = '';
  suggestions: SkillTaxonomy[] = [];
  showDropdown = false;

  constructor() {
    this.search$.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      switchMap(query => query.length >= 1 ? this.skillsApi.searchTaxonomy(query) : [])
    ).subscribe({
      next: results => {
        this.suggestions = results.slice(0, 8);
        this.showDropdown = this.suggestions.length > 0 || this.query.trim().length > 0;
      },
      error: () => {
        this.suggestions = [];
        this.showDropdown = false;
      }
    });
  }

  onQueryChange(value: string): void {
    this.showDropdown = false;
    if (!value) {
      this.suggestions = [];
      return;
    }
    this.search$.next(value);
  }

  selectSkill(skill: SkillTaxonomy): void {
    if (!this.selected.some(s => s.id === skill.id)) {
      this.selectedChange.emit([...this.selected, skill]);
    }
    this.clearSearch();
  }

  removeSkill(skillId?: string): void {
    this.selectedChange.emit(this.selected.filter(skill => skill.id !== skillId));
  }

  clearSearch(): void {
    this.query = '';
    this.suggestions = [];
    this.showDropdown = false;
  }

  hideDropdownDelayed(): void {
    setTimeout(() => this.showDropdown = false, 150);
  }
}
