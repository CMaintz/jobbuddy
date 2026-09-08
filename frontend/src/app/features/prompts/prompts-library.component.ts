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
  { key: 'UNSOLICITED_APPLICATION', label: 'prompts.kind.unsolicited', icon: 'compass', color: '#f2c97d' },
  { key: 'RECRUITER_MESSAGE', label: 'prompts.kind.recruiterEmail', icon: 'mail', color: '#7df2a8' },
  { key: 'FOLLOW_UP_MESSAGE', label: 'prompts.kind.followUp', icon: 'clock', color: '#7dd3f2' },
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

  /** Which library is on screen: the caller's own prompts, or the ones other people shared. */
  view = signal<'mine' | 'shared'>('mine');
  sharedPrompts: PromptTemplate[] = [];
  loadingShared = signal(false);
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

  /**
   * Protected templates ship with the app and are admin-only; everything else belongs to its
   * creator. Mirrors the backend rule — the gate is protection, not "is a system template".
   */
  canModify(p: PromptTemplate): boolean {
    return p.isProtected ? this.isAdmin : p.userId === this.myUserId;
  }

  /** Protected prompts offer Duplicate where a user's own offer Edit and Delete. */
  mustDuplicateToCustomise(p: PromptTemplate): boolean {
    return p.isProtected && !this.isAdmin;
  }

  /** Points this prompt's category at this prompt. Never edits the app's seeded template. */
  useAsDefault(p: PromptTemplate, event?: Event): void {
    event?.stopPropagation();
    if (p.isSelectedDefault) return;
    this.promptApi.selectDefault(p.id).subscribe({
      next: () => {
        this.toast.set(this.translate.instant('prompts.toast.defaultSet', { name: p.name }));
        this.load();
      },
      error: () => this.toast.set(this.translate.instant('prompts.toast.defaultFailed'))
    });
  }

  /** Drops the user's choice for this category so the app's own prompt comes back. */
  resetDefault(p: PromptTemplate, event?: Event): void {
    event?.stopPropagation();
    if (!p.category) return;
    this.promptApi.resetDefault(p.category).subscribe({
      next: () => {
        this.toast.set(this.translate.instant('prompts.toast.defaultReset'));
        this.load();
      },
      error: () => this.toast.set(this.translate.instant('prompts.toast.defaultFailed'))
    });
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

  showShared(): void {
    this.view.set('shared');
    if (this.sharedPrompts.length || this.loadingShared()) return;
    this.loadingShared.set(true);
    this.promptApi.getPublic().subscribe({
      next: shared => { this.sharedPrompts = shared; this.loadingShared.set(false); },
      error: () => {
        this.loadingShared.set(false);
        this.toast.set(this.translate.instant('prompts.toast.sharedLoadFailed'));
      }
    });
  }

  showMine(): void { this.view.set('mine'); }

  /** Sharing is per-prompt and reversible; only your own prompts can be shared. */
  canShare(p: PromptTemplate): boolean {
    return !p.isProtected && p.userId === this.myUserId;
  }

  toggleShared(p: PromptTemplate): void {
    const next = !p.isPublic;
    this.promptApi.update(p.id, {
      name: p.name, category: p.category, description: p.description,
      systemPrompt: p.systemPrompt, userPrompt: p.userPrompt,
      outputConstraints: p.outputConstraints, isPublic: next, tags: p.tags,
    }).subscribe({
      next: () => {
        p.isPublic = next;
        this.toast.set(this.translate.instant(next ? 'prompts.toast.shared' : 'prompts.toast.unshared'));
      },
      error: () => this.toast.set(this.translate.instant('prompts.toast.shareFailed'))
    });
  }

  /** Favourites first, then most-used. */
  filteredPrompts(): PromptTemplate[] {
    const kind = this.activeKind();
    const source = this.view() === 'shared' ? this.sharedPrompts : this.prompts;
    const list = kind === 'all' ? source : source.filter(p => p.category === kind);
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
