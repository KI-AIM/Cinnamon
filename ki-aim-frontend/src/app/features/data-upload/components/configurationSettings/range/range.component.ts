import { Component, Input } from '@angular/core';
import { DataType } from 'src/app/shared/model/data-type';
import { RangeConfiguration } from 'src/app/shared/model/range-configuration';

@Component({
  selector: 'app-range',
  templateUrl: './range.component.html',
  styleUrls: ['./range.component.less']
})
export class RangeComponent {
    rangeConfiguration: RangeConfiguration = new RangeConfiguration();
    dataType: typeof DataType = DataType;

    @Input() type: DataType;

    getRangeConfiguration() : RangeConfiguration {
      return this.rangeConfiguration;
    }

    getInputType() : string {
      switch (+(DataType[this.type])) {
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
