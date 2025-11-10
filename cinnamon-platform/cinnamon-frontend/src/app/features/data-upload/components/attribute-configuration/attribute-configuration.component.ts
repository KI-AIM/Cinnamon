import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { FileType } from "@shared/model/file-configuration";
import { List } from 'src/app/core/utils/list';
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
    @Input() public confidence : number | null = null;
    @Input() public fileType!: FileType | null;

    @Output() onInput = new EventEmitter<any>();

    protected readonly FileType = FileType;

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
        return new List<String>(Object.keys(DataType));
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
