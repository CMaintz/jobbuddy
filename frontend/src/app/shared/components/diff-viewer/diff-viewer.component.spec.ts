import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { DiffViewerComponent } from './diff-viewer.component';

@Component({
  imports: [DiffViewerComponent],
  template: `<jb-diff-viewer [before]="before()" [after]="after()" />`,
})
class HostComponent {
  before = signal('The quick fox');
  after = signal('The quick fox');
}

/**
 * The hosts feed the viewer from text the user keeps editing (the settings style fields,
 * a refine preview), so the diff has to follow its inputs rather than the first pair it saw.
 */
describe('DiffViewerComponent', () => {
  it('recomputes the diff when its inputs change', () => {
    TestBed.configureTestingModule({ imports: [HostComponent, TranslateModule.forRoot()] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    fixture.componentInstance.after.set('The quick brown fox');
    fixture.detectChanges();

    const added = fixture.nativeElement.querySelector('.text-jb-success.rounded-sm') as HTMLElement | null;
    expect(added?.textContent).toContain('brown');
  });
});
