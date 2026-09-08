import { Component, Input } from '@angular/core';

@Component({
  selector: 'jb-fit-bar',
  standalone: true,
  templateUrl: './fit-bar.component.html',
  styleUrls: ['./fit-bar.component.css']
})
export class FitBarComponent {
  @Input() value = 0;
}
