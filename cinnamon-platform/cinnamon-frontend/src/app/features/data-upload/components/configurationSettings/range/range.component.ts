import { Component, Input } from '@angular/core';
import { MatDialog } from "@angular/material/dialog";
import { DataType } from 'src/app/shared/model/data-type';
import { FormGroup } from "@angular/forms";

@Component({
    selector: 'app-range',
    templateUrl: './range.component.html',
    styleUrls: ['./range.component.less'],
    standalone: false
})
export class RangeComponent {
    @Input() type: DataType;
    @Input() form: FormGroup;

    public constructor(
        protected readonly dialog: MatDialog,
    ) {
    }

    getInputType() : string {
      switch (this.type) {
        case DataType.DECIMAL:
        case DataType.INTEGER: {
          return "number";
        }
        case DataType.DATE: {
          return "date";
        }
        case DataType.DATE_TIME: {
          return "datetime-local";
        }
        default: {
          return "text";
        }
      }
    }
}
