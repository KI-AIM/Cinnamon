import { Component, OnInit } from "@angular/core";
import { TitleService } from "@core/services/title-service.service";
import { RiskAssessmentService } from "@features/risk-assessment/services/risk-assessment.service";
import { AlgorithmService, ConfigurationInfo } from "@shared/services/algorithm.service";
import { Observable } from "rxjs";

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
export class RiskAssessmentConfigurationComponent implements OnInit {

    protected configurationInfo$: Observable<ConfigurationInfo>;

    constructor(
        private readonly riskAssessmentService: RiskAssessmentService,
        titleService: TitleService,
    ) {
        titleService.setPageTitle("Risk Assessment");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.riskAssessmentService.fetchInfo();
    }
}
