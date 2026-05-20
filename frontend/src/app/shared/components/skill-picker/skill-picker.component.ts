import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { SkillsApiService } from '../../../core/api/skills.api';
import { SkillTaxonomy } from '../../../core/models/skill-taxonomy.model';

@Component({
  selector: 'app-skill-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div>
      @if (label) {
        <label class="block text-xs font-medium text-gray-700">{{ label }}</label>
      }
      <div class="relative mt-1">
        <input [(ngModel)]="query"
               [ngModelOptions]="{standalone: true}"
               (ngModelChange)="onQueryChange($event)"
               (blur)="hideDropdownDelayed()"
               [placeholder]="placeholder"
               class="w-full border rounded px-2 py-1.5 text-sm" />
        @if (showDropdown) {
          <div class="absolute z-10 top-full left-0 right-0 bg-white border rounded shadow-lg mt-0.5 max-h-48 overflow-y-auto">
            @for (skill of suggestions; track skill.id) {
              <button type="button"
                      (mousedown)="selectSkill(skill)"
                      class="w-full text-left px-3 py-2 text-sm hover:bg-blue-50 border-b border-gray-100 last:border-0">
                <span class="font-medium">{{ skill.name }}</span>
                <span class="text-xs text-gray-400 ml-2">{{ skill.category }}</span>
              </button>
            }
            @if (query.trim()) {
              <button type="button"
                      (mousedown)="createRequested.emit(query.trim()); clearSearch()"
                      class="w-full text-left px-3 py-2 text-sm hover:bg-green-50 text-green-700">
                + Create "{{ query }}"
              </button>
            }
          </div>
        }
      </div>

      <div class="flex flex-wrap gap-1 mt-2">
        @for (skill of selected; track skill.id) {
          <span class="flex items-center gap-1 bg-indigo-50 text-indigo-700 text-xs px-2 py-0.5 rounded-full">
            {{ skill.name }}
            <button type="button" (click)="removeSkill(skill.id)" class="hover:text-indigo-900 ml-0.5">x</button>
          </span>
        }
      </div>
    </div>
  `
})
export class SkillPickerComponent {
  private skillsApi = inject(SkillsApiService);
  private search$ = new Subject<string>();

  @Input() selected: SkillTaxonomy[] = [];
  @Input() label = 'Skills';
  @Input() placeholder = 'Search skills to link...';
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
