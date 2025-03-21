import { Component } from '@angular/core';
import { AlgorithmService } from "../../../../shared/services/algorithm.service";
import { TechnicalEvaluationService } from "../../services/technical-evaluation.service";
import { TitleService } from "../../../../core/services/title-service.service";

@Component({
    selector: 'app-technical-evaluation-configuration',
    templateUrl: './technical-evaluation-configuration.component.html',
    styleUrls: ['./technical-evaluation-configuration.component.less'],
    providers: [
        {
            provide: AlgorithmService,
            useExisting: TechnicalEvaluationService
        },
    ],
    standalone: false
})
export class TechnicalEvaluationConfigurationComponent {
    constructor(
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Technical Evaluation");
    }
}
