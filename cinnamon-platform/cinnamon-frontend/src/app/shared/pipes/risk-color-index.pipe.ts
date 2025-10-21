import { Pipe, PipeTransform } from '@angular/core';

/**
 * Converts a risk score into the corresponding color index.
 *
 * @author Daniel Preciado-Marquez
 */
@Pipe({
  name: 'riskColorIndex',
  standalone: false
})
export class RiskColorIndexPipe implements PipeTransform {

    public transform(riskValue?: number): number {
        if (riskValue === undefined) return 0;
        return Math.min(Math.floor(riskValue * 10), 9) + 1;
    }

}
