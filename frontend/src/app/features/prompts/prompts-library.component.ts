import { Component, DestroyRef, OnInit, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JbIconComponent } from '../../shared/components/jb-icon/jb-icon.component';
import { JbTopbarComponent } from '../../shared/components/jb-topbar/jb-topbar.component';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { JbPillComponent } from '../../shared/components/jb-pill/jb-pill.component';
import { TagInputComponent } from '../../shared/components/tag-input/tag-input.component';
import { JbModalComponent } from '../../shared/components/jb-modal/jb-modal.component';
import { PromptExplainerComponent } from '../../shared/components/prompt-explainer/prompt-explainer.component';
import { PromptApiService } from '../../core/api/prompt.api';
import { AuthService } from '../../core/auth/auth.service';
import { PromptTemplate, PromptCategory, byPreference } from '../../core/models/prompt-template.model';
import { isSectionCategory } from '../../core/models/cv-section-category';
import { PROMPT_KINDS, saveErrorKey } from './prompt-kinds';
import { PromptCreateModalComponent } from './prompt-create-modal.component';

import { NotifyService } from '../../shared/services/notify.service';

@Component({
  selector: 'app-prompts-library',
  imports: [
    JbTopbarComponent, FormsModule, TranslateModule, JbIconComponent, JbButtonComponent,
    JbPillComponent, TagInputComponent, JbModalComponent, PromptExplainerComponent,
    PromptCreateModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './prompts-library.component.html'
})
export class PromptsLibraryComponent implements OnInit {
  private promptApi = inject(PromptApiService);
  protected notify = inject(NotifyService);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);
  private translate = inject(TranslateService);

  loading = signal(true);
  activeKind = signal<'all' | PromptCategory>('all');
  openPrompt = signal<PromptTemplate | null>(null);
  showCreate = signal(false);
  editing = signal(false);
  savingEdit = signal(false);
  promptKinds = PROMPT_KINDS;
  readonly isSectionCategory = isSectionCategory;

  prompts = signal<PromptTemplate[]>([]);

  /** Which library is on screen: the caller's own prompts, or the ones other people shared. */
  view = signal<'mine' | 'shared'>('mine');
  sharedPrompts = signal<PromptTemplate[]>([]);
  loadingShared = signal(false);
  private myUserId = signal('');
  private isAdmin = signal(false);

  // Edit form (populated when editing an existing template)
  editName = '';
  editCategory: PromptCategory = 'APPLICATION';
  editDescription = '';
  editBody = '';
  editSystem = '';
  editTags: string[] = [];

  ngOnInit(): void {
    this.auth.currentUser$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(u => {
      this.myUserId.set(u?.id ?? '');
      this.isAdmin.set(u?.role === 'ADMIN');
    });
    this.load();
  }

  /**
   * Protected templates ship with the app and are admin-only; everything else belongs to its
   * creator. Mirrors the backend rule — the gate is protection, not "is a system template".
   */
  canModify(p: PromptTemplate): boolean {
    return p.isProtected ? this.isAdmin() : p.userId === this.myUserId();
  }

  /** Protected prompts offer Duplicate where a user's own offer Edit and Delete. */
  mustDuplicateToCustomise(p: PromptTemplate): boolean {
    return p.isProtected && !this.isAdmin();
  }

  /** Points this prompt's category at this prompt. Never edits the app's seeded template. */
  useAsDefault(p: PromptTemplate, event?: Event): void {
    event?.stopPropagation();
    if (p.isSelectedDefault) return;
    this.promptApi.selectDefault(p.id).subscribe({
      next: () => {
        this.notify.success('prompts.toast.defaultSet', { name: p.name });
        this.load();
      },
      error: () => this.notify.error('prompts.toast.defaultFailed')
    });
  }

  /** Drops the user's choice for this category so the app's own prompt comes back. */
  resetDefault(p: PromptTemplate, event?: Event): void {
    event?.stopPropagation();
    if (!p.category) return;
    this.promptApi.resetDefault(p.category).subscribe({
      next: () => {
        this.notify.success('prompts.toast.defaultReset');
        this.load();
      },
      error: () => this.notify.error('prompts.toast.defaultFailed')
    });
  }

  startEdit(p: PromptTemplate): void {
    this.editName = p.name;
    this.editCategory = p.category ?? 'GENERAL';
    this.editDescription = p.description ?? '';
    this.editBody = p.userPrompt;
    this.editSystem = p.systemPrompt ?? '';
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
      systemPrompt: isSectionCategory(this.editCategory) ? undefined : this.editSystem.trim() || undefined,
      userPrompt: this.editBody.trim(),
      outputConstraints: p.outputConstraints,
      isPublic: p.isPublic,
      tags: this.editTags,
    }).subscribe({
      next: () => {
        this.savingEdit.set(false);
        this.editing.set(false);
        this.openPrompt.set(null);
        this.notify.success('prompts.toast.updated');
        this.load();
      },
      error: err => {
        this.savingEdit.set(false);
        this.notify.error(err?.status === 403
          ? 'prompts.toast.adminOnlyEdit' : saveErrorKey(err, 'prompts.toast.updateFailed'));
      }
    });
  }

  deletePrompt(p: PromptTemplate): void {
    if (!window.confirm(this.translate.instant('prompts.deleteConfirm', { name: p.name }))) return;
    this.promptApi.delete(p.id).subscribe({
      next: () => {
        this.openPrompt.set(null);
        this.notify.success('prompts.toast.deleted');
        this.load();
      },
      error: err => {
        this.notify.error(err?.status === 403 ? 'prompts.toast.adminOnlyDelete' : 'prompts.toast.deleteFailed');
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
        this.prompts.set(prompts);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.notify.error('prompts.toast.loadFailed');
      }
    });
  }

  showShared(): void {
    this.view.set('shared');
    if (this.sharedPrompts().length || this.loadingShared()) return;
    this.loadingShared.set(true);
    this.promptApi.getPublic().subscribe({
      next: shared => { this.sharedPrompts.set(shared); this.loadingShared.set(false); },
      error: () => {
        this.loadingShared.set(false);
        this.notify.error('prompts.toast.sharedLoadFailed');
      }
    });
  }

  showMine(): void { this.view.set('mine'); }

  /** Sharing is per-prompt and reversible; only your own prompts can be shared. */
  canShare(p: PromptTemplate): boolean {
    return !p.isProtected && p.userId === this.myUserId();
  }

  toggleShared(p: PromptTemplate): void {
    const next = !p.isPublic;
    this.promptApi.update(p.id, {
      name: p.name, category: p.category, description: p.description,
      systemPrompt: p.systemPrompt, userPrompt: p.userPrompt,
      outputConstraints: p.outputConstraints, isPublic: next, tags: p.tags,
    }).subscribe({
      next: () => {
        this.patchPrompt(p.id, { isPublic: next });
        this.notify.success(next ? 'prompts.toast.shared' : 'prompts.toast.unshared');
      },
      error: () => this.notify.error('prompts.toast.shareFailed')
    });
  }

  /** Replaces the prompt everywhere it is shown, so the list and the open modal stay in step. */
  private patchPrompt(id: string, patch: Partial<PromptTemplate>): void {
    const apply = (list: PromptTemplate[]) => list.map(p => p.id === id ? { ...p, ...patch } : p);
    this.prompts.update(apply);
    this.sharedPrompts.update(apply);
    const open = this.openPrompt();
    if (open?.id === id) this.openPrompt.set({ ...open, ...patch });
  }

  filteredPrompts(): PromptTemplate[] {
    const kind = this.activeKind();
    const source = this.view() === 'shared' ? this.sharedPrompts() : this.prompts();
    const list = kind === 'all' ? source : source.filter(p => p.category === kind);
    return [...list].sort(byPreference);
  }

  toggleFavourite(p: PromptTemplate, event?: Event): void {
    event?.stopPropagation();
    const next = !p.favourite;
    this.patchPrompt(p.id, { favourite: next }); // optimistic
    (next ? this.promptApi.favourite(p.id) : this.promptApi.unfavourite(p.id)).subscribe({
      // Starring a shared prompt is what adds it to your own library, so that list must refresh.
      next: () => { if (this.view() === 'shared') this.load(); },
      error: () => {
        this.patchPrompt(p.id, { favourite: !next });
        this.notify.error('prompts.toast.favouriteFailed');
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
        this.notify.success('prompts.toast.duplicated');
        this.load();
      },
      error: err => this.notify.error(saveErrorKey(err, 'prompts.toast.duplicateFailed'))
    });
  }

  onCreated(): void {
    this.showCreate.set(false);
    this.notify.success('prompts.toast.created');
    this.load();
  }
}
