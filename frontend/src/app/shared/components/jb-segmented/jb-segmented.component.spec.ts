import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JbSegmentedComponent } from './jb-segmented.component';

describe('JbSegmentedComponent', () => {
  let fixture: ComponentFixture<JbSegmentedComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [JbSegmentedComponent] });
    fixture = TestBed.createComponent(JbSegmentedComponent);
    fixture.componentRef.setInput('options', ['Week', 'Month', 'Year']);
    fixture.componentRef.setInput('value', 'Month');
    fixture.detectChanges();
  });

  function buttons(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('button'));
  }

  it('renders one button per option, in order', () => {
    expect(buttons().map(b => b.textContent?.trim())).toEqual(['Week', 'Month', 'Year']);
  });

  it('highlights only the selected option', () => {
    const highlighted = buttons().map(b => b.style.background === 'var(--jb-surface)');
    expect(highlighted).toEqual([false, true, false]);
  });

  it('emits the clicked option without changing its own value', () => {
    const emitted: string[] = [];
    fixture.componentInstance.changed.subscribe(v => emitted.push(v));

    buttons()[2].click();
    fixture.detectChanges();

    expect(emitted).toEqual(['Year']);
    expect(buttons()[1].style.background).toBe('var(--jb-surface)');
  });
});
