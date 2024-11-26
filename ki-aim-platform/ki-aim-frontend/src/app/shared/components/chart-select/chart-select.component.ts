import {Component, EventEmitter, Input, Output} from '@angular/core';
import {MatSelectChange} from "@angular/material/select";

@Component({
  selector: 'app-chart-select',
  templateUrl: './chart-select.component.html',
  styleUrls: ['./chart-select.component.less']
})
export class ChartSelectComponent {
    @Input() simple: boolean = false;
    @Input() graph!: string;
    @Output() graphChange: EventEmitter<string> = new EventEmitter<string>();

    protected update(v : MatSelectChange): void {
        this.graphChange.emit(v.value)
    }
}
