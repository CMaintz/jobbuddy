import { Component, HostBinding, Input, Output, EventEmitter, booleanAttribute } from '@angular/core';
import { CommonModule } from '@angular/common';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-button',
  standalone: true,
  imports: [CommonModule, JbIconComponent],
  templateUrl: './jb-button.component.html',
  styleUrls: ['./jb-button.component.css']
})
export class JbButtonComponent {
  @Input() icon?: string;
  @Input({ transform: booleanAttribute }) primary = false;
  @Input({ transform: booleanAttribute }) ghost = false;
  @Input({ transform: booleanAttribute }) small = false;
  @Input({ transform: booleanAttribute }) disabled = false;
  /** Full-width call-to-action variant. */
  @Input({ transform: booleanAttribute }) block = false;
  @Output() clicked = new EventEmitter<MouseEvent>();

  @HostBinding('class.w-full') get hostBlock() { return this.block; }

  private static readonly BASE = 'inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-sm font-medium cursor-pointer disabled:opacity-50 disabled:cursor-default';
  private static readonly DEFAULT = 'jb-btn bg-jb-surface-2 border border-jb-border text-jb-text';
  private static readonly PRIMARY = 'jb-btn-primary bg-jb-accent border';
  private static readonly GHOST = 'jb-btn-ghost bg-transparent border border-transparent text-jb-text-mid';
  private static readonly SM = 'jb-btn-sm py-1 px-2 text-[11.5px]';

  get classes(): string {
    let variant = JbButtonComponent.DEFAULT;
    if (this.primary) variant = JbButtonComponent.PRIMARY;
    else if (this.ghost) variant = JbButtonComponent.GHOST;
    const parts = [JbButtonComponent.BASE, variant];
    if (this.small) parts.push(JbButtonComponent.SM);
    if (this.block) parts.push('w-full justify-center');
    return parts.join(' ');
  }
}
