import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { JbIconComponent } from '../../../shared/components/jb-icon/jb-icon.component';
import { JbButtonComponent } from '../../../shared/components/jb-button/jb-button.component';
import { Certification } from '../../../core/models/profile-section.model';

/**
 * Master CV editor — certifications. Unlike the other sections these persist
 * immediately: the child owns the add-form, the parent owns the API call and
 * calls reset() on success.
 */
@Component({
  selector: 'app-mcv-certifications-section',
  standalone: true,
  imports: [CommonModule, FormsModule, JbIconComponent, JbButtonComponent],
  template: `
    <div class="flex flex-col gap-3">
      @for (cert of list; track cert.id || $index) {
        <div class="card p-3.5 flex justify-between items-center">
          <div>
            <div class="text-base font-medium">{{ cert.name }}</div>
            <div class="text-xs text-jb-text-dim">{{ cert.issuer }} {{ cert.issuedAt ? '· ' + cert.issuedAt : '' }}</div>
          </div>
          <button class="icon-btn" title="Remove" (click)="removeAt.emit($index)"><jb-icon name="x" [size]="12" /></button>
        </div>
      }
      @if (list.length === 0 && !showForm) {
        <div class="p-8 text-center text-jb-text-dim text-base">No certifications yet.</div>
      }
      @if (showForm) {
        <div class="card p-3.5 grid gap-2 grid-cols-[1.4fr_1fr_0.8fr]">
          <label class="flex flex-col gap-[5px]">
            <span class="label">Name</span>
            <input [(ngModel)]="draft.name" class="input" placeholder="AWS Certified Developer" />
          </label>
          <label class="flex flex-col gap-[5px]">
            <span class="label">Issuer</span>
            <input [(ngModel)]="draft.issuer" class="input" placeholder="Amazon Web Services" />
          </label>
          <label class="flex flex-col gap-[5px]">
            <span class="label">Issued</span>
            <input [(ngModel)]="draft.issuedAt" class="input mono" placeholder="2024-06-01" />
          </label>
          <div class="col-span-3 flex gap-2">
            <jb-button small [primary]="true" (clicked)="submit()">Add</jb-button>
            <jb-button small (clicked)="showForm = false">Cancel</jb-button>
          </div>
        </div>
      } @else {
        <jb-button small icon="plus" (clicked)="showForm = true">Add certification</jb-button>
      }
    </div>
  `,
})
export class McvCertificationsSectionComponent {
  @Input({ required: true }) list: Certification[] = [];
  @Output() removeAt = new EventEmitter<number>();
  /** The parent persists the entry and calls reset() when the API call succeeds. */
  @Output() save = new EventEmitter<Certification>();

  showForm = false;
  draft: Certification = { name: '' };

  submit(): void {
    if (!this.draft.name.trim()) return;
    this.save.emit({ ...this.draft });
  }

  reset(): void {
    this.draft = { name: '' };
    this.showForm = false;
  }
}
