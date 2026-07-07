import { Directive, ElementRef, Input, effect, inject } from '@angular/core';
import { ResumeStateService } from '../services/resume-state.service';
import { SectionConfig } from '../models/resume-builder.models';

/**
 * Applied to every layout <section>. Handles two per-section concerns from
 * settings: typography overrides (sectionTypography) and column ordering /
 * visibility (leftColumn/rightColumn). Ordering works via flex `order` since
 * sections are children of flex columns in every template.
 */
@Directive({ selector: '[rbSection]', standalone: true })
export class SectionTypographyDirective {
  @Input('rbSection') sectionId = '';

  private el = inject(ElementRef<HTMLElement>).nativeElement;
  private state = inject(ResumeStateService);

  constructor() {
    effect(() => {
      const settings = this.state.settings();

      // Column order + visibility
      const columns: SectionConfig[][] = [settings.leftColumn ?? [], settings.rightColumn ?? []];
      let config: SectionConfig | undefined;
      let index = -1;
      for (const column of columns) {
        const i = column.findIndex(s => s.id === this.sectionId);
        if (i >= 0) { config = column[i]; index = i; break; }
      }
      this.el.style.order = index >= 0 ? `${index + 1}` : this.sectionId === 'custom' ? '98' : '';
      this.el.style.display = config && !config.visible ? 'none' : '';

      const typo = settings.sectionTypography?.[this.sectionId];
      this.el.style.fontFamily = typo?.fontFamily ?? '';
      this.el.style.fontWeight = typo?.bold ? '600' : '';
      this.el.style.fontStyle = typo?.italic ? 'italic' : '';
      // These vars feed the #previewContainer overrides in styles.css, so the
      // scale/colour reach children with explicit Tailwind text classes too.
      if (typo?.sizeScale && typo.sizeScale !== 1) {
        this.el.style.setProperty('--rb-sec-scale', `${typo.sizeScale}`);
      } else {
        this.el.style.removeProperty('--rb-sec-scale');
      }
      if (typo?.color) {
        this.el.style.setProperty('--rb-sec-text', typo.color);
      } else {
        this.el.style.removeProperty('--rb-sec-text');
      }
      this.el.style.color = typo?.color ?? '';
    });
  }
}
