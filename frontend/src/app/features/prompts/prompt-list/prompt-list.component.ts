import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { PromptApiService } from '../../../core/api/prompt.api';
import { PromptTemplate } from '../../../core/models/prompt-template.model';

@Component({
  selector: 'app-prompt-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-3xl font-bold text-gray-900">Prompt Templates</h1>
        <a routerLink="/prompts/new" class="btn-primary text-sm">New Template</a>
      </div>

      @if (loading) {
        <div class="text-center py-12 text-gray-500">Loading...</div>
      } @else if (templates.length === 0) {
        <div class="card text-center py-12">
          <p class="text-gray-500">No templates yet.</p>
          <a routerLink="/prompts/new" class="btn-primary mt-4 inline-block">Create first template</a>
        </div>
      } @else {
        <div class="space-y-3">
          @for (t of templates; track t.id) {
            <div class="card">
              <div class="flex items-start justify-between">
                <div class="flex-1">
                  <div class="flex items-center gap-2">
                    <span class="font-semibold text-gray-900">{{ t.name }}</span>
                    @if (t.category) {
                      <span class="text-xs bg-purple-100 text-purple-700 px-2 py-0.5 rounded-full">{{ t.category }}</span>
                    }
                    @if (t.isPublic) {
                      <span class="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded-full">Public</span>
                    }
                    <span class="text-xs text-gray-400">v{{ t.versionNumber }}</span>
                  </div>
                  @if (t.description) {
                    <p class="text-sm text-gray-500 mt-1">{{ t.description }}</p>
                  }
                  <p class="text-xs text-gray-400 mt-1 line-clamp-1 font-mono">{{ t.userPrompt }}</p>
                </div>
                <div class="flex gap-2 ml-4 shrink-0">
                  <a [routerLink]="['/prompts', t.id, 'edit']" class="btn-secondary text-xs">Edit</a>
                  <button (click)="duplicate(t)" class="btn-secondary text-xs">Duplicate</button>
                </div>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `
})
export class PromptListComponent implements OnInit {
  private api = inject(PromptApiService);

  templates: PromptTemplate[] = [];
  loading = true;

  ngOnInit(): void {
    this.api.getAll().subscribe({
      next: ts => { this.templates = ts; this.loading = false; },
      error: () => this.loading = false
    });
  }

  duplicate(t: PromptTemplate): void {
    this.api.duplicate(t.id, t.name + ' (copy)').subscribe(copy => {
      this.templates.push(copy);
    });
  }
}
