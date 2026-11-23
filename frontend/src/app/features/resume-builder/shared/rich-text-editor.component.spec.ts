import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { RichTextEditorComponent } from './rich-text-editor.component';

/** Saved CV HTML has to survive a load and an edit unchanged in the marks the toolbar offers. */
describe('RichTextEditorComponent', () => {
  const STORED = '<p><u>Under</u> <span style="color: rgb(255, 0, 0); font-size: 14px; font-family: Georgia">styled</span></p>';

  async function mount(value: string) {
    await TestBed.configureTestingModule({
      imports: [RichTextEditorComponent, TranslateModule.forRoot()],
    }).compileComponents();
    const fixture = TestBed.createComponent(RichTextEditorComponent);
    fixture.componentInstance.value = value;
    fixture.componentInstance.debounceTime = 0;
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('keeps underline, colour, font size and font family from stored HTML', async () => {
    const fixture = await mount(STORED);
    const html = (fixture.nativeElement as HTMLElement).querySelector('.tiptap')!.innerHTML;

    expect(html).toContain('<u>Under</u>');
    expect(html).toContain('font-size: 14px');
    expect(html).toContain('color: rgb(255, 0, 0)');
    expect(html).toContain('font-family: Georgia');
  });

  it('emits the edited HTML with the toggled mark', async () => {
    const fixture = await mount('<p>plain</p>');
    const emitted: string[] = [];
    fixture.componentInstance.debouncedChange.subscribe(v => emitted.push(v));
    const editor = (fixture.componentInstance as unknown as { editor: { commands: { selectAll(): void } } }).editor;
    editor.commands.selectAll();

    fixture.componentInstance.toggle('underline');
    await new Promise(resolve => setTimeout(resolve, 10));

    expect(emitted.at(-1)).toBe('<p><u>plain</u></p>');
  });
});
