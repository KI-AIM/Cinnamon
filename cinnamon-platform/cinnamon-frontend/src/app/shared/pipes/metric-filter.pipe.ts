import { Pipe, PipeTransform } from '@angular/core';
import { StatisticsValueTypes } from "../model/statistics";
import { MetricImportance, MetricImportanceData } from "../model/project-settings";

@Pipe({
    name: 'metricFilter',
    pure: false,
    standalone: false
})
export class MetricFilterPipe implements PipeTransform {

    transform(value: Array<[string, StatisticsValueTypes, number]>,
              filterText: string | null | undefined,
              importance: MetricImportance | null | undefined): Array<[string, StatisticsValueTypes, number]> {
        if (!filterText && !importance) {
            return value;
        } else if (!importance) {
            return value.filter(val => {
                return val[1].display_name.toLowerCase().includes(filterText!.trim().toLowerCase());
            });
        } else if (!filterText) {
            return value.filter(val => {
                return val[2] == MetricImportanceData[importance].value;
            });
        } else {
            return value.filter(val => {
                return val[1].display_name.toLowerCase().includes(filterText.trim().toLowerCase()) && val[2] == MetricImportanceData[importance].value;
            });
        }
    }

}
