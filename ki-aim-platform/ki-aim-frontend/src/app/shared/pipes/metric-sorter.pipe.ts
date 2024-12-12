import { Pipe, PipeTransform } from '@angular/core';
import {StatisticsValues, StatisticsValueTypes} from '../model/statistics';

@Pipe({
    name: 'metricSorter'
})
export class MetricSorterPipe implements PipeTransform {

    transform(value: Array<StatisticsValueTypes>, sortDirection: SortDirection | null, sortType: SortType | null): Array<StatisticsValueTypes> {
        if (sortDirection === null || sortType === null || sortDirection === 'original') {
            return value;
        }

       const direction = sortDirection === 'asc' ? 1 : -1;

        if (sortType === 'metric') {
            return value.sort((a, b) => {
                return direction * a.display_name.localeCompare(b.display_name);
            });
        } else if (sortType === 'colorIndex') {
            return value.sort((a, b) => {
                return direction * (this.getColorIndex(a) - this.getColorIndex(b));
            });
        } else if (sortType === 'absolute' || sortType === 'percentage') {
            return value.sort((a, b) => {
                return direction * (this.getDifference(a, sortType) - this.getDifference(b, sortType));
            });
        } else {
            return value;
        }
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

export type SortDirection = 'asc' | 'desc' | 'original';
export type SortType = 'metric' | 'colorIndex' | 'absolute' | 'percentage';
