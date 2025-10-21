import { Component, Input } from '@angular/core';
import { UtilityMetricData2, UtilityMetricData3, UtilityMetricDataObject } from "@shared/model/statistics";

/**
 * Component for showing the utility metrics.
 *
 * @author Daniel Preciado-Marquez
 */
@Component({
    selector: 'app-data-inspection-utility',
    standalone: false,
    templateUrl: './data-inspection-utility.component.html',
    styleUrl: './data-inspection-utility.component.less'
})
export class DataInspectionUtilityComponent {

    /**
     * Utility metrics to be displayed.
     */
    @Input() public metrics: UtilityMetricDataObject;

    /**
     * If info elements like info icons or legend buttons should be shown.
     */
    @Input() public showInfo: boolean = true;

    protected readonly UtilityMetricData2 = UtilityMetricData2;
    protected readonly UtilityMetricData3 = UtilityMetricData3;
}
