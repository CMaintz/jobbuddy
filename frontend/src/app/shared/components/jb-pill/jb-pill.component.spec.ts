import { TestBed } from '@angular/core/testing';
import { JbPillComponent, PillTone } from './jb-pill.component';

/**
 * Renders against the real global stylesheet, so a tone whose rule Tailwind dropped from the
 * build shows up as a pill with no background. The tone class names are never spelled out here:
 * Tailwind scans spec files too, and a literal name in this file would keep the rule alive and
 * hide the very drop this test exists to catch.
 */
describe('JbPillComponent', () => {
  const tones: PillTone[] = ['neutral', 'accent', 'success', 'info', 'danger', 'violet'];

  for (const tone of tones) {
    it(`gives a ${tone} pill a visible background`, () => {
      const fixture = TestBed.createComponent(JbPillComponent);
      fixture.componentRef.setInput('tone', tone);
      fixture.detectChanges();

      const pill = (fixture.nativeElement as HTMLElement).querySelector('span')!;

      expect(getComputedStyle(pill).backgroundColor).not.toBe('rgba(0, 0, 0, 0)');
    });
  }
});
