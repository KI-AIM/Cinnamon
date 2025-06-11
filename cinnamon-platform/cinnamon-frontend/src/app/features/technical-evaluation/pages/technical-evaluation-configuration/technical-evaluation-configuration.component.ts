import { Component, OnInit } from "@angular/core";
import { Steps } from "@core/enums/steps";
import { TitleService } from "@core/services/title-service.service";
import { TechnicalEvaluationService } from "@features/technical-evaluation/services/technical-evaluation.service";
import { AlgorithmService, ConfigurationInfo } from "@shared/services/algorithm.service";
import { Observable } from "rxjs";

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
export class TechnicalEvaluationConfigurationComponent implements OnInit {
    protected readonly Steps = Steps;

    protected configurationInfo$: Observable<ConfigurationInfo>;

    constructor(
        private readonly technicalEvaluationService: TechnicalEvaluationService,
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Technical Evaluation");
    }

    public ngOnInit(): void {
        this.configurationInfo$ = this.technicalEvaluationService.fetchInfo();
    }
}
