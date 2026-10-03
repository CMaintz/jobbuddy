import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'app-skill-chip-list',
  imports: [],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './skill-chip-list.component.html',
})
export class SkillChipListComponent {
  @Input() skills: string[] = [];
}
