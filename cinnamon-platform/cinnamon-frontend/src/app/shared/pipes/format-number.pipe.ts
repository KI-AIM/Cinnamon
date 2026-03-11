import { Pipe, PipeTransform } from '@angular/core';
import { FormatNumberOptions, StatisticsService } from "@shared/services/statistics.service";

/**
 * Formats values from the statistics into numbers.
 *
 * @author Daniel Preciado-Marquez
 */
@Pipe({
    name: 'formatNumber',
    standalone: false
})
export class FormatNumberPipe implements PipeTransform {

    public constructor(
        private readonly statisticsService: StatisticsService,
    ) {
    }

    public transform(value: number | string | null | undefined, options?: FormatNumberOptions): string {
        return this.statisticsService.formatNumber(value, options);
    }
}
