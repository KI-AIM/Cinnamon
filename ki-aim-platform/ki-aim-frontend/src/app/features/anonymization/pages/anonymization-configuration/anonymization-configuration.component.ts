import { Component } from '@angular/core';
import { TitleService } from "../../../../core/services/title-service.service";

@Component({
    selector: 'app-anonymization-configuration',
    templateUrl: './anonymization-configuration.component.html',
    styleUrls: ['./anonymization-configuration.component.less'],
})
export class AnonymizationConfigurationComponent {

    constructor(
        private titleService: TitleService,
    ) {
        this.titleService.setPageTitle("Anonymization");
    }
}
