import { Component } from '@angular/core';
import { AlgorithmService } from "../../../../shared/services/algorithm.service";
import { RiskAssessmentService } from "../../services/risk-assessment.service";
import { TitleService } from "../../../../core/services/title-service.service";

@Component({
    selector: 'app-risk-assessment-configuration',
    templateUrl: './risk-assessment-configuration.component.html',
    styleUrls: ['./risk-assessment-configuration.component.less'],
    providers: [
        {
            provide: AlgorithmService,
            useExisting: RiskAssessmentService
        },
    ],
    standalone: false
})
export class RiskAssessmentConfigurationComponent {

    constructor(
        titleService: TitleService,
    ) {
        titleService.setPageTitle("Risk Assessment");
    }
}
