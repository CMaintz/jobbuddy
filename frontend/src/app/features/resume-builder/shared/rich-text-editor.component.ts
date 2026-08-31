import {
  Component, ElementRef, EventEmitter, Input, OnChanges, OnDestroy,
  AfterViewInit, Output, SimpleChanges, ViewChild, signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Editor, Extension } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import TextStyle from '@tiptap/extension-text-style';
import { Color } from '@tiptap/extension-color';
import TextAlign from '@tiptap/extension-text-align';
import FontFamily from '@tiptap/extension-font-family';
import { FONT_FAMILIES } from '../data/font-families';

/** Minimal font-size mark on top of TextStyle (TipTap v2 has no first-party one). */
const FontSize = Extension.create({
  name: 'fontSize',
  addOptions() {
    return { types: ['textStyle'] };
  },
  addGlobalAttributes() {
    return [{
      types: this.options['types'],
      attributes: {
        fontSize: {
          default: null,
          parseHTML: element => element.style.fontSize || null,
          renderHTML: attributes => attributes['fontSize']
            ? { style: `font-size: ${attributes['fontSize']}` }
            : {},
        },
      },
    }];
  },
});

const FONT_SIZES = ['10px', '11px', '12px', '13px', '14px', '16px', '18px', '21px', '24px'];

/**
 * Rich text field for resume content. Drop-in replacement for
 * app-debounced-textarea: same [value] / (debouncedChange) contract,
 * but the emitted value is HTML.
 */
@Component({
  selector: 'app-rich-text-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="rte" [class.rte-focused]="focused()">
      <div class="rte-toolbar" (mousedown)="$event.preventDefault()">
        <button type="button" class="rte-btn" [class.on]="isActive('bold')" title="Bold" (click)="toggle('bold')"><b>B</b></button>
        <button type="button" class="rte-btn" [class.on]="isActive('italic')" title="Italic" (click)="toggle('italic')"><i>I</i></button>
        <button type="button" class="rte-btn" [class.on]="isActive('underline')" title="Underline" (click)="toggle('underline')"><u>U</u></button>
        <button type="button" class="rte-btn" [class.on]="isActive('strike')" title="Strikethrough" (click)="toggle('strike')"><s>S</s></button>
        <span class="rte-sep"></span>
        <button type="button" class="rte-btn" [class.on]="isActive('bulletList')" title="Bullet list" (click)="toggle('bulletList')">•≡</button>
        <button type="button" class="rte-btn" [class.on]="isActive('orderedList')" title="Numbered list" (click)="toggle('orderedList')">1≡</button>
        <span class="rte-sep"></span>
        <button type="button" class="rte-btn" [class.on]="isAlign('left')" title="Align left" (click)="setAlign('left')">⇤</button>
        <button type="button" class="rte-btn" [class.on]="isAlign('center')" title="Center" (click)="setAlign('center')">↔</button>
        <button type="button" class="rte-btn" [class.on]="isAlign('right')" title="Align right" (click)="setAlign('right')">⇥</button>
        <button type="button" class="rte-btn" [class.on]="isAlign('justify')" title="Justify" (click)="setAlign('justify')">☰</button>
        <span class="rte-sep"></span>
        <select class="rte-select" title="Font" [ngModel]="currentFont()" (ngModelChange)="setFont($event)">
          <option value="">Font</option>
          @for (f of fonts; track f.value) {
            <option [value]="f.value">{{ f.label }}</option>
          }
        </select>
        <select class="rte-select rte-select-sm" title="Size" [ngModel]="currentSize()" (ngModelChange)="setSize($event)">
          <option value="">Size</option>
          @for (s of sizes; track s) {
            <option [value]="s">{{ s }}</option>
          }
        </select>
        <input type="color" class="rte-color" title="Text colour" [value]="currentColor()" (input)="setColor($any($event.target).value)" />
        <button type="button" class="rte-btn" title="Clear formatting" (click)="clearFormatting()">⌫</button>
      </div>
      <div #editorHost class="rte-content" [attr.data-placeholder]="placeholder"></div>
    </div>
  `,
  styles: [`
    .rte { border:1px solid var(--jb-border); border-radius:6px; background:var(--jb-surface-2); overflow:hidden; }
    .rte-focused { border-color:var(--jb-accent); box-shadow:0 0 0 2px var(--jb-accent-soft); }
    .rte-toolbar { display:flex; align-items:center; gap:2px; padding:4px 6px; border-bottom:1px solid var(--jb-border); flex-wrap:wrap; background:var(--jb-surface); }
    .rte-btn { min-width:24px; height:24px; padding:0 5px; border-radius:4px; border:1px solid transparent; background:transparent; color:var(--jb-text-mid); cursor:pointer; font-size:12px; line-height:1; }
    .rte-btn:hover { background:var(--jb-surface-2); color:var(--jb-text); }
    .rte-btn.on { background:var(--jb-accent-soft); border-color:var(--jb-accent-border); color:var(--jb-accent-2); }
    .rte-sep { width:1px; height:16px; background:var(--jb-border); margin:0 3px; }
    .rte-select { height:24px; max-width:96px; font-size:11px; border:1px solid var(--jb-border); border-radius:4px; background:var(--jb-surface-2); color:var(--jb-text); }
    .rte-select-sm { max-width:64px; }
    .rte-color { width:24px; height:24px; padding:1px; border:1px solid var(--jb-border); border-radius:4px; background:var(--jb-surface-2); cursor:pointer; }
    .rte-content { padding:8px 10px; min-height:72px; font-size:12.5px; color:var(--jb-text); }
    .rte-content .tiptap { outline:none; min-height:56px; }
    .rte-content .tiptap p { margin:0 0 4px; }
    .rte-content .tiptap ul, .rte-content .tiptap ol { margin:0 0 4px; padding-left:18px; }
    .rte-content .tiptap p.is-editor-empty:first-child::before {
      content: attr(data-placeholder); color: var(--jb-text-faint); float:left; height:0; pointer-events:none;
    }
  `],
})
export class RichTextEditorComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('editorHost', { static: true }) editorHost!: ElementRef<HTMLElement>;

  @Input() value = '';
  @Input() placeholder = '';
  @Input() debounceTime = 400;
  @Output() debouncedChange = new EventEmitter<string>();

  fonts = FONT_FAMILIES;
  sizes = FONT_SIZES;
  focused = signal(false);
  /** bumped on every editor transaction so toolbar state getters re-evaluate */
  private tick = signal(0);

  private editor: Editor | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private lastEmitted = '';

  ngAfterViewInit(): void {
    this.editor = new Editor({
      element: this.editorHost.nativeElement,
      extensions: [
        StarterKit.configure({ heading: false, codeBlock: false, blockquote: false, horizontalRule: false }),
        Underline,
        TextStyle,
        Color,
        FontFamily,
        FontSize,
        TextAlign.configure({ types: ['paragraph'] }),
      ],
      content: toHtml(this.value),
      onUpdate: ({ editor }) => this.scheduleEmit(editor.isEmpty ? '' : editor.getHTML()),
      onFocus: () => this.focused.set(true),
      onBlur: () => this.focused.set(false),
      onTransaction: () => this.tick.update(n => n + 1),
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['value'] && this.editor) {
      const incoming = toHtml(this.value);
      // Only reset content for external changes, not our own emissions echoed back
      if (this.value !== this.lastEmitted && incoming !== this.editor.getHTML()) {
        this.editor.commands.setContent(incoming, false);
      }
    }
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
    this.editor?.destroy();
  }

  private scheduleEmit(html: string): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.lastEmitted = html;
      this.debouncedChange.emit(html);
    }, this.debounceTime);
  }

  toggle(name: 'bold' | 'italic' | 'underline' | 'strike' | 'bulletList' | 'orderedList'): void {
    if (!this.editor) return;
    const chain = this.editor.chain().focus();
    switch (name) {
      case 'bold': chain.toggleBold().run(); break;
      case 'italic': chain.toggleItalic().run(); break;
      case 'underline': chain.toggleUnderline().run(); break;
      case 'strike': chain.toggleStrike().run(); break;
      case 'bulletList': chain.toggleBulletList().run(); break;
      case 'orderedList': chain.toggleOrderedList().run(); break;
    }
  }

  setAlign(align: 'left' | 'center' | 'right' | 'justify'): void {
    this.editor?.chain().focus().setTextAlign(align).run();
  }

  isActive(name: string): boolean {
    this.tick();
    return this.editor?.isActive(name) ?? false;
  }

  isAlign(align: string): boolean {
    this.tick();
    return this.editor?.isActive({ textAlign: align }) ?? false;
  }

  currentFont(): string {
    this.tick();
    return this.editor?.getAttributes('textStyle')['fontFamily'] ?? '';
  }

  currentSize(): string {
    this.tick();
    return this.editor?.getAttributes('textStyle')['fontSize'] ?? '';
  }

  currentColor(): string {
    this.tick();
    return this.editor?.getAttributes('textStyle')['color'] ?? '#1f2937';
  }

  setFont(font: string): void {
    if (!this.editor) return;
    const chain = this.editor.chain().focus();
    (font ? chain.setFontFamily(font) : chain.unsetFontFamily()).run();
  }

  setSize(size: string): void {
    if (!this.editor) return;
    const chain = this.editor.chain().focus();
    (size
      ? chain.setMark('textStyle', { fontSize: size })
      : chain.setMark('textStyle', { fontSize: null })).run();
  }

  setColor(color: string): void {
    this.editor?.chain().focus().setColor(color).run();
  }

  clearFormatting(): void {
    this.editor?.chain().focus().unsetAllMarks().setTextAlign('left').run();
  }
}

/** Generated content arrives as plain text — wrap lines in paragraphs so TipTap accepts it. */
export function toHtml(value: string): string {
  if (!value) return '';
  if (value.includes('<')) return value;
  return value.split(/\n/).map(line => `<p>${escapeHtml(line)}</p>`).join('');
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
