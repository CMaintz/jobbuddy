import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { BuddyComponent, BUDDY_REDUCED_MOTION } from './buddy.component';
import { resolveClip } from './buddy-sprites';

const SCALE = 2;

function render(reduced: boolean) {
  TestBed.configureTestingModule({
    imports: [BuddyComponent, TranslateModule.forRoot()],
    providers: [{ provide: BUDDY_REDUCED_MOTION, useValue: () => reduced }],
  });
  const fixture = TestBed.createComponent(BuddyComponent);
  fixture.componentRef.setInput('mood', 'thinking');
  fixture.componentRef.setInput('scale', SCALE);
  fixture.detectChanges();
  const sprite = fixture.nativeElement.querySelector('span') as HTMLElement;
  return { fixture, sprite };
}

/** The atlas row the sprite shows, in sprite pixels. */
const row = (sprite: HTMLElement) => Math.abs(parseFloat(sprite.style.backgroundPosition.split(' ')[1])) / SCALE;

describe('BuddyComponent', () => {
  it('animates the mood when motion is allowed', fakeAsync(() => {
    const { fixture, sprite } = render(false);
    tick(1000);
    expect(row(sprite)).toBe(resolveClip(['think', 'look-around']).y);
    fixture.destroy();
  }));

  it('holds the neutral frame and runs no timer under reduced motion', fakeAsync(() => {
    const { sprite } = render(true);
    tick(5000);
    expect(row(sprite)).toBe(resolveClip('idle').y);
    expect(sprite.style.backgroundPosition.split(' ')[0]).toBe('0px');
  }));

  it('keeps its box at the idle frame size, so nothing moves when he loads', () => {
    const { fixture } = render(true);
    const host = fixture.nativeElement as HTMLElement;
    expect(host.style.width).toBe(`${resolveClip('idle').width * SCALE}px`);
    expect(host.style.height).toBe(`${resolveClip('idle').height * SCALE}px`);
  });

  it('repaints the still frame at a new scale under reduced motion', () => {
    const { fixture, sprite } = render(true);
    fixture.componentRef.setInput('scale', 3);
    fixture.detectChanges();
    const idle = resolveClip('idle');
    expect(sprite.style.width).toBe(`${idle.width * 3}px`);
    expect(sprite.style.backgroundSize).toBe(`${idle.sheetWidth * 3}px ${idle.sheetHeight * 3}px`);
  });

  it('is labelled in the page language, and hidden when a labelled button already names him', () => {
    const { fixture } = render(true);
    const sprite = fixture.nativeElement.querySelector('span') as HTMLElement;
    expect(sprite.getAttribute('role')).toBe('img');
    expect(sprite.getAttribute('aria-label')).toBe('buddy.alt');
    fixture.componentRef.setInput('alt', null);
    fixture.detectChanges();
    expect(sprite.getAttribute('role')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).getAttribute('aria-hidden')).toBe('true');
  });
});
