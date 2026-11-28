import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { BuddyWaitComponent } from './buddy-wait.component';
import { BUDDY_REDUCED_MOTION } from './buddy.component';

describe('BuddyWaitComponent', () => {
  it('mounts an empty live region first, then announces by changing the text inside it', () => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      imports: [BuddyWaitComponent, TranslateModule.forRoot()],
      providers: [{ provide: BUDDY_REDUCED_MOTION, useValue: () => true }],
    });
    const fixture = TestBed.createComponent(BuddyWaitComponent);
    fixture.componentRef.setInput('heading', 'apply.drafting');
    fixture.componentRef.setInput('detail', 'apply.draftingSub');
    fixture.detectChanges();
    const region = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(region).not.toBeNull();
    expect(region.textContent?.trim()).toBe('');

    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBe(region);
    expect(region.textContent).toContain('apply.drafting');
    expect(region.textContent).toContain('apply.draftingSub');

    fixture.componentRef.setInput('heading', 'cvImport.parsingCv');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="status"]')).toBe(region);
    expect(region.textContent).toContain('cvImport.parsingCv');
    fixture.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('lays out as a block, so a width class on it reaches its content', () => {
    // TestBed hosts a component in a div; only a real <jb-buddy-wait> tag shows the default inline display.
    @Component({ imports: [BuddyWaitComponent], template: '<jb-buddy-wait heading="apply.drafting" />' })
    class Host {}
    TestBed.configureTestingModule({ imports: [Host, TranslateModule.forRoot()] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const wait = (fixture.nativeElement as HTMLElement).querySelector('jb-buddy-wait') as HTMLElement;
    expect(getComputedStyle(wait).display).toBe('block');
  });

  it('shows its text from the first frame, so Buddy does not jump when the announcement lands', () => {
    vi.useFakeTimers();
    // Centred like the apply screen, where the host shrinks to its content.
    @Component({
      imports: [BuddyWaitComponent],
      template: '<div class="flex flex-col items-center w-[800px]"><jb-buddy-wait heading="apply.drafting" /></div>',
    })
    class Host {}
    TestBed.configureTestingModule({
      imports: [Host, TranslateModule.forRoot()],
      providers: [{ provide: BUDDY_REDUCED_MOTION, useValue: () => true }],
    });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const buddy = (fixture.nativeElement as HTMLElement).querySelector('jb-buddy') as HTMLElement;
    const before = buddy.getBoundingClientRect().left;
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(buddy.getBoundingClientRect().left).toBe(before);
    fixture.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });
});
