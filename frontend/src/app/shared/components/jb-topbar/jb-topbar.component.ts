import { Component, Input } from '@angular/core';

/**
 * The standard screen topbar strip: title on the left, actions on the right.
 * Extra left-side content (pills, search) goes in the default slot.
 *
 * ```html
 * <jb-topbar title="Tasks">
 *   <jb-pill>3 open</jb-pill>
 *   <ng-container jb-topbar-actions>
 *     <jb-button …>Add follow-up</jb-button>
 *   </ng-container>
 * </jb-topbar>
 * ```
 */
@Component({
  selector: 'jb-topbar',
  imports: [],
  template: `
    <div class="h-11 flex items-center px-4 border-b border-jb-border bg-jb-bg flex-[0_0_auto] gap-3">
      @if (title) {
        <div class="font-medium text-md">{{ title }}</div>
      }
      <ng-content />
      <div class="ml-auto flex gap-2 items-center">
        <ng-content select="[jb-topbar-actions]" />
      </div>
    </div>
  `,
})
export class JbTopbarComponent {
  @Input() title = '';
}
