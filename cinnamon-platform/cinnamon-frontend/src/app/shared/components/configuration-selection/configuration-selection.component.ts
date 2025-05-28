import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MatSelect, MatSelectChange } from "@angular/material/select";
import { Algorithm } from "../../model/algorithm";

@Component({
    selector: 'app-configuration-selection',
    templateUrl: './configuration-selection.component.html',
    styleUrls: ['./configuration-selection.component.less'],
    standalone: false
})
export class ConfigurationSelectionComponent {
    @Input() public algorithms!: Algorithm[]
    @Input() public initialValue!: Algorithm | null;
    @Input() public disabled!: boolean;
    @Output() public change = new EventEmitter<Algorithm>();

    @ViewChild('selectElement') protected selectElement!: MatSelect;

    onChange(event: MatSelectChange) {
        this.change.emit(event.value);
    }

    public get selectedOption(): Algorithm {
        return this.selectElement?.value;
    }

    public set selectedOption(value: Algorithm) {
        this.selectElement.value = value;
    }
}
