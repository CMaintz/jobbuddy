import { Component, Input } from '@angular/core';

@Component({
  selector: 'jb-company-mark',
  standalone: true,
  templateUrl: './company-mark.component.html',
  styleUrls: ['./company-mark.component.css']
})
export class CompanyMarkComponent {
  @Input() name = '';
  @Input() size = 28;

  get initials(): string {
    return this.name.substring(0, 2);
  }

  get bgColor(): string {
    const colors = ['#7c3aed','#2563eb','#059669','#d97706','#dc2626','#7950f2','#0891b2','#be185d'];
    let hash = 0;
    for (let i = 0; i < this.name.length; i++) hash = this.name.charCodeAt(i) + ((hash << 5) - hash);
    return colors[Math.abs(hash) % colors.length];
  }
}
