import { Component, EventEmitter, Input, Output, } from '@angular/core';

@Component({
    selector: 'app-info-card',
    templateUrl: './info-card.component.html',
    styleUrls: ['./info-card.component.less'],
    standalone: false
})
export class InfoCardComponent {
    @Input() closable: boolean = false;
    /**
     * Defines the color of the card.
     * Allowed values are: card-success, card-warn, card-failure
     */
    @Input() typeClass: string;

    @Output() public onClose: EventEmitter<void> = new EventEmitter();

    protected close(): void {
        this.onClose.emit();
    }

}
