import { Pipe, PipeTransform } from '@angular/core';
import {AttributeStatistics} from "../model/statistics";

@Pipe({
  name: 'statisticsFilter'
})
export class StatisticsFilterPipe implements PipeTransform {

  transform(value: {[attribute: string]: AttributeStatistics}, filterText: string): StatisticsFilterResult {
      const result = new StatisticsFilterResult();

      if (!value) {
          return result;
      }

      const values = Object.values(value);
      result.originalCount = values.length;

      if (!filterText) {
          result.filteredList = values;
          result.filteredCount = values.length;
      } else {
          const filtered = values.filter(attributeStatistic => {
              return attributeStatistic.attribute_information.name.includes(filterText);
          });

          result.filteredList = filtered;
          result.filteredCount = filtered.length
      }

      return result;
  }

}

export class StatisticsFilterResult {
    originalCount: number = 0;
    filteredCount: number = 0;
    filteredList: AttributeStatistics[];
}
