import { Component } from '@angular/core';
import { TitleService } from "../../../../core/services/title-service.service";
import { AnonymizationService } from "../../services/anonymization.service";
import { AlgorithmService } from "../../../../shared/services/algorithm.service";

@Component({
    selector: 'app-anonymization-configuration',
    templateUrl: './anonymization-configuration.component.html',
    styleUrls: ['./anonymization-configuration.component.less'],
    providers: [{
        provide: AlgorithmService,
        useClass: AnonymizationService
    }]
})
export class AnonymizationConfigurationComponent {

    constructor(
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Anonymization");
    }
}
