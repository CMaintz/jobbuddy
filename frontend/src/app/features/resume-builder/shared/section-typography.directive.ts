import { Directive, ElementRef, Input, effect, inject } from '@angular/core';
import { ResumeStateService } from '../services/resume-state.service';

/**
 * Applies the per-section typography overrides from
 * settings.sectionTypography to a layout <section>. Font size scales the
 * whole section proportionally via em-inheritance where possible, so
 * headings keep their relative rhythm.
 */
@Directive({ selector: '[rbSection]', standalone: true })
export class SectionTypographyDirective {
  @Input('rbSection') sectionId = '';

  private el = inject(ElementRef<HTMLElement>).nativeElement;
  private state = inject(ResumeStateService);

  constructor() {
    effect(() => {
      const typo = this.state.settings().sectionTypography?.[this.sectionId];
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
