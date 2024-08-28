import { Component } from '@angular/core';
import { TitleService } from "../../../../core/services/title-service.service";
import { AlgorithmService } from "../../../../shared/services/algorithm.service";
import { AnonymizationService } from "../../services/anonymization.service";

@Component({
    selector: 'app-anonymization-configuration',
    templateUrl: './anonymization-configuration.component.html',
    styleUrls: ['./anonymization-configuration.component.less'],
    providers: [
        {
            provide: AlgorithmService,
            useExisting: AnonymizationService
        },
    ]
})
export class AnonymizationConfigurationComponent {

    constructor(
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Anonymization");
    }
}
