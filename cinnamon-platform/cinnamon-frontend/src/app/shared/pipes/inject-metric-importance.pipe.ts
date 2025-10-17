import { Pipe, PipeTransform } from '@angular/core';
import { MetricImportanceData, MetricSettings } from "@shared/model/project-settings";
import { StatisticsValueTypes } from "@shared/model/statistics";

/**
 * Injects the metric importance as configured in the project settings to the given statistics.
 *
 * @author Daniel Preciado-Marquez
 */
@Pipe({
  name: 'injectMetricImportance',
  standalone: false
})
export class InjectMetricImportancePipe implements PipeTransform {

  public transform(input: Array<[string, StatisticsValueTypes]>, config: MetricSettings): Array<[string, StatisticsValueTypes, number]> {
      return input.map(value => {
          return [value[0], value[1], MetricImportanceData[config.userDefinedImportance[value[0]]].value];
      });
  }

}
