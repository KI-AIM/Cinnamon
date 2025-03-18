import { Component, Input, } from '@angular/core';

@Component({
    selector: 'app-info-card',
    templateUrl: './info-card.component.html',
    styleUrls: ['./info-card.component.less'],
    standalone: false
})
export class InfoCardComponent {
  @Input() typeClass: string;
}
