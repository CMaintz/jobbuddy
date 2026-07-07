import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { PromptApiService } from '../../core/api/prompt.api';
import { AuthService } from '../../core/auth/auth.service';
import { PromptTemplate, PromptCategory } from '../../core/models/prompt-template.model';

const PROMPT_KINDS: { key: PromptCategory; label: string; icon: string; color: string }[] = [
  { key: 'APPLICATION', label: 'Application', icon: 'layers', color: 'var(--jb-accent)' },
  { key: 'COVER_LETTER', label: 'Cover letter', icon: 'doc', color: 'var(--jb-info)' },
  { key: 'RECRUITER_MESSAGE', label: 'Recruiter email', icon: 'mail', color: '#7df2a8' },
  { key: 'CV_ANALYSIS', label: 'CV analysis', icon: 'doc', color: 'var(--jb-violet)' },
  { key: 'GENERAL', label: 'General', icon: 'lightbulb', color: 'var(--jb-text-dim)' },
];

@Component({
  selector: 'app-prompts-library',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent, JbPillComponent, JbToastComponent],
  templateUrl: './prompts-library.component.html'
})
export class PromptsLibraryComponent implements OnInit {
  private promptApi = inject(PromptApiService);
  private auth = inject(AuthService);

  loading = signal(true);
  toast = signal('');
  activeKind = signal<'all' | PromptCategory>('all');
  openPrompt = signal<PromptTemplate | null>(null);
  showCreate = signal(false);
  creating = signal(false);
  editing = signal(false);
  savingEdit = signal(false);
  promptKinds = PROMPT_KINDS;

  prompts: PromptTemplate[] = [];
  private myUserId = '';
  private isAdmin = false;

  // Create form
  newName = '';
  newCategory: PromptCategory = 'APPLICATION';
  newDescription = '';
  newBody = '';

  // Edit form (populated when editing an existing template)
  editName = '';
  editCategory: PromptCategory = 'APPLICATION';
  editDescription = '';
  editBody = '';

  ngOnInit(): void {
    this.auth.currentUser$.subscribe(u => {
      this.myUserId = u?.id ?? '';
      this.isAdmin = u?.role === 'ADMIN';
    });
    this.load();
  }

  /** System templates are admin-only; user templates belong to their creator. Mirrors the backend rule. */
  canModify(p: PromptTemplate): boolean {
    return p.isSystem ? this.isAdmin : p.userId === this.myUserId;
  }

  startEdit(p: PromptTemplate): void {
    this.editName = p.name;
    this.editCategory = p.category ?? 'GENERAL';
    this.editDescription = p.description ?? '';
    this.editBody = p.userPrompt;
    this.editing.set(true);
  }

  saveEdit(p: PromptTemplate): void {
    if (!this.editName.trim() || !this.editBody.trim() || this.savingEdit()) return;
    this.savingEdit.set(true);
    this.promptApi.update(p.id, {
      name: this.editName.trim(),
      category: this.editCategory,
      description: this.editDescription.trim() || undefined,
      systemPrompt: p.systemPrompt,
      userPrompt: this.editBody.trim(),
      outputConstraints: p.outputConstraints,
      isPublic: p.isPublic,
    }).subscribe({
      next: () => {
        this.savingEdit.set(false);
        this.editing.set(false);
        this.openPrompt.set(null);
        this.toast.set('Prompt updated');
        this.load();
      },
      error: err => {
        this.savingEdit.set(false);
        this.toast.set(err?.status === 403 ? 'Only admins can change system templates' : 'Could not update the prompt');
      }
    });
  }

  deletePrompt(p: PromptTemplate): void {
    if (!window.confirm(`Delete "${p.name}"? This cannot be undone.`)) return;
    this.promptApi.delete(p.id).subscribe({
      next: () => {
        this.openPrompt.set(null);
        this.toast.set('Prompt deleted');
        this.load();
      },
      error: err => {
        this.toast.set(err?.status === 403 ? 'Only admins can delete system templates' : 'Could not delete the prompt');
      }
    });
  }

  closeModal(): void {
    this.editing.set(false);
    this.openPrompt.set(null);
  }

  private load(): void {
    this.promptApi.getAll().subscribe({
      next: prompts => {
        this.prompts = prompts;
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.set('Could not load prompts');
      }
    });
  }

  filteredPrompts(): PromptTemplate[] {
    const kind = this.activeKind();
    if (kind === 'all') return this.prompts;
    return this.prompts.filter(p => p.category === kind);
  }

  kindConfig(category?: string) {
    return PROMPT_KINDS.find(k => k.key === category)
      ?? { key: 'GENERAL', label: category ?? 'General', icon: 'lightbulb', color: 'var(--jb-text-dim)' };
  }

  ageLabel(iso?: string): string {
    if (!iso) return '';
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
    if (days === 0) return 'today';
    if (days < 7) return `${days}d ago`;
    if (days < 30) return `${Math.floor(days / 7)}w ago`;
    return `${Math.floor(days / 30)}mo ago`;
  }

  duplicate(p: PromptTemplate): void {
    this.promptApi.duplicate(p.id).subscribe({
      next: () => {
        this.openPrompt.set(null);
        this.toast.set('Prompt duplicated');
        this.load();
      },
      error: () => this.toast.set('Could not duplicate the prompt')
    });
  }

  create(): void {
    if (!this.newName.trim() || !this.newBody.trim() || this.creating()) return;
    this.creating.set(true);
    this.promptApi.create({
      name: this.newName.trim(),
      category: this.newCategory,
      description: this.newDescription.trim() || undefined,
      userPrompt: this.newBody.trim(),
      isPublic: false,
    }).subscribe({
      next: () => {
        this.creating.set(false);
        this.showCreate.set(false);
        this.newName = '';
        this.newDescription = '';
        this.newBody = '';
        this.toast.set('Prompt created');
        this.load();
      },
      error: () => {
        this.creating.set(false);
        this.toast.set('Could not create the prompt');
      }
    });
  }
}
