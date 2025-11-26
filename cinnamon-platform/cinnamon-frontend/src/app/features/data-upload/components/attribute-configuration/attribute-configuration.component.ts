import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatDialog } from "@angular/material/dialog";
import { FileType } from "@shared/model/file-configuration";
import { DataScaleMetadata } from 'src/app/shared/model/data-scale';
import { DataTypeMetadata } from 'src/app/shared/model/data-type';

@Component({
    selector: 'app-attribute-configuration',
    templateUrl: './attribute-configuration.component.html',
    styleUrls: ['./attribute-configuration.component.less'],
    standalone: false
})
export class AttributeConfigurationComponent implements AfterViewInit {
    @Input() attrNumber!: number;
    @Input() disabled: boolean = false;
    @Input() public columnConfigurationForm!: FormGroup;
    @Input() public confidence : number | null = null;
    @Input() public fileType!: FileType | null;

    @Output() onInput = new EventEmitter<any>();

    protected readonly FileType = FileType;

    private oldName: string = "";

    protected readonly DataScaleMetadata = DataScaleMetadata;
    protected readonly DataTypeMetadata = DataTypeMetadata;

    constructor(
        protected dialog: MatDialog
    ) { }

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

    protected trimValue(field: string) {
        const originalValue = this.columnConfigurationForm.controls[field].value;
        const trimmedValue = originalValue.trim();
        if (originalValue !== trimmedValue) {
            this.columnConfigurationForm.controls[field].patchValue(trimmedValue);
        }
    }

}
