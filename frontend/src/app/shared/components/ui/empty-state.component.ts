import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-empty-state',
  imports: [TranslateModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './empty-state.component.html'
})
export class EmptyStateComponent {
  @Input({ required: true }) message = '';
}
