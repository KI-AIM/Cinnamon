import { AfterViewInit, Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormGroup } from '@angular/forms';
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

    @Output() onInput = new EventEmitter<any>();

    @ViewChild('tooltip') private tooltip!: ElementRef;
    @ViewChild('tooltipArrow') private tooltipArrow!: ElementRef;

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

    /**
     * Opens the confidence tooltip.
     * Modifies the position to ensure the tooltip is in view.
     * @protected
     */
    protected openTooltip(): void {
        this.tooltip.nativeElement.classList.add("shown");

        const rect = this.tooltip.nativeElement.getBoundingClientRect();
        const maxArrowOffset = (rect.height - 17) / 2;
        const topOffset = 65 - rect.top;
        const bottomOffset = (window.outerHeight - 125) - rect.bottom;

        if (topOffset > 0) {
            this.tooltip.nativeElement.style.transform = `translateY(-50%) translateY(${topOffset}px)`;
            this.tooltipArrow.nativeElement.style.transform = `translateY(-${Math.min(maxArrowOffset, topOffset)}px)`;
        } else if (bottomOffset < 0) {
            this.tooltip.nativeElement.style.transform = `translateY(-50%) translateY(${bottomOffset}px)`;
            this.tooltipArrow.nativeElement.style.transform = `translateY(${Math.min(maxArrowOffset, -bottomOffset)}px)`;
        }
    }

    /**
     * Closes the tooltip and resets its position, so the translation can be calculated correctly when opening it again.
     * @protected
     */
    protected closeTooltip(): void {
        this.tooltip.nativeElement.classList.remove("shown");
        this.tooltip.nativeElement.style.transform = `translateY(-50%)`;
        this.tooltipArrow.nativeElement.style.transform = "";
    }
}
