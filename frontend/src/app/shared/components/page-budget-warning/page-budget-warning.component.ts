import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { PageBudget } from '../../page-budget';
import { JbIconComponent } from '../jb-icon/jb-icon.component';

@Component({
  selector: 'jb-page-budget-warning',
  imports: [TranslateModule, JbIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (budget(); as b) {
      @if (b.overBudget) {
        <div role="status"
          class="flex items-start gap-2 px-3 py-2 text-sm rounded-md border border-jb-accent-border bg-jb-accent-soft text-jb-text">
          <jb-icon name="doc" [size]="14" class="text-jb-accent mt-0.5 flex-shrink-0" />
          <span>
            <span class="block font-medium">{{ 'pageBudget.' + b.kind + '.over' | translate:{ pages: b.pageCount } }}</span>
            <span class="block text-jb-text-dim">{{ 'pageBudget.' + b.kind + '.shorten' | translate }}</span>
          </span>
        </div>
      }
    }
  `,
})
export class PageBudgetWarningComponent {
  readonly budget = input<PageBudget | null>(null);
}
