import { Component, EventEmitter, Input, Output, ChangeDetectionStrategy } from '@angular/core';

@Component({
    selector: 'app-info-card',
    templateUrl: './info-card.component.html',
    styleUrls: ['./info-card.component.less'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class InfoCardComponent {
    @Input() closable: boolean = false;
    /**
     * Defines the color of the card.
     * Allowed values are: card-success, card-warn, card-failure
     */
    @Input({required: true}) typeClass!: 'card-success' | 'card-warn' | 'card-failure' | string;

    @Output() public onClose: EventEmitter<void> = new EventEmitter();

    protected close(): void {
        this.onClose.emit();
    }

}
