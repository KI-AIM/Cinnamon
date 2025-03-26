import { Component } from '@angular/core';
import { TitleService } from "../../../../core/services/title-service.service";
import { AlgorithmService } from "../../../../shared/services/algorithm.service";
import { SynthetizationService } from "../../services/synthetization.service";

@Component({
    selector: 'app-synthetization-configuration',
    templateUrl: './synthetization-configuration.component.html',
    styleUrls: ['./synthetization-configuration.component.less'],
    providers: [
        {
            provide: AlgorithmService,
            useExisting: SynthetizationService
        },
    ],
    standalone: false
})
export class SynthetizationConfigurationComponent {

    constructor(
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Synthetization");
    }
}
