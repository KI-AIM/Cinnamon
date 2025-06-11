import { Pipe, PipeTransform } from '@angular/core';
import { SortDirection } from "src/app/shared/model/metric-table-data";
import { AttributeStatistics } from "src/app/shared/model/statistics";

@Pipe({
    name: 'statisticsSorter'
})
export class StatisticsSorterPipe implements PipeTransform {

    public transform(value: Array<AttributeStatistics>, sortDirection: SortDirection | null, sortType: StatisticsSortType | null): Array<AttributeStatistics> {
        let sorted: Array<AttributeStatistics>;

        if (sortDirection === null || sortType === null || sortDirection === 'original') {
            sorted = value;
        } else {
            const direction = sortDirection === 'asc' ? 1 : -1;

            if (sortType === 'name') {
                sorted = value.sort((a, b) => {
                    return direction * a.attribute_information.name.localeCompare(b.attribute_information.name);
                });
            } else if (sortType === 'index') {
                sorted = value.sort((a, b) => {
                    return direction * (a.attribute_information.index - b.attribute_information.index);
                });
            } else if (sortType === 'score' && value[0].overview != null) {
                sorted = value.sort((a, b) => {
                    return direction * (a.overview!.resemblance_score.value - b.overview!.resemblance_score.value);
                });
            } else {
                sorted = value;
            }
        }

        return sorted;
    }

}

export type StatisticsSortType = 'name' | 'index' | 'score';
