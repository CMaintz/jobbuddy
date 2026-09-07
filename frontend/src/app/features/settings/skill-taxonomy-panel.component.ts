import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { JbButtonComponent } from '../../shared/components/jb-button/jb-button.component';
import { SkillsApiService, TaxonomyCandidate } from '../../core/api/skills.api';

/**
 * Growing the skill vocabulary from what postings actually name.
 *
 * <p>Admin-only, and shared: approving a candidate changes what every user's autocomplete
 * offers and how their CVs group that skill. The queue is derived from the postings on each
 * visit, so it is always current; the only thing remembered is a rejection.
 */
@Component({
  selector: 'app-skill-taxonomy-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslateModule, JbButtonComponent],
  templateUrl: './skill-taxonomy-panel.component.html'
})
export class SkillTaxonomyPanelComponent implements OnInit {
  private api = inject(SkillsApiService);
  private translate = inject(TranslateService);

  candidates = signal<TaxonomyCandidate[]>([]);
  categories = signal<string[]>([]);
  loading = signal(true);
  loadError = signal(false);
  /** The row currently being approved or rejected, so its buttons can go quiet. */
  busy = signal<string | null>(null);
  toast = signal('');

  /** Per-candidate category choice, keyed by normalized name. */
  chosen: Record<string, string> = {};

  ngOnInit(): void {
    this.api.getCategories().subscribe({
      next: c => this.categories.set(c),
      error: () => {}
    });
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.api.getTaxonomyCandidates().subscribe({
      next: list => {
        this.candidates.set(list);
        this.loading.set(false);
      },
      error: () => { this.loadError.set(true); this.loading.set(false); }
    });
  }

  /**
   * The category preselected for a row. A guess offered for correction, never a default the
   * backend would accept on its own — it takes the drawer enrichment kept using and nothing
   * more, and the admin still has to look at it.
   */
  categoryFor(candidate: TaxonomyCandidate): string {
    return this.chosen[candidate.normalizedName]
      ?? (candidate.readAsTechnology ? 'Tool' : 'Soft Skill');
  }

  choose(candidate: TaxonomyCandidate, category: string): void {
    this.chosen[candidate.normalizedName] = category;
  }

  approve(candidate: TaxonomyCandidate): void {
    this.busy.set(candidate.normalizedName);
    this.api.approveTaxonomyCandidate(candidate.name, this.categoryFor(candidate)).subscribe({
      next: () => {
        this.remove(candidate);
        this.toast.set(this.translate.instant('settings.taxonomy.approved', { name: candidate.name }));
      },
      error: () => {
        this.busy.set(null);
        this.toast.set(this.translate.instant('settings.taxonomy.failed'));
      }
    });
  }

  reject(candidate: TaxonomyCandidate): void {
    this.busy.set(candidate.normalizedName);
    this.api.rejectTaxonomyCandidate(candidate.name).subscribe({
      next: () => {
        this.remove(candidate);
        this.toast.set(this.translate.instant('settings.taxonomy.rejected', { name: candidate.name }));
      },
      error: () => {
        this.busy.set(null);
        this.toast.set(this.translate.instant('settings.taxonomy.failed'));
      }
    });
  }

  /** Decided rows leave the list rather than the whole queue being refetched. */
  private remove(candidate: TaxonomyCandidate): void {
    this.candidates.set(this.candidates().filter(c => c.normalizedName !== candidate.normalizedName));
    this.busy.set(null);
  }
}
