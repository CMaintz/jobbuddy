import { Component, Input, ChangeDetectionStrategy } from '@angular/core';

@Component({
  selector: 'jb-fit-bar',
  templateUrl: './fit-bar.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./fit-bar.component.css']
})
export class FitBarComponent {
  @Input() value = 0;
}
