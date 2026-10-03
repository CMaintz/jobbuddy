import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-skill-chip-list',
  imports: [],
  templateUrl: './skill-chip-list.component.html',
})
export class SkillChipListComponent {
  @Input() skills: string[] = [];
}
