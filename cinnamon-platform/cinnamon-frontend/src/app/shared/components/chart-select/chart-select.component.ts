import {Component, EventEmitter, Input, Output} from '@angular/core';
import {MatSelectChange} from "@angular/material/select";
import { DataType } from "../../model/data-type";

@Component({
    selector: 'app-chart-select',
    templateUrl: './chart-select.component.html',
    styleUrls: ['./chart-select.component.less'],
    standalone: false
})
export class ChartSelectComponent {
    @Input() dataType!: DataType;
    @Input() simple: boolean = false;
    @Input() graph!: string;
    @Output() graphChange: EventEmitter<string> = new EventEmitter<string>();

    protected update(v : MatSelectChange): void {
        this.graphChange.emit(v.value)
    }

    protected readonly DataType = DataType;
}
