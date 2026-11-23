import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-skill-chip-list',
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './skill-chip-list.component.html',
})
export class SkillChipListComponent {
  @Input() skills: string[] = [];
}
