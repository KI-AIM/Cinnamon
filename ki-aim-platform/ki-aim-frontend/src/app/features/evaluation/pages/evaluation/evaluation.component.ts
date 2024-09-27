import { Component } from '@angular/core';
import { EvaluationService } from "../../services/evaluation.service";
import { TitleService } from "../../../../core/services/title-service.service";
import { ProcessStatus } from "../../../../core/enums/process-status";

@Component({
    selector: 'app-evaluation',
    templateUrl: './evaluation.component.html',
    styleUrls: ['./evaluation.component.less'],
})
export class EvaluationComponent {
    protected readonly ProcessStatus = ProcessStatus;

    constructor(
        protected readonly evaluationService: EvaluationService,
        private readonly titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Evaluation");
    }

    ngOnInit() {
        this.evaluationService.fetchStatus();
    }
}
