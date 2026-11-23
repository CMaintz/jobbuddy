import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { FunnelStage } from '../../utils/application-insights';

@Component({
  selector: 'jb-funnel',
  imports: [TranslateModule],
  templateUrl: './funnel.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./funnel.component.css']
})
export class FunnelComponent {
  @Input() stages: FunnelStage[] = [
    { labelKey: 'pipeline.stage.saved', value: 42, color: 'var(--jb-text-dim)' },
    { labelKey: 'pipeline.stage.applied', value: 38, color: 'var(--jb-info)' },
    { labelKey: 'pipeline.stage.screen', value: 12, color: 'var(--jb-violet)' },
    { labelKey: 'pipeline.stage.interview', value: 6, color: 'var(--jb-accent)' },
    { labelKey: 'pipeline.stage.offer', value: 2, color: 'var(--jb-success)' },
  ];

  get maxVal(): number {
    return Math.max(...this.stages.map(s => s.value), 1);
  }
}
