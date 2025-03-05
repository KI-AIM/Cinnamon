import { Pipe, PipeTransform } from '@angular/core';
import { StatisticsValues, StatisticsValueTypes } from '../model/statistics';
import { SortDirection, SortType } from "../model/metric-table-data";

@Pipe({
    name: 'metricSorter',
    pure: false
})
export class MetricSorterPipe implements PipeTransform {

    transform(value: Array<[string, StatisticsValueTypes, number]>, sortDirection: SortDirection | null, sortType: SortType | null): Array<[string, StatisticsValueTypes, number]> {
        let sorted: Array<[string, StatisticsValueTypes, number]>;

        if (sortDirection === null || sortType === null || sortDirection === 'original') {
            sorted = value.sort((a, b) => {
                return b[2] - a[2];
            });
        } else {
            const direction = sortDirection === 'asc' ? 1 : -1;

            if (sortType === 'metric') {
                sorted = value.sort((a, b) => {
                    return direction * a[1].display_name.localeCompare(b[1].display_name);
                });
            } else if (sortType === 'colorIndex') {
                sorted = value.sort((a, b) => {
                    return direction * (this.getColorIndex(a[1]) - this.getColorIndex(b[1]));
                });
            } else if (sortType === 'absolute' || sortType === 'percentage') {
                sorted = value.sort((a, b) => {
                    return direction * (this.getDifference(a[1], sortType) - this.getDifference(b[1], sortType));
                });
            } else {
                sorted = value;
            }
        }

        return sorted;
    }

    private getColorIndex(data: StatisticsValueTypes): number {
        if (data instanceof StatisticsValues) {
            return data.difference.color_index;
        } else {
            return Math.max(data.values.real.color_index, data.values.synthetic.color_index);
        }
    }

    private getDifference(data: StatisticsValueTypes, which: 'absolute' | 'percentage'): number  {
        if (data instanceof StatisticsValues) {
            return data.difference[which];
        } else {
            return 0;
        }
    }

}

