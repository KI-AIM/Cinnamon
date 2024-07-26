import { AfterViewInit, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { NgModel } from '@angular/forms';
import { List } from 'src/app/core/utils/list';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { DataScale } from 'src/app/shared/model/data-scale';
import { DataType } from 'src/app/shared/model/data-type';

@Component({
    selector: 'app-attribute-configuration',
    templateUrl: './attribute-configuration.component.html',
    styleUrls: ['./attribute-configuration.component.less'],
})
export class AttributeConfigurationComponent implements AfterViewInit {
    @Input() attrNumber: String;
    @Input() column: ColumnConfiguration;
    @Input() disabled: boolean = false;

    @Output() onValidation = new EventEmitter<boolean>();
    @Output() onInput = new EventEmitter<any>();

    @ViewChild("name") nameInput: NgModel;
    private oldName: string = "";

    constructor() { }

    ngAfterViewInit(): void {
        this.nameInput.statusChanges?.subscribe(() => {
            this.onValidation.emit(this.nameInput.valid ?? true)
        });
        this.nameInput.valueChanges?.subscribe((newValue) => {
            // Trigger validation when the model has been changed programmatically
            if (this.oldName !== newValue) {
                this.oldName = newValue;
                this.onInput.emit();
                this.nameInput.control.markAsTouched();
            }
        });
    }

    getDataTypes(): List<String> {
        const types = Object.keys(DataType).filter(x => !(parseInt(x) >= 0));

        return new List<String>(types);
    }

    getDataScales(): List<String> {
        const scales = Object.keys(DataScale).filter(x => !(parseInt(x) >= 0));

        return new List<String>(scales);
    }

    protected parseInt(value: String): Number {
        return Number(value);
    }
}
