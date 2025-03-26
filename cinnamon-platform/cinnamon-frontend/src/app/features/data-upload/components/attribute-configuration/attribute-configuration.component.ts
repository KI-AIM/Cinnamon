import { AfterViewInit, Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormGroup, NgModel } from '@angular/forms';
import { List } from 'src/app/core/utils/list';
import { ColumnConfiguration } from 'src/app/shared/model/column-configuration';
import { DataScale } from 'src/app/shared/model/data-scale';
import { DataType } from 'src/app/shared/model/data-type';

@Component({
    selector: 'app-attribute-configuration',
    templateUrl: './attribute-configuration.component.html',
    styleUrls: ['./attribute-configuration.component.less'],
    standalone: false
})
export class AttributeConfigurationComponent implements AfterViewInit {
    @Input() attrNumber: String;
    @Input() disabled: boolean = false;
    @Input() public columnConfigurationForm!: FormGroup;

    @Output() onInput = new EventEmitter<any>();

    private oldName: string = "";

    constructor() { }

    ngAfterViewInit(): void {
        this.columnConfigurationForm.controls['name'].valueChanges.subscribe((newValue) => {
            // Trigger validation when the model has been changed programmatically
            if (this.oldName !== newValue) {
                this.oldName = newValue;
                this.onInput.emit();
                this.columnConfigurationForm.controls['name'].markAsTouched();
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

    protected trimValue(field: string) {
        const originalValue = this.columnConfigurationForm.controls[field].value;
        const trimmedValue = originalValue.trim();
        if (originalValue !== trimmedValue) {
            this.columnConfigurationForm.controls[field].patchValue(trimmedValue);
        }
    }
}
