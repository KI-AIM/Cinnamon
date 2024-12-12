import { Pipe, PipeTransform } from '@angular/core';
import {DetailMetrics, StatisticsValues, StatisticsValuesNominal, StatisticsValueTypes} from "../model/statistics";

@Pipe({
    name: 'metricFilter'
})
export class MetricFilterPipe implements PipeTransform {

    transform(value: Array<StatisticsValueTypes>, filterText: string): Array<StatisticsValueTypes> {
        if (!filterText) {
            return value;
        }

        return value.filter(val => {
            return val.display_name.toLowerCase().includes(filterText.toLowerCase());
        });
    }

}
