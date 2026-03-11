import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats the confidence interval.
 */
@Pipe({
  name: 'riskConfidenceInterval',
  standalone: false
})
export class RiskConfidenceIntervalPipe implements PipeTransform {
  public transform(interval?: [number, number]): string {
      if (!interval || interval.length !== 2) {
          return 'N/A';
      }
      return `[${interval[0].toFixed(2)}, ${interval[1].toFixed(2)}]`;
  }
}
