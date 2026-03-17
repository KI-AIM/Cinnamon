import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormArray, FormGroup } from '@angular/forms';
import { MatDialog } from "@angular/material/dialog";
import { FileType } from "@shared/model/file-configuration";
import { DataScale, DataScaleMetadata } from 'src/app/shared/model/data-scale';
import { DataType, DataTypeMetadata } from 'src/app/shared/model/data-type';

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

    protected readonly DataScale = DataScale;
    protected readonly DataType = DataType;
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

        this.columnConfigurationForm.controls['type'].valueChanges.subscribe((newValue) => {
            // Clear values of RangeConfiguration when the data type changes
            const additionalConfigurations = this.columnConfigurationForm.controls['configurations'] as FormArray;
            for (const config of additionalConfigurations.controls) {
                const name = config.get('name')?.value;
                if (name === "RangeConfiguration") {
                    const oldMinValue = config.get('minValue')?.value;
                    if (((newValue === DataType.INTEGER || newValue === DataType.DECIMAL) && !this.isNumeric(oldMinValue)) ||
                        newValue === DataType.DATE || newValue === DataType.DATE_TIME) {
                        config.get('minValue')?.setValue(null);
                        config.get('minValue')?.markAsTouched();
                    }

                    const oldMaxValue = config.get('maxValue')?.value;
                    if (((newValue === DataType.INTEGER || newValue === DataType.DECIMAL) && !this.isNumeric(oldMaxValue)) ||
                        newValue === DataType.DATE || newValue === DataType.DATE_TIME) {
                        config.get('maxValue')?.setValue(null);
                        config.get('maxValue')?.markAsTouched();
                    }
                }
            }
        });
    }

    /**
     * Checks if the given value is numeric.
     * @param value The value to check.
     * @returns True if the value is numeric, false otherwise.
     */
    private isNumeric(value: unknown): boolean {
        if (typeof value !== 'string') {
            return false;
        }

        const trimmed = value.trim();
        return /^[+-]?(?:\d+\.?\d*|\.\d+)$/.test(trimmed);
    }

}
