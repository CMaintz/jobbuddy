import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ActivityStreakService } from '../../../core/activity-streak.service';
import { StreakBadgeComponent } from './streak-badge.component';

describe('StreakBadgeComponent', () => {
  const days = signal(0);

  function render(): HTMLElement {
    const fixture = TestBed.createComponent(StreakBadgeComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    days.set(0);
    TestBed.configureTestingModule({
      imports: [StreakBadgeComponent, TranslateModule.forRoot()],
      providers: [{ provide: ActivityStreakService, useValue: { days } }],
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { nav: { streak: '{{count}}-day streak' } });
    translate.use('en');
  });

  it('is hidden without a streak', () => {
    expect(render().textContent?.trim()).toBe('');
  });

  it('shows the current streak', () => {
    days.set(4);

    expect(render().textContent).toContain('4-day streak');
  });

  it('updates when the streak changes', () => {
    const fixture = TestBed.createComponent(StreakBadgeComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent.trim()).toBe('');

    days.set(1);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('1-day streak');
  });
});
