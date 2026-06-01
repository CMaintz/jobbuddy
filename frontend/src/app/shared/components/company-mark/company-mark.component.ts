import { Component, Input } from '@angular/core';

@Component({
  selector: 'jb-company-mark',
  standalone: true,
  template: `
    <div [style.width.px]="size" [style.height.px]="size" [style.border-radius.px]="size > 24 ? 8 : 5"
      [style.background]="bgColor" [style.font-size.px]="size * 0.45"
      class="flex items-center justify-center font-semibold text-white shrink-0 uppercase tracking-wide">
      {{ initials }}
    </div>
  `,
  styles: [`:host { @apply inline-flex; }`]
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
