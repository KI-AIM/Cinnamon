import { Component, Input, } from '@angular/core';

@Component({
  selector: 'app-info-card',
  templateUrl: './info-card.component.html',
  styleUrls: ['./info-card.component.less']
})
export class InfoCardComponent {
  @Input() typeClass: string;
}
