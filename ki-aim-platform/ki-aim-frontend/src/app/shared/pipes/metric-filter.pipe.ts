import { Pipe, PipeTransform } from '@angular/core';
import { DetailMetrics, StatisticsValues, StatisticsValuesNominal } from "../model/statistics";

@Pipe({
    name: 'metricFilter'
})
export class MetricFilterPipe implements PipeTransform {

    transform(value: Array<StatisticsValues | StatisticsValuesNominal<any>>, filterText: string): Array<StatisticsValues | StatisticsValuesNominal<any>> {
        if (!filterText) {
            return value;
        }

        return value.filter(val => {
            return val.display_name.toLowerCase().includes(filterText.toLowerCase());
        });
    }

}
