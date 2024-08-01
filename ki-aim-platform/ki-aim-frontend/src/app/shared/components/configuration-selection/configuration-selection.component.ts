import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { MatSelectChange } from "@angular/material/select";
import { NgModel } from "@angular/forms";
import { Algorithm } from "../../model/algorithm";

@Component({
  selector: 'app-configuration-selection',
  templateUrl: './configuration-selection.component.html',
  styleUrls: ['./configuration-selection.component.less']
})
export class ConfigurationSelectionComponent {
    @Input() public algorithms!: Algorithm[]
    @Output() public change = new EventEmitter<Algorithm>();

    @ViewChild('selectElement') protected selectElement: NgModel;

    onChange(event: MatSelectChange) {
        this.change.emit(event.value);
    }

    public get selectedOption(): Algorithm {
        return this.selectElement?.value;
    }
}
