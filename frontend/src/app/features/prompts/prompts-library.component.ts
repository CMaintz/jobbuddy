import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { JbToastComponent } from '../../shared/components/jb-toast/jb-toast.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { JbModalComponent } from '../../shared/components/jb-modal/jb-modal.component';
import { PromptApiService } from '../../core/api/prompt.api';
import { AuthService } from '../../core/auth/auth.service';
import { PromptTemplate, PromptCategory } from '../../core/models/prompt-template.model';

const PROMPT_KINDS: { key: PromptCategory; label: string; icon: string; color: string }[] = [
  { key: 'APPLICATION', label: 'prompts.kind.application', icon: 'layers', color: 'var(--jb-accent)' },
  { key: 'COVER_LETTER', label: 'prompts.kind.coverLetter', icon: 'doc', color: 'var(--jb-info)' },
  { key: 'RECRUITER_MESSAGE', label: 'prompts.kind.recruiterEmail', icon: 'mail', color: '#7df2a8' },
  { key: 'CV_TAILORING', label: 'prompts.kind.tailoredCv', icon: 'doc', color: 'var(--jb-violet)' },
  { key: 'CV_ANALYSIS', label: 'prompts.kind.cvAnalysis', icon: 'doc', color: 'var(--jb-text-dim)' },
  { key: 'GENERAL', label: 'prompts.kind.general', icon: 'lightbulb', color: 'var(--jb-text-dim)' },
];

@Component({
  selector: 'app-prompts-library',
  standalone: true,
  imports: [CommonModule, JbTopbarComponent, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent, JbPillComponent, JbToastComponent, TagInputComponent, JbModalComponent],
  templateUrl: './prompts-library.component.html'
})
export class PromptsLibraryComponent implements OnInit {
  private promptApi = inject(PromptApiService);
  private auth = inject(AuthService);
  private translate = inject(TranslateService);

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
  newTags: string[] = [];

  // Edit form (populated when editing an existing template)
  editName = '';
  editCategory: PromptCategory = 'APPLICATION';
  editDescription = '';
  editBody = '';
  editTags: string[] = [];

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
    this.editTags = [...(p.tags ?? [])];
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
      tags: this.editTags,
    }).subscribe({
      next: () => {
        this.savingEdit.set(false);
        this.editing.set(false);
        this.openPrompt.set(null);
        this.toast.set(this.translate.instant('prompts.toast.updated'));
        this.load();
      },
      error: err => {
        this.savingEdit.set(false);
        this.toast.set(this.translate.instant(err?.status === 403 ? 'prompts.toast.adminOnlyEdit' : 'prompts.toast.updateFailed'));
      }
    });
  }

  deletePrompt(p: PromptTemplate): void {
    if (!window.confirm(this.translate.instant('prompts.deleteConfirm', { name: p.name }))) return;
    this.promptApi.delete(p.id).subscribe({
      next: () => {
        this.openPrompt.set(null);
        this.toast.set(this.translate.instant('prompts.toast.deleted'));
        this.load();
      },
      error: err => {
        this.toast.set(this.translate.instant(err?.status === 403 ? 'prompts.toast.adminOnlyDelete' : 'prompts.toast.deleteFailed'));
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
        this.toast.set(this.translate.instant('prompts.toast.loadFailed'));
      }
    });
  }

  /** Favourites first, then most-used. */
  filteredPrompts(): PromptTemplate[] {
    const kind = this.activeKind();
    const list = kind === 'all' ? this.prompts : this.prompts.filter(p => p.category === kind);
    return [...list].sort((a, b) =>
      Number(b.favourite ?? false) - Number(a.favourite ?? false)
      || (b.usageCount ?? 0) - (a.usageCount ?? 0));
  }

  toggleFavourite(p: PromptTemplate, event?: Event): void {
    event?.stopPropagation();
    const next = !p.favourite;
    p.favourite = next; // optimistic
    (next ? this.promptApi.favourite(p.id) : this.promptApi.unfavourite(p.id)).subscribe({
      error: () => {
        p.favourite = !next;
        this.toast.set(this.translate.instant('prompts.toast.favouriteFailed'));
      }
    });
  }

  kindConfig(category?: string) {
    return PROMPT_KINDS.find(k => k.key === category)
      ?? { key: 'GENERAL', label: 'prompts.kind.general', icon: 'lightbulb', color: 'var(--jb-text-dim)' };
  }

  ageLabel(iso?: string): string {
    if (!iso) return '';
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
    if (days === 0) return this.translate.instant('time.today');
    if (days < 7) return this.translate.instant('time.daysAgo', { n: days });
    if (days < 30) return this.translate.instant('time.weeksAgo', { n: Math.floor(days / 7) });
    return this.translate.instant('time.monthsAgo', { n: Math.floor(days / 30) });
  }

  duplicate(p: PromptTemplate): void {
    this.promptApi.duplicate(p.id).subscribe({
      next: () => {
        this.openPrompt.set(null);
        this.toast.set(this.translate.instant('prompts.toast.duplicated'));
        this.load();
      },
      error: () => this.toast.set(this.translate.instant('prompts.toast.duplicateFailed'))
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
      tags: this.newTags,
    }).subscribe({
      next: () => {
        this.creating.set(false);
        this.showCreate.set(false);
        this.newName = '';
        this.newDescription = '';
        this.newBody = '';
        this.newTags = [];
        this.toast.set(this.translate.instant('prompts.toast.created'));
        this.load();
      },
      error: () => {
        this.creating.set(false);
        this.toast.set(this.translate.instant('prompts.toast.createFailed'));
      }
    });
  }
}
