import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skill-chip-list',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './skill-chip-list.component.html',
})
export class SkillChipListComponent {
  @Input() skills: string[] = [];
}
